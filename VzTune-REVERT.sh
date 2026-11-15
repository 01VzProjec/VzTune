#!/bin/bash
# VzTune REVERT - fundo preto AMOLED + logo oficial + sem picker + artista igual foto
# Roda dentro da pasta do projeto: cd ~/VzTune && bash VzTune-REVERT.sh
set -e
echo "== VzTune REVERT - 12 arquivos =="

echo "[1/12] app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/DefaultPlaylistCover.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/DefaultPlaylistCover.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/DefaultPlaylistCover.kt" <<'VZ_EOF_1_REVERT'
package com.vztechcore.vztune.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.ui.components.feedback.VzLogoStatic
import kotlin.math.abs

/**
 * Palette of randomized background colors used when a local playlist is created
 * without a custom cover image. O sistema escolhe sozinho - o usuario NAO escolhe a cor.
 */
object PlaylistCoverPalette {

    private val colorMap: LinkedHashMap<String, Color> = linkedMapOf(
        "BLUE" to Color(0xFF1D4ED8),
        "BLACK" to Color(0xFF0B0D12),
        "WHITE" to Color(0xFFF8FAFC),
        "RED" to Color(0xFFDC2626),
        "GREEN" to Color(0xFF16A34A),
        "PURPLE" to Color(0xFF7C3AED),
        "ORANGE" to Color(0xFFEA580C),
        "PINK" to Color(0xFFDB2777),
        "CYAN" to Color(0xFF0891B2),
        "INDIGO" to Color(0xFF4338CA),
        "AMBER" to Color(0xFFD97706),
        "TEAL" to Color(0xFF0D9488),
    )

    private val colorEntries: List<Map.Entry<String, Color>> = colorMap.entries.toList()

    // Cores escuras onde o logo oficial (V azul + Z branco) fica legivel.
    // WHITE e AMBER ficam fora do sorteio - senao o Z branco some no fundo claro.
    // Elas continuam aqui pra resolver tokens antigos que ja existem.
    private val randomPool: List<Map.Entry<String, Color>> = colorEntries.filter {
        it.key !in setOf("WHITE", "AMBER")
    }

    fun isDefaultColorToken(value: String?): Boolean {
        return !value.isNullOrBlank() && value.startsWith(LocalPlaylistsStore.DEFAULT_COLOR_PREFIX)
    }

    fun randomToken(): String {
        val id = randomPool.random().key
        return "${LocalPlaylistsStore.DEFAULT_COLOR_PREFIX}$id"
    }

    fun resolveBackgroundColor(coverPath: String?, seed: String): Color {
        if (!coverPath.isNullOrBlank() && coverPath.startsWith(LocalPlaylistsStore.DEFAULT_COLOR_PREFIX)) {
            val tokenId = coverPath.removePrefix(LocalPlaylistsStore.DEFAULT_COLOR_PREFIX).uppercase()
            colorMap[tokenId]?.let { return it }
        }
        if (seed.isBlank()) {
            return randomPool.random().value
        }
        val index = abs(seed.hashCode()) % randomPool.size
        return randomPool[index].value
    }
}

/**
 * Capa de playlist sem thumbnail: fundo com cor aleatoria (o sistema escolhe)
 * + logo OFICIAL da VzTechCore (VzLogoStatic de VzLoading: V azul gradiente + Z branco).
 * Sem monograma inventado, sem pontinho em cima do z, sem z menor.
 */
@Composable
fun DefaultPlaylistCover(
    modifier: Modifier = Modifier,
    seed: String = "",
    coverPath: String? = null,
) {
    val bgColor = remember(coverPath, seed) {
        PlaylistCoverPalette.resolveBackgroundColor(coverPath, seed)
    }
    // Em fundo claro antigo (WHITE), o Z branco sumiria - por isso um circulo preto atras do logo.
    val isLightBg = remember(bgColor) { bgColor.luminance() > 0.55f }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        if (isLightBg) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.72f)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                VzLogoStatic(Modifier.fillMaxSize())
            }
        } else {
            VzLogoStatic(Modifier.fillMaxSize(0.62f))
        }
    }
}
VZ_EOF_1_REVERT

echo "[2/12] app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/CoverCard.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/CoverCard.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/CoverCard.kt" <<'VZ_EOF_2_REVERT'
package com.vztechcore.vztune.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.components.feedback.VzLogoStatic
import com.vztechcore.vztune.ui.components.player.DefaultPlaylistCover
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette

/**
 * Square cover with title and subtitle below. Used for albums, singles, playlists
 * (artist screen, "see all" grids and the Library). A mosaic of 4+ thumbnails wins over [imageUrl].
 *
 * When there is neither a mosaic nor a cover (a brand-new local playlist with no
 * tracks and no chosen cover), the card is drawn with a black background and the
 * static VzTechCore logo — same identity shown on the loading screens.
 */
@Composable
fun CoverCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    mosaic: List<String> = emptyList(),
) {
    val isEmpty = mosaic.isEmpty() && imageUrl == null

    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isEmpty) Color.Black
                    else Color.White.copy(alpha = 0.08f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                mosaic.size >= 4 -> PlaylistMosaic(mosaic)
                PlaylistCoverPalette.isDefaultColorToken(imageUrl) -> DefaultPlaylistCover(
                    seed = title,
                    coverPath = imageUrl,
                    modifier = Modifier.fillMaxSize(),
                )
                imageUrl != null -> AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> VzLogoStatic(Modifier.fillMaxSize(0.9f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
VZ_EOF_2_REVERT

echo "[3/12] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/CreatePlaylistSheet.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/CreatePlaylistSheet.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/CreatePlaylistSheet.kt" <<'VZ_EOF_3_REVERT'
package com.vztechcore.vztune.ui.screens.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.AddPhotoAlternate

private val SheetBackground = Color.Black // AMOLED
private val AccentBlue = Color(0xFF60A5FA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistSheet(
    onDismiss: () -> Unit,
    onConfirm: (name: String, coverUri: Uri?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cover = uri
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBackground,
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Nova playlist",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null) {
                        AsyncImage(
                            model = cover,
                            contentDescription = "Capa da playlist",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp),
                        )
                    } else {
                        Icon(
                            VzIcons.AddPhotoAlternate,
                            contentDescription = "Escolher capa",
                            tint = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Nome da playlist", color = Color.White.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        cursorColor = Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Sem capa, o VzTune escolhe uma cor aleatória pra sua playlist.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.75f))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(name, cover) },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = Color(0xFF0A0F1E),
                        disabledContainerColor = Color.White.copy(alpha = 0.12f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f),
                    ),
                ) {
                    Text("Confirmar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
VZ_EOF_3_REVERT

echo "[4/12] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryViewModel.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryViewModel.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryViewModel.kt" <<'VZ_EOF_4_REVERT'
package com.vztechcore.vztune.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.store.FavoritePlaylistsStore
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.PlayHistoryStore
import com.vztechcore.vztune.data.prefs.store.PlaylistCoverStorage
import com.vztechcore.vztune.data.prefs.models.StoredPlaylist
import com.vztechcore.vztune.data.prefs.models.newLocalPlaylistId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    historyStore: PlayHistoryStore,
    private val localStore: LocalPlaylistsStore,
    favoritesStore: FavoritePlaylistsStore,
    private val coverStorage: PlaylistCoverStorage,
    private val playerController: PlayerController,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(LibraryTab.OVERVIEW)

    private var recentTracks: List<Track> = emptyList()

    val uiState: StateFlow<LibraryUiState> = combine(
        selectedTab,
        historyStore.tracks,
        localStore.items,
        favoritesStore.items,
    ) { tab, recent, mine, favorites ->
        recentTracks = recent
        LibraryUiState(
            selectedTab = tab,
            recentTracks = recent,
            myPlaylists = mine.map { it.toItem() },
            favoritePlaylists = favorites.map { it.toItem() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onTabSelected(tab: LibraryTab) {
        selectedTab.value = tab
    }

    fun onTrackClick(track: Track) {
        val index = recentTracks.indexOfFirst { it.id == track.id && it.source == track.source }
        if (index >= 0) playerController.playQueue(recentTracks, index)
        else playerController.playQueue(listOf(track), 0)
    }

    /**
     * Creates a local playlist. Sem capa escolhida, o SISTEMA escolhe uma cor aleatoria
     * (vermelho, azul, etc) - o usuario nao escolhe a cor.
     */
    fun createPlaylist(name: String, coverUri: Uri?) {
        viewModelScope.launch {
            val id = newLocalPlaylistId()
            val coverPath = if (coverUri != null) {
                coverStorage.save(id, coverUri) ?: LocalPlaylistsStore.randomDefaultCoverToken()
            } else {
                LocalPlaylistsStore.randomDefaultCoverToken()
            }
            localStore.addFirst(LocalPlaylist(id = id, name = name.trim(), coverPath = coverPath))
        }
    }

    private fun LocalPlaylist.toItem() = LibraryPlaylistItem(
        id = id,
        title = name,
        subtitle = "Sua playlist",
        imageUrl = coverPath ?: mosaic.firstOrNull(),
        mosaic = if (coverPath == null) mosaic else emptyList(),
    )

    private fun StoredPlaylist.toItem() = LibraryPlaylistItem(
        id = id,
        title = title,
        subtitle = owner,
        imageUrl = thumbnailUrl,
        mosaic = mosaic,
    )
}
VZ_EOF_4_REVERT

echo "[5/12] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryScreen.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryScreen.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryScreen.kt" <<'VZ_EOF_5_REVERT'
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
    Box(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
    }
}
VZ_EOF_5_REVERT

echo "[6/12] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListScreen.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListScreen.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListScreen.kt" <<'VZ_EOF_6_REVERT'
package com.vztechcore.vztune.ui.screens.medialist

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.ui.components.download.LocalDownloadedKeys
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette
import com.vztechcore.vztune.ui.components.sheets.MediaListArtistPickerSheet
import com.vztechcore.vztune.ui.components.sheets.MediaListOptionsSheet
import com.vztechcore.vztune.ui.components.sheets.parseMediaListArtists
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val HEADER_OFFSET = 1

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaListScreen(
    state: MediaListUiState,
    activeDownloads: Map<String, TrackDownloadItem> = emptyMap(),
    onBackClick: () -> Unit,
    onTabSelected: (MediaListTab) -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSortSelected: (SortOrder) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onStartShuffle: () -> Unit,
    onShuffleOnce: () -> Unit,
    onStopShuffle: () -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onFavoriteClick: () -> Unit,
    onPlayNextAll: () -> Unit = {},
    onAddToQueueAll: () -> Unit = {},
    onDownloadAll: () -> Unit = {},
    onOpenDownloadsScreen: () -> Unit = {},
    onOpenArtist: () -> Unit = {},
    onOpenArtistByName: (String) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onDeleteLocalPlaylist: () -> Unit = {},
    onRenameLocalPlaylist: (String) -> Unit = {},
    onChangeLocalPlaylistCover: (Uri) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val nowPlaying = LocalNowPlaying.current
    val downloadedKeys = LocalDownloadedKeys.current
    var showSortSheet by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showListOptionsSheet by remember { mutableStateOf(false) }
    var showHeaderArtistPicker by remember { mutableStateOf(false) }
    var shuffleJob by remember { mutableStateOf<Job?>(null) }

    val isCollapsed by rememberHeaderCollapsed(listState)

    // Fundo preto AMOLED - sem camaleao, sem gradiente de cor dominante
    val dominantColor = Color.Black
    // Thumb do header colapsado: se a capa for token de cor, usa o mosaico
    val collapsedThumbnailUrl = remember(state.thumbnailUrl, state.mosaicThumbnails) {
        val raw = state.thumbnailUrl
        if (!raw.isNullOrBlank() && !PlaylistCoverPalette.isDefaultColorToken(raw)) raw
        else state.mosaicThumbnails.firstOrNull()
    }

    val ownerArtists = remember(state.owner, state.isLocal) {
        if (!state.isLocal) parseMediaListArtists(state.owner.orEmpty()) else emptyList()
    }

    val handleHeaderArtistClick: (() -> Unit)? = remember(state.isLocal, state.owner, ownerArtists) {
        if (state.isLocal || state.owner.isNullOrBlank()) {
            null
        } else if (ownerArtists.size > 1) {
            { showHeaderArtistPicker = true }
        } else {
            onOpenArtist
        }
    }

    val displayedTracks = remember(state.filteredTracks, state.sortOrder, state.manualOrder, state.selectedTab, state.isAlbum) {
        state.getDisplayTracks()
    }

    val allTracksDownloaded = remember(state.allTracks, downloadedKeys) {
        state.allTracks.isNotEmpty() && state.allTracks.all {
            StoredTrack.from(it).key in downloadedKeys
        }
    }

    val batchDownloadState = remember(state.allTracks, downloadedKeys, activeDownloads) {
        if (state.allTracks.isEmpty()) {
            false to 0f
        } else {
            var activeCount = 0
            var totalProgress = 0f
            state.allTracks.forEach { t ->
                val k = StoredTrack.from(t).key
                val item = activeDownloads[k]
                when {
                    k in downloadedKeys -> totalProgress += 1f
                    item != null && (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) -> {
                        activeCount++
                        totalProgress += item.progress.coerceIn(0f, 1f)
                    }
                    item != null && item.status == DownloadStatus.PAUSED -> {
                        totalProgress += item.progress.coerceIn(0f, 1f)
                    }
                }
            }
            val isRunning = activeCount > 0
            val avgProgress = (totalProgress / state.allTracks.size.toFloat()).coerceIn(0f, 1f)
            isRunning to avgProgress
        }
    }

    val canReorder = !state.isAlbum && state.sortOrder == SortOrder.MANUAL && state.selectedTab == MediaListTab.ALL

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (!canReorder) return@rememberReorderableLazyListState
        val fromTrack = from.index - HEADER_OFFSET
        val toTrack = to.index - HEADER_OFFSET
        if (fromTrack !in displayedTracks.indices) return@rememberReorderableLazyListState
        if (toTrack !in displayedTracks.indices) return@rememberReorderableLazyListState
        onReorder(fromTrack, toTrack)
    }

    LaunchedEffect(state.isShuffling) {
        if (state.isShuffling) {
            shuffleJob?.cancel()
            shuffleJob = launch {
                while (isActive) {
                    onShuffleOnce()
                    delay(150)
                }
            }
            delay(1500)
            onStopShuffle()
        } else {
            shuffleJob?.cancel()
        }
    }

    if (showHeaderArtistPicker && ownerArtists.size > 1) {
        MediaListArtistPickerSheet(
            artists = ownerArtists,
            onPick = { selectedArtist ->
                showHeaderArtistPicker = false
                onOpenArtistByName(selectedArtist)
            },
            onDismiss = { showHeaderArtistPicker = false },
        )
    }

    if (showSortSheet) {
        MediaListSortSheet(
            currentSort = state.sortOrder,
            onSelect = { onSortSelected(it); showSortSheet = false },
            onDismiss = { showSortSheet = false },
        )
    }

    if (showListOptionsSheet) {
        MediaListOptionsSheet(
            state = state,
            allTracksDownloaded = allTracksDownloaded,
            isDownloadingBatch = batchDownloadState.first,
            batchDownloadProgress = batchDownloadState.second,
            onDismiss = { showListOptionsSheet = false },
            onToggleFavorite = onFavoriteClick,
            onPlayNextAll = onPlayNextAll,
            onAddToQueueAll = onAddToQueueAll,
            onDownloadAll = onDownloadAll,
            onOpenDownloadsScreen = onOpenDownloadsScreen,
            onOpenArtistByName = onOpenArtistByName,
            onDeleteLocalPlaylist = onDeleteLocalPlaylist,
            onRenameLocalPlaylist = onRenameLocalPlaylist,
            onChangeLocalPlaylistCover = onChangeLocalPlaylistCover,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 140.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            item(key = "header") {
                MediaListExpandedHeader(
                    state = state,
                    dominantColor = dominantColor,
                    allTracksDownloaded = allTracksDownloaded,
                    onBackClick = onBackClick,
                    onTabSelected = onTabSelected,
                    onPlayAllClick = onPlayAllClick,
                    onShuffleClick = onShuffleClick,
                    onSearchClick = { showSearchOverlay = true },
                    onMoreClick = { showListOptionsSheet = true },
                    onSortClick = { showSortSheet = true },
                    onShuffleToggle = {
                        if (state.isShuffling) onStopShuffle() else onStartShuffle()
                    },
                    onFavoriteClick = onFavoriteClick,
                    onArtistClick = handleHeaderArtistClick,
                )
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VzLoading(Modifier.size(160.dp))
                    }
                }
                state.errorMessage != null -> item(key = "error") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(state.errorMessage, color = Color.Red)
                    }
                }
                displayedTracks.isEmpty() -> item(key = "empty") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Nenhuma faixa por aqui.", color = Color.White.copy(alpha = 0.6f))
                    }
                }
                else -> itemsIndexed(
                    items = displayedTracks,
                    key = { _, t -> "${t.source.name}_${t.id}" },
                ) { index, track ->
                    val isCurrent = nowPlaying.isCurrent(track.id, track.source)
                    val isPlaying = isCurrent && nowPlaying.isPlaying
                    val isPaused = isCurrent && !nowPlaying.isPlaying

                    val useVideoLayout = state.selectedTab == MediaListTab.VIDEOS

                    if (useVideoLayout) {
                        MediaListVideoCard(
                            track = track,
                            isPlaying = isPlaying,
                            isPaused = isPaused,
                            onClick = { onTrackClick(track) },
                            onMoreClick = { onTrackInfoClick(track) },
                        )
                    } else if (canReorder) {
                        val itemKey = "${track.source.name}_${track.id}"
                        ReorderableItem(reorderableState, key = itemKey) { isDragging ->
                            MediaListTrackRow(
                                track = track,
                                index = if (state.isAlbum) index + 1 else null,
                                isPlaying = isPlaying,
                                isPaused = isPaused,
                                onClick = { onTrackClick(track) },
                                onMoreClick = { onTrackInfoClick(track) },
                                onPlayNext = { onPlayNextClick(track) },
                                showDragHandle = true,
                                dragHandleModifier = Modifier.longPressDraggableHandle(),
                                modifier = Modifier
                                    .animateItem(
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        )
                                    )
                                    .background(Color.Black.copy(alpha = if (isDragging) 0.5f else 0f)),
                            )
                        }
                    } else {
                        MediaListTrackRow(
                            track = track,
                            index = if (state.isAlbum) index + 1 else null,
                            isPlaying = isPlaying,
                            isPaused = isPaused,
                            onClick = { onTrackClick(track) },
                            onMoreClick = { onTrackInfoClick(track) },
                            onPlayNext = { onPlayNextClick(track) },
                            showDragHandle = false,
                            dragHandleModifier = Modifier,
                            modifier = Modifier,
                        )
                    }
                }
            }

            item(key = "footer") {
                MediaListFooter(
                    state = state,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = handleHeaderArtistClick,
                )
            }
        }

        CollapsedHeaderHost(visible = isCollapsed) {
            MediaListCollapsedHeader(
                title = state.title,
                thumbnailUrl = collapsedThumbnailUrl,
                dominantColor = dominantColor,
                onBackClick = onBackClick,
                onSearchClick = { showSearchOverlay = true },
                onMoreClick = { showListOptionsSheet = true },
            )
        }

        if (showSearchOverlay) {
            MediaListSearchOverlay(
                allTracks = displayedTracks,
                isAlbum = state.isAlbum,
                onClose = { showSearchOverlay = false },
                onTrackClick = onTrackClick,
                onPlayNextClick = onPlayNextClick,
                onTrackInfoClick = onTrackInfoClick,
            )
        }
    }
}
VZ_EOF_6_REVERT

echo "[7/12] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListHeader.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListHeader.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListHeader.kt" <<'VZ_EOF_7_REVERT'
package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.ui.components.player.DefaultPlaylistCover
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.MoreVert
import com.vztechcore.vztune.ui.icons.actions.Sort
import com.vztechcore.vztune.ui.icons.actions.StopCircle
import com.vztechcore.vztune.ui.icons.actions.Sync
import com.vztechcore.vztune.ui.icons.media.DownloadForOffline
import com.vztechcore.vztune.ui.icons.navigation.Search
import com.vztechcore.vztune.ui.icons.player.Play
import com.vztechcore.vztune.ui.icons.player.Shuffle

@Composable
internal fun MediaListExpandedHeader(
    state: MediaListUiState,
    dominantColor: Color = Color.Black,
    allTracksDownloaded: Boolean = false,
    onBackClick: () -> Unit,
    onTabSelected: (MediaListTab) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSortClick: () -> Unit,
    onShuffleToggle: () -> Unit,
    onFavoriteClick: () -> Unit,
    onArtistClick: (() -> Unit)? = null,
) {
    // Sem camaleao - fade sempre pra preto AMOLED
    val chameleonBase = Color.Black

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    AsyncImage(
                        model = state.thumbnailUrl,
                        contentDescription = state.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.42f),
                                0.48f to Color.Transparent,
                                0.82f to chameleonBase.copy(alpha = 0.78f),
                                1.0f to chameleonBase,
                            )
                        )
                    )
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaListCircleButton(VzIcons.Close, "Fechar", onBackClick)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.showFavorite) {
                        MediaListCircleButton(
                            icon = if (state.isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                            contentDescription = if (state.isFavorite) "Remover dos favoritos" else "Favoritar playlist",
                            onClick = onFavoriteClick,
                        )
                    }
                    MediaListCircleButton(VzIcons.Search, "Pesquisar", onSearchClick)
                    MediaListCircleButton(VzIcons.MoreVert, "Mais opções", onMoreClick)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset(y = (-12).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            val ownerName = state.owner?.takeIf { it.isNotBlank() }
            if (!ownerName.isNullOrBlank()) {
                val isClickableOwner = !state.isLocal && onArtistClick != null
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (isClickableOwner) {
                                Modifier
                                    .clickable { onArtistClick?.invoke() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (!state.ownerAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = state.ownerAvatarUrl,
                            contentDescription = ownerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = ownerName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.92f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            val statsText = if (state.isAlbum) {
                listOfNotNull(
                    "Álbum",
                    state.year?.takeIf { it.isNotBlank() },
                    state.trackCountLabel.takeIf { state.allTracks.isNotEmpty() },
                    state.totalDurationLabel.takeIf { it.isNotBlank() },
                ).joinToString(" • ")
            } else {
                listOfNotNull(
                    "Playlist",
                    state.year?.takeIf { it.isNotBlank() },
                    state.trackCountLabel.takeIf { (state.originalTrackCount ?: state.allTracks.size) > 0 },
                    state.totalDurationLabel.takeIf { it.isNotBlank() },
                ).joinToString(" • ")
            }

            if (statsText.isNotBlank()) {
                Text(
                    text = statsText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.showTabs) {
            MediaListTabs(state.selectedTab, onTabSelected)
            Spacer(Modifier.height(12.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1E40AF))
                    .clickable { onPlayAllClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    VzIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Play",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { onShuffleClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    VzIcons.Shuffle,
                    contentDescription = "Aleatório",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (allTracksDownloaded) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VzIcons.DownloadForOffline,
                        contentDescription = if (state.isAlbum) "Álbum baixado" else "Playlist baixada",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (state.isSortEnabled && state.sortOrder == SortOrder.SHUFFLE) {
                IconButton(onClick = onShuffleToggle) {
                    Icon(
                        imageVector = if (state.isShuffling) VzIcons.StopCircle else VzIcons.Sync,
                        contentDescription = "Sortear animado",
                        tint = if (state.isShuffling) Color(0xFF1E40AF) else Color.White,
                    )
                }
            }

            if (state.isSortEnabled) {
                IconButton(onClick = onSortClick) {
                    Icon(
                        VzIcons.Sort,
                        contentDescription = "Ordenar",
                        tint = Color.White,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun MediaListCollapsedHeader(
    title: String,
    thumbnailUrl: String?,
    onBackClick: () -> Unit,
    dominantColor: Color = Color.Black,
    onSearchClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    circularThumbnail: Boolean = false,
) {
    val barTint = remember(dominantColor) {
        lerp(Color.Black, dominantColor, 0.38f)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        barTint.copy(alpha = 0.98f),
                        barTint.copy(alpha = 0.88f),
                        Color.Transparent,
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaListCircleButton(VzIcons.Close, "Fechar", onBackClick)
        Spacer(Modifier.width(12.dp))
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(if (circularThumbnail) CircleShape else RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSearchClick != null) {
            IconButton(onClick = onSearchClick) {
                Icon(VzIcons.Search, contentDescription = "Pesquisar", tint = Color.White)
            }
        }
        if (onMoreClick != null) {
            IconButton(onClick = onMoreClick) {
                Icon(VzIcons.MoreVert, contentDescription = "Mais opções", tint = Color.White)
            }
        }
    }
}
VZ_EOF_7_REVERT

echo "[8/12] app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/MediaListOptionsSheet.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/MediaListOptionsSheet.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/MediaListOptionsSheet.kt" <<'VZ_EOF_8_REVERT'
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
import com.vztechcore.vztune.ui.icons.VzIcons
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
private val DownloadGreen = Color(0xFF4ADE80)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (progress > 0f) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(28.dp),
                    color = DownloadGreen,
                    trackColor = DownloadGreen.copy(alpha = 0.2f),
                    strokeWidth = 2.5.dp,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = DownloadGreen,
                    trackColor = DownloadGreen.copy(alpha = 0.2f),
                    strokeWidth = 2.5.dp,
                )
            }
            Icon(
                imageVector = VzIcons.Download,
                contentDescription = null,
                tint = DownloadGreen,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.65f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
    val rowModifier = if (enabled) {
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 13.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = labelColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
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
VZ_EOF_8_REVERT

echo "[9/12] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt" <<'VZ_EOF_9_REVERT'
package com.vztechcore.vztune.ui.screens.medialist

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.StoredPlaylist
import com.vztechcore.vztune.data.prefs.models.isLocalPlaylistId
import com.vztechcore.vztune.data.prefs.store.FavoritePlaylistsStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.MediaListPreferencesStore
import com.vztechcore.vztune.data.ytmusic.YTMusicOwnerValidator
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class MediaListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val ytMusicRepository: YTMusicRepository,
    private val playerController: PlayerController,
    private val trackDownloader: TrackDownloader,
    private val store: MediaListPreferencesStore,
    private val favoritesStore: FavoritePlaylistsStore,
    private val localStore: LocalPlaylistsStore,
) : ViewModel() {

    sealed interface NavigationEvent {
        data class OpenArtist(val artist: Artist) : NavigationEvent
        data object CloseScreen : NavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private val route = savedStateHandle.toRoute<Routes.MediaList>()

    private val _uiState = MutableStateFlow(
        MediaListUiState(
            listId = route.id,
            title = route.title,
            subtitle = route.subtitle,
            thumbnailUrl = route.thumbnailUrl,
            isAlbum = route.isAlbum,
            isLocal = isLocalPlaylistId(route.id),
        )
    )
    val uiState: StateFlow<MediaListUiState> = _uiState.asStateFlow()

    init {
        val savedSort = store.getSortOrder(route.id)
        val savedManual = store.getManualOrder(route.id)
        _uiState.update { it.copy(sortOrder = savedSort, manualOrder = savedManual) }
        loadContent()
        viewModelScope.launch {
            favoritesStore.contains(route.id).collect { favorite ->
                _uiState.update { it.copy(isFavorite = favorite) }
            }
        }
    }

    private fun observeLocalPlaylist() {
        viewModelScope.launch {
            localStore.ensureRandomDefaultCover(route.id)
            localStore.playlist(route.id).collect { playlist ->
                _uiState.update {
                    if (playlist == null) {
                        it.copy(isLoading = false, errorMessage = "Playlist não encontrada.")
                    } else {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            title = playlist.name,
                            owner = "Você",
                            subtitle = "Você",
                            thumbnailUrl = playlist.coverPath,
                            mosaicThumbnails = playlist.mosaic,
                            allTracks = playlist.tracks.mapNotNull { stored -> stored.toTrack() },
                        )
                    }
                }
            }
        }
    }

    private fun loadContent() {
        if (_uiState.value.isLocal) {
            observeLocalPlaylist()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                if (route.isAlbum) {
                    val album = ytMusicRepository.browseAlbum(route.id)

                    val resolvedArtist = listOfNotNull(
                        album.artist,
                        route.subtitle,
                        _uiState.value.subtitle,
                    ).firstOrNull { YTMusicOwnerValidator.isValidOwnerName(it) }

                    val resolvedTitle = album.title ?: _uiState.value.title

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = resolvedTitle,
                            subtitle = resolvedArtist ?: it.subtitle,
                            owner = resolvedArtist,
                            ownerAvatarUrl = null,
                            artistId = album.artistId,
                            year = album.year,
                            thumbnailUrl = album.coverUrl ?: it.thumbnailUrl,
                            description = album.description,
                            views = album.views,
                            allTracks = album.tracks,
                        )
                    }

                    loadMoreAlbumsByArtist(
                        artistName = resolvedArtist,
                        artistId = album.artistId,
                        excludeAlbumTitle = resolvedTitle,
                    )
                } else {
                    var playlist = ytMusicRepository.browsePlaylist(route.id)
                    if (playlist.tracks.isEmpty()) {
                        delay(1000)
                        playlist = ytMusicRepository.browsePlaylist(route.id)
                    }

                    val resolvedOwner = listOfNotNull(
                        playlist.owner,
                        route.subtitle,
                        _uiState.value.subtitle,
                    ).firstOrNull { YTMusicOwnerValidator.isValidOwnerName(it) }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = playlist.title ?: it.title,
                            subtitle = resolvedOwner ?: it.subtitle,
                            owner = resolvedOwner,
                            ownerAvatarUrl = playlist.ownerAvatarUrl,
                            year = playlist.year,
                            views = playlist.views,
                            originalTrackCount = playlist.trackCount,
                            thumbnailUrl = playlist.coverUrl ?: it.thumbnailUrl,
                            description = playlist.description,
                            mosaicThumbnails = playlist.tracks
                                .asSequence()
                                .mapNotNull { t -> t.thumbnailUrl }
                                .distinct()
                                .take(4)
                                .toList(),
                            allTracks = playlist.tracks,
                        )
                    }

                    if (playlist.ownerAvatarUrl.isNullOrBlank() && !resolvedOwner.isNullOrBlank()) {
                        loadPlaylistOwnerAvatar(resolvedOwner)
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = "Erro: ${e.message}") }
            }
        }
    }

    private fun loadPlaylistOwnerAvatar(ownerName: String) {
        viewModelScope.launch {
            val primary = ownerName
                .substringBefore(",")
                .substringBefore("&")
                .trim()
            if (primary.isBlank() || primary.equals("YouTube Music", ignoreCase = true)) return@launch
            val resolved = runCatching {
                ytMusicRepository.findChannelOrArtist(primary)
            }.getOrNull()
            if (resolved != null) {
                _uiState.update { current ->
                    current.copy(
                        artistId = current.artistId ?: resolved.id,
                        ownerAvatarUrl = current.ownerAvatarUrl ?: resolved.thumbnailUrl,
                    )
                }
            }
        }
    }

    private fun loadMoreAlbumsByArtist(
        artistName: String?,
        artistId: String?,
        excludeAlbumTitle: String?,
    ) {
        viewModelScope.launch {
            val (artist, moreAlbums) = ytMusicRepository.getMoreAlbumsByArtist(
                artistName = artistName,
                artistId = artistId,
                excludeAlbumId = route.id,
                excludeAlbumTitle = excludeAlbumTitle,
            )
            _uiState.update { current ->
                current.copy(
                    artistId = artist?.id ?: current.artistId,
                    ownerAvatarUrl = artist?.thumbnailUrl ?: current.ownerAvatarUrl,
                    moreAlbumsByArtist = moreAlbums,
                )
            }
        }
    }

    fun onTabSelected(tab: MediaListTab) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onSortOrderSelected(order: SortOrder) {
        if (order == _uiState.value.sortOrder) return
        _uiState.update { it.copy(sortOrder = order, isShuffling = false) }
        store.setSortOrder(route.id, order)
    }

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val currentTracks = _uiState.value.getDisplayTracks()
        if (fromIndex !in currentTracks.indices) return
        if (toIndex !in currentTracks.indices) return
        val newList = currentTracks.toMutableList()
        val item = newList.removeAt(fromIndex)
        newList.add(toIndex, item)
        val newOrderIds = newList.map { it.id }
        _uiState.update { it.copy(manualOrder = newOrderIds, sortOrder = SortOrder.MANUAL) }
        store.setManualOrder(route.id, newOrderIds)
        store.setSortOrder(route.id, SortOrder.MANUAL)
    }

    fun startShuffle() { _uiState.update { it.copy(isShuffling = true) } }

    fun shuffleOnce() {
        val currentTracks = _uiState.value.allTracks
        if (currentTracks.isEmpty()) return
        val newOrderIds = currentTracks.map { it.id }.shuffled()
        _uiState.update { it.copy(manualOrder = newOrderIds, sortOrder = SortOrder.SHUFFLE) }
    }

    fun stopShuffle() {
        _uiState.update { it.copy(isShuffling = false) }
        store.setManualOrder(route.id, _uiState.value.manualOrder)
        store.setSortOrder(route.id, SortOrder.SHUFFLE)
    }

    fun onTrackClick(track: Track) {
        val sorted = _uiState.value.getDisplayTracks()
        val idx = sorted.indexOfFirst { it.id == track.id && it.source == track.source }
        if (idx >= 0) playerController.playQueue(sorted, idx)
        else playerController.playQueue(listOf(track), 0)
    }

    fun onFavoriteClick() {
        val state = _uiState.value
        viewModelScope.launch {
            favoritesStore.toggle(
                StoredPlaylist(
                    id = route.id,
                    title = state.title,
                    owner = state.owner,
                    thumbnailUrl = state.thumbnailUrl,
                    mosaic = state.mosaicThumbnails,
                )
            )
        }
    }

    fun onPlayNextClick(track: Track) { playerController.addToQueueNext(track) }

    fun onPlayNextAll() {
        val tracks = _uiState.value.getDisplayTracks()
        tracks.asReversed().forEach { playerController.addToQueueNext(it) }
    }

    fun onAddToQueueAll() {
        val tracks = _uiState.value.getDisplayTracks()
        tracks.forEach { playerController.addToQueueEnd(it) }
    }

    fun onDownloadAll() {
        val tracks = _uiState.value.allTracks
        if (tracks.isNotEmpty()) {
            trackDownloader.downloadAll(tracks)
        }
    }

    fun onOpenArtist() {
        val state = _uiState.value
        val directId = state.artistId?.takeIf { it.startsWith("UC") }
        val artistName = state.owner?.takeIf { it.isNotBlank() }
        if (directId != null && artistName != null) {
            viewModelScope.launch {
                _navigationEvent.emit(
                    NavigationEvent.OpenArtist(
                        Artist(
                            id = directId,
                            name = artistName,
                            thumbnailUrl = state.ownerAvatarUrl,
                            source = Source.YOUTUBE_MUSIC,
                        )
                    )
                )
            }
            return
        }
        if (artistName != null) {
            onOpenArtistByName(artistName)
        }
    }

    fun onOpenArtistByName(artistName: String) {
        viewModelScope.launch {
            val found = runCatching {
                ytMusicRepository.findChannelOrArtist(artistName)
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(found))
            }
        }
    }

    fun onDeleteLocalPlaylist() {
        if (!_uiState.value.isLocal) return
        viewModelScope.launch {
            localStore.remove(route.id)
            _navigationEvent.emit(NavigationEvent.CloseScreen)
        }
    }

    fun onRenameLocalPlaylist(newTitle: String) {
        if (!_uiState.value.isLocal) return
        val clean = newTitle.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            localStore.rename(route.id, clean)
        }
    }

    fun onChangeLocalPlaylistCover(coverUri: Uri) {
        if (!_uiState.value.isLocal) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val coversDir = File(context.filesDir, "playlist_covers").apply {
                    if (!exists()) mkdirs()
                }
                val safeId = route.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val destFile = File(coversDir, "${safeId}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(coverUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) {
                    _uiState.value.thumbnailUrl?.let { oldPath ->
                        val oldFile = File(oldPath)
                        if (oldFile.exists() && oldFile.parentFile == coversDir) {
                            runCatching { oldFile.delete() }
                        }
                    }
                    localStore.updateCover(route.id, destFile.absolutePath)
                }
            }
        }
    }

    fun onPlayAll() {
        val sorted = _uiState.value.getDisplayTracks()
        if (sorted.isNotEmpty()) playerController.playQueue(sorted, 0)
    }

    fun onShuffle() {
        val shuffled = _uiState.value.filteredTracks.shuffled()
        if (shuffled.isNotEmpty()) playerController.playQueue(shuffled, 0)
    }
}
VZ_EOF_9_REVERT

echo "[10/12] app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt" <<'VZ_EOF_10_REVERT'
package com.vztechcore.vztune.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.models.isLocalPlaylistId
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.feedback.EclipseBackground
import com.vztechcore.vztune.ui.components.feedback.rememberDominantColor
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.sheets.TrackMenuContext
import com.vztechcore.vztune.ui.components.sheets.TrackMenuViewModel
import com.vztechcore.vztune.ui.components.sheets.TrackOptionsSheet
import com.vztechcore.vztune.ui.screens.artist.ArtistScreen
import com.vztechcore.vztune.ui.screens.artist.ArtistViewModel
import com.vztechcore.vztune.ui.screens.artist.list.ArtistListScreen
import com.vztechcore.vztune.ui.screens.artist.list.ArtistListViewModel
import com.vztechcore.vztune.ui.screens.downloads.DownloadBannerViewModel
import com.vztechcore.vztune.ui.screens.downloads.DownloadsScreen
import com.vztechcore.vztune.ui.screens.downloads.DownloadsViewModel
import com.vztechcore.vztune.ui.screens.library.LibraryScreen
import com.vztechcore.vztune.ui.screens.library.LibraryViewModel
import com.vztechcore.vztune.ui.screens.library.list.LibraryListScreen
import com.vztechcore.vztune.ui.screens.library.list.LibraryListViewModel
import com.vztechcore.vztune.ui.screens.main.MiniPlayerViewModel
import com.vztechcore.vztune.ui.screens.medialist.MediaListScreen
import com.vztechcore.vztune.ui.screens.medialist.MediaListViewModel
import com.vztechcore.vztune.ui.screens.search.SearchScreen
import com.vztechcore.vztune.ui.screens.search.SearchViewModel
import com.vztechcore.vztune.ui.screens.trackinfo.TrackInfoScreen
import com.vztechcore.vztune.ui.screens.trackinfo.TrackInfoViewModel
import com.vztechcore.vztune.ui.theme.VzOnSurface

private data class ActiveTrackMenu(
    val track: Track,
    val context: TrackMenuContext = TrackMenuContext.DEFAULT,
    val localPlaylistId: String? = null,
)

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val currentTab = when {
        backStackEntry?.destination?.hasRoute<Routes.Search>() == true -> AppTab.SEARCH
        backStackEntry?.destination?.hasRoute<Routes.Analytics>() == true -> AppTab.ANALYTICS
        backStackEntry?.destination?.hasRoute<Routes.Library>() == true -> AppTab.LIBRARY
        else -> AppTab.HOME
    }

    val isMediaListScreen = backStackEntry?.destination?.hasRoute<Routes.MediaList>() == true
    val isArtistScreen = backStackEntry?.destination?.hasRoute<Routes.Artist>() == true
    val isArtistListScreen = backStackEntry?.destination?.hasRoute<Routes.ArtistList>() == true
    val isLibraryListScreen = backStackEntry?.destination?.hasRoute<Routes.LibraryList>() == true
    val isDownloadsScreen = backStackEntry?.destination?.hasRoute<Routes.Downloads>() == true
    val isDetailScreen = isMediaListScreen || isArtistScreen || isArtistListScreen || isLibraryListScreen || isDownloadsScreen
    val isTrackInfoScreen = backStackEntry?.destination?.hasRoute<Routes.TrackInfo>() == true

    val playback = LocalNowPlaying.current
    val track = playback.currentTrack

    val miniPlayerVm: MiniPlayerViewModel = hiltViewModel()
    val bannerVm: DownloadBannerViewModel = hiltViewModel()
    val menuVm: TrackMenuViewModel = hiltViewModel()

    val dominantColor = rememberDominantColor(track?.thumbnailUrl)
    val isMinimized by miniPlayerVm.isMinimized.collectAsStateWithLifecycle()
    val bannerTrack by bannerVm.bannerTrack.collectAsStateWithLifecycle()

    val favoriteKeys by menuVm.favoriteKeys.collectAsStateWithLifecycle()
    val downloadedKeys by menuVm.downloadedKeys.collectAsStateWithLifecycle()
    val activeDownloads by menuVm.activeDownloads.collectAsStateWithLifecycle()
    val localPlaylists by menuVm.localPlaylists.collectAsStateWithLifecycle()
    val resolvedAlbum by menuVm.resolvedAlbum.collectAsStateWithLifecycle()

    var activeMenu by remember { mutableStateOf<ActiveTrackMenu?>(null) }

    fun openTrackMenu(
        targetTrack: Track,
        context: TrackMenuContext = TrackMenuContext.DEFAULT,
        localPlaylistId: String? = null,
    ) {
        menuVm.resolveAlbumFor(targetTrack)
        activeMenu = ActiveTrackMenu(targetTrack, context, localPlaylistId)
    }

    LaunchedEffect(Unit) {
        menuVm.navigationEvent.collect { event ->
            when (event) {
                is TrackMenuViewModel.MenuNavigationEvent.OpenAlbum -> navController.openAlbum(event.album)
                is TrackMenuViewModel.MenuNavigationEvent.OpenArtist -> navController.navigate(
                    Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl)
                )
            }
        }
    }

    LaunchedEffect(backStackEntry?.destination?.route) {
        if (backStackEntry?.destination?.hasRoute<Routes.Search>() == true) {
            miniPlayerVm.setMinimized(true)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -30f) miniPlayerVm.setMinimized(true)
                else if (available.y > 30f) miniPlayerVm.setMinimized(false)
                return Offset.Zero
            }
        }
    }

    activeMenu?.let { menu ->
        val key = StoredTrack.from(menu.track).key
        TrackOptionsSheet(
            track = menu.track,
            menuContext = menu.context,
            resolvedAlbum = resolvedAlbum,
            isFavorite = key in favoriteKeys,
            isDownloaded = key in downloadedKeys,
            downloadItem = activeDownloads[key],
            localPlaylists = localPlaylists,
            onDismiss = { activeMenu = null },
            onRemoveFromRecentHistory = menuVm::onRemoveFromRecentHistory,
            onRemoveFromLocalPlaylist = { t ->
                menu.localPlaylistId?.let { pid -> menuVm.onRemoveFromLocalPlaylist(pid, t) }
            },
            onToggleFavorite = menuVm::onToggleFavorite,
            onAddToPlaylist = menuVm::onAddToPlaylist,
            onPlayNext = menuVm::onPlayNext,
            onAddToQueue = menuVm::onAddToQueue,
            onDownload = menuVm::onDownload,
            onRemoveDownload = menuVm::onRemoveDownload,
            onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
            onOpenAlbum = { album -> navController.openAlbum(album) },
            onOpenAlbumForTrack = menuVm::onOpenAlbumForTrack,
            onOpenArtistByName = menuVm::onOpenArtistByName,
            onTrackInfo = { t -> navController.navigateToTrackInfo(t) },
        )
    }

    EclipseBackground(baseColor = dominantColor) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
            ) {
                composable<Routes.Home> { Placeholder("Home — coming soon") }
                composable<Routes.Analytics> { Placeholder("Analytics — coming soon") }

                composable<Routes.Library> {
                    val vm: LibraryViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    LibraryScreen(
                        state = state,
                        onTabSelected = vm::onTabSelected,
                        onShortcutClick = { navController.navigate(Routes.LibraryList(it.name)) },
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { t ->
                            openTrackMenu(t, context = TrackMenuContext.RECENT_HISTORY)
                        },
                        onPlaylistClick = { item ->
                            navController.navigate(
                                Routes.MediaList(
                                    id = item.id,
                                    isAlbum = false,
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    thumbnailUrl = item.imageUrl,
                                )
                            )
                        },
                        onCreatePlaylist = vm::createPlaylist,
                    )
                }

                composable<Routes.LibraryList> {
                    val vm: LibraryListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    LibraryListScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                        onArtistClick = { artist ->
                            navController.navigate(Routes.Artist(artist.id, artist.name, artist.thumbnailUrl))
                        },
                        onShuffleClick = vm::onShuffleClick,
                        onPlayAllClick = vm::onPlayAllClick,
                        onDownloadsHistoryClick = { navController.navigate(Routes.Downloads) },
                    )
                }

                composable<Routes.Downloads> {
                    val vm: DownloadsViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    DownloadsScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onTabSelected = vm::onTabSelected,
                        onTogglePauseResume = vm::onTogglePauseResume,
                        onCancelDownload = vm::onCancelDownload,
                        onDeleteDownload = vm::onDeleteDownload,
                        onPlayTrack = vm::onPlayTrack,
                        onPauseAll = vm::onPauseAll,
                        onResumeAll = vm::onResumeAll,
                        onCancelAll = vm::onCancelAll,
                    )
                }

                composable<Routes.Search> {
                    val vm: SearchViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        vm.navigationEvent.collect { event ->
                            when (event) {
                                is SearchViewModel.NavigationEvent.OpenPlaylist -> navController.navigate(
                                    Routes.MediaList(
                                        id = event.id,
                                        isAlbum = false,
                                        title = event.title,
                                        subtitle = event.owner,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                                is SearchViewModel.NavigationEvent.OpenArtist -> navController.navigate(
                                    Routes.Artist(
                                        id = event.id,
                                        name = event.name,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                                is SearchViewModel.NavigationEvent.OpenAlbum -> navController.navigate(
                                    Routes.MediaList(
                                        id = event.id,
                                        isAlbum = true,
                                        title = event.title,
                                        subtitle = event.artist,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                            }
                        }
                    }

                    SearchScreen(
                        state = state,
                        favoriteKeys = favoriteKeys,
                        downloadedKeys = downloadedKeys,
                        activeDownloads = activeDownloads,
                        localPlaylists = localPlaylists,
                        resolvedAlbum = resolvedAlbum,
                        onQueryChange = vm::onQueryChange,
                        onFocusChanged = vm::onFocusChanged,
                        onFilterSelect = vm::onFilterSelect,
                        onSubmit = vm::onSubmit,
                        onRefresh = vm::onRefresh,
                        onRemoveHistory = vm::onRemoveHistory,
                        onClearHistory = vm::onClearHistory,
                        onLoadMore = vm::onLoadMore,
                        onHitClick = vm::onHitClick,
                        onPlayNextClick = vm::onPlayNextClick,
                        onAddToQueueClick = vm::onAddToQueueClick,
                        onToggleFavorite = vm::onToggleFavorite,
                        onAddToPlaylist = vm::onAddToPlaylist,
                        onDownloadTrack = vm::onDownloadTrack,
                        onRemoveDownload = vm::onRemoveDownload,
                        onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
                        onPrepareTrackMenu = menuVm::resolveAlbumFor,
                        onOpenAlbum = { album -> navController.openAlbum(album) },
                        onOpenArtistByName = vm::onOpenArtistByName,
                        onTrackInfoClick = { navController.navigateToTrackInfo(it) },
                    )
                }

                composable<Routes.MediaList> {
                    val vm: MediaListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    val playlistId = state.listId
                    val menuCtx = when {
                        state.isAlbum -> TrackMenuContext.DEFAULT
                        state.isLocal || isLocalPlaylistId(playlistId) -> TrackMenuContext.LOCAL_PLAYLIST
                        else -> TrackMenuContext.EXTERNAL_PLAYLIST
                    }

                    LaunchedEffect(Unit) {
                        vm.navigationEvent.collect { event ->
                            when (event) {
                                is MediaListViewModel.NavigationEvent.OpenArtist -> navController.navigate(
                                    Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl)
                                )
                                MediaListViewModel.NavigationEvent.CloseScreen -> navController.popBackStack()
                            }
                        }
                    }

                    MediaListScreen(
                        state = state,
                        activeDownloads = activeDownloads,
                        onBackClick = { navController.popBackStack() },
                        onTabSelected = vm::onTabSelected,
                        onTrackClick = vm::onTrackClick,
                        onPlayAllClick = vm::onPlayAll,
                        onShuffleClick = vm::onShuffle,
                        onSortSelected = vm::onSortOrderSelected,
                        onReorder = vm::onReorder,
                        onStartShuffle = vm::startShuffle,
                        onShuffleOnce = vm::shuffleOnce,
                        onStopShuffle = vm::stopShuffle,
                        onPlayNextClick = vm::onPlayNextClick,
                        onTrackInfoClick = { t ->
                            openTrackMenu(
                                targetTrack = t,
                                context = menuCtx,
                                localPlaylistId = playlistId.takeIf { menuCtx == TrackMenuContext.LOCAL_PLAYLIST },
                            )
                        },
                        onFavoriteClick = vm::onFavoriteClick,
                        onPlayNextAll = vm::onPlayNextAll,
                        onAddToQueueAll = vm::onAddToQueueAll,
                        onDownloadAll = vm::onDownloadAll,
                        onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
                        onOpenArtistByName = vm::onOpenArtistByName,
                        onOpenArtist = vm::onOpenArtist,
                        onAlbumClick = { album ->
                            if (album.id != state.listId) navController.openAlbum(album)
                        },
                        onDeleteLocalPlaylist = vm::onDeleteLocalPlaylist,
                        onRenameLocalPlaylist = vm::onRenameLocalPlaylist,
                        onChangeLocalPlaylistCover = vm::onChangeLocalPlaylistCover,
                    )
                }

                composable<Routes.Artist> {
                    val vm: ArtistViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    ArtistScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onRandomClick = vm::onRandomClick,
                        onFollowClick = vm::onFollowClick,
                        onSongClick = vm::onSongClick,
                        onVideoClick = vm::onVideoClick,
                        onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                        onAlbumClick = { navController.openAlbum(it) },
                        onPlaylistClick = { navController.openPlaylist(it) },
                        onArtistClick = { artist ->
                            navController.navigate(Routes.Artist(artist.id, artist.name, artist.thumbnailUrl))
                        },
                        onSeeAllClick = { kind, ref ->
                            navController.navigate(
                                Routes.ArtistList(
                                    artistId = state.artistId,
                                    artistName = state.name,
                                    kind = kind.name,
                                    browseId = ref.browseId,
                                    params = ref.params,
                                )
                            )
                        },
                    )
                }

                composable<Routes.ArtistList> {
                    val vm: ArtistListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    ArtistListScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onLoadMore = vm::onLoadMore,
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                        onAlbumClick = { navController.openAlbum(it) },
                        onPlaylistClick = { navController.openPlaylist(it) },
                    )
                }

                composable<Routes.TrackInfo> {
                    val vm: TrackInfoViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    TrackInfoScreen(
                        state = state,
                        isDownloaded = state.isDownloaded,
                        onBackClick = { navController.popBackStack() },
                        onFavoriteClick = vm::onFavoriteClick,
                        onAddToPlaylist = vm::onAddToPlaylist,
                        onDownloadClick = {
                            if (state.isDownloaded) vm.onRemoveDownloadClick() else vm.onDownloadClick()
                        },
                    )
                }
            }

            PlaybackChrome(
                navController = navController,
                currentTab = currentTab,
                playback = playback,
                track = track,
                dominantColor = dominantColor,
                miniPlayerVm = miniPlayerVm,
                isMinimized = isMinimized,
                isDetailScreen = isDetailScreen,
                isTrackInfoScreen = isTrackInfoScreen,
                bannerTrack = bannerTrack,
                onDismissBanner = bannerVm::dismissBanner,
                onViewDownloadsClick = { navController.navigate(Routes.Downloads) },
            )
        }
    }
}

@Composable
private fun Placeholder(message: String) {
    Box(Modifier.fillMaxSize()) {
        Text(text = message, color = VzOnSurface, modifier = Modifier.align(Alignment.Center))
        AppCredits(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}
VZ_EOF_10_REVERT

echo "[11/12] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistHeader.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistHeader.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistHeader.kt" <<'VZ_EOF_11_REVERT'
package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vztechcore.vztune.ui.components.feedback.fadeOutBottom
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.ArrowBack
import com.vztechcore.vztune.ui.icons.actions.PersonAdd
import com.vztechcore.vztune.ui.icons.actions.PersonCheck
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.player.Play
import com.vztechcore.vztune.ui.icons.player.Shuffle
import com.vztechcore.vztune.ui.screens.medialist.MediaListCircleButton

// Mantido pra compatibilidade com ArtistScreen antigo - agora sempre retorna preto.
// Camaleao desativado por enquanto, fundo e sempre AMOLED.
@Composable
fun rememberArtistAccent(thumbnailUrl: String?): Color = Color.Black

// Mantido pra compatibilidade - agora e sempre preto solido, sem gradiente de cor.
@Composable
fun rememberArtistChameleonBrush(accent: Color): Brush =
    Brush.verticalGradient(listOf(Color.Black, Color.Black))

/**
 * Hero do artista igual a foto: foto cheia em cima (Crop, corta pra preencher),
 * com fade pra preto embaixo, nome centralizado embaixo da foto,
 * stats centralizados, e 3 botoes circulares centralizados.
 * Fundo sempre preto AMOLED - sem camaleao.
 * Icones mantidos: Play, Shuffle, PersonAdd/PersonCheck.
 */
@Composable
fun ArtistHeader(
    state: ArtistUiState,
    accent: Color,
    onBackClick: () -> Unit,
    onRandomClick: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPlayableItems = state.topSongs.isNotEmpty() || state.videos.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        // Foto de perfil em cima - igual a foto: cheia, Crop, fade pra preto
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
        ) {
            ArtistPhoto(imageUrl = state.thumbnailUrl, name = state.name)

            // Sombra em cima pra botao voltar ficar legivel
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )

            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                MediaListCircleButton(VzIcons.ArrowBack, "Voltar", onBackClick)
            }
        }

        // Nome + stats centralizados embaixo da foto - igual a foto
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.name,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.statsLabel.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.statsLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 3 botoes circulares centralizados - igual a foto
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (hasPlayableItems) {
                // Esquerda: Play (toca embaralhado, igual o Aleatorio)
                CircleButton(
                    icon = VzIcons.Play,
                    contentDescription = "Tocar",
                    size = 56.dp,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    borderColor = Color.White.copy(alpha = 0.4f),
                    onClick = onRandomClick,
                )
                Spacer(Modifier.size(16.dp))
                // Meio: Shuffle branco grandao - igual a foto
                CircleButton(
                    icon = VzIcons.Shuffle,
                    contentDescription = "Aleatório",
                    size = 72.dp,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    borderColor = null,
                    onClick = onRandomClick,
                )
                Spacer(Modifier.size(16.dp))
            }
            // Direita: Seguir / Seguindo
            CircleButton(
                icon = if (state.isFollowing) VzIcons.PersonCheck else VzIcons.PersonAdd,
                contentDescription = if (state.isFollowing) "Seguindo" else "Seguir",
                size = 56.dp,
                containerColor = if (state.isFollowing) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                contentColor = Color.White,
                borderColor = Color.White.copy(alpha = 0.4f),
                onClick = onFollowClick,
            )
        }
    }
}

/**
 * Foto cheia, Crop (corta pra preencher, nao mostra inteira), alinhada em cima,
 * com fade pra preto embaixo pra derreter no fundo AMOLED.
 */
@Composable
private fun ArtistPhoto(imageUrl: String?, name: String) {
    if (imageUrl.isNullOrBlank()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = VzIcons.Artist,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(140.dp),
            )
        }
        return
    }
    val context = LocalContext.current
    val request = androidx.compose.runtime.remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(400)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = name,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .fadeOutBottom(fadeFraction = 0.55f),
    )
}

@Composable
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, CircleShape)
                else Modifier
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
    }
}
VZ_EOF_11_REVERT

echo "[12/12] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt" <<'VZ_EOF_12_REVERT'
package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.ArtistListKind
import com.vztechcore.vztune.core.domain.music.BrowseRef
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.cards.ArtistCircleCard
import com.vztechcore.vztune.ui.components.cards.CoverCard
import com.vztechcore.vztune.ui.components.cards.TrackListRow
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import com.vztechcore.vztune.ui.components.layout.SectionHeader
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.screens.medialist.CollapsedHeaderHost
import com.vztechcore.vztune.ui.screens.medialist.MediaListCollapsedHeader
import com.vztechcore.vztune.ui.screens.medialist.MediaListVideoCard
import com.vztechcore.vztune.ui.screens.medialist.rememberHeaderCollapsed

private val RowPadding = PaddingValues(horizontal = 16.dp)
private val CoverWidth = 160.dp
private val VideoWidth = 280.dp
private val RelatedArtistSize = 124.dp
private val SectionGap = 24.dp

/**
 * Artist profile. Sections (hidden when empty), in order:
 * popular songs, albums, singles, videos, featured, related artists, description, credits.
 * The whole screen sits on a backdrop tinted with the photo color; once the hero scrolls away,
 * a sticky bar (close + photo + name) fades in.
 */
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    onBackClick: () -> Unit,
    onRandomClick: () -> Unit,
    onFollowClick: () -> Unit,
    onSongClick: (Track) -> Unit,
    onVideoClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onSeeAllClick: (ArtistListKind, BrowseRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val isCollapsed by rememberHeaderCollapsed(listState)
    val nowPlaying = LocalNowPlaying.current

    // Fundo sempre preto AMOLED - camaleao desativado por enquanto

    fun seeAll(kind: ArtistListKind, ref: BrowseRef?): (() -> Unit)? =
        ref?.let { { onSeeAllClick(kind, it) } }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 140.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                ArtistHeader(
                    state = state,
                    accent = Color.Black,
                    onBackClick = onBackClick,
                    onRandomClick = onRandomClick,
                    onFollowClick = onFollowClick,
                )
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        Modifier.fillMaxWidth().height(280.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VzLoading(Modifier.size(160.dp))
                    }
                }

                state.errorMessage != null -> item(key = "error") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.errorMessage,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                else -> {
                    // ---------- Popular songs (numbered) ----------
                    if (state.topSongs.isNotEmpty()) {
                        item(key = "songs_header") {
                            SectionHeader(
                                title = "Músicas populares",
                                onActionClick = seeAll(ArtistListKind.SONGS, state.songsMore),
                            )
                        }
                        itemsIndexed(state.topSongs.take(5), key = { _, t -> "song_${t.id}" }) { index, track ->
                            TrackListRow(
                                track = track,
                                number = index + 1,
                                onClick = { onSongClick(track) },
                                onMoreClick = { onTrackInfoClick(track) },
                            )
                        }
                        item(key = "songs_space") { Spacer(Modifier.height(SectionGap)) }
                    }

                    rowSection(
                        key = "albums", title = "Álbuns", items = state.albums,
                        onMoreClick = seeAll(ArtistListKind.ALBUMS, state.albumsMore),
                        itemKey = { it.id },
                    ) { album ->
                        CoverCard(
                            title = album.title, subtitle = "Álbum", imageUrl = album.thumbnailUrl,
                            onClick = { onAlbumClick(album) }, modifier = Modifier.width(CoverWidth),
                        )
                    }

                    rowSection(
                        key = "singles", title = "Singles", items = state.singles,
                        onMoreClick = seeAll(ArtistListKind.SINGLES, state.singlesMore),
                        itemKey = { it.id },
                    ) { single ->
                        CoverCard(
                            title = single.title, subtitle = "Single", imageUrl = single.thumbnailUrl,
                            onClick = { onAlbumClick(single) }, modifier = Modifier.width(CoverWidth),
                        )
                    }

                    rowSection(
                        key = "videos", title = "Vídeos", items = state.videos,
                        onMoreClick = seeAll(ArtistListKind.VIDEOS, state.videosMore),
                        itemKey = { it.id },
                    ) { video ->
                        val playback = nowPlaying.playbackOf(video)
                        MediaListVideoCard(
                            track = video,
                            isPlaying = playback.isPlaying,
                            isPaused = playback.isPaused,
                            onClick = { onVideoClick(video) },
                            onMoreClick = { onTrackInfoClick(video) },
                            modifier = Modifier.width(VideoWidth),
                            // The row already has its own 16dp edge padding; the card's default
                            // inset would push the thumbnails away from the section title.
                            horizontalPadding = 0.dp,
                        )
                    }

                    rowSection(
                        key = "featured", title = "Destacado", items = state.featured,
                        onMoreClick = seeAll(ArtistListKind.FEATURED, state.featuredMore),
                        itemKey = { it.id },
                    ) { playlist ->
                        CoverCard(
                            title = playlist.title, subtitle = playlist.owner, imageUrl = playlist.thumbnailUrl,
                            onClick = { onPlaylistClick(playlist) }, modifier = Modifier.width(CoverWidth),
                        )
                    }

                    rowSection(
                        key = "related", title = "Artistas relacionados", items = state.relatedArtists,
                        onMoreClick = null, spacing = 16.dp,
                        itemKey = { it.id },
                    ) { artist ->
                        ArtistCircleCard(
                            name = artist.name, imageUrl = artist.thumbnailUrl,
                            onClick = { onArtistClick(artist) },
                            size = RelatedArtistSize,
                        )
                    }

                    item(key = "description") { ArtistDescription(description = state.description) }
                }
            }

            item(key = "credits") { AppCredits() }
        }

        CollapsedHeaderHost(visible = isCollapsed) {
            MediaListCollapsedHeader(
                title = state.name,
                thumbnailUrl = state.thumbnailUrl,
                onBackClick = onBackClick,
                dominantColor = Color.Black,
                circularThumbnail = true,
            )
        }
    }
}

/** A titled horizontal row ("Álbuns", "Vídeos"...). Nothing is emitted when [items] is empty. */
private fun <T> LazyListScope.rowSection(
    key: String,
    title: String,
    items: List<T>,
    onMoreClick: (() -> Unit)?,
    itemKey: (T) -> Any,
    spacing: Dp = 12.dp,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "${key}_header") { SectionHeader(title = title, onActionClick = onMoreClick) }
    item(key = "${key}_row") {
        LazyRow(
            contentPadding = RowPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(items, key = itemKey) { itemContent(it) }
        }
    }
    item(key = "${key}_space") { Spacer(Modifier.height(SectionGap)) }
}

/** "About" card: soft translucent surface, expands in place when the text is long. */
@Composable
private fun ArtistDescription(description: String?) {
    var expanded by remember { mutableStateOf(false) }
    val text = description?.takeIf { it.isNotBlank() }
    val canExpand = text != null && text.length > 220

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .animateContentSize()
            .clickable(enabled = canExpand) { expanded = !expanded }
            .padding(16.dp)
    ) {
        Text(
            text = "Sobre",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text ?: "Sem descrição",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = if (text != null) 0.75f else 0.5f),
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
        )
        if (canExpand) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (expanded) "Ver menos" else "Ver mais",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
VZ_EOF_12_REVERT

echo ""
echo "Pronto! 12 arquivos atualizados:"
echo " - Logo oficial VzLogoStatic (V azul + Z branco, sem pontinho)"
echo " - Fundo preto AMOLED em tudo, sem camaleao"
echo " - Sem picker de cor, capa aleatoria pelo sistema"
echo " - Artista igual a foto (Crop + 3 botoes circulares)"
echo "Roda o build agora."