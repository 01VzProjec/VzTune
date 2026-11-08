package com.vztechcore.vztune.ui.screens.library.list

import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.FollowedArtist
import com.vztechcore.vztune.ui.screens.library.LibraryShortcut

data class LibraryListUiState(
    val shortcut: LibraryShortcut,
    val tracks: List<Track> = emptyList(),
    val artists: List<FollowedArtist> = emptyList(),
) {
    val isArtistList: Boolean get() = shortcut == LibraryShortcut.FOLLOWED

    val isEmpty: Boolean get() = if (isArtistList) artists.isEmpty() else tracks.isEmpty()

    /** Message shown when the list has nothing yet. */
    val emptyMessage: String
        get() = when (shortcut) {
            LibraryShortcut.FAVORITES -> "Você ainda não favoritou nenhuma faixa. Use \"Sobre a faixa\" para favoritar."
            LibraryShortcut.MOST_PLAYED -> "Toque algumas faixas para ver as mais ouvidas aqui."
            LibraryShortcut.FOLLOWED -> "Você ainda não segue nenhum artista."
            LibraryShortcut.DOWNLOADS -> "Nenhuma faixa baixada ainda."
        }
}
