package com.vztechcore.vztune.ui.screens.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.StarOutline
import com.vztechcore.vztune.ui.icons.media.CloudDownload
import com.vztechcore.vztune.ui.icons.media.Download
import com.vztechcore.vztune.ui.icons.media.Groups
import com.vztechcore.vztune.ui.icons.media.PlaylistAdd
import com.vztechcore.vztune.ui.icons.media.TrendingUp
import com.vztechcore.vztune.ui.icons.navigation.LibraryMusicFilled

/** The five tabs of the Library screen. */
enum class LibraryTab(val label: String, val icon: ImageVector) {
    OVERVIEW("Sua biblioteca", VzIcons.LibraryMusicFilled),
    VZTUNE("Playlists VzTune", VzIcons.StarOutline),
    MY_PLAYLISTS("Suas playlists", VzIcons.PlaylistAdd),
    FAVORITES("Playlists favoritas", VzIcons.FavoriteOutline),
    DOWNLOADS("Playlists baixadas", VzIcons.Download),
}

/** The four shortcut cards on top of "Sua biblioteca"; each one opens its own list. */
enum class LibraryShortcut(val label: String, val icon: ImageVector, val accent: Color) {
    FAVORITES("Favoritos", VzIcons.FavoriteFilled, Color(0xFFEC4899)),
    MOST_PLAYED("Mais ouvidas", VzIcons.TrendingUp, Color(0xFF8B5CF6)),
    FOLLOWED("Seguindo", VzIcons.Groups, Color(0xFF38BDF8)),
    DOWNLOADS("Baixadas", VzIcons.CloudDownload, Color(0xFF10B981)),
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
    val downloadedPlaylists: List<LibraryPlaylistItem> = emptyList(),
)