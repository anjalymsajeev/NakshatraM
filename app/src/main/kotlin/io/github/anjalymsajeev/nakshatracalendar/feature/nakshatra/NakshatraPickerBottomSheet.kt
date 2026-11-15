package io.github.anjalymsajeev.nakshatracalendar.feature.nakshatra

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.databinding.ItemNakshatraBinding
import io.github.anjalymsajeev.nakshatracalendar.databinding.SheetNakshatraPickerBinding
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraName
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.NakshatraEngine
import javax.inject.Inject

/**
 * Lets the user pick one of the 27 nakshatras (Malayalam and English, searchable). Returns the chosen
 * number through the Fragment Result API under [REQUEST_KEY] / [RESULT_NUMBER]. Reused by later sprints.
 */
@AndroidEntryPoint
class NakshatraPickerBottomSheet : BottomSheetDialogFragment() {
    @Inject lateinit var engine: NakshatraEngine

    private var binding: SheetNakshatraPickerBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetNakshatraPickerBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val all = engine.allNakshatras()
        val selected = arguments?.getInt(ARG_SELECTED) ?: 0
        val adapter = NakshatraAdapter(selected) { star ->
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_NUMBER to star.number))
            dismiss()
        }
        val b = requireNotNull(binding)
        b.list.adapter = adapter
        adapter.submitList(all)
        b.search.doAfterTextChanged { text ->
            val q = text?.toString()?.trim().orEmpty()
            adapter.submitList(
                if (q.isEmpty()) all else all.filter { it.nameEnglish.contains(q, ignoreCase = true) || it.nameMalayalam.contains(q) }
            )
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private class NakshatraAdapter(private val selected: Int, private val onClick: (NakshatraName) -> Unit) :
        ListAdapter<NakshatraName, NakshatraAdapter.Holder>(Diff) {
        class Holder(val binding: ItemNakshatraBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemNakshatraBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val star = getItem(position)
            with(holder.binding) {
                number.text = star.number.toString()
                nameMalayalam.text = star.nameMalayalam
                nameEnglish.text = star.nameEnglish
                root.isSelected = star.number == selected
                check.visibility = if (star.number == selected) View.VISIBLE else View.INVISIBLE
                root.contentDescription = root.context.getString(R.string.nakshatra_item_description, star.nameEnglish, star.nameMalayalam)
                root.setOnClickListener { onClick(star) }
            }
        }

        object Diff : DiffUtil.ItemCallback<NakshatraName>() {
            override fun areItemsTheSame(a: NakshatraName, b: NakshatraName) = a.number == b.number

            override fun areContentsTheSame(a: NakshatraName, b: NakshatraName) = a == b
        }
    }

    companion object {
        const val TAG = "NakshatraPicker"
        const val REQUEST_KEY = "nakshatra_picker"
        const val RESULT_NUMBER = "number"
        private const val ARG_SELECTED = "selected"

        fun newInstance(selected: Int?) = NakshatraPickerBottomSheet().apply { arguments = bundleOf(ARG_SELECTED to (selected ?: 0)) }
    }
}
