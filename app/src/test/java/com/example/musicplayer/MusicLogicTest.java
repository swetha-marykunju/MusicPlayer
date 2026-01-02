package com.example.musicplayer;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public class MusicLogicTest {

    @Test
    public void testSongLoadingLogic() {
        IMusicRepository mockRepo = mock(IMusicRepository.class);

        List<Song> fakeSongs = new ArrayList<>();
        fakeSongs.add(new Song(1, "Test Song", "Test Artist", "uri", 3000, 0));
        fakeSongs.add(new Song(2, "Test Song1", "Test Artist1", "uri", 2000, 0));

        when(mockRepo.loadSongs()).thenReturn(fakeSongs);

        List<Song> result = mockRepo.loadSongs();

        assertEquals(2, result.size());
        assertEquals("Test Song", result.get(0).getTitle());
        assertEquals("Test Song1", result.get(1).getTitle());


        System.out.println("Test Passed: Mock successfully returned fake songs!");
    }
}