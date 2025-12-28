package com.example.musicplayer;

import android.net.Uri;

public class Song {
    private long id;
    private String title;
    private String artist;
    private String path;
    private long duration;

    private long albumId;

    public Song(long id, String title, String artist, String path, long duration, long albumId) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.duration = duration;
        this.albumId = albumId;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getPath() { return path; }
    public long getDuration() { return duration; }

    public Uri getContentUri() {
        return Uri.withAppendedPath(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                String.valueOf(id));
    }
}
