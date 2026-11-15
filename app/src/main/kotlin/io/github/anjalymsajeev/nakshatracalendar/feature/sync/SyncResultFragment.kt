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
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.databinding.FragmentSyncResultBinding
import io.github.anjalymsajeev.nakshatracalendar.databinding.ItemSyncEventBinding
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventSyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncedEvent
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** Synchronization results (screen 15): updates live while the background sync runs. */
@AndroidEntryPoint
class SyncResultFragment : Fragment(R.layout.fragment_sync_result) {
    private val consent = registerConsentLauncher { viewModel.onConsentResult(it) }
    private val viewModel: SyncResultViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSyncResultBinding.bind(view)
        val adapter = EventAdapter()
        binding.events.adapter = adapter
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        // Done returns to where the flow started (Find dates).
        binding.done.setOnClickListener {
            if (!findNavController().popBackStack(R.id.findDatesFragment, false)) findNavController().navigateUp()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.eventFlow.collect { handleRetryEvent(binding.root, it) } }
                viewModel.uiState.collect { state ->
                    val run = state.run ?: return@collect
                    binding.status.setText(run.status.labelRes())
                    binding.summary.text = getString(R.string.result_summary, run.personName, run.nakshatra.nameEnglish, run.calendarName)
                    binding.counts.text = getString(R.string.result_counts, run.succeeded, run.failed, run.total)
                    binding.progress.isVisible = run.status.inProgress
                    binding.progress.isIndeterminate = run.succeeded + run.failed == 0
                    binding.progress.max = run.total.coerceAtLeast(1)
                    binding.progress.setProgressCompat(run.succeeded + run.failed, true)
                    adapter.submitList(state.events)
                    when {
                        run.status == SyncStatus.NEEDS_AUTH -> binding.secondaryAction.apply {
                            isVisible = true
                            setText(R.string.result_reconnect)
                            setOnClickListener { viewModel.retry() }
                        }
                        run.failed > 0 && !run.status.inProgress -> binding.secondaryAction.apply {
                            isVisible = true
                            setText(R.string.result_retry)
                            setOnClickListener { viewModel.retry() }
                        }
                        else -> binding.secondaryAction.apply {
                            isVisible = true
                            setText(R.string.history_title)
                            setOnClickListener { findNavController().navigate(R.id.action_global_sync_history) }
                        }
                    }
                }
            }
        }
    }

    private fun handleRetryEvent(root: View, event: RetryEvent) = when (event) {
        is RetryEvent.LaunchConsent -> consent.launch(event.request)
        is RetryEvent.Message -> Snackbar.make(root, event.message, Snackbar.LENGTH_LONG).show()
    }

    private class EventAdapter : ListAdapter<SyncedEvent, EventAdapter.Holder>(Diff) {
        private val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

        class Holder(val binding: ItemSyncEventBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemSyncEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val event = getItem(position)
            with(holder.binding) {
                date.text = event.occurrence.observanceDate.format(formatter)
                state.setText(event.status.labelRes())
                val color = if (event.status ==
                    EventSyncStatus.FAILED
                ) {
                    com.google.android.material.R.attr.colorError
                } else {
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                }
                state.setTextColor(com.google.android.material.color.MaterialColors.getColor(state, color))
            }
        }

        object Diff : DiffUtil.ItemCallback<SyncedEvent>() {
            override fun areItemsTheSame(a: SyncedEvent, b: SyncedEvent) = a.id == b.id

            override fun areContentsTheSame(a: SyncedEvent, b: SyncedEvent) = a == b
        }
    }
}
