package com.vztechcore.vztune.ui.navigation

import androidx.navigation.NavController
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track

/** Switches the bottom-bar tab, preserving each tab's saved state. */
fun NavController.navigateToTab(tab: AppTab) {
    val route = when (tab) {
        AppTab.HOME -> Routes.Home
        AppTab.ANALYTICS -> Routes.Analytics
        AppTab.LIBRARY -> Routes.Library
        AppTab.SEARCH -> Routes.Search
    }
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Albums and singles open the album screen (same MediaList screen with isAlbum = true). */
fun NavController.openAlbum(album: Album) {
    navigate(
        Routes.MediaList(
            id = album.id,
            isAlbum = true,
            title = album.title,
            subtitle = album.artist,
            thumbnailUrl = album.thumbnailUrl,
        )
    )
}

fun NavController.openPlaylist(playlist: Playlist) {
    navigate(
        Routes.MediaList(
            id = playlist.id,
            isAlbum = false,
            title = playlist.title,
            subtitle = playlist.owner,
            thumbnailUrl = playlist.thumbnailUrl,
        )
    )
}

fun NavController.navigateToTrackInfo(track: Track) {
    navigate(
        Routes.TrackInfo(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            thumbnailUrl = track.thumbnailUrl,
            sourceName = track.source.name,
            durationSeconds = track.durationSeconds,
        )
    )
}