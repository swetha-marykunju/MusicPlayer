package com.example.musicplayer;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

public class PlaylistDetailActivity extends AppCompatActivity
        implements SongAdapter.OnSongClickListener {
    private int currentPlaylistId;
    private String currentPlaylistName;
    private SongViewModel songViewModel;
    private SongAdapter songAdapter;

    private MusicService musicService;
    private boolean isBound = false;

    private TextView textSongTitle, textSongArtist, textCurrentTime, textTotalTime;
    private ImageButton buttonPlayPause, buttonNext, buttonPrev, buttonRepeat, buttonShuffle;
    private SeekBar seekBarProgress;

    private boolean isUserSeeking = false;

    private final Runnable resetSeekingTask = () -> isUserSeeking = false;

    private final ServiceConnection musicConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();

            songViewModel.setMusicServiceCallback(musicService);
            musicService.setViewModel(songViewModel);
            isBound = true;

            if (songViewModel.songList.getValue() != null && !songViewModel.songList.getValue().isEmpty()) {
                musicService.setList(songViewModel.songList.getValue());
            }

            if (musicService.getCurrentSong() != null) {
                musicService.syncCurrentState();
                updateRepeatButtonIcon(musicService.getRepeatMode());
                updateShuffleButtonIcon(musicService.isShuffleOn());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_detail);

        currentPlaylistId = getIntent().getIntExtra("PLAYLIST_ID", -1);
        currentPlaylistName = getIntent().getStringExtra("PLAYLIST_NAME");

        if (currentPlaylistId == -1) {
            Toast.makeText(this, "Error: Invalid Playlist ID.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        songViewModel = new ViewModelProvider(this).get(SongViewModel.class);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(currentPlaylistName);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recyclerView_playlist_songs);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        songAdapter = new SongAdapter(this, this);
        recyclerView.setAdapter(songAdapter);

        textSongTitle = findViewById(R.id.text_song_title);
        textSongArtist = findViewById(R.id.text_song_artist);
        textCurrentTime = findViewById(R.id.text_current_time);
        textTotalTime = findViewById(R.id.text_total_time);
        buttonPlayPause = findViewById(R.id.button_play_pause);
        buttonNext = findViewById(R.id.button_next);
        buttonPrev = findViewById(R.id.button_prev);
        buttonRepeat = findViewById(R.id.button_repeat);
        buttonShuffle = findViewById(R.id.button_shuffle);
        seekBarProgress = findViewById(R.id.seekBar_progress);

        setupPlaybackControls();
        setupSeekBarListener();

        songViewModel.getSongsInPlaylist(currentPlaylistId).observe(this, songs -> {
            songAdapter.setSongs(songs);
            if (songs == null || songs.isEmpty()) {
                Toast.makeText(this, currentPlaylistName + " is empty.", Toast.LENGTH_LONG).show();
            }
        });

        songViewModel.loadSongs();

        observePlaybackState();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, musicConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isBound && musicService != null) {
            musicService.setViewModel(songViewModel);
            if (musicService.getCurrentSong() != null) {
                musicService.syncCurrentState();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(musicConnection);
            isBound = false;
        }
    }

    @Override
    public void onSongClick(int index) {
        Song clickedSong = songAdapter.getSongAtPosition(index);

        if (clickedSong != null) {
            if (isBound) {
                List<Song> currentQueue = songAdapter.getSongList();
                songViewModel.startPlayback(currentQueue, index);
            } else {
                Toast.makeText(this, "Service not connected yet...", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onSongLongClick(Song song) {
        showRemoveSongConfirmationDialog(song);
    }

    private void showRemoveSongConfirmationDialog(Song song) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Song")
                .setMessage("Are you sure you want to remove '" + song.getTitle() + "' from '" + currentPlaylistName + "'?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    songViewModel.removeSongFromPlaylist(currentPlaylistId, song.getId());
                    Toast.makeText(this, song.getTitle() + " removed.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                .show();
    }

    private void setupPlaybackControls() {
        buttonPlayPause.setOnClickListener(v -> {
            if (musicService == null) return;
            if (musicService.isPlaying()) {
                musicService.pause();
            } else {
                musicService.resume();
            }
        });

        buttonNext.setOnClickListener(v -> {
            if (musicService != null) musicService.nextSong();
        });

        buttonPrev.setOnClickListener(v -> {
            if (musicService != null) musicService.prevSong();
        });

        buttonRepeat.setOnClickListener(v -> {
            if (musicService != null) {
                int newMode = musicService.cycleRepeatMode();
                updateRepeatButtonIcon(newMode);
            }
        });

        buttonShuffle.setOnClickListener(v -> {
            if (musicService != null) {
                musicService.toggleShuffle();
                updateShuffleButtonIcon(musicService.isShuffleOn());
            }
        });
    }

    private void setupSeekBarListener() {
        seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    textCurrentTime.setText(formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
                seekBar.removeCallbacks(resetSeekingTask);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isBound && musicService != null) {
                    musicService.seekTo(seekBar.getProgress());
                }
                seekBar.postDelayed(resetSeekingTask, 1000);
            }
        });
    }

    private void observePlaybackState() {
        songViewModel.playbackState.observe(this, state -> {
            if (state == null) return;

            textSongTitle.setText(state.getTitle());
            textSongArtist.setText(state.getArtist());

            buttonPlayPause.setImageResource(state.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

            if (seekBarProgress.getMax() != state.getDuration()) {
                seekBarProgress.setMax(state.getDuration());
                textTotalTime.setText(formatDuration(state.getDuration()));
            }

            if (!isUserSeeking) {
                seekBarProgress.setProgress(state.getCurrentPosition());
                textCurrentTime.setText(formatDuration(state.getCurrentPosition()));
            }
        });
    }

    private void updateRepeatButtonIcon(int mode) {
        int iconRes;
        switch (mode) {
            case MusicService.REPEAT_MODE_ONE:
                iconRes = android.R.drawable.ic_menu_mylocation;
                break;
            case MusicService.REPEAT_MODE_ALL:
                iconRes = android.R.drawable.ic_menu_rotate;
                break;
            default:
                iconRes = android.R.drawable.ic_menu_close_clear_cancel;
                break;
        }
        buttonRepeat.setImageResource(iconRes);
    }

    private void updateShuffleButtonIcon(boolean isShuffleOn) {
        if (isShuffleOn) {
            buttonShuffle.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor
                    (this, com.google.android.material.R.color.design_default_color_primary)));
        } else {
            buttonShuffle.setImageTintList(null);
        }
    }

    private String formatDuration(int duration) {
        long minutes = (duration / 1000) / 60;
        long seconds = (duration / 1000) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
