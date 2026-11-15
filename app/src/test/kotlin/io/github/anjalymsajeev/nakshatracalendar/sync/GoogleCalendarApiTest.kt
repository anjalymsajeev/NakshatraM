package io.github.anjalymsajeev.nakshatracalendar.sync

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ReminderSetting
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.CalendarEventFactory
import io.github.anjalymsajeev.nakshatracalendar.integration.googlecalendar.GoogleCalendarApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The REST client against a local MockWebServer standing in for www.googleapis.com. */
class GoogleCalendarApiTest {
    private val server = MockWebServer()
    private lateinit var api: GoogleCalendarApi
    private val draft = CalendarEventFactory.draft(
        SyncFixtures.occurrence(),
        "nkabc123",
        "Anvidh",
        10,
        "family@group",
        ReminderSetting.Before(60)
    )

    @Before fun setUp() {
        server.start()
        api = GoogleCalendarApi(OkHttpClient(), server.url("/calendar/v3/"), server.url("/revoke"), Dispatchers.Unconfined)
    }

    @After fun tearDown() = server.shutdown()

    @Test
    fun listsWritableCalendarsAcrossPagesPrimaryFirst() = runTest {
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"b","summary":"Work"}],"nextPageToken":"p2"}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"items":[{"id":"me@gmail.com","summary":"Me","primary":true},{"id":"a","summary":"Family","summaryOverride":"Kudumbam"}]}"""
            )
        )
        val calendars = (api.writableCalendars("token-1") as AppResult.Success).value
        assertEquals(listOf("Me", "Kudumbam", "Work"), calendars.map { it.name })
        val first = server.takeRequest()
        assertEquals("Bearer token-1", first.getHeader("Authorization"))
        assertEquals("writer", first.requestUrl?.queryParameter("minAccessRole"))
        assertEquals("p2", server.takeRequest().requestUrl?.queryParameter("pageToken"))
    }

    @Test
    fun insertsAnAllDayEventWithReminderColourAndDeterministicId() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        assertEquals(AppResult.Success(Unit), api.upsertEvent("t", draft))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/calendar/v3/calendars/family@group/events", request.requestUrl?.encodedPath?.replace("%40", "@"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("nkabc123", body["id"]?.jsonPrimitive?.content)
        assertEquals("2026-09-02", body["start"]!!.jsonObject["date"]?.jsonPrimitive?.content)
        assertEquals("2026-09-03", body["end"]!!.jsonObject["date"]?.jsonPrimitive?.content)
        assertEquals("10", body["colorId"]?.jsonPrimitive?.content)
        assertEquals(60, body["reminders"]!!.jsonObject["overrides"]!!.jsonArray.single().jsonObject["minutes"]?.jsonPrimitive?.int)
        assertEquals("nakshatram", body["extendedProperties"]!!.jsonObject["private"]!!.jsonObject["app"]?.jsonPrimitive?.content)
    }

    @Test
    fun existingIdIsUpdatedInsteadOfDuplicated() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody("""{"error":{"code":409,"message":"The requested identifier already exists."}}""")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        assertEquals(AppResult.Success(Unit), api.upsertEvent("t", draft))
        assertEquals("POST", server.takeRequest().method)
        val update = server.takeRequest()
        assertEquals("PUT", update.method)
        assertTrue(update.path!!.endsWith("/events/nkabc123"))
    }

    @Test
    fun httpErrorsAreMapped() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(AppResult.Failure(AppError.AuthRequired), api.upsertEvent("t", draft))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"errors":[{"reason":"rateLimitExceeded"}]}}"""))
        assertEquals(AppResult.Failure(AppError.RateLimited), api.upsertEvent("t", draft))
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(AppResult.Failure(AppError.RateLimited), api.upsertEvent("t", draft))
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(AppResult.Failure(AppError.ServerError(503)), api.upsertEvent("t", draft))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"errors":[{"reason":"forbidden"}]}}"""))
        assertEquals(403, ((api.upsertEvent("t", draft) as AppResult.Failure).error as AppError.ApiError).code)
    }

    @Test
    fun deleteTreatsMissingEventsAsDoneAndOfflineIsANetworkError() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(410))
        assertEquals(AppResult.Success(Unit), api.deleteEvent("t", "primary", "nkabc123"))
        assertEquals(AppResult.Success(Unit), api.deleteEvent("t", "primary", "nkabc123"))
        assertEquals("DELETE", server.takeRequest().method)

        server.shutdown()
        assertTrue((api.writableCalendars("t") as AppResult.Failure).error is AppError.Network)
    }
}
