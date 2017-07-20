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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.MediaController;

/**
 * Created by tfitch on 7/7/17.
 */

public class MusicController extends MediaController {

    public MusicController(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MusicController(Context context, boolean useFastForward) {
        super(context, useFastForward);
    }

    public MusicController(Context context) {
        super(context);
    }

    @Override
    public void show(int timeout) {
        super.show(0);
    }
}
