package com.vztechcore.vztune.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class PlaylistFilterType(val label: String) {
    ALL("Tudo"),
    MUSIC("Músicas"),
    VIDEO("Vídeos")
}

@Composable
fun SearchPlaylistFilterTab(
    selected: PlaylistFilterType,
    onSelect: (PlaylistFilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlaylistFilterType.values().forEach { tab ->
            val isSelected = tab == selected
            val bg = if (isSelected) Color.White else Color.White.copy(alpha = 0.08f)
            val fg = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f)

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
}