package com.momi.watermarker.domain.repository

import com.momi.watermarker.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Persists and observes the user's light/dark appearance choice. */
interface ThemeRepository {

    /** Emits the current [ThemeMode] and every later change. */
    val themeMode: Flow<ThemeMode>

    /** Synchronous snapshot used to seed UI before the [themeMode] flow emits. */
    fun current(): ThemeMode

    suspend fun setThemeMode(mode: ThemeMode)
}
