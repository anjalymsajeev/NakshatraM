package io.github.anjalymsajeev.nakshatracalendar.feature.sync

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.ConsentRequest
import io.github.anjalymsajeev.nakshatracalendar.integration.googlecalendar.PendingIntentConsent

/** Registers the Google consent screen launcher; [onResult] receives the result Intent (null if cancelled). */
fun Fragment.registerConsentLauncher(onResult: (Any?) -> Unit): ActivityResultLauncher<IntentSenderRequest> =
    registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result -> onResult(result.data) }

fun ActivityResultLauncher<IntentSenderRequest>.launch(request: ConsentRequest) {
    val pendingIntent = (request as PendingIntentConsent).pendingIntent
    launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
}
