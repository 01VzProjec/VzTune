package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun MediaListFooter(state: MediaListUiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp)) {
        Text(
            state.descriptionLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Vtzin x",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "@2026 VzTune v1.0.0",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.35f),
        )
    }
}