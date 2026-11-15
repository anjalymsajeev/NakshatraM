package io.github.anjalymsajeev.nakshatracalendar.feature.sync

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.databinding.FragmentEventPreviewBinding
import io.github.anjalymsajeev.nakshatracalendar.databinding.ItemPreviewDateBinding
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncMode
import io.github.anjalymsajeev.nakshatracalendar.feature.common.isMalayalamUi
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Event preview (screen 14): review and choose dates before anything is sent to Google Calendar. */
@AndroidEntryPoint
class EventPreviewFragment : Fragment(R.layout.fragment_event_preview) {
    private val viewModel: EventPreviewViewModel by viewModels()
    private val consent = registerConsentLauncher { viewModel.onConsentResult(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentEventPreviewBinding.bind(view)
        val malayalam = requireContext().isMalayalamUi()
        val adapter = DateAdapter(malayalam) { viewModel.toggle(it) }
        binding.dates.adapter = adapter
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.selectAll.setOnClickListener { viewModel.selectAll() }
        binding.deselectAll.setOnClickListener { viewModel.deselectAll() }
        binding.changeCalendar.setOnClickListener { openAccount() }
        binding.createAll.setOnClickListener { viewModel.requestCreate(SyncMode.AUTOMATIC) }
        binding.createSelected.setOnClickListener { viewModel.requestCreate(SyncMode.MANUAL) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, adapter, it, malayalam) } }
                launch { viewModel.eventFlow.collect { handle(binding, it) } }
            }
        }
    }

    private fun render(binding: FragmentEventPreviewBinding, adapter: DateAdapter, state: PreviewUiState, malayalam: Boolean) {
        val content = state as? PreviewUiState.Content
        binding.progress.isVisible = state is PreviewUiState.Loading || content?.busy == true
        binding.message.isVisible = state is PreviewUiState.Error || content?.rows?.isEmpty() == true
        binding.message.text = when {
            state is PreviewUiState.Error -> getString(state.message)
            content?.rows?.isEmpty() == true -> getString(R.string.find_empty)
            else -> null
        }
        adapter.submitList(content?.rows.orEmpty())
        val enabled = content != null && !content.busy
        listOf(binding.createAll, binding.createSelected, binding.selectAll, binding.deselectAll).forEach { it.isEnabled = enabled }
        content ?: return
        binding.person.text =
            getString(R.string.preview_person, content.personName, if (malayalam) content.nakshatraMl else content.nakshatraEn)
        binding.calendar.text =
            content.calendarName?.let { getString(R.string.preview_calendar, it) } ?: getString(R.string.preview_no_calendar)
        binding.changeCalendar.setText(
            if (content.calendarName ==
                null
            ) {
                R.string.preview_choose_calendar_action
            } else {
                R.string.preview_change_calendar
            }
        )
        binding.selectedCount.text = getString(R.string.preview_selected_count, content.selectedCount, content.eligibleCount)
        binding.createAll.text = getString(R.string.preview_create_all, content.eligibleCount)
        binding.createSelected.text = getString(R.string.preview_create_selected, content.selectedCount)
    }

    private fun handle(binding: FragmentEventPreviewBinding, event: PreviewEvent) {
        when (event) {
            PreviewEvent.ChooseCalendar -> openAccount()
            is PreviewEvent.Confirm -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preview_confirm_title)
                .setMessage(resources.getQuantityString(R.plurals.preview_confirm_message, event.count, event.count, event.calendarName))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.preview_confirm_action) { _, _ -> viewModel.confirm(event.mode) }
                .show()
            is PreviewEvent.LaunchConsent -> consent.launch(event.request)
            is PreviewEvent.Started -> findNavController().navigate(EventPreviewFragmentDirections.actionPreviewToResult(event.syncId))
            is PreviewEvent.Message -> Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun openAccount() = findNavController().navigate(R.id.action_global_google_account)

    private class DateAdapter(private val malayalam: Boolean, private val onToggle: (LocalDate) -> Unit) :
        ListAdapter<PreviewRowUi, DateAdapter.Holder>(Diff) {
        class Holder(val binding: ItemPreviewDateBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemPreviewDateBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = getItem(position)
            val context = holder.itemView.context
            with(holder.binding) {
                check.isChecked = row.selected
                check.isEnabled = row.selectable
                root.isChecked = row.selected
                root.isEnabled = row.selectable
                gregorian.text = row.gregorian
                malayalam.text = if (this@DateAdapter.malayalam) row.malayalamMl else row.malayalamEn
                starTimes.text = context.getString(R.string.preview_star_times, row.starTimes)
                note.isVisible = row.alreadySynced || !row.selectable
                note.setText(if (row.selectable) R.string.preview_already_synced else R.string.find_no_observance_day)
                root.contentDescription = listOfNotNull(
                    row.gregorian,
                    if (this@DateAdapter.malayalam) row.malayalamMl else row.malayalamEn,
                    if (row.alreadySynced) context.getString(R.string.preview_already_synced) else null,
                    context.getString(if (row.selected) R.string.preview_selected else R.string.preview_not_selected)
                ).joinToString(", ")
                root.setOnClickListener { row.date?.let(onToggle) }
            }
        }

        object Diff : DiffUtil.ItemCallback<PreviewRowUi>() {
            override fun areItemsTheSame(a: PreviewRowUi, b: PreviewRowUi) = a.date == b.date && a.gregorian == b.gregorian

            override fun areContentsTheSame(a: PreviewRowUi, b: PreviewRowUi) = a == b
        }
    }
}
