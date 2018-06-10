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
 * Largely inspired by https://stackoverflow.com/questions/11623994/example-using-androids-lrucache
 */

package org.fitchfamily.android.symphony;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;
import android.content.res.Configuration;

public class ImageLoader implements ComponentCallbacks2 {
    private static final String TAG = "Symphony:ImageLoader";

    private static final long MAX_TASKS = 20;

    private ImageLruCache cache;
    private LruCache<Long,Boolean> badArtwork;
    private Context mContext;
    private Drawable mAppIcon;
    private int mAsyncTaskCount;

    private class ImageLruCache extends LruCache<Long, Bitmap> {

        public ImageLruCache(int maxSize) {
            super(maxSize);
        }

        @Override
        protected int sizeOf(Long key, Bitmap value) {
            int kbOfBitmap = value.getByteCount() / 1024;
            return kbOfBitmap;
        }
    }

    public ImageLoader(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        mContext = context;
        mAppIcon = mContext.getDrawable(R.drawable.ic_launcher_icon);
        int maxKb = am.getMemoryClass() * 1024;
        int limitKb = maxKb / 8; // 1/8th of total ram
        cache = new ImageLruCache(limitKb);
        badArtwork = new LruCache<>(1000);
        mAsyncTaskCount = 0;
    }

    public void display(long id, ImageView imageview) {
        imageview.setImageDrawable(mAppIcon);

        Bitmap image = cache.get(id);
        if (image != null) {
            imageview.setImageBitmap(image);
        }
        else if (badArtwork.get(id) == null) {
            startBackgroundImageExtraction(imageview, id);
        } else
            Log.d(TAG, "display(" + id + ") marked as bad.");
    }

    private synchronized void startBackgroundImageExtraction(ImageView imageview, long id) {
        if (mAsyncTaskCount <= MAX_TASKS) {
            mAsyncTaskCount++;
            new SetImageTask(imageview).execute(id);
        } else {
            Log.d(TAG, "startBackgroundImageExtraction(): Too many tasks.");
        }
    }

    private synchronized void backgroundImageExtractionFinished() {
        mAsyncTaskCount--;
        if (mAsyncTaskCount < 0)
            mAsyncTaskCount = 0;
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
                }
                else {
                    Log.d(TAG, "doInBackground("+id+") - Unable to get artwork.");
                    badArtwork.put(id,true);
                    return 0;
                }
            } catch (Exception e) {
                e.printStackTrace();
                badArtwork.put(id,true);
                Log.d(TAG, "doInBackground("+id+") - Unable to get artwork.");
                return 0;
            }
            return 1;
        }

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
        }
        else if (level >= TRIM_MEMORY_BACKGROUND) {
            cache.trimToSize(cache.size() / 2);
        }
    }
}