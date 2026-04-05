/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for native Kotlin bridge algorithm (Phase 8).
 *
 * BridgeAlgorithm and KotlinBridgeCache use constructor injection via
 * @Inject constructor + @Singleton — no explicit @Provides needed.
 * This module exists as documentation and for future @Provides if needed.
 *
 * @ApplicationScope CoroutineScope is already provided by AppModule.provideApplicationScope().
 */
@Module
@InstallIn(SingletonComponent::class)
object BridgeAlgorithmModule
