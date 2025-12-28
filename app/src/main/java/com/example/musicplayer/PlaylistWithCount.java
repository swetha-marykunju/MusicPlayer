package com.example.musicplayer;

public class PlaylistWithCount {
    public int playlistId;
    public String name;

    public int songCount;

    public PlaylistWithCount(int playlistId, String name, int songCount) {
        this.playlistId = playlistId;
        this.name = name;
        this.songCount = songCount;
    }

}
