package com.vztechcore.vztune.ui.screens.shorts

import com.vztechcore.vztune.core.domain.music.Track

/**
 * Handoff em memoria da lista de Shorts pra tela cheia (a rota leva so a chave).
 * Guarda no maximo 5 feeds; o mais antigo sai quando entra um novo.
 */
object ShortsFeed {
    private val feeds = LinkedHashMap<String, List<Track>>()

    @Synchronized
    fun put(items: List<Track>): String {
        val key = "shorts_" + System.nanoTime()
        feeds[key] = items
        while (feeds.size > 5) feeds.remove(feeds.keys.first())
        return key
    }

    @Synchronized
    fun get(key: String): List<Track> = feeds[key].orEmpty()
}
