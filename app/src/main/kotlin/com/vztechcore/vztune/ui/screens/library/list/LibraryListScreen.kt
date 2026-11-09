package com.vztechcore.vztune.ui.screens.library.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.FollowedArtist
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.cards.ArtistCircleCard
import com.vztechcore.vztune.ui.components.layout.DetailTopBar
import com.vztechcore.vztune.ui.components.cards.TrackListRow
import com.vztechcore.vztune.ui.screens.library.EmptyMessage

/** List opened from a Library shortcut card: tracks (favorites, most played) or followed artists. */
@Composable
fun LibraryListScreen(
    state: LibraryListUiState,
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onArtistClick: (FollowedArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        DetailTopBar(title = state.shortcut.label, onBackClick = onBackClick)

        when {
            state.isEmpty -> EmptyMessage(state.emptyMessage)

            state.isArtistList -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.artists, key = { it.id }) { artist ->
                    ArtistCircleCard(
                        name = artist.name,
                        imageUrl = artist.thumbnailUrl,
                        onClick = { onArtistClick(artist) },
                        size = 150.dp,
                    )
                }
                item(key = "credits", span = { GridItemSpan(maxLineSpan) }) { AppCredits() }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 140.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.tracks, key = { "${it.source.name}_${it.id}" }) { track ->
                    TrackListRow(
                        track = track,
                        onClick = { onTrackClick(track) },
                        onMoreClick = { onTrackInfoClick(track) },
                    )
                }
                item(key = "credits") { AppCredits() }
            }
        }
    }
}
