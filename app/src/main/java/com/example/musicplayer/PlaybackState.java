package com.example.musicplayer;

public class PlaybackState {
    private final boolean isPlaying;

    private final int currentPosition;
    private final int duration;
    private final String title;
    private final String artist;

    public PlaybackState(boolean isPlaying, int currentPosition, int duration, String title, String artist) {
        this.isPlaying = isPlaying;
        this.currentPosition = currentPosition;
        this.duration = duration;
        this.title = title;
        this.artist = artist;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public int getDuration() {
        return duration;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }
}
