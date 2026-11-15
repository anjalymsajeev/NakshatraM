package io.github.anjalymsajeev.nakshatracalendar.integration.googlecalendar

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.core.common.IoDispatcher
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalendarEventDraft
import io.github.anjalymsajeev.nakshatracalendar.domain.model.GoogleCalendarInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.CalendarGateway
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Google Calendar API v3 over OkHttp (https://developers.google.com/calendar/api/v3/reference). Only the calls
 * the app needs: list writable calendars, insert/update/delete events, revoke. HTTP failures become [AppError]s:
 * 401 → AuthRequired, 403 rate/quota and 429 → RateLimited, 5xx → ServerError, other 4xx → ApiError.
 */
class GoogleCalendarApi @Inject constructor(
    private val client: OkHttpClient,
    @Named(BASE_URL) private val baseUrl: HttpUrl,
    @Named(REVOKE_URL) private val revokeUrl: HttpUrl,
    @IoDispatcher private val io: CoroutineDispatcher
) : CalendarGateway {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    override suspend fun writableCalendars(accessToken: String): AppResult<List<GoogleCalendarInfo>> = call {
        val calendars = mutableListOf<GoogleCalendarInfo>()
        var pageToken: String? = null
        do {
            val url = url("users", "me", "calendarList") {
                addQueryParameter("minAccessRole", "writer")
                pageToken?.let { addQueryParameter("pageToken", it) }
            }
            val page = execute(get(url, accessToken)) { json.decodeFromString<CalendarListDto>(it) }
            calendars += page.items.map { GoogleCalendarInfo(it.id, it.summaryOverride ?: it.summary ?: it.id, it.primary == true) }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        calendars.sortedWith(compareByDescending<GoogleCalendarInfo> { it.primary }.thenBy { it.name.lowercase() })
    }

    override suspend fun upsertEvent(accessToken: String, draft: CalendarEventDraft): AppResult<Unit> = call {
        val body = json.encodeToString(EventDto.serializer(), draft.toDto()).toRequestBody(JSON)
        val insert = Request.Builder().url(url("calendars", draft.calendarId, "events")).auth(accessToken).post(body).build()
        client.newCall(insert).execute().use { response ->
            when (response.code) {
                in 200..299 -> return@call
                // The id exists (created before, possibly deleted in Google since): update it, which also restores it.
                409 -> Unit
                else -> throw HttpFailure(response.toError())
            }
        }
        val update = Request.Builder().url(url("calendars", draft.calendarId, "events", draft.eventId)).auth(accessToken).put(body).build()
        execute(update) { }
    }

    override suspend fun deleteEvent(accessToken: String, calendarId: String, eventId: String): AppResult<Unit> = call {
        val request = Request.Builder().url(url("calendars", calendarId, "events", eventId)).auth(accessToken).delete().build()
        client.newCall(request).execute().use { response ->
            // 404/410: already gone, which is the goal.
            if (response.code !in 200..299 && response.code != 404 && response.code != 410) throw HttpFailure(response.toError())
        }
    }

    override suspend fun revoke(accessToken: String): AppResult<Unit> = call {
        val request = Request.Builder()
            .url(revokeUrl.newBuilder().addQueryParameter("token", accessToken).build())
            .post(ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(request).execute().use { response ->
            // 400 means the token is already invalid: access is gone either way.
            if (response.code !in 200..299 && response.code != 400) throw HttpFailure(response.toError())
        }
    }

    private fun url(vararg segments: String, query: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        baseUrl.newBuilder().apply { segments.forEach { addPathSegment(it) } }.apply(query).build()

    private fun get(url: HttpUrl, token: String) = Request.Builder().url(url).auth(token).get().build()

    private fun Request.Builder.auth(token: String) = header("Authorization", "Bearer $token")

    private fun <T> execute(request: Request, parse: (String) -> T): T = client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw HttpFailure(response.toError())
        parse(response.body?.string().orEmpty())
    }

    private fun Response.toError(): AppError {
        val text = body?.string().orEmpty()
        return when {
            code == 401 -> AppError.AuthRequired
            code == 429 -> AppError.RateLimited
            code == 403 && RATE_REASONS.any { text.contains(it) } -> AppError.RateLimited
            code >= 500 -> AppError.ServerError(code)
            else -> AppError.ApiError(code, text.take(MAX_DETAIL))
        }
    }

    private suspend fun <T> call(block: suspend () -> T): AppResult<T> = withContext(io) {
        try {
            AppResult.Success(block())
        } catch (e: HttpFailure) {
            AppResult.Failure(e.error)
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network(e))
        } catch (e: SerializationException) {
            AppResult.Failure(AppError.ApiError(0, "Unexpected response: ${e.message}"))
        }
    }

    private class HttpFailure(val error: AppError) : Exception()

    private fun CalendarEventDraft.toDto() = EventDto(
        id = eventId,
        summary = title,
        description = description,
        location = location,
        start = EventDateDto(date.toString()),
        end = EventDateDto(date.plusDays(1).toString()),
        colorId = colorId.toString(),
        reminders = RemindersDto(useDefault = false, overrides = listOfNotNull(reminderMinutes?.let { ReminderDto("popup", it) })),
        extendedProperties = ExtendedPropertiesDto(privateProperties)
    )

    companion object {
        const val BASE_URL = "google_calendar_base_url"
        const val REVOKE_URL = "google_revoke_url"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val RATE_REASONS = listOf("rateLimitExceeded", "userRateLimitExceeded", "quotaExceeded", "dailyLimitExceeded")
        private const val MAX_DETAIL = 300
        val DEFAULT_BASE_URL = "https://www.googleapis.com/calendar/v3/".toHttpUrl()
        val DEFAULT_REVOKE_URL = "https://oauth2.googleapis.com/revoke".toHttpUrl()
    }
}

@Serializable
internal data class CalendarListDto(val items: List<CalendarListEntryDto> = emptyList(), val nextPageToken: String? = null)

@Serializable
internal data class CalendarListEntryDto(
    val id: String,
    val summary: String? = null,
    val summaryOverride: String? = null,
    val primary: Boolean? = null
)

@Serializable
internal data class EventDto(
    val id: String,
    val summary: String,
    val description: String,
    val location: String,
    val start: EventDateDto,
    val end: EventDateDto,
    val colorId: String,
    val reminders: RemindersDto,
    val extendedProperties: ExtendedPropertiesDto,
    /** "confirmed" also restores an event the user deleted in Google Calendar. */
    val status: String = "confirmed",
    /** All-day birth-star days do not block time ("free"). */
    val transparency: String = "transparent"
)

@Serializable
internal data class EventDateDto(val date: String)

@Serializable
internal data class RemindersDto(val useDefault: Boolean, val overrides: List<ReminderDto>)

@Serializable
internal data class ReminderDto(val method: String, val minutes: Int)

@Serializable
internal data class ExtendedPropertiesDto(@SerialName("private") val privateProperties: Map<String, String>)
