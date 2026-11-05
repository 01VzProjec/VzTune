package com.vztechcore.vztune.ui.screens.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class PlaylistFilterType(val label: String, val description: String) {
    ALL("Tudo", "Mostrando playlists do YouTube e YouTube Music"),
    MUSIC("YT Music", "Mostrando playlists oficiais do YouTube Music"),
    VIDEO("YouTube", "Mostrando playlists criadas no YouTube")
}

@Composable
fun SearchPlaylistFilterTab(
    selected: PlaylistFilterType,
    onSelect: (PlaylistFilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlaylistFilterType.entries.forEach { tab ->
                val isSelected = tab == selected
                val bg = if (isSelected) Color(0xFF1E40AF) else Color.White.copy(alpha = 0.08f)
                val fg = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f)

                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = fg,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = selected.description,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}