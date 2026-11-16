package com.vztechcore.vztune.data.prefs.models

import kotlinx.serialization.Serializable

/**
 * Playlist ou álbum que o usuário baixou via "Baixar todas as faixas" / "Baixar álbum".
 * Sem isso a aba "Playlists baixadas" ficava sempre vazia: o download era só por faixa,
 * ninguém registrava a qual playlist/álbum aquelas faixas pertenciam.
 */
@Serializable
data class DownloadedPlaylist(
    val id: String,
    val title: String,
    val owner: String? = null,
    val thumbnailUrl: String? = null,
    val mosaic: List<String> = emptyList(),
    val isAlbum: Boolean = false,
    // Keys "SOURCE_id" das faixas baixadas. A playlist some da aba quando nenhuma
    // dessas keys ainda está baixada (usuário apagou todos os downloads dela).
    val trackKeys: List<String> = emptyList(),
    val downloadedAtMs: Long = System.currentTimeMillis(),
)
