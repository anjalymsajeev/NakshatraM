package io.github.anjalymsajeev.nakshatracalendar.feature.finddates

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.databinding.FragmentFindDatesBinding
import io.github.anjalymsajeev.nakshatracalendar.databinding.ItemOccurrenceBinding
import io.github.anjalymsajeev.nakshatracalendar.databinding.ItemOccurrenceHeaderBinding
import io.github.anjalymsajeev.nakshatracalendar.feature.common.isMalayalamUi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Find dates: members + date range in, every Nakshatra occurrence out. No calculation happens here. */
@AndroidEntryPoint
class FindDatesFragment : Fragment(R.layout.fragment_find_dates) {
    private val viewModel: FindDatesViewModel by viewModels()
    private val fieldFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFindDatesBinding.bind(view)
        val adapter = ResultsAdapter(requireContext().isMalayalamUi())
        binding.results.adapter = adapter
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.startInput.setOnClickListener { pickDate(isStart = true) }
        binding.endInput.setOnClickListener { pickDate(isStart = false) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderMembers(binding, state.members)
                    binding.startInput.setText(state.start.format(fieldFormatter))
                    binding.endInput.setText(state.end.format(fieldFormatter))
                    renderResults(binding, adapter, state.results)
                }
            }
        }
    }

    private fun renderMembers(binding: FragmentFindDatesBinding, members: List<MemberChipUi>) {
        val group = binding.memberChips
        // Rebuild only when the member list itself changes; otherwise just sync the checked state.
        if (group.childCount != members.size || members.indices.any { (group.getChildAt(it) as Chip).tag != members[it].id }) {
            group.removeAllViews()
            members.forEach { member ->
                group.addView(
                    Chip(requireContext()).apply {
                        tag = member.id
                        text = member.name
                        isCheckable = true
                        setOnClickListener { viewModel.toggleMember(member.id) }
                    }
                )
            }
        }
        members.forEachIndexed { index, member -> (group.getChildAt(index) as Chip).isChecked = member.selected }
        binding.memberScroll.isVisible = members.isNotEmpty()
    }

    private fun renderResults(binding: FragmentFindDatesBinding, adapter: ResultsAdapter, results: ResultsState) {
        binding.progress.isVisible = results is ResultsState.Loading
        binding.results.isVisible = results is ResultsState.Content
        adapter.submitList((results as? ResultsState.Content)?.rows.orEmpty())
        binding.summary.isVisible = results is ResultsState.Content
        if (results is ResultsState.Content) {
            binding.summary.text = resources.getQuantityString(R.plurals.find_result_count, results.total, results.total)
        }
        val message = when (results) {
            ResultsState.NoMembers -> getString(R.string.find_no_members)
            ResultsState.NoSelection -> getString(R.string.find_no_selection)
            ResultsState.Empty -> getString(R.string.find_empty)
            is ResultsState.Error -> getString(results.message)
            else -> null
        }
        binding.message.isVisible = message != null
        binding.message.text = message
        binding.endLayout.error = (results as? ResultsState.InvalidRange)?.let { getString(it.message, it.maxYears) }
    }

    private fun pickDate(isStart: Boolean) {
        val state = viewModel.uiState.value
        val supported = state.supportedRange
        val validators = listOfNotNull(
            DateValidatorPointForward.from(if (isStart) supported.start.toUtcMillis() else state.start.toUtcMillis()),
            DateValidatorPointBackward.before(supported.endInclusive.toUtcMillis())
        )
        val constraints = CalendarConstraints.Builder()
            .setStart(supported.start.toUtcMillis())
            .setEnd(supported.endInclusive.toUtcMillis())
            .setOpenAt((if (isStart) state.start else state.end).toUtcMillis())
            .setValidator(CompositeDateValidator.allOf(validators))
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (isStart) R.string.find_start_date else R.string.find_end_date)
            .setSelection((if (isStart) state.start else state.end).toUtcMillis())
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            if (isStart) viewModel.setStart(date) else viewModel.setEnd(date)
        }
        picker.show(childFragmentManager, if (isStart) "start" else "end")
    }

    private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private class ResultsAdapter(private val malayalam: Boolean) : ListAdapter<ResultRowUi, RecyclerView.ViewHolder>(Diff) {
        class HeaderHolder(val binding: ItemOccurrenceHeaderBinding) : RecyclerView.ViewHolder(binding.root)

        class OccurrenceHolder(val binding: ItemOccurrenceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun getItemViewType(position: Int) = if (getItem(position) is ResultRowUi.Header) HEADER else OCCURRENCE

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == HEADER) {
                HeaderHolder(ItemOccurrenceHeaderBinding.inflate(inflater, parent, false))
            } else {
                OccurrenceHolder(ItemOccurrenceBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val context = holder.itemView.context
            when (val row = getItem(position)) {
                is ResultRowUi.Header -> with((holder as HeaderHolder).binding) {
                    colorDot.backgroundTintList = ColorStateList.valueOf(row.colorArgb)
                    name.text = row.name
                    nakshatra.text = context.getString(R.string.home_nakshatra_names, row.nakshatraMalayalam, row.nakshatraEnglish)
                    count.text = context.resources.getQuantityString(R.plurals.find_result_count, row.count, row.count)
                }
                is ResultRowUi.Occurrence -> with((holder as OccurrenceHolder).binding) {
                    observanceDate.text = row.observanceDate ?: context.getString(R.string.find_no_observance_day)
                    malayalamDate.text = (if (malayalam) row.malayalamDateMl else row.malayalamDateEn).orEmpty()
                    malayalamDate.isVisible = !malayalamDate.text.isNullOrEmpty()
                    starTimes.text = context.getString(R.string.find_star_times, row.starStart, row.starEnd)
                    zone.isVisible = row.zoneLabel != null
                    zone.text = row.zoneLabel?.let { context.getString(R.string.find_times_in_zone, it) }
                    spansTwoDays.isVisible = row.spansTwoDays
                }
            }
        }

        object Diff : DiffUtil.ItemCallback<ResultRowUi>() {
            override fun areItemsTheSame(a: ResultRowUi, b: ResultRowUi) = a.key == b.key

            override fun areContentsTheSame(a: ResultRowUi, b: ResultRowUi) = a == b
        }

        private companion object {
            const val HEADER = 0
            const val OCCURRENCE = 1
        }
    }
}
