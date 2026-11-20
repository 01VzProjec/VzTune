package com.vztechcore.vztune.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackCreditsViewModel @Inject constructor(
    private val ytMusicRepository: YTMusicRepository,
) : ViewModel() {
    private val _credits = MutableStateFlow<YTMusicRepository.TrackCredits?>(null)
    val credits: StateFlow<YTMusicRepository.TrackCredits?> = _credits.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private var loadedFor: String? = null
    fun load(track: Track) {
        if (loadedFor == track.id) return
        loadedFor = track.id
        _isLoading.value = true
        _credits.value = null
        viewModelScope.launch {
            _credits.value = runCatching { ytMusicRepository.getTrackCredits(track) }.getOrNull()
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackCreditsSheet(track: Track, onDismiss: () -> Unit, viewModel: TrackCreditsViewModel = hiltViewModel()) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val credits by viewModel.credits.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    LaunchedEffect(track.id) { viewModel.load(track) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF121829), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    if (track.thumbnailUrl != null) AsyncImage(model = track.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = track.title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(text = track.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(text = "Creditos da musica", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { VzLoading(Modifier.size(96.dp)) }
            } else if (credits == null) {
                Text(text = "Creditos nao disponiveis para esta faixa.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
            } else {
                val c = credits!!
                CreditRow(label = "Artista", value = c.artists.joinToString(", ").takeIf { it.isNotBlank() })
                CreditRow(label = "Album", value = c.albumTitle)
                CreditRow(label = "Ano", value = c.albumYear)
                CreditRow(label = "Visualizacoes", value = c.viewCount)
                CreditRow(label = "Compositores", value = c.composers.joinToString(", ").takeIf { it.isNotBlank() })
                CreditRow(label = "Produtores", value = c.producers.joinToString(", ").takeIf { it.isNotBlank() })
                CreditRow(label = "Letra", value = c.lyricists.joinToString(", ").takeIf { it.isNotBlank() })
                val desc = c.description
                if (!desc.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(text = "Sobre", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
@Composable
private fun CreditRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.width(120.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}
