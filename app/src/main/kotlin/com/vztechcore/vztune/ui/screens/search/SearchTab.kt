package com.vztechcore.vztune.ui.screens.search

import androidx.annotation.StringRes
import com.vztechcore.vztune.R

/**
 * Tabs available on the search screen.
 * Each maps to a different result type / source combination.
 */
enum class SearchTab(@StringRes val labelRes: Int) {
    ALL(R.string.search_tab_all),
    SONGS(R.string.search_tab_songs),
    ARTISTS(R.string.search_tab_artists),
    ALBUMS(R.string.search_tab_albums),
    PLAYLISTS(R.string.search_tab_playlists),
    VIDEOS(R.string.search_tab_videos),
}