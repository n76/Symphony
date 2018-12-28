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

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.MediaController.MediaPlayerControl;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.fitchfamily.android.symphony.MusicService.MusicBinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

@TargetApi(23)
public class MainActivity extends AppCompatActivity implements MediaPlayerControl {
    private static final String TAG = "Symphony:MainActivity";
    private static final int REQUEST_PERMISSION_STORAGE = 1234;

    private static final String SYMPHONY_PREFS_NAME = "SymphonyPrefsFile";
    private static final String SAVED_GENRE_NAME = "displayGenreName";
    private static final String SAVED_SHUFFLE_MODE = "shuffleMode";
    private static final String SAVED_TRACK_INDEX = "trackIndex";
    private static final String SAVED_TRACK_POSITION = "trackPosition";

    private ArrayList<Genre> genres;                    // All information about all genres
    private ArrayList<Album> currentDisplayAlbums;      // Albums in currently displayed genre.
    private ArrayList<Song> currentDisplayPlayList;     // Tracks/songs in genre currently being played

    private ImageLoader mImageLoader;                   // LRU cache/background image loader

    //
    // Information to save display or playing state information
    //
    private class PlayInfo {
        protected String genreName;        // Name of playing/display genre
        protected int trackId;             // ID of the playing/display track
        protected int position;            // Play position of the track.
        protected int shuffle;             // Current shuffle mode

        PlayInfo() {
            genreName = "";
            trackId = 0;
            position = 0;
            shuffle = MusicService.PLAY_SEQUENTIAL;
        }

        PlayInfo(PlayInfo playInfo) {
            if (playInfo != null) {
                genreName = playInfo.genreName;
                trackId = playInfo.trackId;
                position = playInfo.position;
                shuffle = playInfo.shuffle;
            } else {
                genreName = "";
                trackId = 0;
                position = 0;
                shuffle = MusicService.PLAY_SEQUENTIAL;
            }
        }

        public String toString() {
            return "{" + genreName + "," + trackId + "," + position + "," + shuffle + "}";
        }
    }

    //
    // Values saved between instantiations
    //
    private PlayInfo playingInfo = null;
    private PlayInfo displayInfo = null;

    //
    // View and display related
    //
    private Spinner shuffleSpinner;

    // Viewing songs
    private SongAdapter songAdt;
    private ListView songView;

    // Viewing Genres
    private Spinner genreSpinner;

    // Viewing Albums
    private Spinner albumSpinner;
    private AlbumSpinnerAdaptor albumAdaptor;

    private ImageButton mPlayPauseButton;
    private SeekBar mSeekBar;
    private boolean mUserIsSeeking = false;
    private TextView mPlayingAlbum, mPlayingSong, mPlayingArtist, mDuration, mSongPosition;
    private ImageView mPlayingArtwork;
    private long mPlayingSongId;

    //runs without a timer by reposting this handler at the end of the runnable
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable;

    //
    // The service that actually does the playing
    private MusicService musicSrv = null;
    private Intent playIntent;

    private boolean paused = false;

    // Receive notifications about what the music service is playing
    private BroadcastReceiver servicePlayingUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String mAction = intent.getAction();
            Log.d(TAG, "servicePlayingUpdateReceiver.onReceive(" + mAction + ")");

            if (mAction != null) {
                switch (mAction) {
                    case MusicService.SERVICE_NOW_PLAYING:
                    case MusicService.SERVICE_PAUSED:
                        playingInfo.trackId = intent.getIntExtra("songIndex", 0);
                        if (displayInfo.genreName.equals(playingInfo.genreName)) {
                            displayInfo.trackId = playingInfo.trackId;
                            selectDisplayAlbum(playingInfo.trackId);
                            songView.setSelection(playingInfo.trackId);
                            updateControls();
                        }
                        break;

                    default:
                        Log.d(TAG, "servicePlayingUpdateReceiver.onReceive() Unknown action");
                }
            }
        }
    };

    //
    // "Normal" methods to override on any activity
    //
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate() entry.");

        mImageLoader = new ImageLoader(this);
        LocalBroadcastManager servicePlayUpdateBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MusicService.SERVICE_NOW_PLAYING);
        intentFilter.addAction(MusicService.SERVICE_PAUSED);
        servicePlayUpdateBroadcastManager.registerReceiver(servicePlayingUpdateReceiver, intentFilter);

        playingInfo = restorePreferences();
        displayInfo = new PlayInfo(playingInfo);

        genres = new ArrayList<>();
        currentDisplayPlayList = new ArrayList<>();
        currentDisplayAlbums = new ArrayList<>();

        setupDisplay(displayInfo);

        // If our saved (playing info) is incomplete or missing, then we will "correct" it
        // when setting up the display. So at the end of setupDisplay() our displayInfo
        // will be a better match to the music currently on the phone than playingInfo is.
        playingInfo = new PlayInfo(displayInfo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart() entry.");

        if (playIntent == null) {
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() entry.");
        paused = true;
        stopSeekTracking();
    }

    @Override
    protected void onResume() {
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
        musicSrv = null;
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged() entry.");

        // Checks the orientation of the screen
/*
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {

        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {

        }
*/

        setupDisplay(displayInfo);

        if ((musicSrv != null) && musicSrv.hasTrack())
            updateControls();
    }

    //
    // Capture back key press and rather than exit, go to background
    //
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                moveTaskToBack(true);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    //
    // End of Activity method overrides
    //


    //
    // Create and bind to our background music service
    //
    private ServiceConnection musicConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "musicConnection.onServiceConnected() entry.");
            MusicBinder binder = (MusicBinder) service;
            //get service
            musicSrv = binder.getService();
            musicSrv.setShuffle(displayInfo.shuffle);
            shuffleSpinner.setSelection(displayInfo.shuffle);
            initializeMusicServerPlaylist(playingInfo);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "musicConnection.onServiceDisconnected() entry.");
            musicSrv = null;
        }
    };

    private void initializeMusicServerPlaylist(PlayInfo playInfo) {
        if (playInfo != null) {
            Genre myGenre = getGenreByName(playInfo.genreName);
            if ((myGenre != null) && (myGenre.getPlaylist() != null)) {
                musicSrv.setList(myGenre.getPlaylist(), myGenre.getName());
                playingInfo.genreName = myGenre.getName();

                if (playInfo.trackId >= 0) {
                    playingInfo.trackId = playInfo.trackId;
                    musicSrv.setTrack(playingInfo.trackId);
                    if (playInfo.position > 0) {
                        playingInfo.position = playInfo.position;
                        musicSrv.seek(playingInfo.position);
                    }
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult() entry.");
        for (int i = 0; i < permissions.length; i++) {
            int rslt = -999;
            if (grantResults.length > i)
                rslt = grantResults[i];
            Log.d(TAG, "onRequestPermissionsResult[" + permissions[i] + "] = " + rslt);
        }
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switch (requestCode) {
                    case REQUEST_PERMISSION_STORAGE: {
                        Log.d(TAG, "onActivityResult() EXT_STORE_REQUEST GRANTED");
                        setupGenreList(playingInfo);
                        initializeMusicServerPlaylist(playingInfo);
                    }
                }
            } else {
                Log.d(TAG, "onActivityResult() Request denied.");
            }
        } else {
            Log.d(TAG, "onActivityResult() Request canceled.");
        }
    }

    public void songPicked(View view) {
        // Note to self: song.xml in layouts specifies this method to be called when
        // a song is touched.
        Log.d(TAG, "songPicked() entry. Selected item = " + view.toString());
        int selectedItem = Integer.parseInt(view.getTag().toString());
        Log.d(TAG, "songPicked() selected= " + selectedItem);
        displayInfo.trackId = Integer.parseInt(view.getTag().toString());
        if (!displayInfo.genreName.equals(playingInfo.genreName)) {
            Genre myGenre = getGenreByName(displayInfo.genreName);
            if (myGenre != null) {
                musicSrv.setList(myGenre.getPlaylist(), myGenre.getName());
                playingInfo.genreName = displayInfo.genreName;
                playingInfo.trackId = displayInfo.trackId;
            }
        }
        musicSrv.playTrack(displayInfo.trackId);
        startSeekTracking();
        updateControls();
    }

    private void playNext() {
        musicSrv.playNext();
        startSeekTracking();
        updateControls();
    }

    private void playPrev() {
        Log.d(TAG, "playPrev() entry.");
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
        if (musicSrv != null)
            return musicSrv.getDuration();
        return 0;
    }

    @Override
    public int getCurrentPosition() {
        // Log.d(TAG, "getCurrentPosition() Entry.");
        if (musicSrv != null)
            return musicSrv.getPosition();
        return 0;

    }

    @Override
    public void seekTo(int pos) {
        musicSrv.seek(pos);
    }

    @Override
    public boolean isPlaying() {
        return (musicSrv != null) && musicSrv.isPlaying();
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
        seekTo(Math.max(0, getCurrentPosition() - 5000));
        updateControls();
    }

    public void seekForward(View v) {
        seekTo(Math.min(getCurrentPosition() + 5000, getDuration()));
        updateControls();
    }

    public void skipBack(View v) {
        Log.d(TAG, "skipBack() entry.");
        playPrev();
    }

    public void skipForward(View v) {
        Log.d(TAG, "skipForward() entry.");
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
    private String genSortTitle(String a) {
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

    private void setupDisplay(PlayInfo playInfo) {
        setContentView(R.layout.main_activity);
        initToolBar();

        genreSpinner = findViewById(R.id.genre_select);
        albumSpinner = findViewById(R.id.album_select);
        songView = findViewById(R.id.song_list);

        mSeekBar = findViewById(R.id.seekTo);
        initializeSeekBar();

        mPlayPauseButton = findViewById(R.id.play_pause);

        mPlayingAlbum = findViewById(R.id.playing_album);
        mPlayingSong = findViewById(R.id.playing_song);
        mPlayingArtist = findViewById(R.id.playing_artist);
        mDuration = findViewById(R.id.duration);
        mSongPosition = findViewById(R.id.song_position);
        mPlayingArtwork = findViewById(R.id.cover_art);
        mPlayingSongId = -1;

        songAdt = new SongAdapter(this, currentDisplayPlayList);
        songView.setAdapter(songAdt);

        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            setupGenreList(playInfo);
        } else {
            Log.d(TAG, "onCreate(): Need permission to access storage.");
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION_STORAGE);
        }
    }

    private void setupGenreList(PlayInfo playInfo) {
        Log.d(TAG, "setupGenreList(" + playInfo.toString() + ") Entry.");
        getGenreList();
        try {
            ArrayAdapter<Genre> genreAdaptor = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genres);
            genreSpinner.setAdapter(genreAdaptor);
            genreSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> adapter, View v,
                                           int position, long id) {
                    // On selecting a spinner item
                    Log.d(TAG, "genreSpinner.setOnItemSelectedListener.onItemSelected(" + position + ")");
                    setDisplayGenre(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {
                    // TODO Auto-generated method stub
                }
            });

            albumAdaptor = new AlbumSpinnerAdaptor(this, currentDisplayAlbums, mImageLoader);
            albumSpinner.setAdapter(albumAdaptor);
            albumSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> adapter, View v,
                                           int position, long id) {
                    // On selecting a spinner item
                    Log.d(TAG, "albumSpinner.setOnItemSelectedListener.onItemSelected(" + position + ")");
                    try {
                        Album selectedAlbum = currentDisplayAlbums.get(position);
                        int lastTrack = currentDisplayPlayList.size();
                        if ((position + 1) < currentDisplayAlbums.size())
                            lastTrack = currentDisplayAlbums.get(position + 1).getTrack() - 1;
                        int firstTrack = selectedAlbum.getTrack();
                        if ((displayInfo.trackId < firstTrack) ||
                                (displayInfo.trackId >= lastTrack))
                            displayInfo.trackId = firstTrack;
                        selectDisplayAlbum(displayInfo.trackId);
                        songView.setSelection(displayInfo.trackId);
                    } catch (Exception e) {
                        Log.e(TAG, "onItemSelected()", e);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {
                    // TODO Auto-generated method stub
                }
            });

            int genreIndex = getGenreIndex(playInfo.genreName);
            setDisplayGenre(genreIndex);
            genreSpinner.setSelection(genreIndex);

            if (playingInfo.genreName.equals(playInfo.genreName)) {
                displayInfo.trackId = Math.max(0, playInfo.trackId);
            }
            selectDisplayAlbum(displayInfo.trackId);
        } catch (Exception e) {
            Log.d(TAG, "setupGenreList() - Failed: " + e.getMessage());
        }
    }

    //
    //  Local utility routines
    //
    private void initToolBar() {
        shuffleSpinner = findViewById(R.id.shuffle_select);

        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(this, R.array.play_select, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shuffleSpinner.setAdapter(adapter);
        shuffleSpinner.setSelection(displayInfo.shuffle);

        shuffleSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> adapter, View v,
                                       int position, long id) {
                // On selecting a spinner item
                Log.d(TAG, "shuffleSpinner.setOnItemSelectedListener.onItemSelected(" + position + ")");
                switch (position) {
                    case 0:
                        displayInfo.shuffle = MusicService.PLAY_SEQUENTIAL;
                        break;
                    case 1:
                        displayInfo.shuffle = MusicService.PLAY_RANDOM_SONG;
                        break;
                    case 2:
                        displayInfo.shuffle = MusicService.PLAY_RANDOM_ALBUM;
                        break;
                }
                if (musicSrv != null) {
                    musicSrv.setShuffle(displayInfo.shuffle);
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
            }
        });
    }

    private void initializeSeekBar() {
        mSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    int userSelectedPosition = 0;

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        mUserIsSeeking = true;
                    }

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            userSelectedPosition = progress;
                        }
                        mSongPosition.setText(formatDuration(progress));
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        mUserIsSeeking = false;
                        musicSrv.seek(userSelectedPosition);
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
                genreCursor.close();
            }
            Collections.sort(genres, (o1, o2) -> o1.getName().compareTo(o2.getName()));
        }
    }

    private ArrayList<Song> getGenreSongs(long genreId) {
        Log.d(TAG, "getGenreSongs() entry");

        ArrayList<Song> rsltPlayList = new ArrayList<>();
        final Uri musicUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId);

        ContentResolver musicResolver = getContentResolver();
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

        if (musicCursor != null && musicCursor.moveToFirst()) {
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
            musicCursor.close();
        }
        Collections.sort(rsltPlayList, (a, b) -> {
            // Sort by album title. If two titles the same, differentiate by ID.
            // Within album, sort by track. If two tracks claim the same position
            // differentiate by title.

            int rslt = a.getAlbumSortTitle().compareTo(b.getAlbumSortTitle());
            if (rslt == 0)
                rslt = (int) (a.getAlbumId() - b.getAlbumId());
            if (rslt == 0)
                rslt = a.getTrack() - b.getTrack();
            if (rslt == 0)
                rslt = a.getTitle().compareTo(b.getTitle());
            return rslt;
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
        Log.d(TAG, "setDisplayGenre(" + Position + ") Entry.");
        Genre selectedGenre = genres.get(Position);
        ArrayList<Song> genrePlaylist;

        if ((selectedGenre != null) &&
                (displayInfo != null)) {

            // If this is the first time the genre has been selected then
            // the play list will be undefined. So build the list on first
            // use.
            genrePlaylist = selectedGenre.getPlaylist();
            if ((genrePlaylist == null) || genrePlaylist.isEmpty()) {
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

            if (!selectedGenre.getName().equals(displayInfo.genreName)) {
                displayInfo.trackId = 0;
            }

            //
            // If we are changing to the genre that is currently playing
            // then select the track and track album currently playing.
            // Otherwise select the first track (and its album) in the genre.
            //
            if ((playingInfo != null) &&
                    playingInfo.genreName.equals(selectedGenre.getName()) &&
                    (musicSrv != null)) {
                displayInfo.trackId = Math.max(0, musicSrv.getTrackIndex());
            }
            displayInfo.genreName = selectedGenre.getName();
            songAdt.notifyDataSetChanged();

            selectDisplayAlbum(displayInfo.trackId);
            songView.setSelection(displayInfo.trackId);
        }
    }

    private Song getTrackInfo(int trackId) {
        Log.d(TAG, "getTrackInfo(" + trackId + ") Entry.");
        if ((currentDisplayPlayList != null) && !currentDisplayPlayList.isEmpty()) {
            if (trackId >= currentDisplayPlayList.size())
                trackId = 0;
            displayInfo.trackId = trackId;
            return currentDisplayPlayList.get(trackId);
        }
        return null;
    }

    private void selectDisplayAlbum(int trackId) {
        Log.d(TAG, "selectDisplayAlbum(" + trackId + ") Entry.");
        Song displaySong = getTrackInfo(trackId);
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
        Log.d(TAG, "getGenreIndex('" + name + "') Entry.");
        if ((genres == null) || (genres.isEmpty()) || (name == null)) {
            Log.d(TAG, "getGenreIndex(): Genre empty or desired name is null.");
            return 0;
        }

        for (int i = 0; i < genres.size(); i++) {
            if (genres.get(i).getName().equals(name)) {
                Log.d(TAG, "getGenreIndex('" + name + "') is index " + i);
                return i;
            }
        }
        Log.d(TAG, "getGenreIndex(): No match for genre name.");
        return 0;
    }

    private Genre getGenreByName(String name) {
        Log.d(TAG, "getGenreByName('" + name + "') Entry.");
        if ((genres == null) || genres.isEmpty()) {
            Log.d(TAG, "getGenreByName(): Genre list null or empty.");
            return null;
        }

        if ((name == null) || name.isEmpty()) {
            Log.d(TAG, "getGenreByName(): Requested name is null or empty");
            return genres.get(0);
        }

        for (int i = 0; i < genres.size(); i++) {
            if (genres.get(i).getName().equals(name)) {
                Log.d(TAG, "getGenreByName('" + name + "') is index " + i);
                return genres.get(i);
            }
        }

        Log.d(TAG, "getGenreByName('" + name + "'): No match for genre name.");
        return genres.get(0);
    }

    private void savePreferences() {
        Log.d(TAG, "savePreferences() Entry.");

        if (musicSrv != null) {
            SharedPreferences.Editor editor = getSharedPreferences(SYMPHONY_PREFS_NAME, MODE_PRIVATE).edit();

            editor.putInt(SAVED_SHUFFLE_MODE, displayInfo.shuffle);

            int trkIndex = musicSrv.getTrackIndex();
            int trackPosition = musicSrv.getPosition();
            String genreName = musicSrv.getGenre();

            if (genreName != null)
                editor.putString(SAVED_GENRE_NAME, genreName);

            if (trkIndex >= 0)
                editor.putInt(SAVED_TRACK_INDEX, trkIndex);

            if (trackPosition > 0)
                editor.putInt(SAVED_TRACK_POSITION, trackPosition);
            editor.apply();
        }
    }

    private PlayInfo restorePreferences() {
        Log.d(TAG, "restorePreferences() Entry.");
        PlayInfo saved = new PlayInfo();

        SharedPreferences prefs = getSharedPreferences(SYMPHONY_PREFS_NAME, MODE_PRIVATE);

        saved.genreName = prefs.getString(SAVED_GENRE_NAME, null);
        saved.shuffle = prefs.getInt(SAVED_SHUFFLE_MODE, MusicService.PLAY_SEQUENTIAL);
        saved.trackId = prefs.getInt(SAVED_TRACK_INDEX, -1);
        saved.position = prefs.getInt(SAVED_TRACK_POSITION, 0);

        if ((saved.genreName == null) || (saved.trackId < 0))
            return null;
        return saved;
    }

    private void updateControls() {
        updatePlayingStatus();
        updateSeekBar();
        updateCurrentTrackInfo();
    }

    private void updatePlayingStatus() {
        int drawable = R.drawable.ic_playback_start;
        if (isPlaying())
            drawable = R.drawable.ic_playback_pause;
        mPlayPauseButton.setImageResource(drawable);
    }

    private synchronized void updateSeekBar() {
        //Log.d(TAG, "updateSeekBar() Entry.");
        if (!mUserIsSeeking) {
            int duration = getDuration();
            mSeekBar.setMax(duration);
            mDuration.setText(formatDuration(duration));

            int currPos = getCurrentPosition();
            mSongPosition.setText(formatDuration(currPos));
            mSeekBar.setProgress(currPos);
        }
    }

    private void updateCurrentTrackInfo() {

        Song currentTrack = null;
        if (musicSrv != null) {
            currentTrack = musicSrv.getCurrentSong();
        }

        if (currentTrack != null) {
            if (currentTrack.getId() != mPlayingSongId) {
                mPlayingSongId = currentTrack.getId();
                mPlayingAlbum.setText(currentTrack.getAlbum());
                mPlayingSong.setText(currentTrack.getTitle());
                mPlayingArtist.setText(currentTrack.getArtist());
                mImageLoader.loadImage(mPlayingSongId, mPlayingArtwork);
            }
        } else {
            mPlayingAlbum.setText("");
            mPlayingSong.setText("");
            mPlayingArtist.setText("");
            mPlayingArtwork.setImageResource(R.drawable.ic_launcher_icon);
            mPlayingSongId = -1;
        }
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
                //Log.d(TAG, "timerRunnable.run() entry");
                updateSeekBar();
                updateCurrentTrackInfo();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopSeekTracking() {
        timerHandler.removeCallbacks(timerRunnable);
    }
}
