package io.github.anjalymsajeev.nakshatracalendar.integration.googlecalendar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.core.common.IoDispatcher
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.AuthorizationState
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.ConsentRequest
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.GoogleAuthRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** The consent screen to show; only the UI launches it. */
class PendingIntentConsent(val pendingIntent: PendingIntent) : ConsentRequest

/**
 * OAuth 2.0 through Google Identity Services' AuthorizationClient, with the two narrowest scopes the features
 * need. Google Play services holds the grant; the app never persists tokens.
 */
class GoogleAuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : GoogleAuthRepository {
    private val client get() = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder().setRequestedScopes(SCOPES.map(::Scope)).build()

    override suspend fun authorize(): AppResult<AuthorizationState> = guarded {
        val result = client.authorize(request).await()
        val pendingIntent = result.pendingIntent
        when {
            result.hasResolution() && pendingIntent != null -> AuthorizationState.NeedsConsent(PendingIntentConsent(pendingIntent))
            result.accessToken != null -> AuthorizationState.Authorized(result.accessToken!!)
            else -> throw ApiException(com.google.android.gms.common.api.Status.RESULT_INTERNAL_ERROR)
        }
    }

    override suspend fun accessToken(): AppResult<String> = when (val state = authorize()) {
        is AppResult.Failure -> state
        is AppResult.Success -> when (val value = state.value) {
            is AuthorizationState.Authorized -> AppResult.Success(value.accessToken)
            // Consent can only be given in the UI.
            is AuthorizationState.NeedsConsent -> AppResult.Failure(AppError.AuthRequired)
        }
    }

    override fun completeConsent(resultData: Any?): AppResult<String> = try {
        val result: AuthorizationResult = client.getAuthorizationResultFromIntent(resultData as? Intent)
        result.accessToken?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.AuthRequired)
    } catch (e: ApiException) {
        AppResult.Failure(e.toAppError())
    }

    override suspend fun invalidate(accessToken: String) {
        withContext(io) {
            try {
                GoogleAuthUtil.clearToken(context, accessToken)
            } catch (e: IOException) {
                // Nothing cached to clear; the next authorize() asks Play services again.
            } catch (e: com.google.android.gms.auth.GoogleAuthException) {
                // Same as above.
            }
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: ApiException) {
        AppResult.Failure(e.toAppError())
    }

    private fun ApiException.toAppError(): AppError =
        if (statusCode == CommonStatusCodes.NETWORK_ERROR) AppError.Network(this) else AppError.AuthRequired

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

    companion object {
        /** Create and edit events; list calendars (read-only). Nothing broader is requested. */
        val SCOPES = listOf(
            "https://www.googleapis.com/auth/calendar.events",
            "https://www.googleapis.com/auth/calendar.calendarlist.readonly"
        )
    }
}
