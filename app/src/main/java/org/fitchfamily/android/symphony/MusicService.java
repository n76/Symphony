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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Random;
import java.util.StringTokenizer;

/**
 * Created by tfitch on 7/6/17.
 */


/*
 *  General notes and comments
 *
 *  Gapless playback
 *      For gapless playback we need to have two media player instances: One currently playing
 *      and the other "prepared" and ready. Since the user is most interested in the currently
 *      playing track we need to maintain current information about it.
 *
 *      We also have to maintain information about the prepared media player (which, borrowing
 *      from baseball, we call the "on deck" instance.
 *
 *  Random play
 *      Unfortunately, the user doesn't really want truly random playback. For example, if a
 *      playlist has only 10 tracks and we randomly choose the next track to play there is a
 *      10% chance the current track will be played again.
 *
 *      So we build a play order list, basically by shuffling the deck. In this case our deck
 *      is an array that contains the play list indexes and we shuffle that. We have two
 *      shuffle lists: One for playing random tracks and one for playing random albums. We
 *      maintain an index into the shuffle list which we simply increment, the track (or album)
 *      to play is based on our index into the shuffle list.
 *
 *      The next complication is that if the user selects a specific track to play our shuffle
 *      list index is not synchronized with their selection and it is possible that the track
 *      (or album) we select next may be the same track (album) that they selected. To avoid
 *      this, when the user selects a specific track (including going to the previous track)
 *      we need to reset our shuffle index so that it point to the entry in the shuffle list
 *      for the user's desired track.
 *
 *  Restoring previous play state
 *      We would like to allow the overall app to be able to resume whatever album/track they
 *      were listening to last time the app was run. To do that, we need to be able to distinquish
 *      between starting a track (preparing it then running from the beginning) and getting a track
 *      ready for the user to resume if they desire. In the later case we may be told to prepare
 *      a track and once prepared position the playback but not actually begin to play it. We handle
 *      that by two values: deferredGo and deferredPosition.
 */
public class MusicService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {

    public static final int PLAY_SEQUENTIAL = 0;
    public static final int PLAY_RANDOM_SONG = 1;
    public static final int PLAY_RANDOM_ALBUM = 2;

    public static final String SERVICE_NOW_PLAYING = "org.fitchfamily.android.symphony.SERVICE_NOW_PLAYING";
    public static final String SERVICE_PAUSED = "org.fitchfamily.android.symphony.SERVICE_PAUSED";


    private static final String TAG = "Symphony:MusicService";

    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    // private MediaControllerCompat.TransportControls transportControls;

    // For Audio "Focus", support pausing or reducing volume with other apps
    // wish to use audio output (alerts, etc.)
    private boolean haveAudioFocus = false;
    private Handler mHandler = new Handler();
    AudioManager am;
    AudioManager.OnAudioFocusChangeListener afChangeListener =
            new AudioManager.OnAudioFocusChangeListener() {
                public void onAudioFocusChange(int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        // Permanent loss of audio focus
                        // Pause playback immediately
                        // mediaController.getTransportControls().pause();
                        pausePlayer();
                        // Wait 30 seconds before stopping playback
                        /*
                        mHandler.postDelayed(new Runnable() {
                                                 @Override
                                                 public void run() {
                                                     currentTrackPlayer.stop();
                                                 }
                                             },
                                TimeUnit.SECONDS.toMillis(30));
                                */
                    }
                    else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        // Pause playback
                        pausePlayer();
                    } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                        // Lower the volume, keep playing
                        if (currentTrackPlayer != null)
                            currentTrackPlayer.setVolume(0.25f, 0.25f);
                    } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        // Your app has been granted audio focus again
                        // Raise volume to normal, restart playback if necessary
                        if (currentTrackPlayer != null)
                            currentTrackPlayer.setVolume(1.0f, 1.0f);
                    }
                }
            };

    // Stuff to handle pausing music when earphones are unplugged
    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                Log.d(TAG, "BecomingNoisyReceiver.onReceive()");
                pausePlayer();
            }
        }
    }
    private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private BecomingNoisyReceiver myNoisyAudioStreamReceiver = new BecomingNoisyReceiver();


    private ArrayList<Song> songs;      // Tracks to play
    private ArrayList<Album> albums;    // Albums found in songs

    private MediaPlayer currentTrackPlayer;     // The media player playing the current track
    private MediaPlayer onDeckTrackPlayer;      // The media player set up for the next track

    // Note on currentTrackPlayer and onDeckTrackPlayer: These are manipulated via both the
    // user display context (MainActivity) and by completion notifications from the actual
    // media player. Not clear from Android documentation but assuming different threads are
    // calling our MusicService methods. So we will use the synchronized attribute on each
    // method that changes or checks currentTrackPlayer and/or onDeckTrackPlayer in a non-atomic
    // fashion.
    //
    // We will always create a new player as the onDeckTrackPlayer. If no current player exists
    // when it becomes prepared, we will make it the currentTrackPlayer and start it. If a
    // currentTrackPlayer exists, then we will add it to the currentTrackPlayer as the next
    // player.
    //

    private Integer[] songOrder;            // Shuffle order for songs
    private Integer[] albumOrder;           // Shuffle order for albums

    private class IndexInfo {           // Information shuffle and track
        private int trackIndex;
        private int shuffleIndex;

        public IndexInfo() {
            trackIndex = 0;
            shuffleIndex = setShuffleToTrack(trackIndex);
        }

        public IndexInfo(int track) {
            trackIndex = track;
            shuffleIndex = setShuffleToTrack(trackIndex);
        }

        public IndexInfo(IndexInfo prevIndex) {
            shuffleIndex = prevIndex.getShuffleIndex();
            trackIndex = nextTrackIndex(prevIndex.getTrackIndex());
            shuffleIndex = setShuffleToTrack(trackIndex);
        }

        public int getTrackIndex() {
            return trackIndex;
        }

        public void shuffleChanged() {
            shuffleIndex = setShuffleToTrack(trackIndex);
        }

        public int getShuffleIndex() {
            return shuffleIndex;
        }

        private int setShuffleToTrack(int currentIndex) {
            int rslt = currentIndex;
            switch (shuffle) {
                case PLAY_RANDOM_SONG:
                    if (songOrder != null) {
                        rslt = Arrays.asList(songOrder).indexOf(currentIndex);
                    }
                    break;

                case PLAY_RANDOM_ALBUM:
                    if (albumOrder != null) {
                        int currentAlbumIndex = -1;
                        Long currentAlbumId = songs.get(trackIndex).getAlbumId();
                        for (int i = 0; i < albums.size(); i++) {
                            if (currentAlbumId == albums.get(i).getID()) {
                                currentAlbumIndex = i;
                                break;
                            }
                        }

                        rslt = Arrays.asList(albumOrder).indexOf(currentAlbumIndex);
                        break;
                    }
            }
            return rslt;
        }

        private int nextTrackIndex(int currentIndex) {
            int rslt = currentIndex + 1;
            switch (shuffle){
                case PLAY_SEQUENTIAL:
                    if(rslt >=songs.size())
                        rslt =0;
                    break;

                case PLAY_RANDOM_SONG:
                    shuffleIndex++;
                    if (shuffleIndex >= songs.size())
                        shuffleIndex=0;
                    rslt = songOrder[shuffleIndex];
                    break;

                case PLAY_RANDOM_ALBUM:
                    long curentAlbum = songs.get(currentIndex).getAlbumId();
                    if(rslt >=songs.size()) {
                        rslt = 0;
                    }
                    long nextAlbum = songs.get(rslt).getAlbumId();

                    if (curentAlbum != nextAlbum) {
                        shuffleIndex++;
                        if (shuffleIndex >= albums.size())
                            shuffleIndex=0;
                        int newAlbum = albumOrder[shuffleIndex];
                        rslt = albums.get(newAlbum).getTrack();
                    }
                    break;
            }
            return rslt;
        };
    }
    public IndexInfo playingIndexInfo;  // Information and control of currently playing track
    public IndexInfo onDeckIndexInfo;   // Information and control of next track to be played

    private int[] history;              // Recently played tracks
    private int historyPosition;        // Current location in history
    private boolean historyInhibit;     // Hack to keep from prev play adding to history.

    private static final int NOTIFY_ID=1;

    private int shuffle=PLAY_RANDOM_ALBUM;
    private Random rand;

    private final IBinder musicBind = new MusicBinder();
    private boolean noisyReceiverRegistered=false;

    // Stuff to allow us to defer requested operations (like starting a track after it
    // has been prepared or positioning the playback point.
    private boolean deferredGo;
    private int deferredPosition;

    // For "gap-less playback" we setup a second "on-deck" player ready to go. When the
    // current player says it is finished, we start the on-deck player and release the
    // old player.

    public void onCreate(){
        super.onCreate();
        Log.d(TAG,"onCreate() entry.");

        currentTrackPlayer = null;
        onDeckTrackPlayer = null;

        deferredPosition = -1;
        deferredGo = false;

        playingIndexInfo = null;
        onDeckIndexInfo = null;

        resetHistory();
        rand=new Random();
        am = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG,"onStartCommand() entry.");
        if (mediaSessionManager == null) {
            try {
                initMediaSession();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"onDestroy() entry.");
        resetToInitialState();
        stopForeground(true);
        super.onDestroy();
    }

    public void setList(ArrayList<Song> theSongs){
        Log.d(TAG,"setList() entry.");
        resetToInitialState();
        songs=theSongs;
        albums=Album.getAlbumIndexes(songs);
        songOrder = genPlayOrder(songs.size());
        albumOrder = genPlayOrder(albums.size());
        resetHistory();
        playingIndexInfo = null;
        onDeckIndexInfo = new IndexInfo();;
    }

    public synchronized void setShuffle(int playMode){
        Log.d(TAG,"setShuffle("+Integer.toString(playMode)+") entry.");
        if (shuffle != playMode) {
            switch (playMode) {
                case PLAY_SEQUENTIAL:
                case PLAY_RANDOM_SONG:
                case PLAY_RANDOM_ALBUM:
                    // Generate new random track order
                    if (songs != null) {
                        songOrder = genPlayOrder(songs.size());
                    }

                    // Generate new random album order
                    if (albums != null)
                        albumOrder = genPlayOrder(albums.size());

                    // If we have a on-deck player, cancel it as our play order is
                    // changing.
                    if (currentTrackPlayer!=null) {
                        currentTrackPlayer.setNextMediaPlayer(null);
                        if (onDeckTrackPlayer != null) {
                            onDeckTrackPlayer.release();
                            onDeckTrackPlayer = null;
                        }
                    }

                    // Set the new play mode and set the shuffle index to select
                    // the current track.
                    shuffle = playMode;
                    if (playingIndexInfo != null)
                        playingIndexInfo.shuffleChanged();

                    // If we are playing something, then prepare the next track
                    // using the new play mode.
                    if (currentTrackPlayer != null) {
                        onDeckIndexInfo = new IndexInfo(playingIndexInfo);
                        currentTrackPlayer.setNextMediaPlayer(null);
                        onDeckTrackPlayer = prepareTrack(onDeckIndexInfo.getTrackIndex());
                    }
                    break;

                default:
                    Log.d(TAG, "setShuffle(" + Integer.toString(playMode) + ") Invalid value.");
            }
        }
    }

    public int getShuffle() {
        return shuffle;
    }

    public class MusicBinder extends Binder {
        MusicService getService() {
            Log.d(TAG,"MusicBinder.getService() entry.");
            return MusicService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG,"onUnbind() entry.");
        resetToInitialState();
        return false;
    }

    // Current track player notifying us that it has finished playing its track
    //
    // Release current track player and replace it by the "On Deck" player.
    // If the on deck player is ready, start it and then setup a new on deck.
    //
    @Override
    public synchronized void onCompletion(MediaPlayer mp) {
        Log.d(TAG,"onCompletion() entry.");
        if((currentTrackPlayer != null) && (currentTrackPlayer.getCurrentPosition()>0)) {
            currentTrackPlayer.reset();
            currentTrackPlayer.release();
            currentTrackPlayer = onDeckTrackPlayer;
            onDeckTrackPlayer = null;
            if (currentTrackPlayer != null) {
                Log.d(TAG,"onCompletion() next track prepared, starting immediately.");
                playingIndexInfo = onDeckIndexInfo;
                setupForegroundNotification();
                notifyMainActivity(SERVICE_NOW_PLAYING);
                updateMetaData();
                addToHistory(playingIndexInfo.getTrackIndex());

                // Setup an "on Deck" player for the next track to play
                onDeckIndexInfo = new IndexInfo(playingIndexInfo);
                onDeckTrackPlayer = prepareTrack(onDeckIndexInfo.getTrackIndex());
            }
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.d(TAG,"onError(what="+what+", extra="+extra+") entry.");
        resetToInitialState();
        return false;
    }

    @Override
    public synchronized void onPrepared(MediaPlayer mp) {
        Log.d(TAG,"onPrepared() entry.");
        // Register a receiver to be notified about headphones being
        // unplugged then start playback
        if (currentTrackPlayer == mp) {
            Log.d(TAG, "onPrepared() Unexpected event on currentTrackPlayer");
        } else if (onDeckTrackPlayer == mp) {
            if (currentTrackPlayer == null) {
                //
                // No currently playing track. Set the on deck track to currently playing
                //
                currentTrackPlayer = onDeckTrackPlayer;
                playingIndexInfo = onDeckIndexInfo;
                onDeckTrackPlayer = null;

                if (deferredPosition > 0)
                    currentTrackPlayer.seekTo(deferredPosition);
                deferredPosition = -1;

                if (deferredGo)
                    this.go();
                else
                    notifyMainActivity(SERVICE_PAUSED);

                addToHistory(playingIndexInfo.getTrackIndex());

                // Setup an "on Deck" player for the next track to play
                onDeckIndexInfo = new IndexInfo(playingIndexInfo);
                onDeckTrackPlayer = prepareTrack(onDeckIndexInfo.getTrackIndex());
            } else {
                // We are currently playing a track. Set the on deck track to play when
                // current track is done.
                currentTrackPlayer.setNextMediaPlayer(onDeckTrackPlayer);
            }
        }
    }

    public synchronized void playTrack(int trackIndex) {
        Log.d(TAG,"playTrack("+trackIndex+") entry.");
        setTrack(trackIndex);
        deferredGo = true;
    }

    public synchronized void setTrack(int trackIndex) {
        Log.d(TAG,"setTrack("+trackIndex+") entry.");
        resetToInitialState();
        deferredGo = false;
        playingIndexInfo = null;
        onDeckIndexInfo = new IndexInfo(trackIndex);
        onDeckTrackPlayer = prepareTrack(trackIndex);
    }

    // Items needed to support media controller calls from main activity.
    public synchronized int getPosition(){
        //Log.d(TAG,"getPosition() entry.");
        if (currentTrackPlayer != null)
            return currentTrackPlayer.getCurrentPosition();
        Log.d(TAG,"getPosition() not playing?");
        return 0;
    }

    public synchronized int getDuration(){
        //Log.d(TAG,"getDuration() entry.");
        if (currentTrackPlayer != null)
            return currentTrackPlayer.getDuration();
        Log.d(TAG,"getDuration() not playing?");
        return 0;
    }

    public synchronized boolean isPlaying(){
        //Log.d(TAG,"isPlaying() entry.");
        if (currentTrackPlayer != null)
            return currentTrackPlayer.isPlaying();
        return false;
    }

    public synchronized boolean hasTrack() {
        return (currentTrackPlayer != null);
    }

    public synchronized void pausePlayer(){
        Log.d(TAG,"pausePlayer() entry.");
        if (currentTrackPlayer != null) {
            currentTrackPlayer.pause();
            notifyMainActivity(SERVICE_PAUSED);
        }
        try {
            if (noisyReceiverRegistered)
                unregisterReceiver(myNoisyAudioStreamReceiver);
            noisyReceiverRegistered = false;
        } catch (Exception e) {
            Log.e("MUSIC SERVICE", "Error unregistering noisy audio receiver.", e);
        }

        if (haveAudioFocus) {
            am.abandonAudioFocus(afChangeListener);
            haveAudioFocus = false;
        }
        stopForeground(true);
    }

    public synchronized void seek(int posn){
        Log.d(TAG,"seek("+posn+") entry.");
        if (currentTrackPlayer != null)
            currentTrackPlayer.seekTo(posn);
        else {
            deferredPosition = posn;
            Log.d(TAG, "seek() deferredPosition set to " + posn);
        }
    }

    public synchronized void go() {
        Log.d(TAG,"go() entry.");
        if (currentTrackPlayer != null) {
            deferredGo = false;
            if (!haveAudioFocus) {
                int result = am.requestAudioFocus(afChangeListener,
                        // Use the music stream.
                        AudioManager.STREAM_MUSIC,
                        // Request permanent focus.
                        AudioManager.AUDIOFOCUS_GAIN);
                haveAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            }
            if (haveAudioFocus) {
                registerReceiver(myNoisyAudioStreamReceiver, intentFilter);
                noisyReceiverRegistered = true;
                currentTrackPlayer.start();
                setupForegroundNotification();
                notifyMainActivity(SERVICE_NOW_PLAYING);
                updateMetaData();
            }
        } else
            deferredGo = true;
    }

    public synchronized void playPrev(){
        Log.d(TAG,"playPrev() entry.");
        // We maintain a list of the most recently played tracks in history[] so all we
        // have to do when requested to play previous is back up on the list and pick up
        // the previous song position.
        //
        // If we are asked to back up farther than we have history (negative song indexes)
        // ignore the request.
        historyPosition--;
        if (historyPosition<0)
            historyPosition = history.length - 1;
        int prevSongIndex = history[historyPosition];
        Log.d(TAG, "playPrev(): historyPosition="+historyPosition+", prevSongIndex="+prevSongIndex);
        if (prevSongIndex >= 0) {
            historyInhibit = true;
            playTrack(prevSongIndex);
        }
    }

    //skip to next
    public synchronized void playNext(){
        Log.d(TAG,"playNext() entry.");
        resetToInitialState();
        deferredGo = true;
        playingIndexInfo = null;
        onDeckTrackPlayer = prepareTrack(onDeckIndexInfo.getTrackIndex());
    }

    //
    // Some internal utility methods
    //

    // Create a new media player instance and get it started on preparing itself
    private MediaPlayer prepareTrack(int trackIndex) {
        MediaPlayer mp = initTrackPlayer();
        Song songToPlay = songs.get(trackIndex);    //get song info
        long currSong = songToPlay.getId();           //set uri

        Uri trackUri = ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                currSong);
        try {
            mp.setDataSource(getApplicationContext(), trackUri);
        } catch (Exception e) {
            Log.e("MUSIC SERVICE", "Error setting data source", e);
            mp.release();
            return null;
        }
        mp.prepareAsync();
        return mp;
    }

    private void setupForegroundNotification() {
        // Let the Music Controller know we are playing the song.
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendInt = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(this);
        String trackTitle = "Unknown";
        String trackAlbum = "Unknown";

        if ((currentTrackPlayer != null) && (playingIndexInfo != null)) {
            Song songToPlay = songs.get(playingIndexInfo.getTrackIndex());    //get song info

            trackTitle = songToPlay.getTitle();            //set title
            trackAlbum = songToPlay.getAlbum();

        }
        String contentTitle = "Playing";
        if (trackTitle.compareTo(trackAlbum) != 0)
            contentTitle = trackAlbum;

        builder.setContentIntent(pendInt)
                .setSmallIcon(R.drawable.play)
                .setTicker(trackTitle)
                .setOngoing(true)
                .setContentTitle(contentTitle)
                .setContentText(trackTitle);
        Notification notify = builder.build();

        startForeground(NOTIFY_ID, notify);
    }

    private void notifyMainActivity(String status) {
        Log.d(TAG,"notifyMainActivity("+status+") entry.");
        // Let the Main Activity know we are playing the song.
        Intent playingIntent = new Intent(status);
        int trackIndex = 0;
        if (playingIndexInfo != null)
            trackIndex = playingIndexInfo.getTrackIndex();
        playingIntent.putExtra("songIndex", trackIndex);
        LocalBroadcastManager.getInstance(this).sendBroadcast(playingIntent);
    }

    private Integer[] genPlayOrder(final int size) {
        Integer[] rslt = new Integer[size];

        // create sorted card deck (each value only occurs once)
        for (int i=0; i<size; i++)
            rslt[i] = i;

        // shuffle the card deck using Durstenfeld algorithm
        for (int i=size-1; i>0; i--) {
            int j=rand.nextInt(i);
            int t = rslt[i];
            rslt[i] = rslt[j];
            rslt[j] = t;
        }
        return rslt;
    }

    private MediaPlayer initTrackPlayer(){
        Log.d(TAG,"initTrackPlayer() entry.");

        MediaPlayer newTrackPlayer = new MediaPlayer();
        //set currentTrackPlayer properties
        newTrackPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        newTrackPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        newTrackPlayer.setOnPreparedListener(this);
        newTrackPlayer.setOnCompletionListener(this);
        newTrackPlayer.setOnErrorListener(this);
        return newTrackPlayer;
    }

    private synchronized void resetToInitialState() {
        if (currentTrackPlayer != null) {
            currentTrackPlayer.release();
            currentTrackPlayer = null;
        }
        if (onDeckTrackPlayer != null) {
            onDeckTrackPlayer.release();
            onDeckTrackPlayer = null;
        }
        try {
            if (noisyReceiverRegistered)
                unregisterReceiver(myNoisyAudioStreamReceiver);
            noisyReceiverRegistered = false;
        } catch (Exception e) {
            Log.e("MUSIC SERVICE", "Error unregistering noisy audio receiver.", e);
        }
        if (haveAudioFocus) {
            am.abandonAudioFocus(afChangeListener);
            haveAudioFocus = false;
        }
    }

    private void resetHistory() {
        Log.d(TAG, "resetHistory() entry.");
        historyInhibit = false;
        historyPosition = 0;
        history = new int[1000];
        for (int i=0; i<history.length; i++)
            history[i] = -1;
    }

    private void addToHistory(int trackIndex) {
        Log.d(TAG, "addToHistory("+trackIndex+") entry.");
        if (historyInhibit) {
            historyInhibit = false;
        } else {
            historyPosition++;
            if (historyPosition >= history.length)
                historyPosition = 0;
            history[historyPosition] = trackIndex;
        }
    }

    // Media Session stuff

    private void initMediaSession() throws RemoteException {
        Log.d(TAG, "initMediaSession() entry.");
        if (mediaSessionManager != null)
            return; //mediaSessionManager exists

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        // Create a new MediaSession
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        //Get MediaSessions transport controls
        // transportControls = mediaSession.getController().getTransportControls();
        //set MediaSession -> ready to receive media commands
        mediaSession.setActive(true);
        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        //Set mediaSession's MetaData
        updateMetaData();

        // Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            // Implement callbacks
            @Override
            public void onPlay() {
                super.onPlay();
                go();
            }

            @Override
            public void onPause() {
                super.onPause();
                pausePlayer();
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                playNext();
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                playPrev();
            }

            @Override
            public void onStop() {
                super.onStop();
                //Stop the service
                stopSelf();
            }

            @Override
            public void onSeekTo(long position) {
                super.onSeekTo(position);
            }
        });
    }

    private void updateMetaData() {
        Log.d(TAG, "updateMetaData() entry.");
        if ((currentTrackPlayer != null) && (playingIndexInfo != null)) {
            Song songToPlay = songs.get(playingIndexInfo.getTrackIndex());    //get song info

            String trackTitle = songToPlay.getTitle();            //set title
            String trackAlbum = songToPlay.getAlbum();
            String trackArtist = songToPlay.getArtist();


            Bitmap albumArt = BitmapFactory.decodeResource(getResources(),
                    R.drawable.violin_icon); //replace with medias albumArt
            // Update the current metadata
            mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, trackArtist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, trackAlbum)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, trackTitle)
                    .build());

            Log.d(TAG, "updateMetaData() trackAlbum="+trackAlbum);
            Log.d(TAG, "updateMetaData() trackTitle="+trackTitle);
            Log.d(TAG, "updateMetaData() trackArtist="+trackArtist);
        }
    }
}
