package com.example.musicplayer;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SongViewModel extends AndroidViewModel {
    private final SongRepository repository;
    private final PlaylistDao playlistDao;

    private final MutableLiveData<List<Song>> _songList = new MutableLiveData<>();
    public final MutableLiveData<PlaybackState> playbackState = new MutableLiveData<>();
    public LiveData<List<Song>> songList = _songList;

    private boolean songsLoaded = false;

    public final LiveData<List<Playlist>> allPlaylists;
    public final LiveData<List<PlaylistWithCount>> allPlaylistsWithCount;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Executor databaseExecutor = AppDatabase.databaseWriteExecutor;

    public interface MusicServiceCallback {

        void onPlaylistLoadRequest(List<Long> songIds);

        void onPlaybackRequest(List<String> songPaths, int startIndex);

    }
    private MusicServiceCallback musicServiceCallback;

    public void setMusicServiceCallback(MusicServiceCallback callback) {
        this.musicServiceCallback = callback;
    }


    public SongViewModel(@NonNull Application application) {
        super(application);
        this.repository = new SongRepository(application);
        this.playlistDao = AppDatabase.getDatabase(application).playlistDao();

        allPlaylists = playlistDao.getAllPlaylists();
        allPlaylistsWithCount = repository.getPlaylistsWithSongCount();
    }
    public void loadSongs() {
        if (songsLoaded) return;

        executor.execute(() -> {
            List<Song> songs = repository.loadSongs();
            _songList.postValue(songs);
            songsLoaded = true;
        });
    }

    public void playPlaylist(int playlistId) {
        databaseExecutor.execute(() -> {
            List<Long> songIds = repository.getSongIdsForPlaylist(playlistId);

            if (songIds != null && !songIds.isEmpty()) {
                if (musicServiceCallback != null) {
                    musicServiceCallback.onPlaylistLoadRequest(songIds);
                }
            }
        });
    }

    public void updatePlaybackState(PlaybackState state) {
        playbackState.postValue(state);
    }
    public void createPlaylist(String name) {
        databaseExecutor.execute(() -> {
            Playlist newPlaylist = new Playlist(name, System.currentTimeMillis());
            playlistDao.insertPlaylist(newPlaylist);
        });
    }

    public void addSongToPlaylist(int playlistId, long songId) {
        databaseExecutor.execute(() -> {
            PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(playlistId, songId);
            playlistDao.insertSongToPlaylist(crossRef);
        });
    }


    public LiveData<List<Song>> getSongsInPlaylist(int playlistId) {
        return repository.getSongsInPlaylist(playlistId);
    }

    public void removeSongFromPlaylist(int playlistId, long songId) {
        repository.removeSongFromPlaylist(playlistId, songId);
    }
    public void deletePlaylist(int playlistId, String playlistName) {
        Playlist playlistToDelete = new Playlist(playlistName, 0);
        playlistToDelete.playlistId = playlistId;

        repository.deletePlaylist(playlistToDelete);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }

    public void startPlayback(List<Song> queue, int startIndex) {
        if (musicServiceCallback != null) {
            List<String> songPaths = new ArrayList<>();
            for (Song song : queue) {
                songPaths.add(song.getPath());
            }
            musicServiceCallback.onPlaybackRequest(songPaths, startIndex);
        }
    }
}
