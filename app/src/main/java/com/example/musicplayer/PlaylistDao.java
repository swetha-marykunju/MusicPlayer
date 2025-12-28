package com.example.musicplayer;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertPlaylist(Playlist playlist);

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    LiveData<List<Playlist>> getAllPlaylists();
    @Delete
    int deleteSongFromPlaylist(PlaylistSongCrossRef crossRef);

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId")
    LiveData<List<Long>> getSongIdsForPlaylistLiveData(int playlistId);

    @Query("SELECT " +
            "p.playlistId, p.name, " +
            "COUNT(psc.songId) AS songCount " +
            "FROM playlists p " +
            "LEFT JOIN playlist_songs psc ON p.playlistId = psc.playlistId " +
            "GROUP BY p.playlistId, p.name")
    LiveData<List<PlaylistWithCount>> getPlaylistsWithSongCount();

    @Delete
    void deletePlaylist(Playlist playlist);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertSongToPlaylist(PlaylistSongCrossRef crossRef);

    @Query("DELETE FROM playlist_songs WHERE playlistId = :pId AND songId = :sId")
    void removeSongFromPlaylist(int pId, long sId);

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId")
    List<Long> getSongIdsForPlaylist(int playlistId);


}
