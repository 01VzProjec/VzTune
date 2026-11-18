#!/bin/bash
set -e

# VzTune HOTFIX - corrige o erro grande do build (lint + 3 arquivos quebrados)
# Rode na raiz do projeto VzTune.
#
# O que quebrou:
# 1) O script anterior copiou junto a linha '===== Fim do arquivo [...] =====' do
#    dump pra dentro de 3 arquivos .kt. O Kotlin nao entende essa linha e o
#    build falhou com 'Expecting a top level declaration' em:
#    - EclipseBackground.kt:108 | LibraryListSearchOverlay.kt:231 | MediaListSearchOverlay.kt:219
#    Este script reescreve os 3 arquivos LIMPOS, sem essa linha, ja com os fixes
#    de navegacao imediata (Eclipse 300 ms + teclado apos o slide).
# 2) Lint do modulo :data falhou: MissingPermission ACCESS_NETWORK_STATE em
#    TrackDownloader.kt:80 (registerDefaultNetworkCallback). O :app tem a permissao,
#    mas o :data nao tinha manifest proprio com ela. Este script cria/arruma o
#    manifest do :data com todas as permissoes que o :data usa (rede, foreground
#    service, notificacao, wake lock), o que tambem cobre os outros erros de lint
#    do :data (startForeground / startForegroundService).
#
# AppNavHost.kt e NavTransitions.kt JA estao certos do script anterior (sem a linha
# Fim do arquivo), por isso este hotfix NAO os reescreve.

if [ ! -f "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt" ]; then
  echo "ERRO: rode este script na raiz do projeto VzTune."
  exit 1
fi

# --- app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback/EclipseBackground.kt (limpo, sem a linha 'Fim do arquivo') ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback/EclipseBackground.kt" <<'EOF'
package com.vztechcore.vztune.ui.components.feedback

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts the most vibrant color from an image URL.
 * Returns Color.Black if the image is missing or extraction fails.
 */
@Composable
fun rememberDominantColor(url: String?): Color {
    var color by remember { mutableStateOf(Color.Black) }
    val context = LocalContext.current

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            color = Color.Black
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(96)
                    .build()
                val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
                val bitmap = drawable?.toBitmap()
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    Color(
                        palette.getVibrantColor(
                            palette.getDominantColor(android.graphics.Color.BLACK)
                        )
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
        if (extracted != null) color = extracted
    }
    return color
}

/**
 * App-wide background gradient: the dominant color of the current image
 * (top) fading into black (bottom).
 */
@Composable
fun EclipseBackground(
    baseColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // 300 ms = igual ao slide das telas. Antes era 900 ms e o fundo ficava
    // "arrastando" 600 ms depois do slide terminar, o que fazia o app
    // parecer lento mesmo com a navegacao ja tendo terminado.
    val animated by animateColorAsState(
        targetValue = baseColor,
        animationSpec = tween(durationMillis = 300),
        label = "eclipse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to animated.copy(alpha = 0.55f),
                            0.45f to animated.copy(alpha = 0.16f),
                            1.0f to Color.Black,
                        )
                    )
                )
        )
        content()
    }
}
EOF

# --- app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list/LibraryListSearchOverlay.kt (limpo, sem a linha 'Fim do arquivo') ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list/LibraryListSearchOverlay.kt" <<'EOF'
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
import kotlinx.coroutines.delay
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

    LaunchedEffect(Unit) {
        // Espera o slide da tela terminar (300 ms) antes de abrir o teclado.
        // Antes o teclado abria no meio do slide -> resize + recomposicao
        // travavam o slide e parecia que a tela demorava pra abrir.
        delay(320)
        focusRequester.requestFocus()
    }

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

            SearchInputBar(
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
                isArtistMode && filteredArtists.isEmpty() -> EmptySearchResult(query)
                !isArtistMode && filteredTracks.isEmpty() -> EmptySearchResult(query)

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
private fun EmptySearchResult(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = if (query.isBlank()) "Nada por aqui." else "Nada encontrado para \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp),
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
                        if (query.isEmpty()) {
                            Text(placeholder, color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp)
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VzIcons.Close, "Limpar", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape).clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(VzIcons.Close, "Fechar busca", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}
EOF

# --- app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt (limpo, sem a linha 'Fim do arquivo') ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt" <<'EOF'
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
import kotlinx.coroutines.delay
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
        // Espera o slide da tela terminar (300 ms) antes de abrir o teclado.
        // Antes o teclado abria no meio do slide -> resize + recomposicao travavam o slide.
        delay(320)
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable {
                        keyboardController?.hide()
                        onClose()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VzIcons.Close,
                    contentDescription = "Fechar pesquisa",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = VzIcons.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = if (isAlbum) "Pesquisar no álbum..." else "Pesquisar na playlist...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.45f),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = VzIcons.Close,
                        contentDescription = "Limpar texto",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { query = "" },
                    )
                }
            }
        }

        if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nenhuma faixa encontrada para \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
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
EOF

# --- data/src/main/AndroidManifest.xml (cria se nao existir, arruma se existir) ---
MANIFEST="data/src/main/AndroidManifest.xml"
ensure_perm() {
  local perm="$1"
  if grep -q "android.permission.$perm" "$MANIFEST"; then
    echo "  ja tem: $perm"
  elif grep -q "<application" "$MANIFEST"; then
    sed -i "/<application/i \    <uses-permission android:name=\"android.permission.$perm\" />" "$MANIFEST"
    echo "  adicionada: $perm"
  else
    sed -i "s|</manifest>|    <uses-permission android:name=\"android.permission.$perm\" />\n</manifest>|" "$MANIFEST"
    echo "  adicionada: $perm"
  fi
}
if [ ! -f "$MANIFEST" ]; then
  mkdir -p data/src/main
  cat > "$MANIFEST" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Permissoes que o modulo :data usa (TrackDownloader, PlaybackService, etc).
         Sem elas o lint do :data falha com MissingPermission, mesmo o :app ja tendo. -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
</manifest>
EOF
  echo "Manifest do :data criado com as 6 permissoes."
else
  echo "Manifest do :data ja existe, garantindo as permissoes:"
  ensure_perm "INTERNET"
  ensure_perm "ACCESS_NETWORK_STATE"
  ensure_perm "FOREGROUND_SERVICE"
  ensure_perm "FOREGROUND_SERVICE_MEDIA_PLAYBACK"
  ensure_perm "WAKE_LOCK"
  ensure_perm "POST_NOTIFICATIONS"
fi

echo ""
echo "Hotfix aplicado. Verificacao rapida:"
grep -c "Fim do arquivo" app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback/EclipseBackground.kt app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list/LibraryListSearchOverlay.kt app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt || echo "  nenhum Fim do arquivo nos 3 arquivos (certo)"
grep -c "ACCESS_NETWORK_STATE" "$MANIFEST"
echo "Roda o build agora."
