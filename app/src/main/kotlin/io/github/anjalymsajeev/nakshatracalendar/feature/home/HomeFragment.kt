package io.github.anjalymsajeev.nakshatracalendar.feature.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

/** Renders [HomeUiState]; contains no business logic. */
@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {
    private val viewModel: HomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentHomeBinding.bind(view)
        binding.actionAddPerson.setOnClickListener { findNavController().navigate(R.id.familyFragment) }
        binding.actionViewCalendar.setOnClickListener { findNavController().navigate(R.id.calendarFragment) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(binding, it) }
            }
        }
    }

    private fun render(binding: FragmentHomeBinding, state: HomeUiState) {
        binding.progress.isVisible = state is HomeUiState.Loading
        binding.content.isVisible = state is HomeUiState.Content
        binding.errorMessage.isVisible = state is HomeUiState.Error
        when (state) {
            is HomeUiState.Loading -> Unit
            is HomeUiState.Error -> binding.errorMessage.setText(state.message)
            is HomeUiState.Content -> with(binding) {
                gregorianDate.text = state.gregorianDate
                malayalamDate.text = state.malayalamDateMalayalam
                malayalamDateEnglish.text = state.malayalamDateEnglish
                malayalamMonth.text = getString(
                    R.string.home_month_year,
                    state.malayalamMonthMalayalam,
                    state.malayalamMonthEnglish,
                    state.kollavarshamYear
                )
                nakshatraName.text = getString(R.string.home_nakshatra_names, state.nakshatraMalayalam, state.nakshatraEnglish)
                nakshatraUntil.text = getString(R.string.home_nakshatra_until, state.nakshatraUntil)
                familyCount.text = resources.getQuantityString(
                    R.plurals.home_family_count,
                    state.familyMemberCount,
                    state.familyMemberCount
                )
            }
        }
    }
}
