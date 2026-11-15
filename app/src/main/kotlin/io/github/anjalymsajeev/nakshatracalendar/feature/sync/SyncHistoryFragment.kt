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
import io.github.anjalymsajeev.nakshatracalendar.databinding.FragmentSyncHistoryBinding
import io.github.anjalymsajeev.nakshatracalendar.databinding.ItemSyncRunBinding
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncRun
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncStatus
import io.github.anjalymsajeev.nakshatracalendar.feature.common.isMalayalamUi
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** Synchronization history (screen 16): every run with counts, status, retry and removal. */
@AndroidEntryPoint
class SyncHistoryFragment : Fragment(R.layout.fragment_sync_history) {
    private val consent = registerConsentLauncher { viewModel.onConsentResult(it) }
    private val viewModel: SyncHistoryViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSyncHistoryBinding.bind(view)
        val adapter = RunAdapter(
            malayalam = requireContext().isMalayalamUi(),
            onOpen = { findNavController().navigate(SyncHistoryFragmentDirections.actionHistoryToResult(it.id)) },
            onRetry = { run -> viewModel.retry(run.id) },
            onRemove = { run ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.history_remove_title)
                    .setMessage(getString(R.string.history_remove_message, run.succeeded, run.calendarName))
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.history_remove) { _, _ -> viewModel.remove(run.id) }
                    .show()
            }
        )
        binding.runs.adapter = adapter
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.eventFlow.collect { event ->
                        when (event) {
                            is RetryEvent.LaunchConsent -> consent.launch(event.request)
                            is RetryEvent.Message -> Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.runs == null
                    binding.empty.isVisible = state.runs?.isEmpty() == true
                    adapter.submitList(state.runs.orEmpty())
                }
            }
        }
    }

    private class RunAdapter(
        private val malayalam: Boolean,
        private val onOpen: (SyncRun) -> Unit,
        private val onRetry: (SyncRun) -> Unit,
        private val onRemove: (SyncRun) -> Unit
    ) : ListAdapter<SyncRun, RunAdapter.Holder>(Diff) {
        private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        private val stampFormatter = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT
        ).withZone(ZoneId.systemDefault())

        class Holder(val binding: ItemSyncRunBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemSyncRunBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val run = getItem(position)
            val context = holder.itemView.context
            with(holder.binding) {
                title.text = context.getString(
                    R.string.preview_person,
                    run.personName,
                    if (malayalam) run.nakshatra.nameMalayalam else run.nakshatra.nameEnglish
                )
                status.setText(run.status.labelRes())
                range.text =
                    context.getString(
                        R.string.history_range,
                        run.rangeStart.format(dateFormatter),
                        run.rangeEnd.format(dateFormatter),
                        run.calendarName
                    )
                counts.text = context.getString(R.string.history_counts, run.total, run.succeeded, run.failed)
                lastSync.text = context.getString(R.string.history_last_sync, stampFormatter.format(run.updatedAt))
                retry.setText(if (run.status == SyncStatus.NEEDS_AUTH) R.string.result_reconnect else R.string.history_retry)
                retry.isVisible =
                    !run.status.inProgress &&
                    (run.failed > 0 || run.status == SyncStatus.NEEDS_AUTH || run.status == SyncStatus.FAILED)
                remove.isVisible = !run.status.inProgress && run.status != SyncStatus.REMOVED && run.succeeded > 0
                retry.setOnClickListener { onRetry(run) }
                remove.setOnClickListener { onRemove(run) }
                root.setOnClickListener { onOpen(run) }
            }
        }

        object Diff : DiffUtil.ItemCallback<SyncRun>() {
            override fun areItemsTheSame(a: SyncRun, b: SyncRun) = a.id == b.id

            override fun areContentsTheSame(a: SyncRun, b: SyncRun) = a == b
        }
    }
}
