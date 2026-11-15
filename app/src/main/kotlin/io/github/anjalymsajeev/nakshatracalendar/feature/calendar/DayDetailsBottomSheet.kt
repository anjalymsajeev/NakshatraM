package io.github.anjalymsajeev.nakshatracalendar.feature.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.databinding.SheetDayDetailsBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Details of one calendar day. Receives ready-made text, so it survives recreation without a ViewModel. */
class DayDetailsBottomSheet : BottomSheetDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetDayDetailsBinding.inflate(inflater, container, false).also { bind(it) }.root

    private fun bind(binding: SheetDayDetailsBinding) {
        val args = requireArguments()
        val locale = resources.configuration.locales[0]
        val date = LocalDate.ofEpochDay(args.getLong(ARG_DATE))
        binding.gregorian.text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
        binding.malayalam.text = args.getString(ARG_MALAYALAM)
        val nakshatra = args.getString(ARG_NAKSHATRA)
        binding.nakshatra.text = nakshatra?.let { name ->
            args.getString(ARG_NAKSHATRA_END)?.let { getString(R.string.calendar_nakshatra_until, name, it) } ?: name
        } ?: getString(R.string.error_calculation)
        val family = args.getStringArrayList(ARG_FAMILY).orEmpty()
        binding.familyTitle.isVisible = family.isNotEmpty()
        binding.family.isVisible = family.isNotEmpty()
        binding.family.text = family.joinToString("\n")
    }

    companion object {
        const val TAG = "day_details"
        private const val ARG_DATE = "date"
        private const val ARG_MALAYALAM = "malayalam"
        private const val ARG_NAKSHATRA = "nakshatra"
        private const val ARG_NAKSHATRA_END = "nakshatra_end"
        private const val ARG_FAMILY = "family"

        fun newInstance(details: DayDetailsUi, malayalam: Boolean) = DayDetailsBottomSheet().apply {
            val endFormatter = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
            arguments = bundleOf(
                ARG_DATE to details.date.toEpochDay(),
                ARG_MALAYALAM to if (malayalam) details.malayalamMl else details.malayalamEn,
                ARG_NAKSHATRA to if (malayalam) details.nakshatraMl else details.nakshatraEn,
                ARG_NAKSHATRA_END to details.nakshatraEnd?.let { "${it.format(endFormatter)} (${it.zone.id})" },
                ARG_FAMILY to ArrayList(if (malayalam) details.familyMl else details.familyEn)
            )
        }
    }
}
