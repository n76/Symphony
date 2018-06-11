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

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;

/**
 * Created by tfitch on 7/11/17.
 */

public class Album {
    private String title;
    private long id;
    private int trackIndex;
    private long mImageId;

    private static final String TAG = "Symphony:Album";

    public Album(long albumId,
                 String albumTitle,
                 long imageID,
                 int songTrack) {
 //       Log.d(TAG,"Album() entry.");
        id=albumId;
        title=albumTitle;
        this.mImageId = imageID;
        trackIndex=songTrack;
    }

    public long getID(){return id;}
    public String getTitle(){return title;}
    public int getTrack(){return trackIndex;}       // Index to first song/track in album

    public static ArrayList<Album> getAlbumIndexes(ArrayList<Song> songs, Context context) {
        Log.d(TAG,"getAlbumIndexes() entry.");
        ArrayList<Album> rslt = new ArrayList<Album>();

        long   aId = 0;
        for (int i=0; i<songs.size(); i++) {
            if ((i == 0) || (songs.get(i).getAlbumId() != aId)) {
                Song s = songs.get(i);
                aId = s.getAlbumId();
                rslt.add(new Album(aId,s.getAlbum(),s.getId(),i));
            }
        }
        return rslt;
    }

    public long getImageId() {
        return mImageId;
    }

    public String toString() {
        return this.title;
    }
}
