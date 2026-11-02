package com.vztechcore.vztune.data.prefs

import android.content.Context
import com.vztechcore.vztune.core.domain.search.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaListPreferencesStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("vztune_medialists", Context.MODE_PRIVATE)

    fun getSortOrder(id: String): SortOrder {
        val name = prefs.getString("sort_$id", SortOrder.OLD_TO_RECENT.name)
        return runCatching { SortOrder.valueOf(name ?: "") }
            .getOrDefault(SortOrder.OLD_TO_RECENT)
    }

    fun setSortOrder(id: String, order: SortOrder) {
        prefs.edit().putString("sort_$id", order.name).apply()
    }

    fun getManualOrder(id: String): List<String> {
        val raw = prefs.getString("manual_$id", null) ?: return emptyList()
        return raw.split("\u0001").filter { it.isNotBlank() }
    }

    fun setManualOrder(id: String, trackIds: List<String>) {
        prefs.edit().putString("manual_$id", trackIds.joinToString("\u0001")).apply()
    }
}