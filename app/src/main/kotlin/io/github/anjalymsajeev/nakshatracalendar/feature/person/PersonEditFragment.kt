package io.github.anjalymsajeev.nakshatracalendar.feature.person

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.databinding.FragmentPersonEditBinding
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventColor
import io.github.anjalymsajeev.nakshatracalendar.domain.model.PersonValidationError
import io.github.anjalymsajeev.nakshatracalendar.feature.common.labelRes
import io.github.anjalymsajeev.nakshatracalendar.feature.nakshatra.NakshatraPickerBottomSheet
import io.github.anjalymsajeev.nakshatracalendar.feature.person.PlaceSearchBottomSheet.Companion.toLocation
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** Add or edit a family member (design: refined_pirannal_calculator). Renders state and forwards input only. */
@AndroidEntryPoint
class PersonEditFragment : Fragment(R.layout.fragment_person_edit) {
    private val viewModel: PersonEditViewModel by viewModels()
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentPersonEditBinding.bind(view)
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.nameInput.doAfterTextChanged { viewModel.onNameChanged(it?.toString().orEmpty()) }
        b.nakshatraInput.setOnClickListener {
            NakshatraPickerBottomSheet.newInstance(
                viewModel.state.value.nakshatra?.number
            ).show(childFragmentManager, NakshatraPickerBottomSheet.TAG)
        }
        b.birthDateInput.setOnClickListener { pickBirthDate() }
        b.birthDateLayout.setEndIconOnClickListener { viewModel.onBirthDateSelected(null) }
        b.birthTimeInput.setOnClickListener { pickBirthTime() }
        b.birthTimeLayout.setEndIconOnClickListener { viewModel.onBirthTimeSelected(null) }
        b.birthPlaceInput.setOnClickListener {
            PlaceSearchBottomSheet.newInstance(KEY_BIRTH_PLACE).show(childFragmentManager, PlaceSearchBottomSheet.TAG)
        }
        b.birthPlaceLayout.setEndIconOnClickListener { viewModel.onBirthPlaceSelected(null) }
        b.calculationLocationInput.setOnClickListener {
            PlaceSearchBottomSheet.newInstance(KEY_CALCULATION_PLACE).show(childFragmentManager, PlaceSearchBottomSheet.TAG)
        }
        b.calculateStar.setOnClickListener { viewModel.calculateStar() }
        b.save.setOnClickListener { viewModel.save() }
        buildColorChips(b)

        childFragmentManager.setFragmentResultListener(NakshatraPickerBottomSheet.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            viewModel.onNakshatraSelected(bundle.getInt(NakshatraPickerBottomSheet.RESULT_NUMBER))
        }
        childFragmentManager.setFragmentResultListener(KEY_BIRTH_PLACE, viewLifecycleOwner) { _, bundle ->
            viewModel.onBirthPlaceSelected(bundle.toLocation())
        }
        childFragmentManager.setFragmentResultListener(KEY_CALCULATION_PLACE, viewLifecycleOwner) { _, bundle ->
            viewModel.onCalculationLocationSelected(bundle.toLocation())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(b, it) } }
                launch { viewModel.eventFlow.collect { handle(b, it) } }
            }
        }
    }

    private fun render(b: FragmentPersonEditBinding, s: PersonFormState) {
        b.toolbar.setTitle(if (s.isEditing) R.string.person_edit_title else R.string.person_add_title)
        b.progress.isVisible = s.isLoading
        b.form.isVisible = !s.isLoading
        if (b.nameInput.text?.toString() != s.name) b.nameInput.setText(s.name)
        b.nakshatraInput.setText(s.nakshatra?.let { getString(R.string.home_nakshatra_names, it.nameMalayalam, it.nameEnglish) }.orEmpty())
        b.birthDateInput.setText(s.birthDate?.format(dateFormatter).orEmpty())
        b.birthTimeInput.setText(s.birthTime?.format(timeFormatter).orEmpty())
        b.birthPlaceInput.setText(s.birthPlace?.let { getString(R.string.place_with_zone, it.name, it.zone.id) }.orEmpty())
        b.calculationLocationInput.setText(getString(R.string.place_with_zone, s.calculationLocation.name, s.calculationLocation.zone.id))
        b.calculateStar.isEnabled = !s.isCalculating
        b.calculateProgress.isVisible = s.isCalculating
        b.save.isEnabled = !s.isSaving
        b.colorGroup.check(s.eventColor.ordinal + COLOR_ID_OFFSET)

        b.nameLayout.error = when {
            PersonValidationError.NameRequired in s.errors -> getString(R.string.error_name_required)
            PersonValidationError.NameTooLong in s.errors -> getString(R.string.error_name_too_long)
            else -> null
        }
        b.nakshatraLayout.error =
            if (PersonValidationError.NakshatraRequired in s.errors) getString(R.string.error_nakshatra_required) else null
        b.birthDateLayout.error = when {
            PersonValidationError.BirthDateInFuture in s.errors -> getString(R.string.error_birth_date_future)
            PersonValidationError.BirthDateTooEarly in s.errors -> getString(R.string.error_birth_date_too_early)
            PersonValidationError.BirthPlaceWithoutDate in s.errors -> getString(R.string.error_birth_details_need_date)
            else -> null
        }
    }

    private fun handle(b: FragmentPersonEditBinding, event: PersonEditEvent) {
        when (event) {
            PersonEditEvent.Saved -> findNavController().navigateUp()
            is PersonEditEvent.Message -> Snackbar.make(b.root, event.message, Snackbar.LENGTH_LONG).show()
            is PersonEditEvent.BirthStarCalculated ->
                Snackbar.make(
                    b.root,
                    getString(R.string.person_star_calculated, event.star.nameMalayalam, event.star.nameEnglish),
                    Snackbar.LENGTH_LONG
                ).show()
            is PersonEditEvent.ChooseBirthStar -> {
                val labels = event.candidates.map {
                    getString(R.string.home_nakshatra_names, it.nameMalayalam, it.nameEnglish)
                }.toTypedArray()
                var chosen = event.candidates.indexOf(event.suggested).coerceAtLeast(0)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.person_choose_star_title)
                    .setSingleChoiceItems(labels, chosen) { _, which -> chosen = which }
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_ok) { _, _ -> viewModel.onNakshatraSelected(event.candidates[chosen].number) }
                    .setMessage(null)
                    .show()
            }
        }
    }

    private fun pickBirthDate() {
        val selection = (viewModel.state.value.birthDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val earliest = LocalDate.of(1900, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.person_birth_date)
            .setSelection(selection)
            .setCalendarConstraints(
                CalendarConstraints.Builder().setStart(earliest).setValidator(DateValidatorPointBackward.now()).build()
            )
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            viewModel.onBirthDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
        picker.show(childFragmentManager, "birthDate")
    }

    private fun pickBirthTime() {
        val current = viewModel.state.value.birthTime ?: LocalTime.of(6, 0)
        val picker = MaterialTimePicker.Builder()
            .setTitleText(R.string.person_birth_time)
            .setTimeFormat(if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(current.hour)
            .setMinute(current.minute)
            .build()
        picker.addOnPositiveButtonClickListener { viewModel.onBirthTimeSelected(LocalTime.of(picker.hour, picker.minute)) }
        picker.show(childFragmentManager, "birthTime")
    }

    private fun buildColorChips(b: FragmentPersonEditBinding) {
        EventColor.entries.forEach { color ->
            val chip = Chip(requireContext()).apply {
                id = color.ordinal + COLOR_ID_OFFSET
                isCheckable = true
                text = ""
                chipBackgroundColor = ColorStateList.valueOf(color.argb.toInt())
                chipMinHeight = resources.getDimension(R.dimen.color_swatch)
                chipStartPadding = 0f
                chipEndPadding = 0f
                isCheckedIconVisible = true
                contentDescription = getString(color.labelRes())
                setOnClickListener { viewModel.onColorSelected(color) }
            }
            b.colorGroup.addView(chip)
        }
    }

    private companion object {
        const val KEY_BIRTH_PLACE = "birth_place"
        const val KEY_CALCULATION_PLACE = "calculation_place"
        const val COLOR_ID_OFFSET = 1000
    }
}
