package io.github.anjalymsajeev.nakshatracalendar.feature.sync

import androidx.annotation.StringRes
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventSyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncStatus

@StringRes
fun SyncStatus.labelRes(): Int = when (this) {
    SyncStatus.QUEUED -> R.string.sync_status_queued
    SyncStatus.RUNNING -> R.string.sync_status_running
    SyncStatus.SUCCEEDED -> R.string.sync_status_succeeded
    SyncStatus.PARTIAL -> R.string.sync_status_partial
    SyncStatus.FAILED -> R.string.sync_status_failed
    SyncStatus.NEEDS_AUTH -> R.string.sync_status_needs_auth
    SyncStatus.REMOVED -> R.string.sync_status_removed
}

@StringRes
fun EventSyncStatus.labelRes(): Int = when (this) {
    EventSyncStatus.PENDING -> R.string.event_status_pending
    EventSyncStatus.SYNCED -> R.string.event_status_synced
    EventSyncStatus.FAILED -> R.string.event_status_failed
    EventSyncStatus.DELETED -> R.string.event_status_deleted
}

val SyncStatus.inProgress: Boolean get() = this == SyncStatus.QUEUED || this == SyncStatus.RUNNING
