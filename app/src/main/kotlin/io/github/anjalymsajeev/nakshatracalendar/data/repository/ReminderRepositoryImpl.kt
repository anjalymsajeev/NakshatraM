package io.github.anjalymsajeev.nakshatracalendar.data.repository

import io.github.anjalymsajeev.nakshatracalendar.data.local.dao.SyncDao
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.ReminderConfigurationEntity
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ReminderSetting
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.ReminderRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Reminder configuration in Room (single row). No row yet means the default (1 day before). */
class ReminderRepositoryImpl @Inject constructor(private val dao: SyncDao) : ReminderRepository {
    override val reminder: Flow<ReminderSetting> = dao.observeReminder().map { row ->
        when {
            row == null -> ReminderSetting.DEFAULT
            row.kind == KIND_NONE -> ReminderSetting.None
            else -> row.minutes?.takeIf { it in 0..ReminderSetting.MAX_MINUTES }?.let(ReminderSetting::Before) ?: ReminderSetting.DEFAULT
        }
    }.distinctUntilChanged()

    override suspend fun set(reminder: ReminderSetting) {
        dao.upsertReminder(
            when (reminder) {
                ReminderSetting.None -> ReminderConfigurationEntity(kind = KIND_NONE, minutes = null)
                is ReminderSetting.Before -> ReminderConfigurationEntity(kind = KIND_BEFORE, minutes = reminder.minutes)
            }
        )
    }

    private companion object {
        const val KIND_NONE = "NONE"
        const val KIND_BEFORE = "BEFORE"
    }
}
