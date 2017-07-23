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

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.MediaController.MediaPlayerControl;
import android.widget.Spinner;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;

import org.fitchfamily.android.symphony.MusicService.MusicBinder;

@TargetApi(23)
public class MainActivity extends AppCompatActivity implements MediaPlayerControl {
    private static final String TAG = "Symphony:MainActivity";
    private static final int REQUEST_PERMISSION_STORAGE = 1234;

    private static final String SYMPHONY_PREFS_NAME = "SymphonyPrefsFile";
    private static final String SAVED_GENRE_NAME = "displayGenreName";
    private static final String SAVED_SHUFFLE_MODE = "shuffleMode";
    private static final String SAVED_TRACK_INDEX = "trackIndex";

    private ArrayList<Genre> genres;        // All information about all genres
    private int displayGenreId = -1;        // The genre the user is looking at
    private int playingGenreId = -1;        // The genre the service is playing

    private ArrayList<Album> currentDisplayAlbums;      // Albums in currently displayed genre.
    private ArrayList<Song> currentDisplayPlayList;     // Tracks/songs in genre currently being played

    //
    // Values saved between instantiations
    //
    private int currentShuffleValue = MusicService.PLAY_SEQUENTIAL;
    private String genreName = null;
    private int savedTrackIndex;

    //
    // View and display related
    //
    // Toolbar
    private Toolbar toolbar;
    private Spinner shuffleSpinner;

    // Viewing songs
    private SongAdapter songAdt;
    private ListView songView;
    private int currentlyPlaying;               // Song/track most recently reported as playing by service.

    // Viewing Genres
    private Spinner genreSpinner;
    private ArrayAdapter<Genre> genreAdaptor;

    // Viewing Albums
    private Spinner albumSpinner;
    private ArrayAdapter<Genre> albumAdaptor;

    // Controlling currently playing song/track
    private MediaController controller;

    //
    // The service that actually does the playing
    private MusicService musicSrv = null;
    private Intent playIntent;
    private boolean musicBound;

    private boolean paused=false;
    private boolean playbackPaused=false;

    // Receive notifications about what the music service is playing
    private BroadcastReceiver servicePlayingUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "servicePlayingUpdateReceiver.onReceive()");
            switch (intent.getAction()) {
                case MusicService.SERVICE_NOW_PLAYING:
                    Log.d(TAG, "servicePlayingUpdateReceiver.onReceive().SERVICE_NOW_PLAYING");
                    currentlyPlaying = intent.getIntExtra("songIndex",0);
                    if (controller != null)
                        controller.show();
                    if (displayGenreId == playingGenreId) {
                        selectDisplayAlbum(currentlyPlaying);
                        songView.setSelection(currentlyPlaying);
                    }
                    break;

                case MusicService.SERVICE_PAUSED:
                    Log.d(TAG, "servicePlayingUpdateReceiver.onReceive().SERVICE_PAUSED");
                    if (controller != null) {
                        controller.show();
                        // FIXME: somehow tell controller to show paused
                    }
                    break;
            }
        }
    };
    LocalBroadcastManager servicePlayUpdateBroadcastManager;

    //
    // "Normal" methods to override on any activity
    //
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate() entry.");

        restorePreferences();

        setContentView(R.layout.main_activity);
        initToolBar();

        genres = new ArrayList<Genre>();
        currentDisplayPlayList = new ArrayList<Song>();
        currentDisplayAlbums = new ArrayList<Album>();

        genreSpinner = (Spinner)findViewById(R.id.genre_select);
        albumSpinner = (Spinner)findViewById(R.id.album_select);
        songView = (ListView)findViewById(R.id.song_list);;

        songAdt = new SongAdapter(this, currentDisplayPlayList);
        songView.setAdapter(songAdt);
        setController();

        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            setupGenreList();
        } else {
            Log.d(TAG, "onCreate(): Need permission to access storage.");
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION_STORAGE);
        }
        servicePlayUpdateBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MusicService.SERVICE_NOW_PLAYING);
        intentFilter.addAction(MusicService.SERVICE_PAUSED);
        servicePlayUpdateBroadcastManager.registerReceiver(servicePlayingUpdateReceiver, intentFilter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart() entry.");

        if(playIntent==null){
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        Log.d(TAG, "onPause() entry.");
        paused=true;
    }

    @Override
    protected void onResume(){
        super.onResume();
        Log.d(TAG, "onResume() entry.");
        if (musicSrv != null && musicSrv.isPlaying()) {
            if (paused) {
                paused = false;
                controller.show();
            }
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop() entry.");
        controller.hide();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy() entry.");

        savePreferences();
        stopService(playIntent);
        if ((controller != null) && controller.isShowing())
            controller.hide();
        controller = null;
        if (musicBound) {
            unbindService(musicConnection);
        }
        musicBound = false;
        musicSrv=null;
        super.onDestroy();
    }

    //
    // Capture back key press and rather than exit, go to background
    //
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        switch(keyCode)
        {
            case KeyEvent.KEYCODE_BACK:
                moveTaskToBack(true);
                return true;
        }
        return super.onKeyDown(keyCode,event);
    }

    //
    // End of Activity method overrides
    //


    //
    // Create and bind to our background music service
    //
    private ServiceConnection musicConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "musicConnection.onServiceConnected() entry.");
            MusicBinder binder = (MusicBinder)service;
            //get service
            musicSrv = binder.getService();
            musicSrv.setShuffle(currentShuffleValue);
            shuffleSpinner.setSelection(currentShuffleValue);
            musicBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "musicConnection.onServiceDisconnected() entry.");
            musicBound = false;
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult() entry.");
        for (int i=0; i<permissions.length; i++) {
            int rslt = -999;
            if (grantResults.length > i)
                rslt = grantResults[i];
            Log.d(TAG, "onRequestPermissionsResult[" + permissions[i] +"] = " + rslt);
        }
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switch (requestCode) {
                    case REQUEST_PERMISSION_STORAGE: {
                        Log.d(TAG, "onActivityResult() EXT_STORE_REQUEST GRANTED");
                        setupGenreList();
                        return;
                    }
                }
            } else {
                Log.d(TAG, "onActivityResult() Request denied.");
            }
        } else {
            Log.d(TAG, "onActivityResult() Request canceled.");
        }
    }

    public void songPicked(View view){
        // Note to self: song.xml in layouts specifies this method to be called when
        // a song is touched.
        Log.d(TAG, "songPicked() entry. Selected item = "+ view.toString());
        int selectedItem = Integer.parseInt(view.getTag().toString());
        Log.d(TAG, "songPicked() selected= "+ selectedItem);
        if (displayGenreId != playingGenreId) {
            musicSrv.setList(genres.get(displayGenreId).getPlaylist());
            playingGenreId = displayGenreId;
        }
        musicSrv.playTrack(Integer.parseInt(view.getTag().toString()));
        if(playbackPaused){
            setController();
            playbackPaused=false;
        }
        controller.show(0);
    }

    //
    // Music/media controller setup and call back methods
    //
    // SetController sets it up and anchors it to a place on
    // the screen. We set up the play previous and play next
    // call backs here too.
    //
    private void setController(){
        //set the controller up
        controller = new MusicController(this);

        controller.setPrevNextListeners(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNext();
            }
        }, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPrev();
            }
        });

        controller.setMediaPlayer(this);
        controller.setAnchorView(findViewById(R.id.song_list));
        controller.setEnabled(true);
    }

    private void playNext(){
        musicSrv.playNext();
        if(playbackPaused){
            playbackPaused=false;
            controller.show();
        }
        controller.show();
    }

    private void playPrev(){
        musicSrv.playPrev();
        if(playbackPaused){
            playbackPaused=false;
            controller.show();
        }
        controller.show();
    }

    // Media Controller methods

    @Override
    public void start() {
        Log.d(TAG, "start() Entry.");
        playbackPaused = false;
        musicSrv.go();
    }

    @Override
    public void pause() {
        Log.d(TAG, "start() Entry.");
        playbackPaused=true;
        musicSrv.pausePlayer();
    }

    @Override
    public int getDuration() {
        if(musicSrv!=null && musicBound && !playbackPaused)
            return musicSrv.getDuration();
        Log.d(TAG, "getDuration() musicSrv="+(musicSrv!=null));
        Log.d(TAG, "getDuration() musicBound="+musicBound);
        return 0;
    }

    @Override
    public int getCurrentPosition() {
        // Log.d(TAG, "getCurrentPosition() Entry.");
        if(musicSrv!=null && musicBound && !playbackPaused)
            return musicSrv.getPosition();
        Log.d(TAG, "getCurrentPosition() musicSrv="+(musicSrv!=null));
        Log.d(TAG, "getCurrentPosition() musicBound="+musicBound);
        return 0;

    }

    @Override
    public void seekTo(int pos) {
        musicSrv.seek(pos);
    }

    @Override
    public boolean isPlaying() {
        if(musicSrv!=null && musicBound)
            return musicSrv.isPlaying();
        Log.d(TAG, "isPlaying() musicSrv="+(musicSrv!=null));
        Log.d(TAG, "isPlaying() musicBound="+musicBound);
        return false;
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    //
    // Sorting by name should ignore 'The ' and 'A ' prefixes.
    //
    // Rather than hardcode a language specific set of prefixes,
    // get them from a string resource that can be easily extended and
    // internationalized.
    //
    // Note that trailing spaces are dropped in XML so we a one here.
    //
    public String genSortTitle(String a) {
        Resources res = getResources();
        String[] prefixes = res.getStringArray(R.array.ignore_prefixes);
        String a1 = a.trim();

        for (String s : prefixes) {
            String s1 = s + " ";
            if (a1.startsWith(s1))
                return a1.substring(s1.length()).trim();
        }
        return a1;
    }

    private void setupGenreList() {
        Log.d(TAG, "setupGenreList() Entry.");
        getGenreList();

        genreAdaptor = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genres);
        genreSpinner.setAdapter(genreAdaptor);
        genreSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> adapter, View v,
                                       int position, long id) {
                // On selecting a spinner item
                Log.d(TAG,"genreSpinner.setOnItemSelectedListener.onItemSelected("+position+")");
                setDisplayGenre(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
            }
        });

        albumAdaptor = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, currentDisplayAlbums);
        albumSpinner.setAdapter(albumAdaptor);
        albumSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> adapter, View v,
                                       int position, long id) {
                // On selecting a spinner item
                Log.d(TAG,"albumSpinner.setOnItemSelectedListener.onItemSelected("+position+")");
                Album selectedAlbum = currentDisplayAlbums.get(position);
                if (selectedAlbum != null) {
                    // Selected album has changed. If we are currently playing a track on
                    // this album, then select that track. Otherwise select the first track
                    // on the album.
                    int trackIndex = selectedAlbum.getTrack();
                    if (displayGenreId == playingGenreId) {
                        int lastTrackIndex = currentDisplayPlayList.size();
                        int lastAlbumIndex = currentDisplayAlbums.size();
                        if (position+1 < lastAlbumIndex) {
                            Album nextAlbum = currentDisplayAlbums.get(position + 1);
                            if (nextAlbum != null) {
                                lastTrackIndex = nextAlbum.getTrack() - 1;
                            }
                        }
                        if ((currentlyPlaying > trackIndex) && (currentlyPlaying <= lastTrackIndex))
                            trackIndex = currentlyPlaying;
                    }
                    songView.setSelection(trackIndex);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
            }
        });
        if (genreName != null) {
            int genreIndex = getGenreIndex(genreName);
            setDisplayGenre(genreIndex);
            genreSpinner.setSelection(genreIndex);
            if (savedTrackIndex >= 0) {
                selectDisplayAlbum(savedTrackIndex);
                songView.setSelection(savedTrackIndex);
            }
        }
    }

    //
    //  Local utility routines
    //
    private void initToolBar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        shuffleSpinner = (Spinner)findViewById(R.id.shuffle_select);

        // toolbar.setTitle(R.string.toolbarTitle);
        setSupportActionBar(toolbar);

        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(this,R.array.play_select,android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shuffleSpinner.setAdapter(adapter);

        shuffleSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> adapter, View v,
                                       int position, long id) {
                // On selecting a spinner item
                Log.d(TAG,"shuffleSpinner.setOnItemSelectedListener.onItemSelected("+position+")");
                switch (position) {
                    case 0:
                        currentShuffleValue = MusicService.PLAY_SEQUENTIAL;
                        break;
                    case 1:
                        currentShuffleValue = MusicService.PLAY_RANDOM_SONG;
                        break;
                    case 2:
                        currentShuffleValue = MusicService.PLAY_RANDOM_ALBUM;
                        break;
                }
                if (musicSrv != null) {
                    musicSrv.setShuffle(currentShuffleValue);
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
            }
        });
    }


    private void getGenreList() {
        final Uri genreUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;

        Log.d(TAG, "getGenreList() entry");
        ContentResolver genreResolver = getContentResolver();
        Cursor genreCursor = genreResolver.query(genreUri, null, null, null, null);

        if (genreCursor!= null && genreCursor.moveToFirst()) {
            int idColumn = genreCursor.getColumnIndex(MediaStore.Audio.Genres._ID);
            int nameColumn = genreCursor.getColumnIndex(MediaStore.Audio.Genres.NAME);

            do {
                genres.add(new Genre(
                        genreCursor.getLong(idColumn),
                        genreCursor.getString(nameColumn)
                ));
            } while (genreCursor.moveToNext());
        }
        Collections.sort(genres, new Comparator<Genre>() {
            @Override
            public int compare(Genre o1, Genre o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
    }

    private ArrayList<Song> getGenreSongs(long genreId) {
        Log.d(TAG, "getGenreSongs() entry");

        ArrayList<Song> rsltPlayList = new ArrayList<Song>();
        final Uri musicUri = MediaStore.Audio.Genres.Members.getContentUri("external",genreId);

        ContentResolver musicResolver = getContentResolver();
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

        if(musicCursor!=null && musicCursor.moveToFirst()){
            //get columns
            int idColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media._ID);
            int albumColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.ALBUM);
            int albumIdColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.ALBUM_ID);
            int titleColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media.TITLE);
            int composerColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.COMPOSER);
            int artistColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media.ARTIST);
            int trackColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.TRACK);
            //add songs to list
            do {
                // Log.d(TAG, "getGenreSongs(): Adding '"+musicCursor.getString(titleColumn)+"' ("+musicCursor.getString(albumColumn)+")");
                rsltPlayList.add(new Song(musicCursor.getLong(idColumn),
                        musicCursor.getString(titleColumn),
                        musicCursor.getString(artistColumn),
                        musicCursor.getString(albumColumn),
                        genSortTitle(musicCursor.getString((albumColumn))),
                        musicCursor.getLong(albumIdColumn),
                        musicCursor.getString(composerColumn),
                        musicCursor.getInt(trackColumn)));
            }
            while (musicCursor.moveToNext());
        }
        Collections.sort(rsltPlayList, new Comparator<Song>(){
            public int compare(Song a, Song b){
                // Sort by album title. If two titles the same, differentiate by ID.
                // Within album, sort by track. If two tracks claim the same position
                // differentiate by title.

                int rslt = a.getAlbumSortTitle().compareTo(b.getAlbumSortTitle());
                if (rslt == 0)
                    rslt = (int)(a.getAlbumId() - b.getAlbumId());
                if (rslt == 0)
                    rslt = a.getTrack() - b.getTrack();
                if (rslt == 0)
                    rslt = a.getTitle().compareTo(b.getTitle());
                return rslt;
            }
        });
        return rsltPlayList;
    }

    // Display the genre indicated by the position in the genre list.
    //
    // If the playlist for the genre is empty, then create it and the
    // album start index array.
    //
    // The actual display needs to be consistent, so when we set the
    // genre we need to set the album list and the song list for the genre.
    //
    private void setDisplayGenre(int Position) {
        Log.d(TAG, "setDisplayGenre("+Position+") Entry.");
        Genre selectedGenre = genres.get(Position);
        ArrayList<Song> genrePlaylist;

        if ((selectedGenre != null) && (displayGenreId != Position)) {

            // If this is the first time the genre has been selected then
            // the play list will be undefined. So build the list on first
            // use.
            genrePlaylist = selectedGenre.getPlaylist();
            if (genrePlaylist == null) {
                genrePlaylist = getGenreSongs(selectedGenre.getId());
                selectedGenre.setPlaylist(genrePlaylist);
            }
            currentDisplayPlayList.clear();
            currentDisplayPlayList.addAll(genrePlaylist);

            ArrayList<Album> genreAlbums = Album.getAlbumIndexes(genrePlaylist);

            currentDisplayAlbums.clear();
            if (genreAlbums != null)
                currentDisplayAlbums.addAll(genreAlbums);
            if (albumAdaptor != null)
                albumAdaptor.notifyDataSetChanged();

            displayGenreId = Position;
            songAdt.notifyDataSetChanged();

            //
            // If we are changing to the genre that is currently playing
            // then select the track and track album currently playing.
            // Otherwise select the first track (and its album) in the genre.
            //
            int songDisplayIndex = 0;
            if (displayGenreId == playingGenreId)
                songDisplayIndex = currentlyPlaying;
            selectDisplayAlbum(songDisplayIndex);
            songView.setSelection(songDisplayIndex);
        }
    }

    private void selectDisplayAlbum(int songIndex) {
        Log.d(TAG, "selectDisplayAlbum("+songIndex+") Entry.");
        if (songIndex >= currentDisplayPlayList.size())
            songIndex = 0;
        Song displaySong = currentDisplayPlayList.get(songIndex);
        String albumTitle = displaySong.getAlbum();

        for (int i=0; i<currentDisplayAlbums.size(); i++) {

            if (currentDisplayAlbums.get(i).getTitle().compareTo(albumTitle) == 0) {
                albumSpinner.setSelection(i);
                return;
            }
        }
    }

    private int getGenreIndex(String name) {
        Log.d(TAG, "getGenreIndex('"+name+"') Entry.");
        if (genres != null) {
            for (int i = 0; i < genres.size(); i++) {
                if (genres.get(i).getName().compareTo(name) == 0) {
                    Log.d(TAG, "getGenreIndex('"+name+"') is index " + i);
                    return i;
                }
            }
        }
        Log.d(TAG, "getGenreIndex(): No match for genre name.");
        return 0;
    }

    private int getTrackIndex(String songName) {
        Log.d(TAG, "getGenreIndex('"+songName+"') Entry.");
        if (currentDisplayPlayList != null) {
            for (int i=0; i < currentDisplayPlayList.size(); i++) {
                if (currentDisplayPlayList.get(i).getTitle().compareTo(songName) == 0)
                    return i;
            }
        }
        return 0;
    }

    private void savePreferences() {
        Log.d(TAG, "savePreferences() Entry.");
        SharedPreferences.Editor editor = getSharedPreferences(SYMPHONY_PREFS_NAME, MODE_PRIVATE).edit();
        int genreId = playingGenreId;
        if (genreId < 0)
            genreId = displayGenreId;
        if (genreId >= 0)
            editor.putString(SAVED_GENRE_NAME, genres.get(genreId).getName());
        editor.putInt(SAVED_SHUFFLE_MODE, currentShuffleValue);
        if (currentlyPlaying >= 0)
            editor.putInt(SAVED_TRACK_INDEX, currentlyPlaying);

        editor.apply();

    }

    private void restorePreferences() {
        Log.d(TAG, "restorePreferences() Entry.");
        SharedPreferences prefs = getSharedPreferences(SYMPHONY_PREFS_NAME, MODE_PRIVATE);
        genreName = prefs.getString(SAVED_GENRE_NAME, null);
        currentShuffleValue = prefs.getInt(SAVED_SHUFFLE_MODE, MusicService.PLAY_SEQUENTIAL);
        savedTrackIndex = prefs.getInt(SAVED_TRACK_INDEX, 0);
    }
}
