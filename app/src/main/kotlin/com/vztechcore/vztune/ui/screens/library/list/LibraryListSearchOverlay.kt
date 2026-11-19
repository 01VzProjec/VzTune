package com.vztechcore.vztune.ui.screens.library.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.FollowedArtist
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.cards.ArtistCircleCard
import com.vztechcore.vztune.ui.components.cards.TrackListRow
import com.vztechcore.vztune.ui.components.feedback.VzEmptyState
import com.vztechcore.vztune.ui.components.layout.VzSearchInputBar
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.navigation.Search

@Composable
internal fun LibraryListSearchOverlay(
    tracks: List<Track>,
    artists: List<FollowedArtist>,
    isArtistMode: Boolean,
    onClose: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onArtistClick: (FollowedArtist) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val filteredTracks = remember(tracks, query) {
        if (query.isBlank()) tracks.sortedBy { it.title.lowercase() }
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
        }
    }
    val filteredArtists = remember(artists, query) {
        if (query.isBlank()) artists.sortedBy { it.name.lowercase() }
        else artists.filter { it.name.contains(query, ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0F1E))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            VzSearchInputBar(
                query = query,
                onQueryChange = { query = it },
                onClose = onClose,
                placeholder = if (isArtistMode) "Pesquisar artista…" else "Pesquisar faixa…",
                focusRequester = focusRequester,
                onSearch = { keyboardController?.hide() },
            )

            val count = if (isArtistMode) filteredArtists.size else filteredTracks.size
            Text(
                text = if (query.isBlank()) "$count ${if (isArtistMode) "artistas" else "faixas"} • A → Z"
                else "$count resultados",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            when {
                isArtistMode && filteredArtists.isEmpty() -> VzEmptyState(text = if (query.isBlank()) "Nada por aqui." else "Nada encontrado para \"$query\"", fillMaxSize = true)
                !isArtistMode && filteredTracks.isEmpty() -> VzEmptyState(text = if (query.isBlank()) "Nada por aqui." else "Nada encontrado para \"$query\"", fillMaxSize = true)

                isArtistMode -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filteredArtists, key = { it.id }) { artist ->
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
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        filteredTracks,
                        key = { _, t -> "${t.source.name}_${t.id}" },
                    ) { _, track ->
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
}
