package com.example.musicplayer;

import androidx.room.Entity;

@Entity(tableName = "playlist_songs",
        primaryKeys = {"playlistId", "songId"})
public class PlaylistSongCrossRef {
    public int playlistId;
    public long songId;

    public PlaylistSongCrossRef(int playlistId, long songId) {
        this.playlistId = playlistId;
        this.songId = songId;
    }
}
