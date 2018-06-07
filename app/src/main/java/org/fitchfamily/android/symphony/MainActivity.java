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
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.MediaController.MediaPlayerControl;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.Locale;

import org.fitchfamily.android.symphony.MusicService.MusicBinder;

@TargetApi(23)
public class MainActivity extends AppCompatActivity implements MediaPlayerControl {
    private static final String TAG = "Symphony:MainActivity";
    private static final int REQUEST_PERMISSION_STORAGE = 1234;

    private static final String SYMPHONY_PREFS_NAME = "SymphonyPrefsFile";
    private static final String SAVED_GENRE_NAME = "displayGenreName";
    private static final String SAVED_SHUFFLE_MODE = "shuffleMode";
    private static final String SAVED_TRACK_INDEX = "trackIndex";
    private static final String SAVED_TRACK_POSITION = "trackPosition";

    private ArrayList<Genre> genres;        // All information about all genres
    private int displayGenreId = -1;        // The genre the user is looking at
    private int playingGenreId = -1;        // The genre the service is playing

    private ArrayList<Album> currentDisplayAlbums;      // Albums in currently displayed genre.
    private ArrayList<Song> currentDisplayPlayList;     // Tracks/songs in genre currently being played
    private int displayTrackIndex;          // Index into play list of currently displayed track

    //
    // Values saved between instantiations
    //
    private int currentShuffleValue = MusicService.PLAY_SEQUENTIAL;
    private String playingGenreName = null;
    private int savedTrackIndex;
    private int savedTrackPosition;

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

    private ImageButton mPlayPauseButton;
    private SeekBar mSeekBar;
    private TextView mPlayingAlbum, mPlayingSong, mPlayingArtist, mDuration, mSongPosition;
    private ImageView mAlbumArt;

    //runs without a timer by reposting this handler at the end of the runnable
    Handler timerHandler = new Handler();
    Runnable timerRunnable;

    //
    // The service that actually does the playing
    private MusicService musicSrv = null;
    private Intent playIntent;

    private boolean paused=false;

    // Receive notifications about what the music service is playing
    private BroadcastReceiver servicePlayingUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String mAction = intent.getAction();
            Log.d(TAG, "servicePlayingUpdateReceiver.onReceive("+mAction+")");

            switch (mAction) {
                case MusicService.SERVICE_NOW_PLAYING:
                case MusicService.SERVICE_PAUSED:
                    currentlyPlaying = intent.getIntExtra("songIndex",0);
                    if (displayGenreId == playingGenreId) {
                        selectDisplayAlbum(currentlyPlaying);
                        songView.setSelection(currentlyPlaying);
                        updateControls(currentlyPlaying);
                    }
                    break;

                default:
                    Log.d(TAG, "servicePlayingUpdateReceiver.onReceive() Unknown action");
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

        servicePlayUpdateBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MusicService.SERVICE_NOW_PLAYING);
        intentFilter.addAction(MusicService.SERVICE_PAUSED);
        servicePlayUpdateBroadcastManager.registerReceiver(servicePlayingUpdateReceiver, intentFilter);

        restorePreferences();
        genres = new ArrayList<Genre>();
        currentDisplayPlayList = new ArrayList<Song>();
        currentDisplayAlbums = new ArrayList<Album>();

        setupDisplay();
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
        stopSeekTracking();
    }

    @Override
    protected void onResume(){
        super.onResume();
        Log.d(TAG, "onResume() entry.");
        if (musicSrv != null && musicSrv.hasTrack()) {
            if (paused) {
                paused = false;
                updateControls();
                if (isPlaying())
                    startSeekTracking();
            }
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop() entry.");
        savePreferences();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy() entry.");

        savePreferences();
        stopService(playIntent);
        if (musicSrv != null) {
            unbindService(musicConnection);
        }
        musicSrv=null;
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged() entry.");

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {

        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){

        }
        // Save current genre and track
        int savedTrackIndex = displayTrackIndex;
        int savedGenreIndex = displayGenreId;

        setupDisplay();

        // Put genre and track back to saved
        if (savedGenreIndex >= 0) {
            setDisplayGenre(savedGenreIndex);
            if (genreSpinner != null)
                genreSpinner.setSelection(savedGenreIndex);
            if (savedTrackIndex >= 0) {
                selectDisplayAlbum(savedTrackIndex);
                updateControls(savedTrackIndex);
                if (songView != null)
                    songView.setSelection(savedTrackIndex);
            }
        }
        if ((musicSrv != null) && musicSrv.hasTrack())
            updateControls();
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

            if (displayGenreId >= 0) {
                musicSrv.setList(genres.get(displayGenreId).getPlaylist());
                playingGenreId = displayGenreId;

                if (savedTrackIndex >= 0) {
                    musicSrv.setTrack(savedTrackIndex);
                    if (savedTrackPosition > 0)
                        musicSrv.seek(savedTrackPosition);
                }
            }


        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "musicConnection.onServiceDisconnected() entry.");
            musicSrv = null;
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
        displayTrackIndex = Integer.parseInt(view.getTag().toString());
        if (displayGenreId != playingGenreId) {
            musicSrv.setList(genres.get(displayGenreId).getPlaylist());
            playingGenreId = displayGenreId;
        }
        musicSrv.playTrack(displayTrackIndex);
        startSeekTracking();
        updateControls();
    }

    private void playNext(){
        musicSrv.playNext();
        startSeekTracking();
        updateControls();
    }

    private void playPrev(){
        Log.d(TAG,"playPrev() entry.");
        musicSrv.playPrev();
        startSeekTracking();
        updateControls();
    }

    // Media Controller methods

    @Override
    public void start() {
        Log.d(TAG, "start() Entry.");
        musicSrv.go();
        startSeekTracking();
    }

    @Override
    public void pause() {
        Log.d(TAG, "start() Entry.");
        musicSrv.pausePlayer();
        stopSeekTracking();
    }

    @Override
    public int getDuration() {
        if(musicSrv != null)
            return musicSrv.getDuration();
        Log.d(TAG, "getDuration() musicSrv="+(musicSrv!=null));
        return 0;
    }

    @Override
    public int getCurrentPosition() {
        // Log.d(TAG, "getCurrentPosition() Entry.");
        if(musicSrv != null)
            return musicSrv.getPosition();
        Log.d(TAG, "getCurrentPosition() musicSrv="+(musicSrv!=null));
        return 0;

    }

    @Override
    public void seekTo(int pos) {
        musicSrv.seek(pos);
    }

    @Override
    public boolean isPlaying() {
        if(musicSrv != null)
            return musicSrv.isPlaying();
        Log.d(TAG, "isPlaying() musicSrv="+(musicSrv!=null));
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


    public void stopOrPause(View v) {
        if (isPlaying())
            pause();
        else
            start();
    }

    public void seekBack(View v) {
        seekTo(Math.max(0,getCurrentPosition() - 5000));
        updateControls();
    }

    public void seekForward(View v) {
        seekTo(Math.min(getCurrentPosition() + 5000, getDuration()));
        updateControls();
    }

    public void skipBack(View v) {
        Log.d(TAG,"skipBack() entry.");
        playPrev();
    }

    public void skipForward(View v) {
        Log.d(TAG,"skipForward() entry.");
        playNext();
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

    private void setupDisplay() {
        setContentView(R.layout.main_activity);
        initToolBar();

        genreSpinner = (Spinner)findViewById(R.id.genre_select);
        albumSpinner = (Spinner)findViewById(R.id.album_select);
        songView = (ListView)findViewById(R.id.song_list);

        mSeekBar = (SeekBar) findViewById(R.id.seekTo);

        mPlayPauseButton = (ImageButton) findViewById(R.id.play_pause);

        mPlayingAlbum = (TextView) findViewById(R.id.playing_album);
        mPlayingSong = (TextView) findViewById(R.id.playing_song);
        mPlayingArtist = (TextView) findViewById(R.id.playing_artist);
        mDuration = (TextView) findViewById(R.id.duration);
        mSongPosition = (TextView) findViewById(R.id.song_position);
        mAlbumArt = (ImageView) findViewById(R.id.cover_art);

        songAdt = new SongAdapter(this, currentDisplayPlayList);
        songView.setAdapter(songAdt);

        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            setupGenreList();
        } else {
            Log.d(TAG, "onCreate(): Need permission to access storage.");
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION_STORAGE);
        }
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
                    displayTrackIndex = trackIndex;
                    songView.setSelection(trackIndex);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
            }
        });
        if (playingGenreName != null) {
            int genreIndex = getGenreIndex(playingGenreName);
            setDisplayGenre(genreIndex);
            genreSpinner.setSelection(genreIndex);
            if (savedTrackIndex >= 0) {
                selectDisplayAlbum(savedTrackIndex);
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
        shuffleSpinner.setSelection(currentShuffleValue);

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
        if (genres.size() == 0) {
            ContentResolver genreResolver = getContentResolver();
            Cursor genreCursor = genreResolver.query(genreUri, null, null, null, null);

            if (genreCursor != null && genreCursor.moveToFirst()) {
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

    private Song getSongInfo(int songIndex) {
        Log.d(TAG, "getSongInfo(" + songIndex + ") Entry.");
        if ((currentDisplayPlayList != null) && !currentDisplayPlayList.isEmpty()) {
            if (songIndex >= currentDisplayPlayList.size())
                songIndex = 0;
            displayTrackIndex = songIndex;
            return currentDisplayPlayList.get(songIndex);
        }
        return null;
    }

    private void selectDisplayAlbum(int songIndex) {
        Log.d(TAG, "selectDisplayAlbum("+songIndex+") Entry.");
        Song displaySong = getSongInfo(songIndex);
        if (displaySong != null) {
            String albumTitle = displaySong.getAlbum();

            for (int i = 0; i < currentDisplayAlbums.size(); i++) {

                if (currentDisplayAlbums.get(i).getTitle().compareTo(albumTitle) == 0) {
                    albumSpinner.setSelection(i);
                    return;
                }
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

    private void savePreferences() {
        Log.d(TAG, "savePreferences() Entry.");

        if (musicSrv != null) {
            SharedPreferences.Editor editor = getSharedPreferences(SYMPHONY_PREFS_NAME, MODE_PRIVATE).edit();
            if (displayGenreId >= 0)
                editor.putString(SAVED_GENRE_NAME, genres.get(displayGenreId).getName());

            editor.putInt(SAVED_SHUFFLE_MODE, currentShuffleValue);
            if (currentlyPlaying >= 0)
                editor.putInt(SAVED_TRACK_INDEX, currentlyPlaying);
            int trackPosition = musicSrv.getPosition();
            if (trackPosition > 0)
                editor.putInt(SAVED_TRACK_POSITION, trackPosition);
            editor.apply();
        }

    }

    private void restorePreferences() {
        Log.d(TAG, "restorePreferences() Entry.");
        SharedPreferences prefs = getSharedPreferences(SYMPHONY_PREFS_NAME, MODE_PRIVATE);
        playingGenreName = prefs.getString(SAVED_GENRE_NAME, null);
        currentShuffleValue = prefs.getInt(SAVED_SHUFFLE_MODE, MusicService.PLAY_SEQUENTIAL);
        savedTrackIndex = prefs.getInt(SAVED_TRACK_INDEX, -1);
        savedTrackPosition = prefs.getInt(SAVED_TRACK_POSITION, -1);
    }

    private void updateControls(int trackIndex) {
        updatePlayingStatus();
        updateSeekBar();
        updateCurrentTrackInfo(trackIndex);
    }

    private void updateControls() {
        updateControls(currentlyPlaying);
    }

    private void updatePlayingStatus() {
        int drawable = R.drawable.ic_playback_start;
        if (isPlaying())
            drawable = R.drawable.ic_playback_pause;
        mPlayPauseButton.setImageResource(drawable);
    }

    private synchronized void updateSeekBar() {
        Log.d(TAG, "updateSeekBar() Entry.");
        int duration = getDuration();
        mSeekBar.setMax(duration);
        mDuration.setText(formatDuration(duration));

        int currPos = getCurrentPosition();
        mSongPosition.setText(formatDuration(currPos));
        mSeekBar.setProgress(currPos);
    }

    private void updateCurrentTrackInfo(int trackIndex) {
        Song displaySong = getSongInfo(trackIndex);
        Bitmap albumArt = null;

        if (displaySong != null) {
            mPlayingAlbum.setText(displaySong.getAlbum());
            mPlayingSong.setText(displaySong.getTitle());
            mPlayingArtist.setText(displaySong.getArtist());
            Uri trackUri = ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    displaySong.getId());
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(getApplicationContext(), trackUri);

            byte [] data = mmr.getEmbeddedPicture();
            //coverart is an Imageview object

            // convert the byte array to a bitmap
            if(data != null)
            {
                try {
                    albumArt = BitmapFactory.decodeByteArray(data, 0, data.length);
                } catch (Exception e) {
                    Log.e("MUSIC SERVICE", "Error getting album artwork", e);
                    albumArt = null;
                }
            }
        } else {
            mPlayingAlbum.setText("");
            mPlayingSong.setText("");
            mPlayingArtist.setText("");
        }
        if (albumArt == null)
            albumArt = BitmapFactory.decodeResource(getResources(), R.drawable.violin_icon);
        mAlbumArt.setImageBitmap(albumArt);
    }

    private static String formatDuration(int duration) {
        return String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(duration),
                TimeUnit.MILLISECONDS.toSeconds(duration) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)));
    }

    private void startSeekTracking() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "timerRunnable.run() entry");
                updateSeekBar();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopSeekTracking() {
        timerHandler.removeCallbacks(timerRunnable);
    }
}
