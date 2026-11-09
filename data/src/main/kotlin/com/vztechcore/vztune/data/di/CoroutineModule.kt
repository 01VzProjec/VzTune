package com.vztechcore.vztune.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks a CoroutineScope that lives for the entire process lifetime.
 * Use this for singletons that must keep running even when every ViewModel
 * and Activity is destroyed (e.g. [com.vztechcore.vztune.data.player.PlaybackRecorder]).
 *
 * Lives in :data (not :app) because its only consumer is a :data singleton.
 * :app is not allowed to depend on :data internals, and :data cannot see :app.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}