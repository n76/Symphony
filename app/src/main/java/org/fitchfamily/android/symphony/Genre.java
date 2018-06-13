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

import java.util.ArrayList;

/**
 * Created by tfitch on 7/13/17.
 */

public class Genre {
    private long id;
    private String name;
    private ArrayList<Song> playlist;

    public Genre(long genreId, String genreName) {
        id = genreId;
        name = genreName;
        playlist = null;
    }

    public long getId() {
        return this.id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public ArrayList<Song> getPlaylist() {
        return playlist;
    }

    public void setPlaylist(ArrayList<Song> playlist) {
        this.playlist = playlist;
    }

}
