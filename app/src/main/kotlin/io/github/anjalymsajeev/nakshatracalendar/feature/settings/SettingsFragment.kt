package io.github.anjalymsajeev.nakshatracalendar.feature.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.databinding.FragmentSettingsBinding
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StarDayConvention
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ThemeMode
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSettingsBinding.bind(view)

        // Language: AppCompat's per-app locale is the single source of truth (persisted by AppCompat).
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        binding.languageGroup.check(if (current.startsWith("ml")) R.id.language_malayalam else R.id.language_english)
        binding.languageGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val tag = if (checkedId == R.id.language_malayalam) "ml" else "en"
            // Changing the locale recreates the activity; skip when nothing changes.
            if (AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith(tag)) return@addOnButtonCheckedListener
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }

        // True while the screen renders stored values, so programmatic check() calls are not treated as user input.
        var rendering = false

        binding.themeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || rendering) return@addOnButtonCheckedListener
            viewModel.setThemeMode(
                when (checkedId) {
                    R.id.theme_light -> ThemeMode.LIGHT
                    R.id.theme_dark -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                }
            )
        }
        binding.starDayGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || rendering) return@addOnButtonCheckedListener
            viewModel.setStarDayConvention(
                if (checkedId == R.id.star_day_six_nazhika) StarDayConvention.SIX_NAZHIKA else StarDayConvention.SUNRISE
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.filterNotNull().collect { settings ->
                    rendering = true
                    binding.themeGroup.check(
                        when (settings.themeMode) {
                            ThemeMode.SYSTEM -> R.id.theme_system
                            ThemeMode.LIGHT -> R.id.theme_light
                            ThemeMode.DARK -> R.id.theme_dark
                        }
                    )
                    binding.starDayGroup.check(
                        when (settings.starDayConvention) {
                            StarDayConvention.SUNRISE -> R.id.star_day_sunrise
                            StarDayConvention.SIX_NAZHIKA -> R.id.star_day_six_nazhika
                        }
                    )
                    binding.calculationLocation.text = settings.calculationLocation.name
                    rendering = false
                }
            }
        }
    }
}
