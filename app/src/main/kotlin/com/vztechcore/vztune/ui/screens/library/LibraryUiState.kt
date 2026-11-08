package com.vztechcore.vztune.ui.screens.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vztechcore.vztune.core.domain.music.Track

/** The five tabs of the Library screen. */
enum class LibraryTab(val label: String, val icon: ImageVector) {
    OVERVIEW("Sua biblioteca", Icons.Filled.LibraryMusic),
    VZTUNE("Playlists VzTune", Icons.Filled.StarBorder),
    MY_PLAYLISTS("Suas playlists", Icons.AutoMirrored.Filled.PlaylistAdd),
    FAVORITES("Playlists favoritas", Icons.Filled.FavoriteBorder),
    DOWNLOADS("Playlists baixadas", Icons.Filled.Download),
}

/** The four shortcut cards on top of "Sua biblioteca"; each one opens its own list. */
enum class LibraryShortcut(val label: String, val icon: ImageVector, val accent: Color) {
    FAVORITES("Favoritos", Icons.Filled.Favorite, Color(0xFFEC4899)),
    MOST_PLAYED("Mais ouvidas", Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF8B5CF6)),
    FOLLOWED("Seguindo", Icons.Filled.Groups, Color(0xFF38BDF8)),
    DOWNLOADS("Baixadas", Icons.Filled.CloudDownload, Color(0xFF10B981)),
}

/** One playlist card of the Library grids (local, favorite or downloaded). */
data class LibraryPlaylistItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val mosaic: List<String> = emptyList(),
)

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.OVERVIEW,
    val recentTracks: List<Track> = emptyList(),
    val myPlaylists: List<LibraryPlaylistItem> = emptyList(),
    val favoritePlaylists: List<LibraryPlaylistItem> = emptyList(),
    // Filled when downloads are implemented.
    val downloadedPlaylists: List<LibraryPlaylistItem> = emptyList(),
)
