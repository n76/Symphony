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

import android.util.Log;

import java.util.ArrayList;

/**
 * Created by tfitch on 7/11/17.
 */

public class Album {
    private static final String TAG = "Symphony:Album";
    private String title;
    private long id;
    private int firstTrackIndex;
    private int lastTrackIndex;
    private long mImageId;

    private Album(long albumId,
                  String albumTitle,
                  long imageID,
                  int startTrack,
                  int endTrack) {
//       Log.d(TAG,"Album() entry.");
        id = albumId;
        title = albumTitle;
        this.mImageId = imageID;
        firstTrackIndex = startTrack;
        lastTrackIndex = endTrack;
    }

    public static ArrayList<Album> getAlbumIndexes(ArrayList<Song> songs) {
        Log.d(TAG, "getAlbumIndexes() entry.");
        ArrayList<Album> rslt = new ArrayList<>();

        long curAlbumId = 0;
        int albumStartTrack = -1;
        int albumEndTrack = -1;
        String albumTitle = "";
        long imageID = 0;

        for (int i = 0; i < songs.size(); i++) {
            Song s = songs.get(i);
            long nextAlbumId = s.getAlbumId();
            if (i == 0) {
                curAlbumId = nextAlbumId;
                albumStartTrack = i;
                albumEndTrack = i;
                albumTitle = s.getAlbum();
                imageID = s.getId();
            } else if (nextAlbumId != curAlbumId) {
                rslt.add(new Album(curAlbumId, albumTitle, imageID, albumStartTrack, albumEndTrack));
                curAlbumId = nextAlbumId;
                albumStartTrack = i;
                albumEndTrack = i;
                albumTitle = s.getAlbum();
                imageID = s.getId();
            } else {
                albumEndTrack = i;
            }
        }
        rslt.add(new Album(curAlbumId, albumTitle, imageID, albumStartTrack, albumEndTrack));
        return rslt;
    }

    public long getID() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getTrack() {
        return firstTrackIndex;
    }       // Index to first song/track in album

    public int getLastTrackIndex() {
        return lastTrackIndex;
    }

    public long getImageId() {
        return mImageId;
    }

    public String toString() {
        return this.title;
    }
}
