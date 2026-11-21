package com.vztechcore.vztune.data.ytmusic

import java.text.Normalizer

/**
 * Resolve o artista certo quando só temos o NOME (clique em artista dentro de álbum/faixa).
 *
 * Bug antigo: searchArtists(nome).firstOrNull() pegava o primeiro da busca.
 * Com nomes repetidos (ex: "MC Ryan", "Henrique e Juliano", "Matheus") o primeiro
 * quase sempre era o artista errado ou o canal "- Topic".
 *
 * Sistema do SimpMusic (StringMatcher.kt no TXT):
 * - Levenshtein pra pegar o nome MAIS PARECIDO, não o primeiro.
 * - Navegação usa channelId (UC...) direto quando já tem, nunca re-busca por nome.
 *
 * Aqui + a verificação que você pediu:
 * - 1. Rankeia candidatos por Levenshtein no nome normalizado (sem acento, sem " - Topic").
 * - 2. Desempata Topic: mesmo nome, o oficial (sem " - Topic") ganha.
 * - 3. Verifica catálogo: abre a página dos 3 mais parecidos e só aceita quem tem
 *    aquela faixa / aquele álbum no catálogo. Quem não tem, não é ele.
 * - 4. Sem contexto de faixa/álbum (ex: clique no dono da playlist), cai pro mais parecido.
 */
internal object ArtistResolver {

    /** Normaliza pra comparar: minúsculo, sem acento, sem " - Topic", sem pontuação, espaços únicos. */
    fun normalize(raw: String): String {
        var s = raw.trim().lowercase()
        s = s.removeSuffix(" - topic").removeSuffix("- topic").trim()
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        s = s.replace(Regex("[^a-z0-9 ]"), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun isTopicName(raw: String): Boolean =
        raw.contains(" - topic", ignoreCase = true) || raw.trim().endsWith("topic", ignoreCase = true)

    // Levenshtein VERBATIM do SimpMusic (extension/StringMatcher.kt). Não mudei nada.
    fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1) { 0 }
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val editCost = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + editCost
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }

    fun distance(query: String, candidateName: String): Int =
        levenshtein(normalize(query), normalize(candidateName))

    /** Título de faixa/álbum bate? Igual normalizado, ou quase igual, ou um contém o outro. */
    fun titlesMatch(catalogTitle: String, targetTitle: String): Boolean {
        val a = normalize(catalogTitle)
        val b = normalize(targetTitle)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        // "Nome (feat. X)" / "Nome (Deluxe)" no catálogo vs "Nome" limpo no clique
        if (a.contains(b) || b.contains(a)) return true
        // Erro de digitação / acento que sobrou: distância pequena
        val maxDist = maxOf(2, b.length / 5)
        return levenshtein(a, b) <= maxDist
    }

    /** O candidato tem ESSA playlist no catálogo? featured/destaque contém o id ou o título da playlist. */
    fun playlistContains(
        featured: List<com.vztechcore.vztune.core.domain.music.Playlist>,
        playlistId: String?,
        playlistTitle: String?,
    ): Boolean {
        if (!playlistId.isNullOrBlank()) {
            if (featured.any { it.id == playlistId }) return true
        }
        if (!playlistTitle.isNullOrBlank()) {
            if (featured.any { titlesMatch(it.title, playlistTitle) }) return true
        }
        return false
    }

    /**
     * O candidato tem essa faixa / esse álbum no catálogo?
     * topSongs + videos provam a faixa, albums + singles provam o álbum.
     */
    fun catalogContains(
        topSongTitles: List<String>,
        videoTitles: List<String>,
        albumTitles: List<String>,
        singleTitles: List<String>,
        trackTitle: String?,
        albumTitle: String?,
    ): Boolean {
        if (!trackTitle.isNullOrBlank()) {
            val hit = (topSongTitles + videoTitles).any { titlesMatch(it, trackTitle) }
            if (hit) return true
        }
        if (!albumTitle.isNullOrBlank()) {
            val hit = (albumTitles + singleTitles).any { titlesMatch(it, albumTitle) }
            if (hit) return true
        }
        return false
    }
}
