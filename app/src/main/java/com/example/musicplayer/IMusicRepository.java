package com.example.musicplayer;

import androidx.lifecycle.LiveData;

import java.util.List;

public interface IMusicRepository {
    List<Song> loadSongs();
    LiveData<List<Song>> getSongsInPlaylist(int playlistId);
}
