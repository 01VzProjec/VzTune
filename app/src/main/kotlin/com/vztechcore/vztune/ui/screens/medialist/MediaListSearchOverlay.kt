package com.vztechcore.vztune.ui.screens.medialist

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
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.feedback.VzEmptyState
import com.vztechcore.vztune.ui.components.layout.VzSearchInputBar
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.navigation.Search

@Composable
internal fun MediaListSearchOverlay(
    allTracks: List<Track>,
    isAlbum: Boolean = false,
    onClose: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val nowPlaying = LocalNowPlaying.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val filteredTracks = remember(allTracks, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) allTracks
        else allTracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .statusBarsPadding(),
    ) {
        VzSearchInputBar(
            query = query,
            onQueryChange = { query = it },
            onClose = { keyboardController?.hide(); onClose() },
            placeholder = if (isAlbum) "Pesquisar no álbum..." else "Pesquisar na playlist...",
            focusRequester = focusRequester,
            onSearch = { keyboardController?.hide() },
            closeOnStart = true,
        )

        if (filteredTracks.isEmpty()) {
            VzEmptyState(text = "Nenhuma faixa encontrada para \"$query\"", fillMaxSize = true)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = filteredTracks,
                    key = { index, t -> "search_${t.source.name}_${t.id}_$index" },
                ) { _, track ->
                    val isCurrent = nowPlaying.isCurrent(track.id, track.source)
                    val originalIndex = if (isAlbum) {
                        allTracks.indexOfFirst { it.id == track.id && it.source == track.source }
                            .takeIf { it >= 0 }
                            ?.plus(1)
                    } else {
                        null
                    }

                    MediaListTrackRow(
                        track = track,
                        index = originalIndex,
                        isPlaying = isCurrent && nowPlaying.isPlaying,
                        isPaused = isCurrent && !nowPlaying.isPlaying,
                        onClick = {
                            keyboardController?.hide()
                            onTrackClick(track)
                        },
                        onMoreClick = { onTrackInfoClick(track) },
                        onPlayNext = { onPlayNextClick(track) },
                        showDragHandle = false,
                        dragHandleModifier = Modifier,
                    )
                }
                item(key = "credits_search") { AppCredits() }
            }
        }
    }
}
