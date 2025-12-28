package com.example.musicplayer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PlaylistActivity extends AppCompatActivity implements PlaylistAdapter.OnPlaylistClickListener {

    private SongViewModel songViewModel;
    private PlaylistAdapter playlistAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        songViewModel = new ViewModelProvider(this).get(SongViewModel.class);

        RecyclerView recyclerView = findViewById(R.id.recyclerView_playlists);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        playlistAdapter = new PlaylistAdapter(this, this);
        recyclerView.setAdapter(playlistAdapter);

        songViewModel.allPlaylistsWithCount.observe(this, playlistsWithCount -> {
            if (playlistsWithCount != null && !playlistsWithCount.isEmpty()) {
                playlistAdapter.setPlaylists(playlistsWithCount);
            } else {
                playlistAdapter.setPlaylists(null);
                Toast.makeText(this, "No playlists created yet.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    @Override
    public void onPlaylistClick(Playlist playlist) {
        Intent detailIntent = new Intent(this, PlaylistDetailActivity.class);

        detailIntent.putExtra("PLAYLIST_ID", playlist.playlistId);
        detailIntent.putExtra("PLAYLIST_NAME", playlist.name);

        startActivity(detailIntent);
    }
    @Override
    public void onPlaylistLongClick(Playlist playlist) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Playlist")
                .setMessage("Are you sure you want to delete '" + playlist.name + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    songViewModel.deletePlaylist(playlist.playlistId, playlist.name);
                    Toast.makeText(this, "Playlist deleted.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                .show();
    }
}
