package com.momi.watermarker.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.momi.watermarker.di.IoDispatcher
import com.momi.watermarker.domain.model.ThemeMode
import com.momi.watermarker.domain.repository.ThemeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** [ThemeRepository] backed by a private SharedPreferences file. */
@Singleton
class ThemeRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ThemeRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val themeMode: Flow<ThemeMode> = callbackFlow {
        fun emitCurrent() {
            trySend(current())
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_THEME_MODE) emitCurrent()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        emitCurrent()
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
        .distinctUntilChanged()
        .flowOn(dispatcher)

    override fun current(): ThemeMode =
        ThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, null))

    override suspend fun setThemeMode(mode: ThemeMode) {
        withContext(dispatcher) {
            prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "momi_theme"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
