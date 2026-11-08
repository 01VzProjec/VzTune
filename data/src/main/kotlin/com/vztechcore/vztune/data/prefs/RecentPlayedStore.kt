package com.vztechcore.vztune.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks played from the Search screen; feeds the "recently played" row under the search input. */
@Singleton
class RecentPlayedStore @Inject constructor(
    @ApplicationContext context: Context,
) : TrackListStore(context, storeName = "vztune_recent_played", maxItems = 20)
