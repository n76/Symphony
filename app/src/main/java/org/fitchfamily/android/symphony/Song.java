/*
 *    Symphony
 *
 *    Copyright (C) 2017 Tod Fitch
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

/**
 * Created by tfitch on 7/5/17.
 */

public class Song {
    private long id;
    private String album;
    private long albumId;
    private String title;
    private String composer;
    private String artist;
    private int track;

    public Song(long songId,
                String songTitle,
                String songArtist,
                String songAlbum,
                long songAlbumId,
                String songComposer,
                int songTrack) {
        id=songId;
        title=songTitle;
        artist=songArtist;
        album = songAlbum;
        albumId = songAlbumId;
        composer = songComposer;
        track = songTrack;
    }

    public long getId(){return id;}
    public String getTitle(){return title;}
    public String getArtist(){return artist;}
    public String getAlbum(){return album;}
    public long getAlbumId(){return albumId;}
    public String getComposer(){return composer;}
    public int getTrack(){return track;}

}
