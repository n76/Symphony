/*
 *    Symphony
 *
 *    Copyright (C) 2018 Tod Fitch
 *
 *    This program is Free Software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as
 *    published by the Free Software Foundation, either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Inspired by https://stackoverflow.com/questions/11623994/example-using-androids-lrucache
 *
 * Theory of operation:
 * 1. A large music library may have thousands of "albums".
 * 2. The album artwork is not tracked by the Android media manager, so if we want to display
 *    it we need to open and parse each file.
 * 3. Between the potentially large number of albums and the time it takes to extract them,
 *    we should not perform that operation in the UI thread while building the spinner.
 *
 * Our approach is layered:
 * 1. We maintain "least recently used" (LRU) structures to track the most recent images we've
 *    extracted. And another to track a list of images that we were unable to extract.
 * 2. When we are asked to provide an image for a imageView we check to see if we have it (immediate
 *    completion) or if we've been unable to extract it in the past (also an immediate completion).
 * 3. If we don't have it and we haven't noted it as a problem image/file, then we will try to
 *    start a asynchronous task to extract the image. When the async task finishes we place the
 *    image in the originally provided imageView.
 * 4. However there can be problems if we start too many async tasks. Specifically a genre with a
 *    large number of albums/performances can use up all of our available open files leading to a
 *    crash.
 * 5. So we limit the number of async tasks running at any given time. But we don't want to discard
 *    a request to place an image in a imageView. So if we are unable to start a task we will queue
 *    the request in a deferred queue to be processed when the current async task(s) complete.
 */

package org.fitchfamily.android.symphony;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ImageLoader implements ComponentCallbacks2 {
    private static final String TAG = "Symphony:ImageLoader";
    private static final int CACHE_SIZE = 20;       // in Percent of application heap size
    private static final int MAX_ASYNC_TASKS = 1;   // Maximum number of concurrent image tasks

    private ImageLruCache cache;
    private LruCache<Long, Boolean> badArtwork;
    private Context mContext;
    private Drawable mAppIcon;

    /*
     * We want to limit the number of async tasks running at a time. If we don't
     * we will hog resources and may crash due to too many open files. So we use
     * a counter to track the number of active tasks.
     *
     * And we keep a queue of deferred images for when we are limiting the number
     * of async tasks.
     */
    private int mAsyncTaskCount;

    private class WorkItem {
        public long imageID;
        public ImageView imageView;

        WorkItem(long id, ImageView view) {
            imageID = id;
            imageView = view;
        }
    }

    private Queue<WorkItem> deferredQueue = new ConcurrentLinkedQueue<>();


    private class ImageLruCache extends LruCache<Long, Bitmap> {

        public ImageLruCache(int maxSize) {
            super(maxSize);
        }

        @Override
        protected int sizeOf(Long key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    }

    public ImageLoader(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        mContext = context;
        mAppIcon = mContext.getDrawable(R.drawable.ic_launcher_icon);

        /*
         *  Get the heap size for our application in MB and compute a
         *  safe size for our image cache. Image cache sizze is in KB
         */
        int maxKb;
        try {
            maxKb = am.getMemoryClass() * 1024;
        } catch (Exception e) {
            maxKb = 1024 * 1024 * 3;
            Log.d(TAG, "ImageLoader() Unable to get heap size: " + e.getMessage());
        }
        int limitKb = (maxKb * CACHE_SIZE) / 100;
        Log.d(TAG, "ImageLoader() - Application heap size: " + maxKb + " KB");
        Log.d(TAG, "ImageLoader() - Image cache size: " + limitKb + " KB");
        cache = new ImageLruCache(limitKb);
        badArtwork = new LruCache<>(1000);
        mAsyncTaskCount = 0;
    }

    public void loadImage(long id, ImageView imageview) {
        imageview.setImageDrawable(mAppIcon);

        Bitmap image = cache.get(id);
        if (image != null) {
            imageview.setImageBitmap(image);
        } else if (badArtwork.get(id) == null) {
            startBackgroundImageExtraction(imageview, id);
        } else
            Log.d(TAG, "display(" + id + ") marked as bad.");
    }

    /*
     *  Called from loadImage which is running in the UI thread. Since this
     *  and backgroundImageExtractionFinished() are in the same thread we
     *  don't need to worry about synchronizing them.
     */
    private void startBackgroundImageExtraction(ImageView imageview, long id) {
        if (mAsyncTaskCount < MAX_ASYNC_TASKS) {
            mAsyncTaskCount++;
            new SetImageTask(imageview).execute(id);
        } else {
            Log.d(TAG, "startBackgroundImageExtraction(): Too many tasks.");
            WorkItem work = new WorkItem(id, imageview);
            deferredQueue.offer(work);
        }
    }

    /*
     *  Called only from the async task's onPostExecute() method which runs in
     *  the UI thread.
     */
    private void backgroundImageExtractionFinished() {
        mAsyncTaskCount--;
        if (mAsyncTaskCount < 0)
            mAsyncTaskCount = 0;
        WorkItem myWork = deferredQueue.poll();
        while ((myWork != null) && (mAsyncTaskCount < MAX_ASYNC_TASKS)) {
            mAsyncTaskCount++;
            Log.d(TAG, "backgroundImageExtractionFinished(): Starting deferred task.");
            new SetImageTask(myWork.imageView).execute(myWork.imageID);
        }
        Log.d(TAG, "backgroundImageExtractionFinished(): Task count=" + mAsyncTaskCount);
    }

    private class SetImageTask extends AsyncTask<Long, Void, Integer> {
        private ImageView mImageView;
        private Bitmap bmp = null;

        public SetImageTask(ImageView imageview) {
            this.mImageView = imageview;
        }

        @Override
        protected Integer doInBackground(Long... params) {
            long id = params[0];
            try {
                bmp = getTrackArtwork(id);
                if (bmp != null) {
                    cache.put(id, bmp);
                } else {
                    Log.d(TAG, "doInBackground(" + id + ") - Unable to get artwork.");
                    badArtwork.put(id, true);
                    return 0;
                }
            } catch (Exception e) {
                e.printStackTrace();
                badArtwork.put(id, true);
                Log.d(TAG, "doInBackground(" + id + ") - Unable to get artwork.");
                return 0;
            }
            return 1;
        }

        /*
         *  onPostExecute runs in the UI thread, so we can update our image View in it.
         */
        @Override
        protected void onPostExecute(Integer result) {
            if ((result == 1) && (bmp != null)) {
                mImageView.setImageBitmap(bmp);
            }
            backgroundImageExtractionFinished();
            super.onPostExecute(result);
        }

        private Bitmap getTrackArtwork(long id) {
            Bitmap artwork = null;
            try {
                Uri trackUri = ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                if (trackUri != null) {
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    mmr.setDataSource(mContext, trackUri);

                    byte[] data = mmr.getEmbeddedPicture();
                    //coverart is an Imageview object

                    // convert the byte array to a bitmap
                    if (data != null)
                        artwork = BitmapFactory.decodeByteArray(data, 0, data.length);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting album artwork", e);
                artwork = null;
            }
            return artwork;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }

    @Override
    public void onLowMemory() {
    }

    @Override
    public void onTrimMemory(int level) {
        if (level >= TRIM_MEMORY_MODERATE) {
            cache.evictAll();
        } else if (level >= TRIM_MEMORY_BACKGROUND) {
            cache.trimToSize(cache.size() / 2);
        }
    }
}
