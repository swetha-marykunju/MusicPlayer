package com.example.musicplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {
    private List<Song> songList = new ArrayList<>();
    private final LayoutInflater inflater;

    private OnSongClickListener songClickListener;

    public interface OnSongClickListener {
        void onSongClick(int index);
        void onSongLongClick(Song song);
    }

    public SongAdapter(Context context, OnSongClickListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.songClickListener = listener;
    }
    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song currentSong = songList.get(position);
        holder.titleTextView.setText(currentSong.getTitle());
        holder.artistTextView.setText(currentSong.getArtist());
        holder.durationTextView.setText(formatDuration(currentSong.getDuration()));


        holder.itemView.setOnClickListener(v -> {
            if (songClickListener != null) {
                songClickListener.onSongClick(position);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (songClickListener != null) {
                songClickListener.onSongLongClick(currentSong);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }
    public void setSongs(List<Song> songs) {
        this.songList = songs;
        notifyDataSetChanged();
    }

    private String formatDuration(long duration) {
        long minutes = (duration / 1000) / 60;
        long seconds = (duration / 1000) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        final TextView titleTextView;
        final TextView artistTextView;
        final TextView durationTextView;

        SongViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.text_song_title);
            artistTextView = itemView.findViewById(R.id.text_song_artist);
            durationTextView = itemView.findViewById(R.id.text_song_duration);
        }
    }

    public Song getSongAtPosition(int position) {
        if (position >= 0 && position < songList.size()) {
            return songList.get(position);
        }
        return null;
    }
    public List<Song> getSongList() {
        return songList;
    }
}
