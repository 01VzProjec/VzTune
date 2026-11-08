package com.vztechcore.vztune.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Every track that played in the app; feeds "Recentemente tocadas" in the Library. */
@Singleton
class PlayHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
) : TrackListStore(context, storeName = "vztune_play_history", maxItems = 50)
