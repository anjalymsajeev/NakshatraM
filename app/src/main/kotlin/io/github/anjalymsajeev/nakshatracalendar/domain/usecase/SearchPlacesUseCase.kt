package io.github.anjalymsajeev.nakshatracalendar.domain.usecase

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.PlaceCandidate
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PlaceSearchRepository
import javax.inject.Inject

class SearchPlacesUseCase @Inject constructor(private val repository: PlaceSearchRepository) {
    /** Searches for [query]; queries shorter than [MIN_QUERY_LENGTH] return no results without a network call. */
    suspend operator fun invoke(query: String): AppResult<List<PlaceCandidate>> {
        val q = query.trim()
        if (q.length < MIN_QUERY_LENGTH) return AppResult.Success(emptyList())
        return repository.search(q)
    }

    companion object {
        const val MIN_QUERY_LENGTH = 3
    }
}
