package com.example.musicplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class MainActivity extends AppCompatActivity implements SongAdapter.OnSongClickListener {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private SongViewModel songViewModel;
    private SongAdapter songAdapter;

    private MusicService musicService;
    private Intent playIntent;
    private boolean isBound = false;

    private TextView textSongTitle, textSongArtist, textCurrentTime, textTotalTime;
    private ImageButton buttonPlayPause, buttonNext, buttonPrev, buttonRepeat, buttonShuffle;
    private SeekBar seekBarProgress;

    private int queuedSongIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        songViewModel = new ViewModelProvider(this).get(SongViewModel.class);

        RecyclerView recyclerView = findViewById(R.id.recyclerView_song_list);
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

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab_new_playlist);
        fab.setOnClickListener(v -> showCreatePlaylistDialog());

        setupPlaybackControls();
        setupSeekBarListener();

        if (checkPermissions()) {
            observeViewModel();
        } else {
            requestPermissions();
        }

        observePlaybackState();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_view_playlists) {
            Intent intent = new Intent(this, PlaylistActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupSeekBarListener() {
        seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isBound && musicService != null) {
                    musicService.seekTo(seekBar.getProgress());
                }
            }
        });
    }

    private void observePlaybackState() {
        songViewModel.playbackState.observe(this, state -> {
            if (state == null) return;

            textSongTitle.setText(state.getTitle());
            textSongArtist.setText(state.getArtist());

            buttonPlayPause.setImageResource(state.isPlaying()
                    ? android.R.drawable.ic_media_pause
                    : android.R.drawable.ic_media_play);

            if (seekBarProgress.getMax() != state.getDuration()) {
                seekBarProgress.setMax(state.getDuration());
                textTotalTime.setText(formatDuration(state.getDuration()));
            }

            seekBarProgress.setProgress(state.getCurrentPosition());
            textCurrentTime.setText(formatDuration(state.getCurrentPosition()));
        });
    }

    private String formatDuration(int duration) {
        long minutes = (duration / 1000) / 60;
        long seconds = (duration / 1000) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, PERMISSION_REQUEST_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            observeViewModel();
        } else {
            Toast.makeText(this, "Permission denied. Cannot load music.", Toast.LENGTH_LONG).show();
        }
    }

    private void observeViewModel() {
        songViewModel.songList.observe(this, songs -> {
            if (songs != null && !songs.isEmpty()) {
                songAdapter.setSongs(songs);
                if (isBound && musicService != null) {
                    musicService.setList(songs);
                }
            } else {
                Toast.makeText(this, "No music files found.", Toast.LENGTH_LONG).show();
            }
        });
        songViewModel.loadSongs();
    }

    private final ServiceConnection musicConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            musicService.setViewModel(songViewModel);
            songViewModel.setMusicServiceCallback(musicService);
            isBound = true;

            List<Song> currentSongs = songViewModel.songList.getValue();
            if (currentSongs != null && !currentSongs.isEmpty()) {
                musicService.setList(currentSongs);
            }

            if (musicService.getCurrentSong() == null) {
                musicService.loadPlaybackState();
            } else {
                musicService.syncCurrentState();
            }
            updateRepeatButtonIcon(musicService.getRepeatMode());
            updateShuffleButtonIcon(musicService.isShuffleOn());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (playIntent == null) {
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isBound && musicService != null) {
            musicService.setViewModel(songViewModel);
            musicService.syncCurrentState();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(musicConnection);
        }
    }

    @Override
    public void onSongClick(int index) {
        if (isBound) {
            List<Song> currentQueue = songAdapter.getSongList();
            songViewModel.startPlayback(currentQueue, index);
        } else {
            queuedSongIndex = index;
        }
    }

    @Override
    public void onSongLongClick(Song song) {
        showAddToPlaylistDialog(song);
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

    private void updateRepeatButtonIcon(int mode) {
        int iconRes;
        String toastText;
        switch (mode) {
            case MusicService.REPEAT_MODE_ONE:
                iconRes = R.drawable.repeatone;
                break;
            case MusicService.REPEAT_MODE_ALL:
                iconRes = R.drawable.repeaton;
                break;
            default:
                iconRes = R.drawable.repeat;
                break;
        }
        buttonRepeat.setImageResource(iconRes);
    }

    private void updateShuffleButtonIcon(boolean isShuffleOn) {
        if (isShuffleOn) {
            buttonShuffle.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, com.google.android.material.R.color.design_default_color_primary)));
        } else {
            buttonShuffle.setImageTintList(null);
        }
    }

    private void showCreatePlaylistDialog() {
        final EditText input = new EditText(this);
        input.setHint("Enter Playlist Name");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Create New Playlist")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String playlistName = input.getText().toString().trim();
                    if (!playlistName.isEmpty()) {
                        songViewModel.createPlaylist(playlistName);
                        Toast.makeText(this, "Playlist '" + playlistName + "' created.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                .show();
    }

    private void showAddToPlaylistDialog(Song song) {
        songViewModel.allPlaylists.observe(this, new Observer<List<Playlist>>() {
            @Override
            public void onChanged(List<Playlist> playlists) {
                songViewModel.allPlaylists.removeObserver(this);
                if (playlists == null || playlists.isEmpty()) {
                    Toast.makeText(MainActivity.this, "No playlists. Create one first!", Toast.LENGTH_LONG).show();
                    return;
                }
                final CharSequence[] playlistNames = new CharSequence[playlists.size()];
                for (int i = 0; i < playlists.size(); i++) {
                    playlistNames[i] = playlists.get(i).name;
                }
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("Add '" + song.getTitle() + "' to:")
                        .setItems(playlistNames, (dialog, which) -> {
                            Playlist selectedPlaylist = playlists.get(which);
                            songViewModel.addSongToPlaylist(selectedPlaylist.playlistId, song.getId());
                            Toast.makeText(MainActivity.this, "Added to " + selectedPlaylist.name, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                        .show();
            }
        });
    }
}