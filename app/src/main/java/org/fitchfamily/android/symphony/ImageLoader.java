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

package org.fitchfamily.android.symphony;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;
import android.content.res.Configuration;

public class ImageLoader implements ComponentCallbacks2 {
    private ImageLruCache cache;
    private Context mContext;

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
        int maxKb = am.getMemoryClass() * 1024;
        int limitKb = maxKb / 8; // 1/8th of total ram
        cache = new ImageLruCache(limitKb);
    }

    public void display(long id, ImageView imageview) {
        imageview.setImageResource(R.drawable.ic_launcher_icon);
        Bitmap image = cache.get(id);
        if (image != null) {
            imageview.setImageBitmap(image.copy(image.getConfig(),true));
        }
        else {
            new SetImageTask(imageview).execute(id);
        }
    }

    private class SetImageTask extends AsyncTask<Long, Void, Integer> {
        private ImageView mImageView;
        private Bitmap bmp;

        public SetImageTask(ImageView imageview) {
            this.mImageView = imageview;
        }

        @Override
        protected Integer doInBackground(Long... params) {
            long id = params[0];
            try {
                bmp = getBitmapTrack(id);
                if (bmp != null) {
                    cache.put(id, bmp);
                }
                else {
                    return 0;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return 0;
            }
            return 1;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == 1) {
                mImageView.setImageBitmap(bmp);
            }
            super.onPostExecute(result);
        }

        private Bitmap getBitmapTrack(long id) {
            Bitmap artwork = null;
            try {
                Uri trackUri = ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(mContext, trackUri);

                byte[] data = mmr.getEmbeddedPicture();
                //coverart is an Imageview object

                // convert the byte array to a bitmap
                if (data != null)
                    artwork = BitmapFactory.decodeByteArray(data, 0, data.length);
            } catch (Exception e) {
                Log.e("MUSIC SERVICE", "Error getting album artwork", e);
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