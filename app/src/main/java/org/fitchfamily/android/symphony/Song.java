/*
 *    Symphony
 *
 *    Copyright (C) 2017, 2018 Tod Fitch
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

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * Created by tfitch on 7/5/17.
 */

public class Song {
    private long id;
    private String album;
    private String albumSortTitle;
    private long albumId;
    private String title;
    private String composer;
    private String artist;
    private int track;

    public Song(long songId,
                String songTitle,
                String songArtist,
                String songAlbum,
                String songAlbumSortName,
                long songAlbumId,
                String songComposer,
                int songTrack) {
        id = songId;
        if (songTitle != null)
            title = songTitle.trim();
        if (songArtist != null)
            artist = songArtist.trim();
        if (songAlbum != null)
            album = songAlbum.trim();
        if (songAlbumSortName != null)
            albumSortTitle = songAlbumSortName;
        albumId = songAlbumId;
        if (songComposer != null)
            composer = songComposer.trim();
        track = songTrack;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public String getAlbumSortTitle() {
        return albumSortTitle;
    }

    public long getAlbumId() {
        return albumId;
    }

    public String getComposer() {
        return composer;
    }

    public int getTrack() {
        return track;
    }

    public Bitmap getArtwork(Context mContext) {
        Bitmap artwork = null;
        try {
            Uri trackUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(mContext, trackUri);

            byte[] data = mmr.getEmbeddedPicture();

            // convert the byte array to a bitmap
            if (data != null)
                artwork = BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) {
            Log.e("MUSIC SERVICE", "Error getting album artwork", e);
            artwork = null;
        }
        if (artwork == null) {
            Drawable drawable = ContextCompat.getDrawable(mContext, R.drawable.ic_launcher_icon);
            if (drawable != null) {
                artwork = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                        drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(artwork);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }
        }
        return artwork;
    }
}
