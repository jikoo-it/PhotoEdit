package com.momi.watermarker.presentation

import app.cash.turbine.test
import com.momi.watermarker.MainDispatcherRule
import com.momi.watermarker.domain.model.ThemeMode
import com.momi.watermarker.domain.repository.ThemeRepository
import com.momi.watermarker.presentation.theme.ThemeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `starts with the repository snapshot`() = runTest {
        val repo = FakeThemeRepository(ThemeMode.DARK)
        val viewModel = ThemeViewModel(repo)
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `selecting light updates theme mode`() = runTest {
        val repo = FakeThemeRepository()
        val viewModel = ThemeViewModel(repo)
        viewModel.themeMode.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem())
            viewModel.onThemeModeSelected(ThemeMode.LIGHT)
            advanceUntilIdle()
            assertEquals(ThemeMode.LIGHT, awaitItem())
        }
    }

    private class FakeThemeRepository(
        initial: ThemeMode = ThemeMode.SYSTEM,
    ) : ThemeRepository {
        private val mode = MutableStateFlow(initial)
        override val themeMode: Flow<ThemeMode> = mode.asStateFlow()
        override fun current(): ThemeMode = mode.value
        override suspend fun setThemeMode(mode: ThemeMode) {
            this.mode.value = mode
        }
    }
}
