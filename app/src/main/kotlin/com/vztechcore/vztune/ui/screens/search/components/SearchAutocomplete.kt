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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.ui.components.NowPlayingThumbnail

@Composable
fun SearchAutocomplete(
    query: String,
    history: List<String>,
    suggestions: SearchSuggestions,
    showHistory: Boolean,
    onPickQuery: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPickSong: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 200.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (showHistory) {
            if (history.isEmpty()) {
                item { EmptyHistory() }
            } else {
                item { ClearHistoryRow(onClearHistory) }
                items(history, key = { "h_$it" }) { q ->
                    HistoryRow(
                        query = q,
                        onPick = { onPickQuery(q) },
                        onRemove = { onRemoveHistory(q) },
                    )
                }
            }
        } else {
            // 1. Songs (up to 3-5 based on query length)
            items(suggestions.songs, key = { "sg_${it.id}" }) { track ->
                SuggestSongRow(track = track, onClick = { onPickSong(track) })
            }

            // Spacer between songs and queries
            if (suggestions.songs.isNotEmpty() && suggestions.queries.isNotEmpty()) {
                item { Spacer(Modifier.height(12.dp)) }
            }

            // 2. Text Queries (with highlighting)
            items(suggestions.queries, key = { "q_$it" }) { q ->
                SuggestQueryRow(
                    query = q,
                    currentInput = query,
                    onPick = { onPickQuery(q) },
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Box(
        Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Nenhuma pesquisa ainda.\nComece digitando algo :)",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun ClearHistoryRow(onClearAll: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClearAll() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = null,
            tint = Color(0xFFFBBF24),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Excluir Histórico de Pesquisa",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFBBF24),
            fontWeight = FontWeight.SemiBold,
        )
    }
    Spacer(Modifier.height(4.dp))
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
            .clip(RoundedCornerShape(10.dp))
            .clickable { onPick() }
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = query,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remover",
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SuggestSongRow(track: Track, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NowPlayingThumbnail(
            model = track.thumbnailUrl,
            isPlaying = false,
            modifier = Modifier.size(44.dp),
            cornerRadius = 6.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SuggestQueryRow(query: String, currentInput: String, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onPick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))

        // Highlight matching text (bold)
        val annotated = buildAnnotatedString {
            val start = query.indexOf(currentInput, ignoreCase = true)
            if (start != -1 && currentInput.isNotBlank()) {
                append(query.substring(0, start))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(query.substring(start, start + currentInput.length))
                }
                append(query.substring(start + currentInput.length))
            } else {
                append(query)
            }
        }

        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}