package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.theme.VzTuneTheme

@Preview(showBackground = true)
@Composable
private fun MediaListLoadingPreview() {
    VzTuneTheme {
        MediaListScreen(
            state = MediaListUiState(isLoading = true),
            onBackClick = {},
            onTabSelected = {},
            onTrackClick = {},
            onPlayAllClick = {},
            onShuffleClick = {},
            onSortSelected = {},
            onReorder = { _, _ -> },
            onStartShuffle = {},
            onShuffleOnce = {},
            onStopShuffle = {},
            onPlayNextClick = {},
            onTrackInfoClick = {},
            onFavoriteClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaListContentPreview() {
    VzTuneTheme {
        MediaListScreen(
            state = MediaListUiState(
                isLoading = false,
                title = "Daft Punk Essentials",
                subtitle = "Vários Artistas",
                isAlbum = false,
                allTracks = listOf(
                    Track(
                        id = "1",
                        title = "Get Lucky",
                        artist = "Daft Punk, Pharrell Williams",
                        durationSeconds = 369,
                        source = Source.YOUTUBE_MUSIC,
                    ),
                    Track(
                        id = "2",
                        title = "One More Time",
                        artist = "Daft Punk",
                        durationSeconds = 320,
                        source = Source.YOUTUBE_MUSIC,
                    ),
                    Track(
                        id = "3",
                        title = "Get Lucky (Official Video)",
                        artist = "Daft Punk",
                        durationSeconds = 248,
                        source = Source.YOUTUBE,
                    ),
                ),
            ),
            onBackClick = {},
            onTabSelected = {},
            onTrackClick = {},
            onPlayAllClick = {},
            onShuffleClick = {},
            onSortSelected = {},
            onReorder = { _, _ -> },
            onStartShuffle = {},
            onShuffleOnce = {},
            onStopShuffle = {},
            onPlayNextClick = {},
            onTrackInfoClick = {},
            onFavoriteClick = {},
        )
    }
}
