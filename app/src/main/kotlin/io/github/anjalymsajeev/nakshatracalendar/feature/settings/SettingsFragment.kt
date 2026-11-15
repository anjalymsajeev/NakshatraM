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
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.databinding.DialogCustomReminderBinding
import io.github.anjalymsajeev.nakshatracalendar.databinding.FragmentSettingsBinding
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ReminderSetting
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StarDayConvention
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ThemeMode
import io.github.anjalymsajeev.nakshatracalendar.feature.common.label
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

        binding.googleAccount.setOnClickListener { findNavController().navigate(R.id.action_global_google_account) }
        binding.syncHistory.setOnClickListener { findNavController().navigate(R.id.action_global_sync_history) }
        binding.reminder.setOnClickListener { chooseReminder() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.calendar.collect { calendar ->
                        binding.googleAccount.text = getString(
                            R.string.settings_google_account_value,
                            calendar?.name ?: getString(R.string.account_not_connected_short)
                        )
                    }
                }
                launch {
                    viewModel.reminder.filterNotNull().collect { reminder ->
                        binding.reminder.text = getString(R.string.settings_reminder_value, reminder.label(requireContext()))
                    }
                }
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

    /** Presets plus "Custom…" (Feature 7). */
    private fun chooseReminder() {
        val presets = ReminderSetting.PRESETS
        val current = viewModel.reminder.value
        val labels = presets.map { it.label(requireContext()) } + getString(R.string.reminder_custom)
        val checked = presets.indexOf(current).takeIf { it >= 0 } ?: presets.size
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_reminder)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                if (which < presets.size) viewModel.setReminder(presets[which]) else chooseCustomReminder()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun chooseCustomReminder() {
        val dialogBinding = DialogCustomReminderBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reminder_custom)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .show()
        // Validate before closing: Google accepts up to four weeks.
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val amount = dialogBinding.amount.text?.toString()?.toIntOrNull()
            val factor = when (dialogBinding.unitGroup.checkedButtonId) {
                R.id.unit_minutes -> 1
                R.id.unit_days -> 1_440
                else -> 60
            }
            val minutes = amount?.let { it.toLong() * factor }
            if (minutes == null || minutes < 0 || minutes > ReminderSetting.MAX_MINUTES) {
                dialogBinding.amountLayout.error = getString(R.string.reminder_custom_error)
            } else {
                viewModel.setReminder(ReminderSetting.Before(minutes.toInt()))
                dialog.dismiss()
            }
        }
    }
}
