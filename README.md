MODERN ANDROID MUSIC PLAYER

A straightforward, locally-focused music player for Android, built with modern components and background playback support. 
This app demonstrates core Android development concepts including Service-based background audio, Room for database management, and a UI built with Google's Material Design components.

FEATURES

• Local Music Playback: Scans and plays audio files directly from the device's storage.

• Playlist Management:◦Create new playlists.◦Add songs to any playlist.

    ◦ View and play songs within a playlist.
    ◦ Delete songs and entire playlists.
• Full Playback Control:◦Standard controls: Play, Pause, Next, Previous, and a responsive Seek Bar.

◦ Advanced modes: Shuffle queue, and cycle between Repeat All, Repeat One, and Repeat Off.

◦ Background Playback: Music continues to play seamlessly in the background thanks to a foreground Service.

• Rich Media Notification:◦Full media controls directly in the notification, including shuffle and repeat.

◦ Correctly handles audio focus interruptions (e.g., from a phone call or another media app).

• Modern Material You UI: 

   ◦Clean, card-based layouts for song and playlist lists.
   
   ◦Consistent UI styling using MaterialToolbar, FloatingActionButton, and MaterialAlertDialogBuilder.
   
   ◦Subtle animations to indicate the currently playing song.

TECH STACK AND KEY COMPONENTS

• Architecture:

  ◦MVVM (Model-View-ViewModel)◦ViewModel and LiveData for managing UI-related data in a lifecycle-aware way.
  
  ◦Repository Pattern to abstract data sources.
  
• UI:

    ◦RecyclerView for displaying efficient, scrollable lists.◦Material Components for Android (MaterialToolbar, FloatingActionButton, MaterialCardView, MaterialAlertDialogBuilder) for a modern, standard look and feel.
    
• Database:

    ◦Room Persistence Library to locally save and manage user-created playlists.
    
• Media Playback:◦Service and MediaPlayer for robust background audio playback.

    ◦MediaSessionCompat to handle media controls and integrate with the Android system.
    
• Concurrency:

    ◦ExecutorService for performing database and disk I/O off the main thread.
