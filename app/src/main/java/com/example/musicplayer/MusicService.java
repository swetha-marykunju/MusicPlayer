package com.example.musicplayer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MusicService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener,
        SongViewModel.MusicServiceCallback {

    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;
    private static final String TAG = "MusicService";
    private final IBinder musicBinder = new MusicBinder();
    private MediaPlayer mediaPlayer;

    private List<Song> libraryList;
    private List<Song> playbackQueue;
    private int songPosn = -1;
    private List<Song> originalPlaybackQueue;
    private boolean isShuffleOn = false;

    private SongViewModel songViewModel;
    private Handler handler = new Handler(Looper.getMainLooper());
    private final int UPDATE_FREQUENCY = 1000;
    private NotificationManager notificationManager;
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private AudioFocusRequest audioFocusRequest;
    private boolean wasPlayingWhenFocusLost = false;

    private static final int NOTIFY_ID = 1;
    private static final String CHANNEL_ID = "music_playback_channel";
    private static final String PREFS_NAME = "MusicPlayerPrefs";
    private static final String KEY_SONG_INDEX = "CurrentSongIndex";
    private static final String KEY_SONG_POSITION = "CurrentSongPosition";
    public static final int REPEAT_MODE_ONE = 1;
    public static final int REPEAT_MODE_ALL = 2;

    private int repeatMode = REPEAT_MODE_ALL;


    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();

        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCallback());
        mediaSession.setActive(true);
        transportControls = mediaSession.getController().getTransportControls();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioFocusChangeListener = this::handleAudioFocusChange;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return musicBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        savePlaybackState();
        if (mediaPlayer != null) mediaPlayer.release();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        abandonAudioFocus();
        handler.removeCallbacks(updatePositionTask);
    }

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }
    @Override
    public void onPlaylistLoadRequest(List<Long> songIds) {
        if (libraryList == null || libraryList.isEmpty()) {
            return;
        }

        List<Song> playlistQueue = new ArrayList<>();
        for (Long songId : songIds) {
            for (Song song : libraryList) {
                if (song.getId() == songId) {
                    playlistQueue.add(song);
                    break;
                }
            }
        }

        if (playlistQueue.isEmpty()) {
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(this, "Playlist is empty!", Toast.LENGTH_SHORT).show()
            );
            return;
        }

        setPlaybackQueue(playlistQueue);

        playSong(0);

        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(this, "Playing playlist!", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onPlaybackRequest(List<String> songPaths, int startIndex) {

        if (libraryList == null || libraryList.isEmpty()) {
            return;
        }

        List<Song> newPlaybackQueue = new ArrayList<>();
        for (String path : songPaths) {
            for (Song song : libraryList) {
                if (song.getPath().equals(path)) {
                    newPlaybackQueue.add(song);
                    break;
                }
            }
        }

        if (newPlaybackQueue.isEmpty()) {
            return;
        }

        setPlaybackQueue(newPlaybackQueue);

        if (startIndex >= 0 && startIndex < newPlaybackQueue.size()) {
            playSong(startIndex);
        } else {
            playSong(0);
        }
    }

    public void setPlaybackQueue(List<Song> newQueue) {
        this.playbackQueue = new ArrayList<>(newQueue);
        this.songPosn = 0;

        this.isShuffleOn = false;
        this.originalPlaybackQueue = null;
    }

    public void setList(List<Song> theSongs){
        this.libraryList = theSongs;
        if (this.playbackQueue == null || this.playbackQueue.isEmpty()) {
            this.playbackQueue = theSongs;
        }
    }

    public void playSong(int songIndex) {
        songPosn = songIndex;
        if (playbackQueue == null || songPosn < 0 || songPosn >= playbackQueue.size()) {
            return;
        }

        Song playSong = playbackQueue.get(songPosn);

        mediaPlayer.reset();

        try {
            mediaPlayer.setDataSource(getApplicationContext(), playSong.getContentUri());

            if (requestAudioFocus()) {
                mediaPlayer.prepareAsync();

                mediaPlayer.setOnPreparedListener(mp -> {
                    mp.start();
                    startMusicForeground();
                    pushPlaybackStateUpdate();
                    handler.post(updatePositionTask);
                });
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void nextSong() {
        songPosn++;
        if (songPosn >= playbackQueue.size()) {
            songPosn = 0;
        }
        playSong(songPosn);
    }

    public void prevSong() {
        final int RESTART_THRESHOLD_MS = 3000;
        if (mediaPlayer != null && mediaPlayer.getCurrentPosition() > RESTART_THRESHOLD_MS) {
            seekTo(0);
        } else {
            songPosn--;
            if (songPosn < 0) {
                songPosn = playbackQueue.size() - 1;
            }
            playSong(songPosn);
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            startMusicForeground();
            pushPlaybackStateUpdate();
            handler.removeCallbacks(updatePositionTask);
        }
    }

    public void resume() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            if (requestAudioFocus()) {
                mediaPlayer.start();
                startMusicForeground();
                pushPlaybackStateUpdate();
                handler.post(updatePositionTask);
            }
        }
    }
    public Song getCurrentSong() {
        if (playbackQueue != null && songPosn >= 0 && songPosn < playbackQueue.size()) {
            return playbackQueue.get(songPosn);
        }
        return null;
    }

    public int getPosition() { return mediaPlayer.getCurrentPosition(); }
    public int getDuration() { return mediaPlayer.getDuration(); }
    public boolean isPlaying() { return mediaPlayer.isPlaying(); }
    public int getRepeatMode() { return repeatMode; }

    public void seekTo(int pos) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(pos);
            startMusicForeground();
            pushPlaybackStateUpdate();
        }
    }

    public void stopPlayback() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            abandonAudioFocus();
            stopForeground(true);
        }
    }

    public void syncCurrentState() {
        pushPlaybackStateUpdate();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) { handler.post(updatePositionTask); }
    }

    public int cycleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3;
        return repeatMode;
    }

    public boolean isShuffleOn() {
        return isShuffleOn;
    }

    public void toggleShuffle() {
        if (playbackQueue == null || playbackQueue.isEmpty()) {
            isShuffleOn = false;
            return;
        }
        isShuffleOn = !isShuffleOn;
        if (isShuffleOn) {
            originalPlaybackQueue = new ArrayList<>(playbackQueue);
            Song currentSong = playbackQueue.get(songPosn);
            playbackQueue.remove(songPosn);
            Collections.shuffle(playbackQueue);
            playbackQueue.add(0, currentSong);
            songPosn = 0;
        } else {
            if (originalPlaybackQueue != null) {
                Song currentSong = playbackQueue.get(songPosn);
                playbackQueue = new ArrayList<>(originalPlaybackQueue);
                originalPlaybackQueue = null;
                songPosn = playbackQueue.indexOf(currentSong);
                if (songPosn == -1) songPosn = 0;
            }
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mp.reset();
        pushPlaybackStateUpdate();
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        handler.removeCallbacks(updatePositionTask);

        if (mediaPlayer.getCurrentPosition() > 0) {
            mp.reset();
            if (repeatMode == REPEAT_MODE_ONE) {
                playSong(songPosn);
            } else if (songPosn < playbackQueue.size() - 1) {
                nextSong();
            } else {
                if (repeatMode == REPEAT_MODE_ALL) {
                    nextSong();
                } else {
                    stopPlayback();
                    pushPlaybackStateUpdate();
                }
            }
        }
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Playback Controls",
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void startMusicForeground() {
        Song currentSong = getCurrentSong();
        if (currentSong == null) return;

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(currentSong.getTitle())
                .setContentText(currentSong.getArtist())
                .setContentIntent(contentIntent)
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));

        if (mediaPlayer.isPlaying()) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE));
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Play",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY));
        }
        builder.addAction(android.R.drawable.ic_media_previous, "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));
        builder.addAction(android.R.drawable.ic_media_next, "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT));

        startForeground(NOTIFY_ID, builder.build());
        updateMediaSessionMetadata(currentSong);
    }

    private void updateMediaSessionMetadata(Song song) {
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtist())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.getDuration())
                .build());
    }

    private void pushPlaybackStateUpdate() {
        if (songViewModel != null && getCurrentSong() != null) {
            PlaybackState state = new PlaybackState(
                    isPlaying(),
                    getPosition(),
                    getDuration(),
                    getCurrentSong().getTitle(),
                    getCurrentSong().getArtist()
            );
            songViewModel.updatePlaybackState(state);
            updateMediaSessionState();
        }
    }

    private void updateMediaSessionState() {
        int state = isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        long actions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_STOP | PlaybackStateCompat.ACTION_SEEK_TO;

        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(state, getPosition(), 1.0f)
                .setActions(actions)
                .build());
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override public void onPlay() { resume(); }
        @Override public void onPause() { pause(); }
        @Override public void onSkipToNext() { nextSong(); }
        @Override public void onSkipToPrevious() { prevSong(); }
        @Override public void onStop() { stopPlayback(); }
        @Override public void onSeekTo(long pos) { seekTo((int) pos); }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return START_STICKY;
    }

    public void setViewModel(SongViewModel viewModel) {
        this.songViewModel = viewModel;
    }

    private final Runnable updatePositionTask = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                pushPlaybackStateUpdate();
                handler.postDelayed(this, UPDATE_FREQUENCY);
            }
        }
    };

    private void handleAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (mediaPlayer != null) {
                    if (wasPlayingWhenFocusLost) {
                        resume();
                    }
                    mediaPlayer.setVolume(1.0f, 1.0f);
                    wasPlayingWhenFocusLost = false;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    pause();
                }
                wasPlayingWhenFocusLost = false;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    wasPlayingWhenFocusLost = true;
                    pause();
                } else {
                    wasPlayingWhenFocusLost = false;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(0.3f, 0.3f);
                }
                break;
        }
    }

    private boolean requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build();
            int result = audioManager.requestAudioFocus(audioFocusRequest);
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            int result = audioManager.requestAudioFocus(audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
    }

    private void abandonAudioFocus() {
        if (mediaPlayer != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
        }
    }

    private void savePlaybackState() {
        if (playbackQueue != null && songPosn >= 0 && songPosn < playbackQueue.size()) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_SONG_INDEX, songPosn)
                    .putInt(KEY_SONG_POSITION, mediaPlayer.getCurrentPosition())
                    .apply();
        }
    }

    public void loadPlaybackState() {
        if (playbackQueue == null || playbackQueue.isEmpty()) {
            return;
        }

        int savedIndex = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_SONG_INDEX, -1);
        int savedPosition = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_SONG_POSITION, 0);

        if (savedIndex >= 0 && savedIndex < playbackQueue.size()) {
            songPosn = savedIndex;
            Song savedSong = playbackQueue.get(songPosn);

            try {
                mediaPlayer.reset();
                mediaPlayer.setDataSource(getApplicationContext(), savedSong.getContentUri());
                mediaPlayer.prepare();
                mediaPlayer.seekTo(savedPosition);
                pushPlaybackStateUpdate();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
