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
import io.github.anjalymsajeev.nakshatracalendar.databinding.FragmentGoogleAccountBinding
import io.github.anjalymsajeev.nakshatracalendar.databinding.ItemCalendarChoiceBinding
import io.github.anjalymsajeev.nakshatracalendar.domain.model.GoogleCalendarInfo
import kotlinx.coroutines.launch

/** Google account connection and calendar selection (screens 12 and 13). */
@AndroidEntryPoint
class GoogleAccountFragment : Fragment(R.layout.fragment_google_account) {
    private val viewModel: GoogleAccountViewModel by viewModels()
    private val consent = registerConsentLauncher { viewModel.onConsentResult(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentGoogleAccountBinding.bind(view)
        val adapter = CalendarAdapter { viewModel.choose(it) }
        binding.calendars.adapter = adapter
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.connect.setOnClickListener { viewModel.connect() }
        binding.disconnect.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_disconnect)
                .setMessage(R.string.account_disconnect_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.account_disconnect) { _, _ -> viewModel.disconnect() }
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.status.text = state.selected?.let { getString(R.string.account_connected_to, it.name) }
                            ?: getString(R.string.account_not_connected)
                        binding.connect.setText(if (state.selected == null) R.string.account_connect else R.string.account_change_calendar)
                        binding.disconnect.isVisible = state.selected != null
                        val calendars = state.calendars
                        binding.progress.isVisible = calendars is CalendarsState.Loading
                        binding.connect.isEnabled = calendars !is CalendarsState.Loading
                        binding.error.isVisible = calendars is CalendarsState.Error
                        if (calendars is CalendarsState.Error) binding.error.setText(calendars.message)
                        val list = (calendars as? CalendarsState.Loaded)?.calendars.orEmpty()
                        binding.calendarsTitle.isVisible = list.isNotEmpty()
                        adapter.selectedId = state.selected?.id
                        adapter.submitList(list)
                        adapter.notifyItemRangeChanged(0, list.size)
                    }
                }
                launch {
                    viewModel.eventFlow.collect { event ->
                        when (event) {
                            is GoogleAccountEvent.LaunchConsent -> consent.launch(event.request)
                            GoogleAccountEvent.CalendarChosen -> findNavController().navigateUp()
                            is GoogleAccountEvent.Message -> Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private class CalendarAdapter(private val onChoose: (GoogleCalendarInfo) -> Unit) :
        ListAdapter<GoogleCalendarInfo, CalendarAdapter.Holder>(Diff) {
        var selectedId: String? = null

        class Holder(val binding: ItemCalendarChoiceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemCalendarChoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val calendar = getItem(position)
            with(holder.binding) {
                name.text = calendar.name
                primary.isVisible = calendar.primary
                radio.isChecked = calendar.id == selectedId
                root.setOnClickListener { onChoose(calendar) }
            }
        }

        object Diff : DiffUtil.ItemCallback<GoogleCalendarInfo>() {
            override fun areItemsTheSame(a: GoogleCalendarInfo, b: GoogleCalendarInfo) = a.id == b.id

            override fun areContentsTheSame(a: GoogleCalendarInfo, b: GoogleCalendarInfo) = a == b
        }
    }
}
