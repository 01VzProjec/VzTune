package com.vztechcore.vztune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Look of the selected chip. */
enum class ChipStyle { LIGHT, ACCENT }

private val AccentBlue = Color(0xFF1E40AF)

/** Horizontally scrollable pill tabs, shared by the playlist, search and library screens. */
@Composable
fun <T> VzChipRow(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: (T) -> ImageVector? = { null },
    style: ChipStyle = ChipStyle.LIGHT,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items) { item ->
            val isSelected = item == selected
            val background = when {
                !isSelected -> Color.White.copy(alpha = 0.08f)
                style == ChipStyle.ACCENT -> AccentBlue
                else -> Color.White
            }
            val foreground = when {
                !isSelected -> Color.White.copy(alpha = 0.75f)
                style == ChipStyle.ACCENT -> Color.White
                else -> Color.Black
            }
            val chipIcon = icon(item)

            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chipIcon != null) {
                    Icon(chipIcon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = foreground,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
