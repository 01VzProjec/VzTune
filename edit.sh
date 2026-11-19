#!/bin/bash
# ============================================================
# VzTune - FIX 2: Card do Search = mesmo tamanho da Playlist
#            + zera duplicacao inutil que sobrou nos overlays
# ============================================================
# 1) VzRowCard (Search) era 56dp + padding 8dp = 72dp de altura.
#    VzTrackRow (playlist) e 52dp + padding 6dp = 64dp.
#    -> VzRowCard agora: thumbnail 52dp, canto 6dp, padding 16/8/6/6,
#       botao ••• 40dp. Altura total 64dp, IGUAL a playlist.
#    -> SearchResultsContent: pai nao repete padding lateral (0dp) e
#       spacedBy 0, porque a linha ja da o padding. Video mantem 12dp.
# 2) Duplicacao inutil que sobrou depois do FIX 1:
#    - EmptySearchResult (Library) + empty inline (MediaList) -> VzEmptyState
#    - SearchInputBar (Library) + barra inline (MediaList) -> VzSearchInputBar
#      com param closeOnStart: false = fechar na direita (Library),
#      true = fechar na esquerda (MediaList). Visual de cada uma preservado.
# 3) NAO e duplicacao inutil (deixado como esta, de proposito):
#    - MiniPlayerBar x CompactMiniPlayerBar: variantes diferentes
#      (uma completa, uma compacta), nao copias.
#    - TrackOptionsSheet x MediaListOptionsSheet: dominios diferentes
#      (faixa x lista), so as linhas ja sao compartilhadas.
#    - RecentTrackCard (carrossel horizontal) x VzTrackRow (linha):
#      formatos diferentes.
# Backup automatico em VzTune-BACKUP-FIX2-<data>/
# Rode na raiz do projeto VzTune.
# ============================================================
set -e
if [ -d "app/src/main/kotlin/com/vztechcore/vztune" ]; then
  BASE="."
elif [ -d "[STRIPPED 88 bytes]" ]; then
  BASE="/storage/emulated/0/AndroidIDE_Projects/VzTune"
  cd "$BASE"
else
  echo "❌ Nao achei o projeto. Rode dentro da pasta VzTune."
  exit 1
fi
echo "📁 Projeto: $(pwd)"
BACKUP="VzTune-BACKUP-FIX2-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP"
cp app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/VzRowCard.kt "$BACKUP/" 2>/dev/null || true
cp app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/components/SearchResultCards.kt "$BACKUP/" 2>/dev/null || true
cp app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list/LibraryListSearchOverlay.kt "$BACKUP/" 2>/dev/null || true
cp app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt "$BACKUP/" 2>/dev/null || true
echo "💾 Backup em: $BACKUP"
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/components/layout
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/components
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list
mkdir -p app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist
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
 *
 * TAMANHO = IGUAL ao VzTrackRow (card das playlists):
 * thumbnail 52dp, canto 6dp, padding 16dp esq / 8dp dir / 6dp cima-baixo,
 * botao ••• 40dp. Antes era 56dp + 8dp, por isso o Search parecia maior.
 * Altura total: 52 + 12 = 64dp, igual a playlist.
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
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() }.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (mosaic.size >= 4) { Box(Modifier.size(52.dp)) { PlaylistMosaic(mosaic) } }
        else { MediaThumbnail(imageUrl = imageUrl, shape = shape, size = 52.dp, cornerRadius = 6.dp, isPlaying = isPlaying, isPaused = isPaused) }
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
            IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/components/layout/VzSearchInputBar.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/layout/VzSearchInputBar.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.navigation.Search

/**
 * UNICA barra de pesquisa dos overlays.
 * Antes: SearchInputBar (Library, fechar na DIREITA, campo 48dp) + barra inline
 * da MediaList (fechar na ESQUERDA, campo 44dp) copiadas.
 * Agora as duas usam esta, so muda [closeOnStart]:
 * - false = fechar na direita (Library)
 * - true  = fechar na esquerda (MediaList / Album / Playlist)
 * Visual de cada uma preservado: campo 48dp, fundo 0.12, borda 0.18, icone 20dp.
 */
@Composable
fun VzSearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    closeOnStart: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (closeOnStart) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) {
                Icon(VzIcons.Close, "Fechar busca", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Row(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(VzIcons.Search, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) { Text(placeholder, color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp) }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(Modifier.size(24.dp).clip(CircleShape).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) {
                    Icon(VzIcons.Close, "Limpar", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }
        if (!closeOnStart) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) {
                Icon(VzIcons.Close, "Fechar busca", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list/LibraryListSearchOverlay.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list/LibraryListSearchOverlay.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.screens.library.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.FollowedArtist
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.cards.ArtistCircleCard
import com.vztechcore.vztune.ui.components.cards.TrackListRow
import com.vztechcore.vztune.ui.components.feedback.VzEmptyState
import com.vztechcore.vztune.ui.components.layout.VzSearchInputBar
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.navigation.Search

@Composable
internal fun LibraryListSearchOverlay(
    tracks: List<Track>,
    artists: List<FollowedArtist>,
    isArtistMode: Boolean,
    onClose: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onArtistClick: (FollowedArtist) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val filteredTracks = remember(tracks, query) {
        if (query.isBlank()) tracks.sortedBy { it.title.lowercase() }
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
        }
    }
    val filteredArtists = remember(artists, query) {
        if (query.isBlank()) artists.sortedBy { it.name.lowercase() }
        else artists.filter { it.name.contains(query, ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0F1E))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            VzSearchInputBar(
                query = query,
                onQueryChange = { query = it },
                onClose = onClose,
                placeholder = if (isArtistMode) "Pesquisar artista…" else "Pesquisar faixa…",
                focusRequester = focusRequester,
                onSearch = { keyboardController?.hide() },
            )

            val count = if (isArtistMode) filteredArtists.size else filteredTracks.size
            Text(
                text = if (query.isBlank()) "$count ${if (isArtistMode) "artistas" else "faixas"} • A → Z"
                else "$count resultados",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            when {
                isArtistMode && filteredArtists.isEmpty() -> VzEmptyState(text = if (query.isBlank()) "Nada por aqui." else "Nada encontrado para \"$query\"", fillMaxSize = true)
                !isArtistMode && filteredTracks.isEmpty() -> VzEmptyState(text = if (query.isBlank()) "Nada por aqui." else "Nada encontrado para \"$query\"", fillMaxSize = true)

                isArtistMode -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filteredArtists, key = { it.id }) { artist ->
                        ArtistCircleCard(
                            name = artist.name,
                            imageUrl = artist.thumbnailUrl,
                            onClick = { onArtistClick(artist) },
                            size = 150.dp,
                        )
                    }
                    item(key = "credits", span = { GridItemSpan(maxLineSpan) }) { AppCredits() }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        filteredTracks,
                        key = { _, t -> "${t.source.name}_${t.id}" },
                    ) { _, track ->
                        TrackListRow(
                            track = track,
                            onClick = { onTrackClick(track) },
                            onMoreClick = { onTrackInfoClick(track) },
                        )
                    }
                    item(key = "credits") { AppCredits() }
                }
            }
        }
    }
}

@Composable

@Composable
KOTLIN_EOF
echo "✏️  app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt" <<'KOTLIN_EOF'
package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.feedback.VzEmptyState
import com.vztechcore.vztune.ui.components.layout.VzSearchInputBar
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.navigation.Search

@Composable
internal fun MediaListSearchOverlay(
    allTracks: List<Track>,
    isAlbum: Boolean = false,
    onClose: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val nowPlaying = LocalNowPlaying.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val filteredTracks = remember(allTracks, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) allTracks
        else allTracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .statusBarsPadding(),
    ) {
        VzSearchInputBar(
            query = query,
            onQueryChange = { query = it },
            onClose = { keyboardController?.hide(); onClose() },
            placeholder = if (isAlbum) "Pesquisar no álbum..." else "Pesquisar na playlist...",
            focusRequester = focusRequester,
            onSearch = { keyboardController?.hide() },
            closeOnStart = true,
        )

        if (filteredTracks.isEmpty()) {
            VzEmptyState(text = "Nenhuma faixa encontrada para \"$query\"", fillMaxSize = true)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = filteredTracks,
                    key = { index, t -> "search_${t.source.name}_${t.id}_$index" },
                ) { _, track ->
                    val isCurrent = nowPlaying.isCurrent(track.id, track.source)
                    val originalIndex = if (isAlbum) {
                        allTracks.indexOfFirst { it.id == track.id && it.source == track.source }
                            .takeIf { it >= 0 }
                            ?.plus(1)
                    } else {
                        null
                    }

                    MediaListTrackRow(
                        track = track,
                        index = originalIndex,
                        isPlaying = isCurrent && nowPlaying.isPlaying,
                        isPaused = isCurrent && !nowPlaying.isPlaying,
                        onClick = {
                            keyboardController?.hide()
                            onTrackClick(track)
                        },
                        onMoreClick = { onTrackInfoClick(track) },
                        onPlayNext = { onPlayNextClick(track) },
                        showDragHandle = false,
                        dragHandleModifier = Modifier,
                    )
                }
                item(key = "credits_search") { AppCredits() }
            }
        }
    }
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
            // Tamanho igual a playlist: linha ja da 16dp esq / 8dp dir, entao o pai nao repete padding lateral.
            // Video mantem 12dp do pai + VzVideoCard 0dp = 12dp, igual MediaList.
            contentPadding = PaddingValues(bottom = 160.dp, top = 8.dp, start = if (isVideoMode) 12.dp else 0.dp, end = if (isVideoMode) 12.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(if (isVideoMode) 20.dp else 0.dp),
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

echo ""
echo "🔍 Verificacao:"
echo -n "VzRowCard thumbnail 52dp (tem que ser 1): "; grep -c "size = 52.dp" app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/VzRowCard.kt
echo -n "VzRowCard 56dp restante (tem que ser 0): "; grep -c "56.dp" app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/VzRowCard.kt || true
echo -n "VzTrackRow thumbnail 52dp (tem que ser 1, se FIX1 aplicado): "; grep -c "size = 52.dp" app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/VzTrackRow.kt || true
echo -n "VzSearchInputBar definido (tem que ser 1): "; grep -r "fun VzSearchInputBar" app/src/main/kotlin --include="*.kt" | wc -l
echo -n "EmptySearchResult restante (tem que ser 0): "; grep -r "EmptySearchResult" app/src/main/kotlin --include="*.kt" | wc -l
echo -n "BasicTextField( uso nos overlays (tem que ser 0): "; grep -r "BasicTextField(" app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list/LibraryListSearchOverlay.kt app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt 2>/dev/null | wc -l
echo ""
echo "✅ Pronto! Search agora tem o mesmo tamanho da playlist: thumbnail 52dp, altura 64dp."
echo "   Overlays sem duplicacao: VzEmptyState + VzSearchInputBar."
echo "   Backup em: $BACKUP"
