package io.github.anjalymsajeev.nakshatracalendar.feature.person

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
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
import io.github.anjalymsajeev.nakshatracalendar.databinding.FragmentFamilyBinding
import io.github.anjalymsajeev.nakshatracalendar.databinding.ItemFamilyMemberBinding
import io.github.anjalymsajeev.nakshatracalendar.feature.common.isMalayalamUi
import kotlinx.coroutines.launch

/** Family members as gold-trimmed cards (design: pirannal_scheduler). Renders state only. */
@AndroidEntryPoint
class FamilyFragment : Fragment(R.layout.fragment_family) {
    private val viewModel: FamilyViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFamilyBinding.bind(view)
        val malayalam = requireContext().isMalayalamUi()
        val adapter = FamilyAdapter(
            malayalam = malayalam,
            onOpen = { id -> openEditor(id) },
            onMenu = { anchor, item -> showMenu(anchor, item) }
        )
        binding.list.adapter = adapter
        binding.addPerson.setOnClickListener { openEditor(NEW_PERSON) }
        binding.emptyAddPerson.setOnClickListener { openEditor(NEW_PERSON) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progress.isVisible = state is FamilyUiState.Loading
                        binding.emptyState.isVisible = state is FamilyUiState.Empty
                        binding.list.isVisible = state is FamilyUiState.Content
                        binding.errorMessage.isVisible = state is FamilyUiState.Error
                        binding.addPerson.isVisible = state is FamilyUiState.Content
                        when (state) {
                            is FamilyUiState.Content -> adapter.submitList(state.items)
                            is FamilyUiState.Error -> binding.errorMessage.setText(state.message)
                            else -> Unit
                        }
                    }
                }
                launch {
                    viewModel.eventFlow.collect { event ->
                        when (event) {
                            is FamilyEvent.Deleted ->
                                Snackbar.make(binding.root, getString(R.string.family_deleted, event.person.name), Snackbar.LENGTH_LONG)
                                    .setAction(R.string.action_undo) { viewModel.undoDelete(event.person) }
                                    .show()
                            is FamilyEvent.Message -> Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun openEditor(id: Long) {
        findNavController().navigate(FamilyFragmentDirections.actionFamilyToPersonEdit(id))
    }

    private fun showMenu(anchor: View, item: FamilyItemUi) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.family_member_actions, menu)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> openEditor(item.id)
                    R.id.action_delete -> confirmDelete(item)
                }
                true
            }
        }.show()
    }

    private fun confirmDelete(item: FamilyItemUi) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.family_delete_title)
            .setMessage(getString(R.string.family_delete_message, item.name))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(item.id) }
            .show()
    }

    private class FamilyAdapter(
        private val malayalam: Boolean,
        private val onOpen: (Long) -> Unit,
        private val onMenu: (View, FamilyItemUi) -> Unit
    ) : ListAdapter<FamilyItemUi, FamilyAdapter.Holder>(Diff) {
        class Holder(val binding: ItemFamilyMemberBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemFamilyMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = getItem(position)
            val context = holder.itemView.context
            with(holder.binding) {
                colorDot.background.mutate().setTint(item.colorArgb)
                name.text = item.name
                nakshatra.text = context.getString(R.string.home_nakshatra_names, item.nakshatraMalayalam, item.nakshatraEnglish)
                val nextMalayalam = if (malayalam) item.nextMalayalamMl else item.nextMalayalamEn
                nextPirannal.text = if (item.nextGregorian != null && nextMalayalam != null) {
                    context.getString(R.string.family_next_pirannal, item.nextGregorian, nextMalayalam)
                } else {
                    context.getString(R.string.family_next_unknown)
                }
                root.setOnClickListener { onOpen(item.id) }
                more.contentDescription = context.getString(R.string.family_more_actions, item.name)
                more.setOnClickListener { onMenu(it, item) }
            }
        }

        object Diff : DiffUtil.ItemCallback<FamilyItemUi>() {
            override fun areItemsTheSame(a: FamilyItemUi, b: FamilyItemUi) = a.id == b.id

            override fun areContentsTheSame(a: FamilyItemUi, b: FamilyItemUi) = a == b
        }
    }

    private companion object {
        const val NEW_PERSON = -1L
    }
}
