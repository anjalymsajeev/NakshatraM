package io.github.anjalymsajeev.nakshatracalendar.feature

import io.github.anjalymsajeev.nakshatracalendar.domain.model.StarDayConvention
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ThemeMode
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import io.github.anjalymsajeev.nakshatracalendar.feature.settings.SettingsViewModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Regression tests for the Settings recreate loop (redundant theme writes recreated the activity endlessly). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<SettingsRepository>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `settings are null until stored values arrive, and nothing is written meanwhile`() = runTest(dispatcher) {
        every { repository.settings } returns emptyFlow()
        val viewModel = SettingsViewModel(repository, mockk(relaxed = true), mockk(relaxed = true))
        backgroundScope.launch { viewModel.settings.collect {} }

        assertNull(viewModel.settings.value)
        viewModel.setThemeMode(ThemeMode.SYSTEM)
        viewModel.setStarDayConvention(StarDayConvention.SUNRISE)

        coVerify(exactly = 0) { repository.setThemeMode(any()) }
        coVerify(exactly = 0) { repository.setStarDayConvention(any()) }
    }

    @Test
    fun `writing the stored value again is skipped, a new value is written`() = runTest(dispatcher) {
        val stored = UserSettings(themeMode = ThemeMode.DARK, starDayConvention = StarDayConvention.SIX_NAZHIKA)
        every { repository.settings } returns MutableStateFlow(stored)
        val viewModel = SettingsViewModel(repository, mockk(relaxed = true), mockk(relaxed = true))
        backgroundScope.launch { viewModel.settings.collect {} }
        assertEquals(stored, viewModel.settings.value)

        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setStarDayConvention(StarDayConvention.SIX_NAZHIKA)
        coVerify(exactly = 0) { repository.setThemeMode(any()) }
        coVerify(exactly = 0) { repository.setStarDayConvention(any()) }

        viewModel.setThemeMode(ThemeMode.LIGHT)
        coVerify(exactly = 1) { repository.setThemeMode(ThemeMode.LIGHT) }
    }
}
