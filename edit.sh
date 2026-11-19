#!/bin/bash
# ============================================================
# VzTune - UNIFICAR COMPONENTES DUPLICADOS
# Organizado, profissional, sem quebrar as telas
# ============================================================
# O que faz:
#  - Cria 6 arquivos UNICOS compartilhados (1 codigo pra todos):
#      components/cards/VzTrackRow.kt     -> unico card de faixa
#      components/cards/VzVideoCard.kt    -> unico card de video 16:9
#      components/cards/VzRowCard.kt      -> card generico de linha
#      components/rows/SwipeableRow.kt    -> arrastar pra tocar em seguida
#      components/rows/SheetOptionRow.kt  -> linha de opcao + progresso dos sheets
#      components/feedback/VzEmptyState.kt-> unico estado vazio
#      theme/Color.kt                     -> DownloadGreen unico (antes em 3 arquivos)
#  - Arquivos antigos viram ATALHOS FINOS (mesma assinatura, sem logica):
#      TrackListRow, MediaListTrackRow, MediaListVideoCard, VideoResultCard,
#      SheetOptionRow, ListOptionRow, SheetDownloadProgressRow,
#      BatchDownloadProgressRow, EmptyMessage, EmptyDownloadsState,
#      SearchNoResultsState
#    -> NENHUMA tela precisa mudar de chamada. Compila igual.
#  - Apaga codigo morto: artist/components/ArtistHeader.kt (orfao, ninguem usava)
#    -> o ArtistHeader usado e o de screens/artist/ArtistHeader.kt, que fica.
#  - O que NAO foi unificado de proposito (sao visuais diferentes, nao duplicacao):
#      SearchHome RecentTrackCard = card horizontal de carrossel, nao e linha
#      ArtistHeader = especifico da tela de artista, nao e compartilhado
#      LibraryListSearchOverlay / MediaListSearchOverlay = filtros diferentes,
#        mas agora ja usam o mesmo VzTrackRow por baixo
#
# Visual que unifica (unico ponto que muda, de proposito):
#  - Toda linha de faixa: thumbnail 52dp, titulo SemiBold, subtitulo
#    TrackSubtitle ("Song — artista" + badge download + "E"), ••• 40dp
#  - Library passa a mostrar "Song — artista" igual MediaList/Search
#  - Sheet progress label: alpha 0.6 (antes 0.55 / 0.65)
#  - Empty: alpha 0.5 em todos (Search era 0.7, Downloads 0.55)
#  - Video: mesmo layout em Search e MediaList, so muda o padding lateral
#
# Como usar: rode na raiz do projeto VzTune (onde tem app/build.gradle.kts)
# Backup automatico em VzTune-BACKUP-UNIFICAR-<data>/
# ============================================================
set -e
if [ -d "app/src/main/kotlin/com/vztechcore/vztune" ]; then
  BASE="."
elif [ -d "/storage/emulated/0/AndroidIDE_Projects/VzTune/app/src/main/kotlin/com/vztechcore/vztune" ]; then
  BASE="/storage/emulated/0/AndroidIDE_Projects/VzTune"
  cd "$BASE"
else
  echo "❌ Nao achei o projeto. Rode dentro da pasta VzTune (onde tem app/build.gradle.kts)."
  exit 1
fi
echo "📁 Projeto: $(pwd)"
BACKUP="VzTune-BACKUP-UNIFICAR-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP"
cp -r app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards "$BACKUP/" 2>/dev/null || true
cp -r app/src/main/kotlin/com/vztechcore/vztune/ui/components/rows "$BACKUP/" 2>/dev/null || true
cp -r app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback "$BACKUP/" 2>/dev/null || true
cp -r app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets "$BACKUP/" 2>/dev/null || true
cp -r app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist "$BACKUP/" 2>/dev/null || true
cp -r app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search "$BACKUP/" 2>/dev/null || true
cp -r app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library "$BACKUP/" 2>/dev/null || true
cp -r app/src/main/kotlin/com/vztechcore/vztune/ui/screens/downloads "$BACKUP/" 2>/dev/null || true
cp -r app/src/main/kotlin/com/vztechcore/vztune/ui/theme "$BACKUP/" 2>/dev/null || true
echo "💾 Backup em: $BACKUP"
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/components/rows
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/components
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/screens/downloads
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/theme

echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/components/rows/SwipeableRow.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/rows/SwipeableRow.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.components.rows

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.media.QueueMusic

/**
 * Single source of truth para "arrastar pra tocar em seguida".
 * Antes copiado, linha por linha igual, em Search e MediaList.
 * Agora os dois usam este. Mexeu aqui, mudou nas duas telas.
 */
@Composable
fun SwipeableRow(
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    Box(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (dragOffset > 0f) {
            val iconAlpha = (dragOffset / 100f).coerceIn(0.4f, 1f)
            Box(
                Modifier.fillMaxHeight().width(dragOffset.dp.coerceAtMost(80.dp)).padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(imageVector = VzIcons.QueueMusic, contentDescription = "Tocar em seguida", tint = Color.White.copy(alpha = iconAlpha), modifier = Modifier.size(24.dp))
            }
        }
        Box(
            Modifier.fillMaxWidth().graphicsLayer { translationX = dragOffset }.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dragOffset > 30f) onPlayNext(); dragOffset = 0f },
                    onDragCancel = { dragOffset = 0f },
                ) { change, dragAmount ->
                    if (dragAmount > 0 || dragOffset > 0) { dragOffset = (dragOffset + dragAmount).coerceIn(0f, 300f); change.consume() }
                }
            },
        ) { content() }
    }
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/VzRowCard.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/VzRowCard.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.MoreVert

/**
 * Card generico de linha: thumbnail + titulo + subtitulo + duracao + botao •••.
 * Era o RowCard privado do Search. Agora e compartilhado.
 */
@Composable
fun VzRowCard(
    imageUrl: String?,
    shape: ThumbnailShape,
    title: String,
    subtitle: @Composable () -> Unit,
    durationLabel: String?,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    showMoreButton: Boolean,
    mosaic: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (mosaic.size >= 4) { Box(Modifier.size(56.dp)) { PlaylistMosaic(mosaic) } }
        else { MediaThumbnail(imageUrl = imageUrl, shape = shape, size = 56.dp, cornerRadius = 8.dp, isPlaying = isPlaying, isPaused = isPaused) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            subtitle()
        }
        if (durationLabel != null && showMoreButton) {
            Spacer(Modifier.width(8.dp))
            Text(text = durationLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
        }
        if (showMoreButton) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/VzTrackRow.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/VzTrackRow.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.PlayingEqualizer
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.components.rows.SwipeableRow
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.DragHandle
import com.vztechcore.vztune.ui.icons.actions.MoreVert

/**
 * UNICO card de faixa do app. Single source of truth.
 * Antes: TrackListRow (Library/Artista) + MediaListTrackRow (Album/Playlist) + SearchResultCard (Search).
 * Agora todas chamam este. Visual canonico: thumbnail 52dp, titulo SemiBold,
 * subtitulo TrackSubtitle (ja mostra download + "E" + "Song — artista"), ••• 40dp, padding 6dp.
 * Library passa a mostrar "Song — artista" igual MediaList/Search, em vez de so artista.
 * - [number]: numero pequeno a esquerda + thumbnail (Artista)
 * - [index]: modo album, numero GRANDE / equalizador / ••• NO LUGAR da thumbnail
 * - [onPlayNext]: se nao for null, liga o arrastar pra tocar em seguida
 * - [isPlaying]/[isPaused]: se null, resolve sozinho via playbackOf(track)
 */
@Composable
fun VzTrackRow(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    number: Int? = null,
    index: Int? = null,
    isPlaying: Boolean? = null,
    isPaused: Boolean? = null,
    durationLabel: String? = null,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)
    val resolvedIsPlaying = isPlaying ?: playback.isPlaying
    val resolvedIsPaused = isPaused ?: playback.isPaused
    val rowContent: @Composable () -> Unit = {
        Row(modifier.fillMaxWidth().clickable { onClick() }.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (index != null) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    when {
                        resolvedIsPlaying -> PlayingEqualizer(color = Color.White, barCount = 3, maxHeight = 20.dp, barWidth = 3.dp, gap = 3.dp)
                        resolvedIsPaused -> Text(text = "•••", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        else -> Text(text = "$index", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                if (number != null) {
                    Text(text = number.toString(), style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
                }
                MediaThumbnail(imageUrl = track.thumbnailUrl, shape = ThumbnailShape.SQUARE, size = 52.dp, cornerRadius = 6.dp, isPlaying = resolvedIsPlaying, isPaused = resolvedIsPaused)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                TrackSubtitle(track = track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f))
            }
            if (durationLabel != null) {
                Spacer(Modifier.width(8.dp))
                Text(text = durationLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
            }
            if (showDragHandle) {
                Icon(imageVector = VzIcons.DragHandle, contentDescription = "Arrastar", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(32.dp).padding(4.dp).then(dragHandleModifier))
            }
        }
    }
    if (onPlayNext != null) { SwipeableRow(onPlayNext = onPlayNext) { rowContent() } } else { rowContent() }
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/VzVideoCard.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/VzVideoCard.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.MoreVert

/**
 * UNICO card de video 16:9 do app.
 * Antes: MediaListVideoCard + VideoResultCard copiados. Agora os dois chamam este.
 * [horizontalPadding]: 12.dp no MediaList/Artista, 0.dp no Search (pai ja da 12.dp).
 */
@Composable
fun VzVideoCard(
    track: Track,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
) {
    Column(modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = horizontalPadding, vertical = 8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            MediaThumbnail(imageUrl = track.thumbnailUrl, shape = ThumbnailShape.WIDE, cornerRadius = 10.dp, isPlaying = isPlaying, isPaused = isPaused)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(text = track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                TrackSubtitle(track = track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f))
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/components/rows/SheetOptionRow.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/rows/SheetOptionRow.kt" <<'KOTLIN_EOF'
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
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback/VzEmptyState.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback/VzEmptyState.kt" <<'KOTLIN_EOF'
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
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/TrackListRow.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/TrackListRow.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.components.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vztechcore.vztune.core.domain.music.Track

/**
 * Atalho fino, mantido pra nao quebrar as telas que ja usam TrackListRow.
 * Logica mora em VzTrackRow (mesma pasta). Codigo novo: use VzTrackRow direto.
 */
@Composable
fun TrackListRow(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
) {
    VzTrackRow(track = track, onClick = onClick, onMoreClick = onMoreClick, number = number, modifier = modifier)
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListTrackRow.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListTrackRow.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.cards.VzTrackRow
import com.vztechcore.vztune.ui.components.cards.VzVideoCard
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.components.text.VzChipRow

/**
 * Atalhos finos. Logica mora em VzTrackRow / VzVideoCard (components/cards).
 * Mesma assinatura pra nao quebrar MediaListScreen, ArtistScreen e overlays.
 */
@Composable
internal fun MediaListTrackRow(
    track: Track,
    index: Int?,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayNext: () -> Unit,
    showDragHandle: Boolean,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    VzTrackRow(track = track, onClick = onClick, onMoreClick = onMoreClick, onPlayNext = onPlayNext, index = index, isPlaying = isPlaying, isPaused = isPaused, showDragHandle = showDragHandle, dragHandleModifier = dragHandleModifier, modifier = modifier)
}

@Composable
internal fun MediaListVideoCard(
    track: Track,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
) {
    VzVideoCard(track = track, isPlaying = isPlaying, isPaused = isPaused, onClick = onClick, onMoreClick = onMoreClick, modifier = modifier, horizontalPadding = horizontalPadding)
}

@Composable
internal fun MediaListTabs(selected: MediaListTab, onSelect: (MediaListTab) -> Unit) {
    VzChipRow(items = MediaListTab.entries, selected = selected, label = { it.label }, onSelect = onSelect)
}

@Composable
internal fun PlayableTrackRow(
    track: Track,
    index: Int?,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)
    MediaListTrackRow(track = track, index = index, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = onClick, onMoreClick = onMoreClick, onPlayNext = onPlayNext, showDragHandle = false, dragHandleModifier = Modifier, modifier = modifier)
}

@Composable
internal fun PlayableVideoCard(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)
    MediaListVideoCard(track = track, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = onClick, onMoreClick = onMoreClick, modifier = modifier)
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/components/SearchResultCards.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/components/SearchResultCards.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.screens.search.components

// Unificacao: SwipeableRow, RowCard e VideoResultCard duplicados foram para components/rows e components/cards.
// Aqui so ficou a lista + o mapeamento SearchHit -> card. Sem logica duplicada.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.cards.VzRowCard
import com.vztechcore.vztune.ui.components.cards.VzVideoCard
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.components.rows.SwipeableRow
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.MoreVert

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsContent(
    hits: List<SearchHit>,
    isLoadingMore: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onHitClick: (SearchHit) -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onTrackMoreClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVideoMode = hits.all { it is SearchHit.VideoHit } && hits.isNotEmpty()
    val lazyListState = rememberLazyListState()

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            val last = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = lazyListState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }.collect { nearEnd -> if (nearEnd) onLoadMore() }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 160.dp, top = 8.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isVideoMode) 20.dp else 4.dp),
        ) {
            itemsIndexed(hits, key = { _, item -> item.stableKey }) { _, hit ->
                if (isVideoMode && hit is SearchHit.VideoHit) {
                    VideoResultCard(
                        hit = hit,
                        onClick = { onHitClick(hit) },
                        onMoreClick = { onTrackMoreClick(hit.track) },
                    )
                } else {
                    SearchResultCard(
                        hit = hit,
                        onClick = { onHitClick(hit) },
                        onMoreClick = {
                            val track = when (hit) {
                                is SearchHit.SongHit -> hit.track
                                is SearchHit.VideoHit -> hit.track
                                else -> null
                            }
                            if (track != null) onTrackMoreClick(track)
                        },
                        onPlayNextClick = onPlayNextClick,
                    )
                }
            }

            if (isLoadingMore) {
                item(key = "loading_more") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VzLoading(Modifier.size(96.dp))
                    }
                }
            }
            if (!isLoadingMore) {
                item(key = "credits") { AppCredits() }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    hit: SearchHit,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayNextClick: (Track) -> Unit,
) {
    val nowPlaying = LocalNowPlaying.current

    when (hit) {
        is SearchHit.SongHit -> {
            val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
            SwipeableRow(onPlayNext = { onPlayNextClick(hit.track) }) {
                VzRowCard(
                    imageUrl = hit.track.thumbnailUrl, shape = ThumbnailShape.SQUARE, title = hit.track.title,
                    subtitle = { TrackSubtitle(hit.track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f)) },
                    durationLabel = formatDuration(hit.track.durationSeconds),
                    isPlaying = isCurrent && nowPlaying.isPlaying, isPaused = isCurrent && !nowPlaying.isPlaying,
                    onClick = onClick, onMoreClick = onMoreClick, showMoreButton = true,
                )
            }
        }
        is SearchHit.VideoHit -> {
            val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
            SwipeableRow(onPlayNext = { onPlayNextClick(hit.track) }) {
                VzRowCard(
                    imageUrl = hit.track.thumbnailUrl, shape = ThumbnailShape.SQUARE, title = hit.track.title,
                    subtitle = { TrackSubtitle(hit.track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f)) },
                    durationLabel = null,
                    isPlaying = isCurrent && nowPlaying.isPlaying, isPaused = isCurrent && !nowPlaying.isPlaying,
                    onClick = onClick, onMoreClick = onMoreClick, showMoreButton = true,
                )
            }
        }
        is SearchHit.PlaylistHit -> {
            val owner = hit.playlist.owner?.takeIf { it.isNotBlank() }
            val views = hit.playlist.views?.takeIf { it.isNotBlank() }
            val subtitleText = listOfNotNull(owner, views)
                .joinToString(" • ")
                .ifBlank { "Playlist" }
            VzRowCard(
                imageUrl = hit.playlist.thumbnailUrl,
                mosaic = hit.playlist.mosaicThumbnails,
                shape = ThumbnailShape.SQUARE,
                title = hit.playlist.title,
                subtitle = {
                    Text(
                        subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                durationLabel = null,
                isPlaying = false,
                isPaused = false,
                onClick = onClick,
                onMoreClick = onMoreClick,
                showMoreButton = false,
            )
        }
        is SearchHit.ArtistHit -> VzRowCard(
            imageUrl = hit.artist.thumbnailUrl, shape = ThumbnailShape.CIRCLE, title = hit.artist.name,
            subtitle = { Text("Artista", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f)) },
            durationLabel = null, isPlaying = false, isPaused = false, onClick = onClick, onMoreClick = onMoreClick, showMoreButton = false,
        )
        is SearchHit.AlbumHit -> {
            val artist = hit.album.artist.takeIf { it.isNotBlank() }
            val views = hit.album.views?.takeIf { it.isNotBlank() }
            val year = hit.album.year?.toString()?.takeIf { it.isNotBlank() }
            val subtitleText = listOfNotNull(artist, views ?: year)
                .joinToString(" • ")
                .ifBlank { "Álbum" }
            VzRowCard(
                imageUrl = hit.album.thumbnailUrl,
                shape = ThumbnailShape.SQUARE,
                title = hit.album.title,
                subtitle = {
                    Text(
                        subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                durationLabel = null,
                isPlaying = false,
                isPaused = false,
                onClick = onClick,
                onMoreClick = onMoreClick,
                showMoreButton = false,
            )
        }
    }
}

@Composable
fun VideoResultCard(hit: SearchHit.VideoHit, onClick: () -> Unit, onMoreClick: () -> Unit) {
    // Atalho fino: logica mora em VzVideoCard. Search usa horizontalPadding = 0.dp porque o pai ja da 12.dp.
    val nowPlaying = LocalNowPlaying.current
    val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
    VzVideoCard(track = hit.track, isPlaying = isCurrent && nowPlaying.isPlaying, isPaused = isCurrent && !nowPlaying.isPlaying, onClick = onClick, onMoreClick = onMoreClick, horizontalPadding = 0.dp)
}

private fun formatDuration(seconds: Long): String? {
    if (seconds <= 0) return null
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackOptionsSheet.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackOptionsSheet.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.components.sheets

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.ui.components.rows.SheetDownloadProgressRow
import com.vztechcore.vztune.ui.components.rows.SheetOptionRow
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.theme.DownloadGreen
import com.vztechcore.vztune.ui.icons.actions.Delete
import com.vztechcore.vztune.ui.icons.actions.DeleteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.Info
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.media.Album
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.media.Download
import com.vztechcore.vztune.ui.icons.media.DownloadDone
import com.vztechcore.vztune.ui.icons.media.Groups
import com.vztechcore.vztune.ui.icons.media.PlaylistAdd
import com.vztechcore.vztune.ui.icons.media.QueueMusic
import com.vztechcore.vztune.ui.icons.media.Radio
import com.vztechcore.vztune.ui.icons.player.PlayCircleFilled

private val SheetContainerColor = Color(0xFF121829)
private val DangerRed = Color(0xFFF87171)

enum class TrackMenuContext {
    DEFAULT,
    RECENT_HISTORY,
    EXTERNAL_PLAYLIST,
    LOCAL_PLAYLIST,
}

/**
 * Context menu opened from the three-dots button on any track card across the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsSheet(
    track: Track,
    menuContext: TrackMenuContext = TrackMenuContext.DEFAULT,
    resolvedAlbum: Album? = null,
    isFavorite: Boolean,
    isDownloaded: Boolean = false,
    downloadItem: TrackDownloadItem? = null,
    localPlaylists: List<LocalPlaylist>,
    onDismiss: () -> Unit,
    onRemoveFromRecentHistory: (Track) -> Unit = {},
    onRemoveFromLocalPlaylist: (Track) -> Unit = {},
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (playlistId: String, track: Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit = {},
    onRemoveDownload: (Track) -> Unit = {},
    onOpenDownloadsScreen: () -> Unit = {},
    onOpenAlbum: (Album) -> Unit = {},
    onOpenAlbumForTrack: (Track) -> Unit = {},
    onOpenArtistByName: (String) -> Unit,
    onOpenArtistForTrack: (Track, String) -> Unit = { _, name -> onOpenArtistByName(name) },
    onStartRadio: (Track) -> Unit = {},
    onTrackInfo: (Track) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }
    var showRemoveDownloadSheet by remember { mutableStateOf(false) }

    val artists = remember(track.artist) { parseTrackArtists(track.artist) }
    val isDownloading = downloadItem?.status == DownloadStatus.DOWNLOADING ||
        downloadItem?.status == DownloadStatus.QUEUED ||
        downloadItem?.status == DownloadStatus.PAUSED

    val addToPlaylistLabel = when (menuContext) {
        TrackMenuContext.EXTERNAL_PLAYLIST, TrackMenuContext.LOCAL_PLAYLIST -> "Adicionar à outra playlist"
        else -> "Adicionar à playlist"
    }

    if (showRemoveDownloadSheet) {
        RemoveDownloadSheet(
            track = track,
            onConfirmRemove = {
                showRemoveDownloadSheet = false
                onRemoveDownload(track)
                onDismiss()
            },
            onDismiss = { showRemoveDownloadSheet = false },
        )
        return
    }

    if (showPlaylistPicker) {
        PlaylistPickerSheet(
            title = addToPlaylistLabel,
            playlists = localPlaylists,
            onPick = { playlist ->
                onAddToPlaylist(playlist.id, track)
                showPlaylistPicker = false
                onDismiss()
            },
            onDismiss = { showPlaylistPicker = false },
        )
        return
    }

    if (showArtistPicker) {
        ArtistPickerSheet(
            artists = artists,
            onPick = { artistName ->
                showArtistPicker = false
                onDismiss()
                // Passa a faixa: o resolver só aceita quem tem ESSA faixa no catálogo
                onOpenArtistForTrack(track, artistName)
            },
            onDismiss = { showArtistPicker = false },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            TrackSheetHeader(track = track)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.08f),
            )

            // Context-specific top removal actions
            if (menuContext == TrackMenuContext.RECENT_HISTORY) {
                SheetOptionRow(
                    icon = VzIcons.DeleteFilled,
                    iconTint = DangerRed,
                    label = "Apagar",
                    labelColor = DangerRed,
                    onClick = {
                        onRemoveFromRecentHistory(track)
                        onDismiss()
                    },
                )
            }

            if (menuContext == TrackMenuContext.LOCAL_PLAYLIST) {
                SheetOptionRow(
                    icon = VzIcons.DeleteFilled,
                    iconTint = DangerRed,
                    label = "Excluir faixa da playlist",
                    labelColor = DangerRed,
                    onClick = {
                        onRemoveFromLocalPlaylist(track)
                        onDismiss()
                    },
                )
            }

            // 1. Favorite / Unfavorite
            SheetOptionRow(
                icon = if (isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                iconTint = if (isFavorite) Color(0xFFEC4899) else Color.White,
                label = if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                onClick = {
                    onToggleFavorite(track)
                    onDismiss()
                },
            )

            // 2. Add to playlist / Add to another playlist
            SheetOptionRow(
                icon = VzIcons.PlaylistAdd,
                label = addToPlaylistLabel,
                onClick = { showPlaylistPicker = true },
            )

            // 3. Play next
            SheetOptionRow(
                icon = VzIcons.PlayCircleFilled,
                label = "Reproduzir como próxima faixa",
                onClick = {
                    onPlayNext(track)
                    onDismiss()
                },
            )

            // 4. Add to queue
            SheetOptionRow(
                icon = VzIcons.QueueMusic,
                label = "Adicionar à fila",
                onClick = {
                    onAddToQueue(track)
                    onDismiss()
                },
            )

            // 4b. FIX 3: Iniciar rádio (Mix infinito) - a partir de qualquer faixa,
            // em qualquer lugar (busca, playlist, álbum, artista). Nome "Iniciar rádio":
            // é o termo do YT Music / Spotify, mais profissional que "Mix".
            // Ícone de rádio (ondas) do YT Music, não o play genérico.
            SheetOptionRow(
                icon = VzIcons.Radio,
                label = "Iniciar rádio",
                subtitle = "Mix infinito a partir desta faixa",
                onClick = {
                    onStartRadio(track)
                    onDismiss()
                },
            )

            // 5. Download / Downloading / Downloaded
            when {
                isDownloading -> {
                    val progress = downloadItem?.progress ?: 0f
                    val pct = downloadItem?.percentage ?: 0
                    val statusLabel = if (downloadItem?.status == DownloadStatus.PAUSED) {
                        "Download pausado ($pct%)"
                    } else if (pct > 0) {
                        "Baixando faixa ($pct%)"
                    } else {
                        "Baixando faixa"
                    }
                    SheetDownloadProgressRow(
                        progress = progress,
                        label = statusLabel,
                        onClick = {
                            onDismiss()
                            onOpenDownloadsScreen()
                        },
                    )
                }
                isDownloaded -> {
                    SheetOptionRow(
                        icon = VzIcons.DownloadDone,
                        iconTint = DownloadGreen,
                        label = "Faixa baixada",
                        labelColor = Color.White,
                        onClick = { showRemoveDownloadSheet = true },
                    )
                }
                else -> {
                    SheetOptionRow(
                        icon = VzIcons.Download,
                        iconTint = Color.White,
                        label = "Baixar faixa",
                        onClick = {
                            onDownload(track)
                            onDismiss()
                        },
                    )
                }
            }

            // 6. Share
            SheetOptionRow(
                icon = VzIcons.Share,
                label = "Compartilhar",
                onClick = {
                    shareTrack(context, track)
                    onDismiss()
                },
            )

            // 7. Go to album (shows immediately when track.album is known or once resolved)
            val albumTitle = resolvedAlbum?.title ?: track.album?.takeIf {
                it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true)
            }
            if (!albumTitle.isNullOrBlank()) {
                SheetOptionRow(
                    icon = VzIcons.Album,
                    label = "Álbum",
                    subtitle = albumTitle,
                    onClick = {
                        onDismiss()
                        if (resolvedAlbum != null) {
                            onOpenAlbum(resolvedAlbum)
                        } else {
                            onOpenAlbumForTrack(track)
                        }
                    },
                )
            }

            // 8. Go to artist(s) / canal
            // FIX 2: vídeo do YouTube -> "Ir para o canal" (resolve o canal real via NewPipe).
            // YT Music -> "Ir para o(s) artista(s)" como antes.
            // Sem canal real, o ViewModel não navega (nunca chuta artista por nome).
            if (artists.isNotEmpty()) {
                val isYouTubeVideo = track.source == Source.YOUTUBE
                val hasMultiple = artists.size > 1 && !isYouTubeVideo
                SheetOptionRow(
                    icon = if (hasMultiple) VzIcons.Groups else VzIcons.Artist,
                    label = when {
                        isYouTubeVideo -> "Ir para o canal"
                        hasMultiple -> "Ir para os artistas"
                        else -> "Ir para o artista"
                    },
                    subtitle = if (hasMultiple) artists.joinToString(", ") else artists.first(),
                    onClick = {
                        if (hasMultiple) {
                            showArtistPicker = true
                        } else {
                            onDismiss()
                            onOpenArtistForTrack(track, artists.first())
                        }
                    },
                )
            }

            // 9. Track details
            SheetOptionRow(
                icon = VzIcons.Info,
                label = "Ver informações da faixa",
                onClick = {
                    onDismiss()
                    onTrackInfo(track)
                },
            )
        }
    }
}

/**
 * Confirmation bottom sheet displayed when tapping "Faixa baixada" or the delete icon in completed downloads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoveDownloadSheet(
    track: Track,
    onConfirmRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            TrackSheetHeader(track = track)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.08f),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = VzIcons.DownloadDone,
                        contentDescription = null,
                        tint = DownloadGreen,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Faixa salva para reprodução offline",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Esta música já está armazenada no seu dispositivo para você ouvir mesmo sem conexão com a internet. Deseja removê-la dos seus downloads?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Text(
                            text = "Manter faixa",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = onConfirmRemove,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed.copy(alpha = 0.18f),
                            contentColor = DangerRed,
                        ),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Icon(
                            imageVector = VzIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Apagar",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSheetHeader(track: Track) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (track.thumbnailUrl != null) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
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
private fun SheetOptionRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    iconTint: Color = Color.White,
    labelColor: Color = Color.White,
    onClick: () -> Unit,
) {
    // Atalho fino: logica mora em components/rows/SheetOptionRow.kt
    SheetOptionRow(icon = icon, label = label, subtitle = subtitle, iconTint = iconTint, labelColor = labelColor, onClick = onClick)
}



@Composable
private fun SheetDownloadProgressRow(
    progress: Float,
    label: String,
    onClick: () -> Unit,
) {
    // Atalho fino: logica mora em components/rows/SheetOptionRow.kt
    SheetDownloadProgressRow(progress = progress, label = label, onClick = onClick)
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    title: String = "Adicionar à playlist",
    playlists: List<LocalPlaylist>,
    onPick: (LocalPlaylist) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (playlists.isEmpty()) {
                Text(
                    text = "Você ainda não criou nenhuma playlist. Crie uma na Biblioteca, em \"Suas playlists\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                playlists.forEach { playlist ->
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(playlist) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistPickerSheet(
    artists: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Escolher artista",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            artists.forEach { artistName ->
                SheetOptionRow(
                    icon = VzIcons.Artist,
                    label = artistName,
                    onClick = { onPick(artistName) },
                )
            }
        }
    }
}

private fun parseTrackArtists(raw: String): List<String> {
    if (raw.isBlank() || raw.equals("Desconhecido", ignoreCase = true)) return emptyList()
    return raw
        .split(Regex("\\s*(?:,|&|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bx\\b)\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private val SharePhrases = listOf(
    "Ouvindo no VzTune 🎧",
    "Escuta essa faixa no VzTune ✨",
    "Descobri no VzTune 🔥",
    "Tocando agora no VzTune 🎵",
    "Recomendo ouvir no VzTune 🚀",
    "Vibe boa no VzTune 🎶",
)

private fun shareTrack(context: Context, track: Track) {
    val url = if (track.source == Source.YOUTUBE) {
        "https://www.youtube.com/watch?v=${track.id}"
    } else {
        "https://music.youtube.com/watch?v=${track.id}"
    }
    val phrase = SharePhrases.random()
    val shareText = "$phrase\n${track.title} — ${track.artist}\n$url"

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, track.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    val chooser = Intent.createChooser(sendIntent, "Compartilhar faixa")
    context.startActivity(chooser)
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/MediaListOptionsSheet.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/MediaListOptionsSheet.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.components.sheets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.ui.components.player.DefaultPlaylistCover
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.components.rows.SheetDownloadProgressRow
import com.vztechcore.vztune.ui.components.rows.SheetOptionRow
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.theme.DownloadGreen
import com.vztechcore.vztune.ui.icons.actions.AddPhotoAlternate
import com.vztechcore.vztune.ui.icons.actions.Delete
import com.vztechcore.vztune.ui.icons.actions.DeleteFilled
import com.vztechcore.vztune.ui.icons.actions.Edit
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.media.Download
import com.vztechcore.vztune.ui.icons.media.DownloadDone
import com.vztechcore.vztune.ui.icons.media.Groups
import com.vztechcore.vztune.ui.icons.media.QueueMusic
import com.vztechcore.vztune.ui.icons.player.PlayCircleFilled
import com.vztechcore.vztune.ui.screens.medialist.MediaListUiState

private val SheetContainerColor = Color(0xFF121829)
private val DangerRed = Color(0xFFF87171)

/**
 * Context menu opened from the three-dots button in the header of a Playlist or Album screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListOptionsSheet(
    state: MediaListUiState,
    allTracksDownloaded: Boolean,
    isDownloadingBatch: Boolean = false,
    batchDownloadProgress: Float = 0f,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNextAll: () -> Unit,
    onAddToQueueAll: () -> Unit,
    onDownloadAll: () -> Unit,
    onOpenDownloadsScreen: () -> Unit,
    onOpenArtistByName: (String) -> Unit,
    onDeleteLocalPlaylist: () -> Unit,
    onRenameLocalPlaylist: (String) -> Unit = {},
    onChangeLocalPlaylistCover: (Uri) -> Unit = {},
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameSheet by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }

    val albumArtists = remember(state.owner, state.isAlbum) {
        if (state.isAlbum) parseMediaListArtists(state.owner.orEmpty()) else emptyList()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            onChangeLocalPlaylistCover(uri)
            onDismiss()
        }
    }

    if (showDeleteConfirm) {
        DeleteLocalPlaylistSheet(
            title = state.title,
            onConfirmDelete = {
                showDeleteConfirm = false
                onDismiss()
                onDeleteLocalPlaylist()
            },
            onDismiss = { showDeleteConfirm = false },
        )
        return
    }

    if (showRenameSheet) {
        RenameLocalPlaylistSheet(
            currentTitle = state.title,
            onConfirmRename = { newTitle ->
                showRenameSheet = false
                onRenameLocalPlaylist(newTitle)
                onDismiss()
            },
            onDismiss = { showRenameSheet = false },
        )
        return
    }

    if (showArtistPicker) {
        MediaListArtistPickerSheet(
            artists = albumArtists,
            onPick = { selectedArtist ->
                showArtistPicker = false
                onDismiss()
                onOpenArtistByName(selectedArtist)
            },
            onDismiss = { showArtistPicker = false },
        )
        return
    }

    val typeLabel = when {
        state.isAlbum -> "Álbum"
        state.isLocal -> "Sua playlist"
        else -> "Playlist"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                ) {
                    val hasDefaultColorToken = PlaylistCoverPalette.isDefaultColorToken(state.thumbnailUrl)
                    when {
                        state.isLocal && !hasDefaultColorToken && !state.thumbnailUrl.isNullOrBlank() -> {
                            AsyncImage(
                                model = state.thumbnailUrl,
                                contentDescription = state.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        state.mosaicThumbnails.size >= 4 -> {
                            PlaylistMosaic(state.mosaicThumbnails)
                        }
                        state.isLocal -> {
                            DefaultPlaylistCover(
                                seed = state.listId,
                                coverPath = state.thumbnailUrl,
                            )
                        }
                        !state.thumbnailUrl.isNullOrBlank() -> {
                            AsyncImage(
                                model = state.thumbnailUrl,
                                contentDescription = state.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    val sub = listOfNotNull(
                        typeLabel,
                        state.owner?.takeIf { it.isNotBlank() },
                        state.trackCountLabel.takeIf { state.allTracks.isNotEmpty() },
                    ).joinToString(" • ")
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.08f),
            )

            // Local playlist management options
            if (state.isLocal) {
                ListOptionRow(
                    icon = VzIcons.DeleteFilled,
                    iconTint = DangerRed,
                    label = "Excluir playlist",
                    labelColor = DangerRed,
                    onClick = { showDeleteConfirm = true },
                )

                ListOptionRow(
                    icon = VzIcons.Edit,
                    label = "Alterar título",
                    onClick = { showRenameSheet = true },
                )

                ListOptionRow(
                    icon = VzIcons.AddPhotoAlternate,
                    label = "Alterar miniatura",
                    onClick = { imagePickerLauncher.launch("image/*") },
                )
            }

            // Favorite / Unfavorite external playlist
            if (state.showFavorite) {
                ListOptionRow(
                    icon = if (state.isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                    iconTint = if (state.isFavorite) Color(0xFFEC4899) else Color.White,
                    label = if (state.isFavorite) "Remover dos favoritos" else "Salvar na biblioteca",
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    },
                )
            }

            // Queue & Batch Download actions
            if (state.allTracks.isNotEmpty()) {
                ListOptionRow(
                    icon = VzIcons.PlayCircleFilled,
                    label = "Reproduzir a seguir",
                    subtitle = "${state.allTracks.size} faixas",
                    onClick = {
                        onPlayNextAll()
                        onDismiss()
                    },
                )

                ListOptionRow(
                    icon = VzIcons.QueueMusic,
                    label = "Adicionar à fila",
                    onClick = {
                        onAddToQueueAll()
                        onDismiss()
                    },
                )

                when {
                    allTracksDownloaded -> {
                        ListOptionRow(
                            icon = VzIcons.DownloadDone,
                            iconTint = DownloadGreen,
                            label = "Todas as faixas já baixadas",
                            labelColor = Color.White.copy(alpha = 0.75f),
                            enabled = false,
                            onClick = {},
                        )
                    }
                    isDownloadingBatch -> {
                        val pct = (batchDownloadProgress.coerceIn(0f, 1f) * 100).toInt()
                        val label = if (pct > 0) {
                            "Baixando todas as faixas ($pct%)"
                        } else {
                            "Baixando todas as faixas"
                        }
                        BatchDownloadProgressRow(
                            progress = batchDownloadProgress,
                            label = label,
                            onClick = {
                                onDismiss()
                                onOpenDownloadsScreen()
                            },
                        )
                    }
                    else -> {
                        ListOptionRow(
                            icon = VzIcons.Download,
                            label = if (state.isAlbum) "Baixar álbum" else "Baixar todas as faixas",
                            subtitle = "Até 30 downloads simultâneos",
                            onClick = {
                                onDownloadAll()
                                onDismiss()
                            },
                        )
                    }
                }
            }

            // Go to Artist(s) (for Albums — opens a picker submenu when there are multiple artists)
            if (albumArtists.isNotEmpty()) {
                val hasMultiple = albumArtists.size > 1
                ListOptionRow(
                    icon = if (hasMultiple) VzIcons.Groups else VzIcons.Artist,
                    label = if (hasMultiple) "Ir para os artistas" else "Ir para o artista",
                    subtitle = if (hasMultiple) albumArtists.joinToString(", ") else albumArtists.first(),
                    onClick = {
                        if (hasMultiple) {
                            showArtistPicker = true
                        } else {
                            onDismiss()
                            onOpenArtistByName(albumArtists.first())
                        }
                    },
                )
            }

            // Share (for external playlists & albums)
            if (!state.isLocal) {
                ListOptionRow(
                    icon = VzIcons.Share,
                    label = "Compartilhar",
                    onClick = {
                        shareMediaList(context, state)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListArtistPickerSheet(
    artists: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Escolher artista",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            artists.forEach { artistName ->
                ListOptionRow(
                    icon = VzIcons.Artist,
                    label = artistName,
                    onClick = { onPick(artistName) },
                )
            }
        }
    }
}

internal fun parseMediaListArtists(raw: String): List<String> {
    if (raw.isBlank() || raw.equals("Desconhecido", ignoreCase = true)) return emptyList()
    return raw
        .split(
            Regex(
                "\\s*(?:,|&|/|;|\\be\\b|\\band\\b|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bx\\b|\\bcom\\b|\\bwith\\b)\\s*",
                RegexOption.IGNORE_CASE,
            )
        )
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }
        .distinct()
}

@Composable
private fun BatchDownloadProgressRow(
    progress: Float,
    label: String,
    onClick: () -> Unit,
) {
    // Atalho fino: logica mora em components/rows/SheetOptionRow.kt
    SheetDownloadProgressRow(progress = progress, label = label, onClick = onClick)
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameLocalPlaylistSheet(
    currentTitle: String,
    onConfirmRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember(currentTitle) { mutableStateOf(currentTitle) }
    val trimmed = text.trim()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Alterar título da playlist",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Nome da playlist", color = Color.White.copy(alpha = 0.45f)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                    cursorColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Text("Cancelar", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { if (trimmed.isNotBlank()) onConfirmRename(trimmed) },
                    enabled = trimmed.isNotBlank() && trimmed != currentTitle,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E40AF),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Text("Salvar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteLocalPlaylistSheet(
    title: String,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Excluir \"$title\"?",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Esta playlist será removida permanentemente da sua biblioteca. As faixas baixadas no dispositivo não serão apagadas.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Text("Cancelar", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onConfirmDelete,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed.copy(alpha = 0.18f),
                        contentColor = DangerRed,
                    ),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Icon(VzIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ListOptionRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    iconTint: Color = Color.White,
    labelColor: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // Atalho fino: logica mora em components/rows/SheetOptionRow.kt
    SheetOptionRow(icon = icon, label = label, subtitle = subtitle, iconTint = iconTint, labelColor = labelColor, enabled = enabled, onClick = onClick)
}



private fun shareMediaList(context: Context, state: MediaListUiState) {
    val cleanId = state.listId.removePrefix("VL")
    val url = if (state.isAlbum) {
        "https://music.youtube.com/browse/$cleanId"
    } else {
        "https://music.youtube.com/playlist?list=$cleanId"
    }
    val kindLabel = if (state.isAlbum) "álbum" else "playlist"
    val ownerPart = state.owner?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
    val shareText = "Confere esse $kindLabel no VzTune 🎧\n${state.title}$ownerPart\n$url"

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, state.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Compartilhar"))
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryScreen.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryScreen.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.screens.library

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.cards.CoverCard
import com.vztechcore.vztune.ui.components.layout.SectionHeader
import com.vztechcore.vztune.ui.components.cards.TrackListRow
import com.vztechcore.vztune.ui.components.feedback.VzEmptyState
import com.vztechcore.vztune.ui.components.text.VzChipRow
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Add

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onTabSelected: (LibraryTab) -> Unit,
    onShortcutClick: (LibraryShortcut) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onPlaylistClick: (LibraryPlaylistItem) -> Unit,
    onCreatePlaylist: (name: String, coverUri: Uri?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateSheet by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 200.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") { LibraryTitle() }
            item(key = "tabs") {
                VzChipRow(
                    items = LibraryTab.entries,
                    selected = state.selectedTab,
                    label = { it.label },
                    icon = { it.icon },
                    onSelect = onTabSelected,
                )
                Spacer(Modifier.height(16.dp))
            }

            when (state.selectedTab) {
                LibraryTab.OVERVIEW -> overviewTab(state.recentTracks, onShortcutClick, onTrackClick, onTrackInfoClick)

                LibraryTab.VZTUNE -> item(key = "vztune_soon") {
                    EmptyMessage("As playlists do VzTune vão aparecer aqui em breve.")
                }

                LibraryTab.MY_PLAYLISTS -> playlistGrid(
                    items = state.myPlaylists,
                    emptyText = "Toque em \"Nova playlist\" para criar a primeira.",
                    showCreateCard = true,
                    onCreateClick = { showCreateSheet = true },
                    onPlaylistClick = onPlaylistClick,
                )

                LibraryTab.FAVORITES -> playlistGrid(
                    items = state.favoritePlaylists,
                    emptyText = "Favorite uma playlist (coração na tela dela) para ela aparecer aqui.",
                    onPlaylistClick = onPlaylistClick,
                )

                LibraryTab.DOWNLOADS -> playlistGrid(
                    items = state.downloadedPlaylists,
                    emptyText = "Nenhuma playlist baixada ainda.",
                    onPlaylistClick = onPlaylistClick,
                )
            }

            item(key = "credits") { AppCredits() }
        }

        if (showCreateSheet) {
            CreatePlaylistSheet(
                onDismiss = { showCreateSheet = false },
                onConfirm = { name, cover ->
                    onCreatePlaylist(name, cover)
                    showCreateSheet = false
                },
            )
        }
    }
}

@Composable
private fun LibraryTitle() {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Sua Biblioteca",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "Tudo que é seu, num só lugar",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

private fun LazyListScope.overviewTab(
    recentTracks: List<Track>,
    onShortcutClick: (LibraryShortcut) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
) {
    items(LibraryShortcut.entries.chunked(2), key = { row -> row.first().name }) { row ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { shortcut ->
                ShortcutCard(shortcut, { onShortcutClick(shortcut) }, Modifier.weight(1f))
            }
        }
    }

    item(key = "recent_header") {
        Spacer(Modifier.height(16.dp))
        SectionHeader(title = "Recentemente tocadas")
    }

    if (recentTracks.isEmpty()) {
        item(key = "recent_empty") { EmptyMessage("Nada tocado ainda. Toque uma faixa e ela aparece aqui.") }
    } else {
        itemsIndexed(recentTracks, key = { _, t -> "recent_${t.source.name}_${t.id}" }) { _, track ->
            TrackListRow(
                track = track,
                onClick = { onTrackClick(track) },
                onMoreClick = { onTrackInfoClick(track) },
            )
        }
    }
}

@Composable
private fun ShortcutCard(shortcut: LibraryShortcut, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .height(54.dp)
            .clip(shape)
            .background(shortcut.accent.copy(alpha = 0.14f))
            .border(BorderStroke(1.5.dp, shortcut.accent.copy(alpha = 0.55f)), shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(shortcut.icon, contentDescription = null, tint = shortcut.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = shortcut.label,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}


private sealed interface GridCell {
    val key: String
    data object Create : GridCell { override val key = "create" }
    data class Playlist(val item: LibraryPlaylistItem) : GridCell { override val key get() = item.id }
}

private fun LazyListScope.playlistGrid(
    items: List<LibraryPlaylistItem>,
    emptyText: String,
    onPlaylistClick: (LibraryPlaylistItem) -> Unit,
    showCreateCard: Boolean = false,
    onCreateClick: () -> Unit = {},
) {
    val cells = buildList<GridCell> {
        if (showCreateCard) add(GridCell.Create)
        items.forEach { add(GridCell.Playlist(it)) }
    }
    val columns = 3

    items(cells.chunked(columns), key = { row -> row.joinToString("|") { it.key } }) { row ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { cell ->
                Box(Modifier.weight(1f)) {
                    when (cell) {
                        GridCell.Create -> CreatePlaylistCard(onCreateClick)
                        is GridCell.Playlist -> CoverCard(
                            title = cell.item.title,
                            subtitle = cell.item.subtitle,
                            imageUrl = cell.item.imageUrl,
                            mosaic = cell.item.mosaic,
                            onClick = { onPlaylistClick(cell.item) },
                        )
                    }
                }
            }
            repeat(columns - row.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (items.isEmpty()) item(key = "grid_empty") { EmptyMessage(emptyText) }
}

@Composable
private fun CreatePlaylistCard(onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                VzIcons.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Nova playlist",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
    }
}

@Composable
internal fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    // Atalho fino: logica mora em components/feedback/VzEmptyState.kt
    VzEmptyState(text = text, modifier = modifier)
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/screens/downloads/DownloadsScreen.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/downloads/DownloadsScreen.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.screens.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.prefs.models.DownloadedTrack
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.feedback.VzEmptyState
import com.vztechcore.vztune.ui.theme.DownloadGreen
import com.vztechcore.vztune.ui.components.layout.DetailTopBar
import com.vztechcore.vztune.ui.components.sheets.RemoveDownloadSheet
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.actions.Delete
import com.vztechcore.vztune.ui.icons.player.Pause
import com.vztechcore.vztune.ui.icons.player.Play

private val NavyBorder = Color(0xFF1E3A8A)
private val ProgressBlue = Color(0xFF3B82F6)
private val DangerRed = Color(0xFFF87171)

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onBackClick: () -> Unit,
    onTabSelected: (DownloadsTab) -> Unit,
    onTogglePauseResume: (Track) -> Unit,
    onCancelDownload: (Track) -> Unit,
    onDeleteDownload: (Track) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPauseAll: () -> Unit = {},
    onResumeAll: () -> Unit = {},
    onCancelAll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var trackPendingDeletion by remember { mutableStateOf<Track?>(null) }

    trackPendingDeletion?.let { targetTrack ->
        RemoveDownloadSheet(
            track = targetTrack,
            onConfirmRemove = {
                onDeleteDownload(targetTrack)
                trackPendingDeletion = null
            },
            onDismiss = { trackPendingDeletion = null },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        DetailTopBar(title = "Histórico de downloads", onBackClick = onBackClick)

        DownloadsTabBar(
            selectedTab = state.selectedTab,
            inProgressCount = state.activeDownloads.size,
            completedCount = state.completedDownloads.size,
            onTabSelected = onTabSelected,
        )

        Spacer(Modifier.height(10.dp))

        when (state.selectedTab) {
            DownloadsTab.IN_PROGRESS -> {
                if (state.activeDownloads.isEmpty()) {
                    EmptyDownloadsState(message = "Nenhum download em andamento no momento.")
                } else {
                    QueueStatusHeader(
                        state = state,
                        onPauseAll = onPauseAll,
                        onResumeAll = onResumeAll,
                        onCancelAll = onCancelAll,
                    )

                    Spacer(Modifier.height(10.dp))

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = state.activeDownloads,
                            key = { "${it.track.source.name}_${it.track.id}" },
                        ) { item ->
                            ActiveDownloadCard(
                                item = item,
                                onTogglePauseResume = { onTogglePauseResume(item.track) },
                                onCancel = { onCancelDownload(item.track) },
                            )
                        }
                        item(key = "credits_active") { AppCredits() }
                    }
                }
            }

            DownloadsTab.COMPLETED -> {
                if (state.completedDownloads.isEmpty()) {
                    EmptyDownloadsState(message = "Nenhuma faixa baixada ainda.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = state.completedDownloads,
                            key = { it.key },
                        ) { downloaded ->
                            val track = downloaded.toTrack()
                            CompletedDownloadCard(
                                downloaded = downloaded,
                                onClick = { onPlayTrack(track) },
                                onDeleteClick = { trackPendingDeletion = track },
                            )
                        }
                        item(key = "credits_completed") { AppCredits() }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueStatusHeader(
    state: DownloadsUiState,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelAll: () -> Unit,
) {
    val summaryParts = buildList {
        add("${state.downloadingCount}/${TrackDownloader.MAX_CONCURRENT_DOWNLOADS} simultâneos")
        if (state.queuedCount > 0) add("${state.queuedCount} na fila")
        if (state.pausedCount > 0) add("${state.pausedCount} pausados")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summaryParts.joinToString(" • "),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (state.activeDownloads.size > 1) {
            Spacer(Modifier.width(8.dp))
            if (state.hasRunningOrQueued) {
                QueueActionChip(label = "Pausar tudo", onClick = onPauseAll)
            } else if (state.hasPausedOrFailed) {
                QueueActionChip(label = "Retomar tudo", onClick = onResumeAll)
            }
            Spacer(Modifier.width(6.dp))
            QueueActionChip(label = "Limpar", isDanger = true, onClick = onCancelAll)
        }
    }
}

@Composable
private fun QueueActionChip(
    label: String,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    val borderColor = if (isDanger) DangerRed.copy(alpha = 0.45f) else NavyBorder
    val textColor = if (isDanger) DangerRed else Color.White.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DownloadsTabBar(
    selectedTab: DownloadsTab,
    inProgressCount: Int,
    completedCount: Int,
    onTabSelected: (DownloadsTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, NavyBorder), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DownloadsTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val count = if (tab == DownloadsTab.IN_PROGRESS) inProgressCount else completedCount
            val labelText = if (count > 0) "${tab.label} ($count)" else tab.label

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) NavyBorder.copy(alpha = 0.65f) else Color.Transparent)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(
    item: TrackDownloadItem,
    onTogglePauseResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val isPaused = item.status == DownloadStatus.PAUSED
    val isFailed = item.status == DownloadStatus.FAILED
    val isQueued = item.status == DownloadStatus.QUEUED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Transparent)
            .border(BorderStroke(1.5.dp, NavyBorder), shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                if (item.track.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.track.thumbnailUrl,
                        contentDescription = item.track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        isFailed -> item.errorMessage ?: "Falha no download"
                        isPaused -> "Pausado • ${item.percentage}%"
                        isQueued -> "Aguardando vaga na fila..."
                        else -> "Baixando • ${item.percentage}%"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isFailed -> DangerRed
                        isPaused || isQueued -> Color.White.copy(alpha = 0.6f)
                        else -> ProgressBlue
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Botão Pausar / Continuar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(1.dp, NavyBorder), CircleShape)
                    .clickable { onTogglePauseResume() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPaused || isFailed) VzIcons.Play else VzIcons.Pause,
                    contentDescription = if (isPaused || isFailed) "Continuar download" else "Pausar download",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            // Botão Parar / Cancelar (X)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(1.dp, NavyBorder), CircleShape)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VzIcons.Close,
                    contentDescription = "Parar download",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when {
            isQueued -> {
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = ProgressBlue,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
            item.progress > 0f || isPaused -> {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = if (isPaused) Color.White.copy(alpha = 0.45f) else ProgressBlue,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
            else -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = ProgressBlue,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
        }
    }
}

@Composable
private fun CompletedDownloadCard(
    downloaded: DownloadedTrack,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val sizeLabel = formatBytes(downloaded.fileSizeBytes)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Transparent)
            .border(BorderStroke(1.5.dp, NavyBorder), shape)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (downloaded.thumbnailUrl != null) {
                AsyncImage(
                    model = downloaded.thumbnailUrl,
                    contentDescription = downloaded.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = downloaded.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = downloaded.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (sizeLabel != null) "Baixada • $sizeLabel" else "Baixada",
                style = MaterialTheme.typography.labelSmall,
                color = DownloadGreen,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(BorderStroke(1.dp, NavyBorder), CircleShape)
                .clickable { onDeleteClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = VzIcons.Delete,
                contentDescription = "Remover download",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun EmptyDownloadsState(message: String) {
    // Atalho fino: logica mora em components/feedback/VzEmptyState.kt
    VzEmptyState(text = message, fillMaxSize = true)
}

private fun formatBytes(bytes: Long): String? {
    if (bytes <= 0L) return null
    val mb = bytes / (1024.0 * 1024.0)
    return String.format("%.1f MB", mb)
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/components/SearchEmptyState.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/components/SearchEmptyState.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.ui.components.feedback.SkeletonColumn
import com.vztechcore.vztune.ui.components.feedback.VzEmptyState
import com.vztechcore.vztune.ui.components.feedback.fadeOutBottom

/** Loading state: skeleton cards (like YouTube / YT Music) that fade out at the bottom. */
@Composable
fun SearchLoadingState(
    video: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .fadeOutBottom(0.5f),
    ) {
        SkeletonColumn(
            count = if (video) 4 else 10,
            video = video,
            horizontalPadding = if (video) 12.dp else 8.dp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Error state with retry button. */
@Composable
fun SearchErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = "Tentar novamente")
            }
        }
    }
}

/** Shown when the search returned no results. */
@Composable
fun SearchNoResultsState(query: String, modifier: Modifier = Modifier) {
    // Atalho fino: logica mora em components/feedback/VzEmptyState.kt
    VzEmptyState(text = "Nada encontrado para \"$query\"", modifier = modifier, fillMaxSize = true)
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/theme/Color.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/theme/Color.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ===== DARK PALETTE (AMOLED) =====
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF0A0E1A)
val DarkSurfaceVariant = Color(0xFF141A2E)
val DarkPrimary = Color(0xFF1E40AF)       // Navy Blue
val DarkSecondary = Color(0xFF38BDF8)     // Sky Blue
val DarkOnBackground = Color(0xFFFFFFFF)
val DarkOnSurface = Color(0xFFE5E7EB)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkError = Color(0xFFEF4444)

// ===== LIGHT PALETTE =====
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF7F8FA)
val LightSurfaceVariant = Color(0xFFE5E7EB)
val LightPrimary = Color(0xFF1E40AF)
val LightSecondary = Color(0xFF0284C7)
val LightOnBackground = Color(0xFF0A0E1A)
val LightOnSurface = Color(0xFF1F2937)
val LightOnSurfaceVariant = Color(0xFF64748B)
val LightError = Color(0xFFDC2626)

// ===== FIXED (do not change between themes) =====
val VzPrimaryDark = Color(0xFF0F1E4A)

// ===== Brand aliases (from legacy VzMusic theme) =====
val NavyPrimary = DarkPrimary          // #1E40AF
val NavyAccent = DarkSecondary         // #38BDF8
val NavyLight = Color(0xFF3B82F6)      // lighter mid-blue for gradients
val VzGold = Color(0xFFFBBF24)         // gold accent (used on search empty state)
val DownloadGreen = Color(0xFF4ADE80) // verde de download, antes duplicado em 3 arquivos
val DarkSurfaceVariantHigh = Color(0xFF1E2640)

// ===== Accent colors for library cards (from legacy) =====
val CardFavorites = Color(0xFFEC407A)
val CardTopPlayed = Color(0xFF7C4DFF)
val CardArtists = Color(0xFF29B6F6)
val CardDownloads = Color(0xFF26A69A)

// ===== COMPOSABLE GETTERS — read from current theme =====
val VzBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background

val VzSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surface

val VzSurfaceVariant: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val VzPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.primary

val VzSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.secondary

val VzOnBackground: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground

val VzOnSurface: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

val VzOnSurfaceVariant: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val VzError: Color
    @Composable get() = MaterialTheme.colorScheme.error

// Fixed white (used on top of album art, never themed)
val VzOnMedia = Color(0xFFFFFFFF)
KOTLIN_EOF
# --- Codigo morto: ArtistHeader duplicado orfao ---
# O usado e screens/artist/ArtistHeader.kt (mesmo pacote do ArtistScreen).
# O de components/ nunca era importado por ninguem. Apagar nao muda nenhuma tela.
rm -f "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/components/ArtistHeader.kt"
echo "🗑️  Removido orfao: screens/artist/components/ArtistHeader.kt"

echo ""
echo "🔍 Verificacao (tem que dar 1 em cada linha de Vz* e 0 em duplicacao):"
echo -n "VzTrackRow definido: "; grep -r "fun VzTrackRow" app/src/main/kotlin --include="*.kt" | wc -l
echo -n "VzVideoCard definido: "; grep -r "fun VzVideoCard" app/src/main/kotlin --include="*.kt" | wc -l
echo -n "VzRowCard definido: "; grep -r "fun VzRowCard" app/src/main/kotlin --include="*.kt" | wc -l
echo -n "SwipeableRow compartilhado definido: "; grep -r "fun SwipeableRow" app/src/main/kotlin --include="*.kt" | wc -l
echo -n "SheetOptionRow compartilhado definido: "; grep -r "^fun SheetOptionRow" app/src/main/kotlin --include="*.kt" | wc -l
echo -n "VzEmptyState definido: "; grep -r "fun VzEmptyState" app/src/main/kotlin --include="*.kt" | wc -l
echo -n "DownloadGreen definido (tem que ser 1): "; grep -r "val DownloadGreen" app/src/main/kotlin --include="*.kt" | wc -l
echo -n "detectHorizontalDragGestures fora do compartilhado (tem que ser 0): "; grep -r "detectHorizontalDragGestures" app/src/main/kotlin --include="*.kt" | grep -v "components/rows/SwipeableRow" | wc -l
echo -n "ArtistHeader orfao restante (tem que ser 0): "; ls app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/components/ArtistHeader.kt 2>/dev/null | wc -l
echo ""
echo "✅ Pronto! Unificacao aplicada."
echo "   Codigo novo: use VzTrackRow / VzVideoCard / VzRowCard / SwipeableRow / SheetOptionRow / VzEmptyState"
echo "   Nomes antigos continuam funcionando como atalhos finos."
echo "   Se algo estranhar, restaure de: $BACKUP"
echo "   Agora sincronize no AndroidIDE e rode o build."
