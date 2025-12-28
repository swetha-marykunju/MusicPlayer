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

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {
    private List<PlaylistWithCount> playlistList = new ArrayList<>();

    private final LayoutInflater inflater;
    private OnPlaylistClickListener playlistClickListener;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
        void onPlaylistLongClick(Playlist playlist);
    }

    public PlaylistAdapter(Context context, OnPlaylistClickListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.playlistClickListener = listener;
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        PlaylistWithCount currentItem = playlistList.get(position);

        holder.nameTextView.setText(currentItem.name);

        String countText = currentItem.songCount == 1 ? "1 song" : currentItem.songCount + " songs";
        holder.countTextView.setText(countText);

        holder.itemView.setOnClickListener(v -> {
            if (playlistClickListener != null) {
                Playlist clickedPlaylist = new Playlist(currentItem.name, 0);
                clickedPlaylist.playlistId = currentItem.playlistId;

                playlistClickListener.onPlaylistClick(clickedPlaylist);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (playlistClickListener != null) {
                Playlist clickedPlaylist = new Playlist(currentItem.name, 0);
                clickedPlaylist.playlistId = currentItem.playlistId;

                playlistClickListener.onPlaylistLongClick(clickedPlaylist);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return playlistList.size();
    }

    public void setPlaylists(List<PlaylistWithCount> playlists) {
        this.playlistList = playlists;
        notifyDataSetChanged();
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        final TextView nameTextView;
        final TextView countTextView;

        PlaylistViewHolder(View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.text_playlist_name);
            countTextView = itemView.findViewById(R.id.text_song_count);
        }
    }
}
