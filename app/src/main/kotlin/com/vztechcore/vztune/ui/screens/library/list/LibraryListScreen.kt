package com.vztechcore.vztune.ui.screens.library.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.FollowedArtist
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.cards.ArtistCircleCard
import com.vztechcore.vztune.ui.components.cards.TrackListRow
import com.vztechcore.vztune.ui.components.layout.DetailTopBar
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.navigation.Search
import com.vztechcore.vztune.ui.icons.player.Play
import com.vztechcore.vztune.ui.icons.player.Shuffle
import com.vztechcore.vztune.ui.screens.library.EmptyMessage

private val ActionBlue = Color(0xFF1E40AF)

@Composable
fun LibraryListScreen(
    state: LibraryListUiState,
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onArtistClick: (FollowedArtist) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSearchOverlay by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        DetailTopBar(
            title = state.shortcut.label,
            onBackClick = onBackClick,
            actions = {
                if (state.isArtistList) {
                    TopBarCircleButton(VzIcons.Search, "Pesquisar") { showSearchOverlay = true }
                } else {
                    TopBarCircleButton(VzIcons.Shuffle, "Tocar faixa aleatória", onShuffleClick)
                    TopBarPlayPill(onClick = onPlayAllClick)
                    TopBarCircleButton(VzIcons.Search, "Pesquisar") { showSearchOverlay = true }
                }
            },
        )

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

    if (showSearchOverlay) {
        LibraryListSearchOverlay(
            tracks = state.tracks,
            artists = state.artists,
            isArtistMode = state.isArtistList,
            onClose = { showSearchOverlay = false },
            onTrackClick = onTrackClick,
            onTrackInfoClick = onTrackInfoClick,
            onArtistClick = onArtistClick,
        )
    }
}

@Composable
private fun TopBarCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TopBarPlayPill(onClick: () -> Unit) {
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(ActionBlue)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VzIcons.Play, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            "Play",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}