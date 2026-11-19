package com.vztechcore.vztune.ui.components.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.media.Download
import com.vztechcore.vztune.ui.theme.DownloadGreen

/**
 * UNICA linha de opcao dos bottom sheets.
 * Antes: SheetOptionRow + ListOptionRow copiados, so mudava nome e enabled.
 */
@Composable
fun SheetOptionRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    iconTint: Color = Color.White,
    labelColor: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val rowModifier = if (enabled) Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 13.dp)
    else Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp)
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = labelColor, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * UNICA linha de progresso de download dos sheets.
 * Antes: SheetDownloadProgressRow + BatchDownloadProgressRow copiados.
 */
@Composable
fun SheetDownloadProgressRow(
    progress: Float,
    label: String,
    onClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (progress > 0f) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(28.dp), color = DownloadGreen, trackColor = DownloadGreen.copy(alpha = 0.2f), strokeWidth = 2.5.dp)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = DownloadGreen, trackColor = DownloadGreen.copy(alpha = 0.2f), strokeWidth = 2.5.dp)
            }
            Icon(imageVector = VzIcons.Download, contentDescription = null, tint = DownloadGreen, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
