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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.Random;

/**
 * Created by tfitch on 7/6/17.
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
                        mHandler.postDelayed(new Runnable() {
                                                 @Override
                                                 public void run() {
                                                     currentTrackPlayer.stop();
                                                 }
                                             },
                                TimeUnit.SECONDS.toMillis(30));
                    }
                    else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        // Pause playback
                        pausePlayer();
                    } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                        // Lower the volume, keep playing
                        currentTrackPlayer.setVolume(0.25f, 0.25f);
                    } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        // Your app has been granted audio focus again
                        // Raise volume to normal, restart playback if necessary
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

    private int[] songOrder;            // Shuffle order for songs
    private int[] albumOrder;           // Shuffle order for albums
    private int shuffleIndex;           // Where we are in the shuffle

    private class IndexInfo {           // Information shuffle and track
        private int trackIndex;
        private int shuffleIndex;

        public IndexInfo() {
            trackIndex = 0;
            shuffleIndex = 0;
        }

        public IndexInfo(IndexInfo prevIndex) {
            trackIndex = prevIndex.getTrackIndex();
            shuffleIndex = prevIndex.getShuffleIndex();
        }

        public int getTrackIndex() {
            return trackIndex;
        }

        public int getShuffleIndex() {
            return shuffleIndex;
        }

        public void setTrackIndex(int newTrackIndex) {
            trackIndex = newTrackIndex;
        }

        public int nextTrackIndex() {
            switch (shuffle){
                case PLAY_SEQUENTIAL:
                    Log.d(TAG,"IndexInfo.nextTrackIndex() PLAY_SEQUENTIAL.");
                    trackIndex++;
                    if(trackIndex >=songs.size())
                        trackIndex =0;
                    break;

                case PLAY_RANDOM_SONG:
                    shuffleIndex++;
                    if (shuffleIndex >= songs.size())
                        shuffleIndex=0;
                    trackIndex = songOrder[shuffleIndex];
                    Log.d(TAG,"IndexInfo.nextTrackIndex() PLAY_RANDOM_SONG. shuffle="+shuffleIndex+", song="+ trackIndex);
                    break;

                case PLAY_RANDOM_ALBUM:
                    Log.d(TAG,"IndexInfo.nextTrackIndex() PLAY_RANDOM_ALBUM.");
                    long curentAlbum = songs.get(trackIndex).getAlbumId();
                    Log.d(TAG,"IndexInfo.nextTrackIndex() Next sequential track.");
                    trackIndex++;
                    if(trackIndex >=songs.size())
                        trackIndex =0;
                    long nextAlbum = songs.get(trackIndex).getAlbumId();

                    if (curentAlbum != nextAlbum) {
                        Log.d(TAG,"IndexInfo.nextTrackIndex() New album detected, select random.");
                        shuffleIndex++;
                        if (shuffleIndex >= albums.size())
                            shuffleIndex=0;
                        int newAlbum = albumOrder[shuffleIndex];
                        trackIndex = albums.get(newAlbum).getTrack();
                        Log.d(TAG,"IndexInfo.nextTrackIndex() PLAY_RANDOM_ALBUM. shuffle="+shuffleIndex+
                                ", newAlbum="+newAlbum+", song="+ trackIndex);
                    }
                    break;
            }
            return trackIndex;
        };
    }
    public IndexInfo playingIndexInfo;
    public IndexInfo onDeckIndexInfo;

    private int[] history;              // Recently played tracks
    private int historyPosition;        // Current location in history
    private boolean historyInhibit;     // Hack to keep from prev play adding to history.

    private String songTitle="";
    private static final int NOTIFY_ID=1;

    private int shuffle=PLAY_RANDOM_ALBUM;
    private Random rand;

    private final IBinder musicBind = new MusicBinder();
    private boolean noisyReceiverRegistered=false;

    // For "gap-less playback" we setup a second "on-deck" player ready to go. When the
    // current player says it is finished, we start the on-deck player and release the
    // old player.

    public void onCreate(){
        super.onCreate();
        Log.d(TAG,"onCreate() entry.");

        currentTrackPlayer = null;
        onDeckTrackPlayer = null;;

        playingIndexInfo = new IndexInfo();
        onDeckIndexInfo = new IndexInfo();

        resetHistory();
        rand=new Random();
        am = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"onDestroy() entry.");
        resetToInitialState();
        stopForeground(true);
    }


    public void setList(ArrayList<Song> theSongs){
        Log.d(TAG,"setList() entry.");
        songs=theSongs;
        albums=Album.getAlbumIndexes(songs);
        songOrder = genPlayOrder(songs.size());
        albumOrder = genPlayOrder(albums.size());
        shuffleIndex = 0;
        resetHistory();
        playingIndexInfo = new IndexInfo();
        onDeckIndexInfo = new IndexInfo();
    }

    public synchronized void setShuffle(int playMode){
        Log.d(TAG,"setShuffle("+Integer.toString(playMode)+") entry.");
        switch (playMode) {
            case PLAY_SEQUENTIAL:
            case PLAY_RANDOM_SONG:
            case PLAY_RANDOM_ALBUM:
                if (playMode != shuffle) {
                    if (songs != null)
                        songOrder = genPlayOrder(songs.size());
                    if (albums != null)
                        albumOrder = genPlayOrder(albums.size());
                    shuffleIndex = 0;
                    if (onDeckTrackPlayer != null) {
                        onDeckTrackPlayer.release();
                        onDeckTrackPlayer = null;
                    }
                }
                shuffle = playMode;
                break;

            default:
                Log.d(TAG,"setShuffle("+Integer.toString(playMode)+") Invalid value.");
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
    public boolean onUnbind(Intent intent){
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
            if (currentTrackPlayer != null) {
                Log.d(TAG,"onCompletion() next track prepared, starting immediately.");
                playingIndexInfo = onDeckIndexInfo;
                currentTrackPlayer.start();
                setupForegroundNotification();
                notifyMainActivity(SERVICE_NOW_PLAYING);
                addToHistory(playingIndexInfo.getTrackIndex());

                // Setup an "on Deck" player for the next track to play
                onDeckIndexInfo = new IndexInfo(playingIndexInfo);
                onDeckTrackPlayer = prepareTrack(onDeckIndexInfo.nextTrackIndex());
            } else {
                Log.d(TAG,"onCompletion() Preparing next track.");
                currentTrackPlayer = prepareTrack(playingIndexInfo.nextTrackIndex());
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
            if (!haveAudioFocus) {
                int result = am.requestAudioFocus(afChangeListener,
                        // Use the music stream.
                        AudioManager.STREAM_MUSIC,
                        // Request permanent focus.
                        AudioManager.AUDIOFOCUS_GAIN);
                haveAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            }
            if (haveAudioFocus) {
                if (!noisyReceiverRegistered)
                    registerReceiver(myNoisyAudioStreamReceiver, intentFilter);
                noisyReceiverRegistered = true;
                currentTrackPlayer.start();
                setupForegroundNotification();
                notifyMainActivity(SERVICE_NOW_PLAYING);
                addToHistory(playingIndexInfo.getTrackIndex());

                // Setup an "on Deck" player for the next track to play
                onDeckIndexInfo = new IndexInfo(playingIndexInfo);
                onDeckTrackPlayer = prepareTrack(onDeckIndexInfo.nextTrackIndex());
            }
        }
    }

    public synchronized void playTrack(int trackIndex) {
        Log.d(TAG,"playTrack("+trackIndex+") entry.");
        resetToInitialState();
        playingIndexInfo.setTrackIndex(trackIndex);
        currentTrackPlayer = prepareTrack(trackIndex);
    }

    // Items needed to support media controller calls from main activity.
    public synchronized int getPosition(){
        //Log.d(TAG,"getPosition() entry.");
        if ((currentTrackPlayer != null) && currentTrackPlayer.isPlaying())
            return currentTrackPlayer.getCurrentPosition();
        Log.d(TAG,"getPosition() not playing?");
        return 0;
    }

    public synchronized int getDuration(){
        //Log.d(TAG,"getDuration() entry.");
        if ((currentTrackPlayer != null) && currentTrackPlayer.isPlaying())
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
        Log.d(TAG,"seek() entry.");
        if (currentTrackPlayer != null)
            currentTrackPlayer.seekTo(posn);
    }

    public synchronized void go(){
        Log.d(TAG,"go() entry.");
        if (currentTrackPlayer != null) {
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
            }
        }
    }

    public void playPrev(){
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
    public void playNext(){
        Log.d(TAG,"playNext() entry.");
        playTrack(playingIndexInfo.nextTrackIndex());
    }

    //
    // Some internal utility methods
    //

    // Create a new media player instance and get it started on preparing itself
    private MediaPlayer prepareTrack(int trackIndex) {
        MediaPlayer mp = initTrackPlayer();
        Song songToPlay = songs.get(trackIndex);    //get song info
        long currSong = songToPlay.getId();           //set uri
        songTitle = songToPlay.getTitle();            //set title
        Uri trackUri = ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                currSong);
        try {
            mp.setDataSource(getApplicationContext(), trackUri);
        } catch (Exception e) {
            Log.e("MUSIC SERVICE", "Error setting data source", e);
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

        builder.setContentIntent(pendInt)
                .setSmallIcon(R.drawable.play)
                .setTicker(songTitle)
                .setOngoing(true)
                .setContentTitle("Playing")
                .setContentText(songTitle);
        Notification notify = builder.build();

        startForeground(NOTIFY_ID, notify);
    }

    private void notifyMainActivity(String status) {
        Log.d(TAG,"notifyMainActivity() entry.");
        // Let the Main Activity know we are playing the song.
        Intent playingIntent = new Intent(status);
        playingIntent.putExtra("songIndex", playingIndexInfo.getTrackIndex());
        LocalBroadcastManager.getInstance(this).sendBroadcast(playingIntent);
    }

    private int[] genPlayOrder(final int size) {
        int[] rslt = new int[size];

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
            Log.d(TAG, "addToHistory(): history[" + historyPosition + "]=" + trackIndex);
            history[historyPosition] = trackIndex;
        }
    }
}
