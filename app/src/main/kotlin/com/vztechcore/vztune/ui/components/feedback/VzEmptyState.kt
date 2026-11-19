package com.vztechcore.vztune.ui.components.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * UNICO estado vazio do app: texto centralizado.
 * Antes: EmptyMessage + EmptyDownloadsState + SearchNoResultsState copiados.
 * [fillMaxSize] = true pra telas cheias (Downloads/Search), false pra linhas em lista (Library).
 */
@Composable
fun VzEmptyState(
    text: String,
    modifier: Modifier = Modifier,
    fillMaxSize: Boolean = false,
) {
    val boxModifier = if (fillMaxSize) modifier.fillMaxSize().padding(32.dp) else modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp)
    Box(boxModifier, contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
    }
}
