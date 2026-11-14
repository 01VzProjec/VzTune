package com.vztechcore.vztune.ui.screens.search.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits

private val RECENT_CARD_HEIGHT = 120.dp

/**
 * Home of the Search screen (shown while the input is empty, focused or not):
 *   1. horizontal row with tracks already played from Search
 *   2. search history, each row with the "search again" icon
 */
@Composable
fun SearchHome(
    recentTracks: List<Track>,
    history: List<String>,
    onPickTrack: (Track) -> Unit,
    onPickQuery: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 200.dp),
    ) {
        if (recentTracks.isNotEmpty()) {
            item(key = "recent_row") {
                Column(Modifier.fillMaxWidth()) {
                    SectionTitle("Tocadas recentemente")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recentTracks, key = { "r_${it.source.name}_${it.id}" }) { track ->
                            RecentTrackCard(track = track, onClick = { onPickTrack(track) })
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        if (history.isNotEmpty()) {
            item(key = "history_header") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { SectionTitle("Histórico de pesquisa", horizontalPadding = 0) }
                    Text(
                        text = "Limpar tudo",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFFBBF24),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onClearHistory() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
            items(history, key = { "h_$it" }) { q ->
                HistoryRow(
                    query = q,
                    onPick = { onPickQuery(q) },
                    onRemove = { onRemoveHistory(q) },
                )
            }
        }

        item(key = "credits") { AppCredits() }

        if (recentTracks.isEmpty() && history.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Nada por aqui ainda.\nPesquise algo pra começar :)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, horizontalPadding: Int = 16) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = horizontalPadding.dp),
    )
}

/**
 * Songs get a square thumbnail, videos a 16:9 one — both with the same height
 * (the video card is simply wider), title below.
 */
@Composable
private fun RecentTrackCard(track: Track, onClick: () -> Unit) {
    val isVideo = track.source == Source.YOUTUBE
    val cardWidth = if (isVideo) RECENT_CARD_HEIGHT * (16f / 9f) else RECENT_CARD_HEIGHT

    Column(
        Modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = cardWidth, height = RECENT_CARD_HEIGHT)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HistoryRow(
    query: String,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPick() }
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Search icon = tap to search this term again
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Pesquisar novamente",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = query,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = VzIcons.Close,
                contentDescription = "Remover",
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
