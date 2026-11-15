package io.github.anjalymsajeev.nakshatracalendar.feature.calendar

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.databinding.FragmentCalendarBinding
import io.github.anjalymsajeev.nakshatracalendar.databinding.ItemCalendarDayBinding
import io.github.anjalymsajeev.nakshatracalendar.feature.common.isMalayalamUi
import java.time.DayOfWeek
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** Calendar tab (Feature 5): renders [CalendarViewModel] state; no date logic here. */
@AndroidEntryPoint
class CalendarFragment : Fragment(R.layout.fragment_calendar) {
    private val viewModel: CalendarViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCalendarBinding.bind(view)
        val malayalam = requireContext().isMalayalamUi()
        val locale = resources.configuration.locales[0]
        val adapter = DayAdapter(malayalam, locale) { details ->
            DayDetailsBottomSheet.newInstance(details, malayalam).show(childFragmentManager, DayDetailsBottomSheet.TAG)
        }
        binding.grid.adapter = adapter
        buildWeekdayHeader(binding.weekdays, locale)

        binding.previous.setOnClickListener { viewModel.previous() }
        binding.next.setOnClickListener { viewModel.next() }
        binding.today.setOnClickListener { viewModel.goToToday() }
        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) viewModel.setMode(if (checkedId == R.id.mode_malayalam) CalendarMode.MALAYALAM else CalendarMode.GREGORIAN)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.period?.let { period ->
                        val checked = if (period.mode == CalendarMode.MALAYALAM) R.id.mode_malayalam else R.id.mode_gregorian
                        if (binding.modeGroup.checkedButtonId != checked) binding.modeGroup.check(checked)
                        binding.title.text = title(period, malayalam, locale)
                        binding.subtitle.text = subtitle(period, malayalam, locale)
                    }
                    binding.previous.isEnabled = state.canGoBack
                    binding.next.isEnabled = state.canGoForward
                    binding.progress.isVisible = state.loading
                    binding.error.isVisible = state.error != null
                    state.error?.let { binding.error.setText(it) }
                    adapter.submitList(state.cells)
                }
            }
        }
    }

    private fun title(period: CalendarPeriod, malayalam: Boolean, locale: Locale): String = when (period.mode) {
        CalendarMode.GREGORIAN -> period.gregorianMonth!!.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        CalendarMode.MALAYALAM -> period.malayalamMonth!!.let {
            getString(R.string.calendar_malayalam_title, if (malayalam) it.nameMalayalam else it.nameEnglish, it.kollavarshamYear)
        }
    }

    private fun subtitle(period: CalendarPeriod, malayalam: Boolean, locale: Locale): String = when (period.mode) {
        CalendarMode.GREGORIAN -> {
            val names = period.overlappedMalayalam.map { if (malayalam) it.nameMalayalam else it.nameEnglish }
            val years = period.overlappedMalayalam.map { it.kollavarshamYear }.distinct().joinToString("–")
            getString(R.string.calendar_gregorian_subtitle, names.joinToString(" – "), years)
        }
        CalendarMode.MALAYALAM -> {
            val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            getString(R.string.calendar_malayalam_subtitle, period.first.format(formatter), period.last.format(formatter))
        }
    }

    private fun buildWeekdayHeader(row: LinearLayout, locale: Locale) {
        row.removeAllViews()
        val sundayFirst = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }
        sundayFirst.forEach { day ->
            row.addView(
                TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    gravity = Gravity.CENTER
                    text = day.getDisplayName(TextStyle.SHORT, locale)
                    contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                    setTextAppearance(R.style.TextAppearance_NakshatraCalendar_Label)
                }
            )
        }
    }

    private class DayAdapter(private val malayalam: Boolean, private val locale: Locale, private val onDay: (DayDetailsUi) -> Unit) :
        ListAdapter<CalendarCellUi, DayAdapter.Holder>(Diff) {
        class Holder(val binding: ItemCalendarDayBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val cell = getItem(position)
            val context = holder.itemView.context
            with(holder.binding) {
                val blank = cell.date == null
                root.visibility = if (blank) View.INVISIBLE else View.VISIBLE
                root.isClickable = !blank
                if (blank) return
                primary.text = cell.primary
                secondary.text = cell.secondaryMonth?.let {
                    "${cell.secondary} ${Month.of(it).getDisplayName(TextStyle.SHORT, locale)}"
                } ?: cell.secondary
                nakshatra.text = if (malayalam) cell.nakshatraMl else cell.nakshatraEn
                root.strokeWidth =
                    context.resources.getDimensionPixelSize(if (cell.isToday) R.dimen.today_stroke else R.dimen.gold_trim_hairline)
                synced.isVisible = cell.synced
                dots.removeAllViews()
                cell.familyColors.take(MAX_DOTS).forEach { color ->
                    dots.addView(
                        View(context).apply {
                            val size = context.resources.getDimensionPixelSize(R.dimen.family_dot)
                            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = size / 2 }
                            setBackgroundResource(R.drawable.color_dot)
                            backgroundTintList = ColorStateList.valueOf(color)
                        }
                    )
                }
                val details = cell.details!!
                root.contentDescription = buildString {
                    append(details.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)))
                    append(", ").append(if (malayalam) details.malayalamMl else details.malayalamEn)
                    (if (malayalam) details.nakshatraMl else details.nakshatraEn)?.let { append(", ").append(it) }
                    if (cell.familyColors.isNotEmpty()) {
                        append(
                            ", "
                        ).append(
                            context.getString(
                                R.string.calendar_family_days,
                                (if (malayalam) details.familyMl else details.familyEn).joinToString()
                            )
                        )
                    }
                    if (cell.isToday) append(", ").append(context.getString(R.string.calendar_today))
                }
                root.setOnClickListener { onDay(details) }
            }
        }

        object Diff : DiffUtil.ItemCallback<CalendarCellUi>() {
            override fun areItemsTheSame(a: CalendarCellUi, b: CalendarCellUi) = a.key == b.key

            override fun areContentsTheSame(a: CalendarCellUi, b: CalendarCellUi) = a == b
        }

        private companion object {
            const val MAX_DOTS = 4
        }
    }
}
