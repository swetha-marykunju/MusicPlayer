package com.example.musicplayer;

import android.app.Application;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SongRepository {

    private static final String TAG = "SongRepository";
    private final ContentResolver contentResolver;
    private final PlaylistDao playlistDao;
    private final AppDatabase db;

    public SongRepository(Application application) {
        this.contentResolver = application.getContentResolver();
        this.db = AppDatabase.getDatabase(application);
        this.playlistDao = db.playlistDao();
    }

    public List<Song> loadSongs() {
        List<Song> songs = new ArrayList<>();

        Uri mediaStoreUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        try (Cursor cursor = contentResolver.query(
                mediaStoreUri,
                projection,
                selection,
                null,
                sortOrder)) {

            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

                do {
                    long id = cursor.getLong(idColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    long duration = cursor.getLong(durationColumn);
                    long albumId = cursor.getLong(albumIdColumn);

                    Uri contentUri = Uri.withAppendedPath(mediaStoreUri, String.valueOf(id));

                    songs.add(new Song(
                            id,
                            title,
                            artist,
                            contentUri.toString(),
                            duration,
                            albumId
                    ));
                } while (cursor.moveToNext());

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return songs;
    }

    public List<Long> getSongIdsForPlaylist(int playlistId) {
        return playlistDao.getSongIdsForPlaylist(playlistId);
    }

    public LiveData<List<PlaylistWithCount>> getPlaylistsWithSongCount() {
        return playlistDao.getPlaylistsWithSongCount();
    }

    public LiveData<List<Song>> getSongsInPlaylist(int playlistId) {
        LiveData<List<Long>> songIdsLiveData = playlistDao.getSongIdsForPlaylistLiveData(playlistId);

        return Transformations.switchMap(songIdsLiveData, songIds -> {
            if (songIds == null || songIds.isEmpty()) {
                MutableLiveData<List<Song>> emptyList = new MutableLiveData<>();
                emptyList.setValue(Collections.emptyList());
                return emptyList;
            }
            return getSongObjectsFromIds(songIds);
        });
    }
    public LiveData<List<Song>> getSongObjectsFromIds(List<Long> songIds) {
        MutableLiveData<List<Song>> result = new MutableLiveData<>();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Song> songs = new ArrayList<>();
            Uri mediaStoreUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

            StringBuilder selectionBuilder = new StringBuilder(MediaStore.Audio.Media._ID + " IN (");
            String[] selectionArgs = new String[songIds.size()];
            for (int i = 0; i < songIds.size(); i++) {
                selectionBuilder.append("?");
                if (i < songIds.size() - 1) {
                    selectionBuilder.append(",");
                }
                selectionArgs[i] = String.valueOf(songIds.get(i));
            }
            selectionBuilder.append(")");

            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION
            };

            try (Cursor cursor = contentResolver.query(
                    mediaStoreUri,
                    projection,
                    selectionBuilder.toString(),
                    selectionArgs,
                    MediaStore.Audio.Media.TITLE + " ASC")) {

                if (cursor != null && cursor.moveToFirst()) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

                    do {
                        long id = cursor.getLong(idColumn);
                        String title = cursor.getString(titleColumn);
                        String artist = cursor.getString(artistColumn);
                        long duration = cursor.getLong(durationColumn);

                        Uri contentUri = Uri.withAppendedPath(mediaStoreUri, String.valueOf(id));

                        songs.add(new Song(
                                id,
                                title,
                                artist,
                                contentUri.toString(),
                                duration,
                                0L
                        ));
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            result.postValue(songs);
        });

        return result;
    }

    public void removeSongFromPlaylist(int playlistId, long songId) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                playlistDao.removeSongFromPlaylist(playlistId, songId)
        );
    }
    public void deletePlaylist(Playlist playlist) {
        AppDatabase.databaseWriteExecutor.execute(() -> playlistDao.deletePlaylist(playlist));
    }
}
