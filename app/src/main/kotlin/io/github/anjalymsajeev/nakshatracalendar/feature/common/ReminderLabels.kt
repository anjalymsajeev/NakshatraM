package io.github.anjalymsajeev.nakshatracalendar.feature.common

import android.content.Context
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ReminderSetting

/** Localised reminder text, e.g. "1 day before" / "1 ദിവസം മുമ്പ്". */
fun ReminderSetting.label(context: Context): String = when (this) {
    ReminderSetting.None -> context.getString(R.string.reminder_none)
    is ReminderSetting.Before -> when {
        minutes == 0 -> context.getString(R.string.reminder_at_start)
        minutes % 1_440 == 0 -> context.resources.getQuantityString(R.plurals.reminder_days_before, minutes / 1_440, minutes / 1_440)
        minutes % 60 == 0 -> context.resources.getQuantityString(R.plurals.reminder_hours_before, minutes / 60, minutes / 60)
        else -> context.resources.getQuantityString(R.plurals.reminder_minutes_before, minutes, minutes)
    }
}
