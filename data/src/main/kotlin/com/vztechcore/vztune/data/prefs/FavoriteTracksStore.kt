package com.vztechcore.vztune.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks the user favorited; shown in Library > Favoritos. */
@Singleton
class FavoriteTracksStore @Inject constructor(
    @ApplicationContext context: Context,
) : TrackListStore(context, storeName = "vztune_favorite_tracks", maxItems = Int.MAX_VALUE)
