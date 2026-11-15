package io.github.anjalymsajeev.nakshatracalendar.feature.person

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.databinding.ItemPlaceBinding
import io.github.anjalymsajeev.nakshatracalendar.databinding.SheetPlaceSearchBinding
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.PlaceCandidate
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Online place search (platform geocoder) with time-zone choice and a manual-entry fallback.
 * Returns the place through the Fragment Result API under the request key passed to [newInstance].
 */
@AndroidEntryPoint
class PlaceSearchBottomSheet : BottomSheetDialogFragment() {
    private val viewModel: PlaceSearchViewModel by viewModels()
    private var binding: SheetPlaceSearchBinding? = null
    private val requestKey: String get() = requireArguments().getString(ARG_REQUEST_KEY).orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetPlaceSearchBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = requireNotNull(binding)
        val adapter = PlaceAdapter { place -> choose(place) }
        b.results.adapter = adapter
        b.query.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        b.manualToggle.setOnClickListener { b.manualForm.isVisible = !b.manualForm.isVisible }
        b.manualZone.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, ZoneId.getAvailableZoneIds().sorted()))
        b.manualSave.setOnClickListener { saveManual(b) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    b.progress.isVisible = state is PlaceSearchUiState.Loading
                    b.results.isVisible = state is PlaceSearchUiState.Results
                    b.message.isVisible = state is PlaceSearchUiState.NoResults || state is PlaceSearchUiState.Error
                    when (state) {
                        is PlaceSearchUiState.Results -> adapter.submitList(state.places)
                        is PlaceSearchUiState.NoResults -> b.message.setText(R.string.place_no_results)
                        is PlaceSearchUiState.Error -> {
                            b.message.setText(state.message)
                            if (state.offerManualEntry) b.manualForm.isVisible = true
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun choose(place: PlaceCandidate) {
        when (place.zones.size) {
            0 -> {
                // No zone known for this country: let the user complete it manually.
                val b = requireNotNull(binding)
                b.manualForm.isVisible = true
                b.manualName.setText(place.label)
                b.manualLatitude.setText(place.latitude.toString())
                b.manualLongitude.setText(place.longitude.toString())
            }
            1 -> deliver(CalculationLocation(place.label, place.latitude, place.longitude, place.zones.single()))
            else -> {
                val ids = place.zones.map { it.id }.toTypedArray()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.place_choose_zone)
                    .setItems(ids) { _, which ->
                        deliver(CalculationLocation(place.label, place.latitude, place.longitude, place.zones[which]))
                    }
                    .show()
            }
        }
    }

    private fun saveManual(b: SheetPlaceSearchBinding) {
        val name = b.manualName.text?.toString()?.trim().orEmpty()
        val lat = b.manualLatitude.text?.toString()?.toDoubleOrNull()
        val lon = b.manualLongitude.text?.toString()?.toDoubleOrNull()
        val zone = b.manualZone.text?.toString()?.trim()?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        b.manualNameLayout.error = if (name.isEmpty()) getString(R.string.error_required) else null
        b.manualLatitudeLayout.error = if (lat == null || lat !in -90.0..90.0) getString(R.string.place_invalid_latitude) else null
        b.manualLongitudeLayout.error = if (lon == null || lon !in -180.0..180.0) getString(R.string.place_invalid_longitude) else null
        b.manualZoneLayout.error = if (zone == null) getString(R.string.place_invalid_zone) else null
        if (name.isNotEmpty() && lat != null && lat in -90.0..90.0 && lon != null && lon in -180.0..180.0 && zone != null) {
            deliver(CalculationLocation(name, lat, lon, zone))
        }
    }

    private fun deliver(location: CalculationLocation) {
        setFragmentResult(requestKey, location.toBundle())
        dismiss()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private class PlaceAdapter(private val onClick: (PlaceCandidate) -> Unit) : ListAdapter<PlaceCandidate, PlaceAdapter.Holder>(Diff) {
        class Holder(val binding: ItemPlaceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemPlaceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val place = getItem(position)
            with(holder.binding) {
                label.text = place.label
                detail.text = root.context.getString(
                    R.string.place_coordinates,
                    place.latitude,
                    place.longitude,
                    place.zones.singleOrNull()?.id ?: root.context.getString(R.string.place_zone_choose_later)
                )
                root.setOnClickListener { onClick(place) }
            }
        }

        object Diff : DiffUtil.ItemCallback<PlaceCandidate>() {
            override fun areItemsTheSame(a: PlaceCandidate, b: PlaceCandidate) = a.label == b.label

            override fun areContentsTheSame(a: PlaceCandidate, b: PlaceCandidate) = a == b
        }
    }

    companion object {
        const val TAG = "PlaceSearch"
        private const val ARG_REQUEST_KEY = "request_key"

        fun newInstance(requestKey: String) = PlaceSearchBottomSheet().apply { arguments = bundleOf(ARG_REQUEST_KEY to requestKey) }

        fun CalculationLocation.toBundle() = bundleOf("name" to name, "lat" to latitude, "lon" to longitude, "zone" to zone.id)

        fun Bundle.toLocation() =
            CalculationLocation(getString("name").orEmpty(), getDouble("lat"), getDouble("lon"), ZoneId.of(getString("zone")))
    }
}
