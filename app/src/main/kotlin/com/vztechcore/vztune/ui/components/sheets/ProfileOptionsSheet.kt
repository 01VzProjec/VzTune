package com.vztechcore.vztune.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import com.vztechcore.vztune.ui.components.rows.SheetOptionRow
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.media.Groups

private val ProfileSheetColor = Color(0xFF121829)

// Nomes do item do menu. Troque aqui se quiser outro texto.
private const val FOUND_CHANNELS_LABEL = "Canais encontrados" // aparece na tela do CANAL
private const val FOUND_ACCOUNTS_LABEL = "Contas encontradas" // aparece na tela do ARTISTA

private fun foundProfilesLabel(isYouTubeChannel: Boolean): String =
    if (isYouTubeChannel) FOUND_CHANNELS_LABEL else FOUND_ACCOUNTS_LABEL

/**
 * Menu de 3 pontinhos das telas de Canal e de Artista (sobe de baixo pra cima, igual aos outros):
 * "Canais encontrados" / "Contas encontradas" (icone Groups) e, abaixo, "Compartilhar".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileOptionsSheet(
    isYouTubeChannel: Boolean,
    foundCount: Int,
    isLoadingFound: Boolean,
    onFoundProfilesClick: () -> Unit,
    onShareClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProfileSheetColor,
        contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            SheetOptionRow(
                icon = VzIcons.Groups,
                label = foundProfilesLabel(isYouTubeChannel),
                subtitle = when {
                    isLoadingFound -> "Procurando..."
                    foundCount > 0 -> "$foundCount com o mesmo nome"
                    else -> "Nenhum com o mesmo nome"
                },
                onClick = onFoundProfilesClick,
            )
            SheetOptionRow(
                icon = VzIcons.Share,
                label = "Compartilhar",
                onClick = {
                    onShareClick()
                    onDismiss()
                },
            )
        }
    }
}

/**
 * Segundo menu: perfis do OUTRO mundo com o mesmo nome.
 * Na tela do canal lista artistas/contas do YT Music; na tela do artista lista canais do YouTube.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoundProfilesSheet(
    isYouTubeChannel: Boolean,
    profiles: List<Artist>,
    isLoading: Boolean,
    onProfileClick: (Artist) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProfileSheetColor,
        contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = foundProfilesLabel(isYouTubeChannel),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            when {
                isLoading -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VzLoading(Modifier.size(96.dp))
                }

                profiles.isEmpty() -> Text(
                    text = if (isYouTubeChannel) "Nenhum artista com o mesmo nome foi encontrado." else "Nenhum canal com o mesmo nome foi encontrado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )

                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(profiles, key = { it.source.name + "_" + it.id }) { profile ->
                        FoundProfileRow(profile = profile, onClick = { onProfileClick(profile) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FoundProfileRow(profile: Artist, onClick: () -> Unit) {
    val kind = if (profile.source == Source.YOUTUBE) "Canal do YouTube" else "Artista do YT Music"
    val subtitle = listOfNotNull(kind, profile.subscribers?.takeIf { it.isNotBlank() }).joinToString(" • ")
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
            if (!profile.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile.thumbnailUrl,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
