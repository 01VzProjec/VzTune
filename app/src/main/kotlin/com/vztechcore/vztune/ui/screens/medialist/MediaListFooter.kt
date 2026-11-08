package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.ui.components.AppCredits

@Composable
internal fun MediaListFooter(state: MediaListUiState) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            state.descriptionLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        AppCredits()
    }
}
