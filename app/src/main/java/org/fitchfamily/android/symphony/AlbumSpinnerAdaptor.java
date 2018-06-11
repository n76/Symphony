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
 *  Inspired, in part, by https://www.codingdemos.com/android-custom-spinner-images-text/
 */
package org.fitchfamily.android.symphony;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class AlbumSpinnerAdaptor extends ArrayAdapter {
    private static final String TAG = "Symphony:SpinnerAdaptor";

    public AlbumSpinnerAdaptor(@NonNull Context context, int resource) {
        super(context, resource);
    }

    private ArrayList<Album> mAlbums;
    private Context mContext;
    private ImageLoader mImageLoader;

    private static class ViewHolder {
        ImageView mImage;
        TextView mName;
    }

    public AlbumSpinnerAdaptor(@NonNull Context context, ArrayList<Album> albums, ImageLoader loader) {
        super(context, R.layout.album_spinner_row);
        this.mAlbums = albums;
        this.mContext = context;
        this.mImageLoader = loader;
    }

    @Override
    public int getCount() {
        return mAlbums.size();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder mViewHolder = new ViewHolder();
        if (convertView == null) {
            LayoutInflater mInflater = (LayoutInflater) mContext.
                    getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = mInflater.inflate(R.layout.album_spinner_row, parent, false);
            mViewHolder.mImage = (ImageView) convertView.findViewById(R.id.album_spinner_image);
            mViewHolder.mName = (TextView) convertView.findViewById(R.id.album_spinner_text);
            convertView.setTag(mViewHolder);
        } else {
            //Log.d(TAG,"getView() - Recycling view");
            mViewHolder = (ViewHolder) convertView.getTag();
        }

        try {
            Album a = mAlbums.get(position);
            mViewHolder.mName.setText(a.getTitle());
            mImageLoader.loadImage(a.getImageId(),mViewHolder.mImage);
        } catch (Exception e) {
            mViewHolder.mName.setText("Unknown");
            mViewHolder.mImage.setImageResource(R.drawable.ic_launcher_icon);
        }
        return convertView;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return getView(position, convertView, parent);
    }
}
