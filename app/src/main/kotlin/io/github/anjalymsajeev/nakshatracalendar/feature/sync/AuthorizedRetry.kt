package io.github.anjalymsajeev.nakshatracalendar.feature.sync

import androidx.annotation.StringRes
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.AuthorizationState
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.ConsentRequest
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.GoogleAuthRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.RetrySyncUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.common.messageRes
import javax.inject.Inject

/** One-off UI events of screens that can retry a run. */
sealed interface RetryEvent {
    data class LaunchConsent(val request: ConsentRequest) : RetryEvent

    data class Message(@StringRes val message: Int) : RetryEvent
}

/**
 * Retry with authorization: makes sure Google access is granted in the foreground (asking for consent if it
 * expired or was revoked) before the background worker runs again.
 */
class AuthorizedRetry @Inject constructor(private val auth: GoogleAuthRepository, private val retrySync: RetrySyncUseCase) {
    private var pending: Long? = null

    /** Returns an event for the UI, or null when the retry was started. */
    suspend fun retry(syncId: Long): RetryEvent? = when (val result = auth.authorize()) {
        is AppResult.Failure -> RetryEvent.Message(result.error.messageRes())
        is AppResult.Success -> when (val state = result.value) {
            is AuthorizationState.Authorized -> null.also { retrySync(syncId) }
            is AuthorizationState.NeedsConsent -> RetryEvent.LaunchConsent(state.request).also { pending = syncId }
        }
    }

    suspend fun onConsentResult(data: Any?): RetryEvent? {
        val syncId = pending ?: return null
        pending = null
        return when (val token = auth.completeConsent(data)) {
            is AppResult.Failure -> RetryEvent.Message(token.error.messageRes())
            is AppResult.Success -> null.also { retrySync(syncId) }
        }
    }
}
