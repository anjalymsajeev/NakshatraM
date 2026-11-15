package io.github.anjalymsajeev.nakshatracalendar.sync

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventColor
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventSyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ReminderSetting
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.CalendarSyncEngine
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.SyncOutcome
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSyncEngineTest {
    private val gateway = FakeGateway()
    private val auth = FakeAuth()
    private val repo = FakeSyncRepository()
    private val reminders = FakeReminders()
    private var member = Person(4, "Anvidh", 13, calculationLocation = CalculationLocation.Thiruvananthapuram, eventColor = EventColor.SAGE)
    private val persons = mockk<PersonRepository> { coEvery { get(4) } answers { AppResult.Success(member) } }
    private val engine = CalendarSyncEngine(gateway, auth, repo, persons, reminders)

    private fun threeDates() = repo.addRun(1, (1L..3L).map { SyncFixtures.occurrence(id = it, date = LocalDate.of(2026, 9, it.toInt())) })

    @Test
    fun createsEveryEventWithTheMembersCurrentNameColourAndReminder() = runTest {
        threeDates()
        assertEquals(SyncOutcome.Finished(SyncStatus.SUCCEEDED), engine.run(1))
        assertEquals(listOf("nk1", "nk2", "nk3"), gateway.upserts.map { it.eventId })
        assertTrue(
            gateway.upserts.all {
                it.colorId == EventColor.SAGE.colorId &&
                    it.reminderMinutes == 1_440 &&
                    it.title == "Anvidh - Atham Nakshatra"
            }
        )
        assertEquals(3, repo.runs.getValue(1).succeeded)
    }

    @Test
    fun reSyncingUnchangedEventsSendsNothingAndChangesAreUpdated() = runTest {
        threeDates()
        engine.run(1)
        gateway.upserts.clear()

        repo.resync()
        assertEquals(SyncOutcome.Finished(SyncStatus.SUCCEEDED), engine.run(1))
        assertTrue("unchanged events are skipped", gateway.upserts.isEmpty())

        reminders.set(ReminderSetting.Before(30))
        member = member.copy(name = "Anvidh K")
        repo.resync()
        engine.run(1)
        // Same deterministic ids: the events are updated in place, never duplicated.
        assertEquals(listOf("nk1", "nk2", "nk3"), gateway.upserts.map { it.eventId })
        assertTrue(gateway.upserts.all { it.reminderMinutes == 30 && it.title.startsWith("Anvidh K") })
    }

    @Test
    fun temporaryFailuresRetryLaterAndOnlyFailedEventsAreResent() = runTest {
        threeDates()
        gateway.failures["nk2"] = ArrayDeque(listOf(AppError.Network(IOException("offline"))))
        assertEquals(SyncOutcome.RetryLater, engine.run(1))
        assertEquals(SyncStatus.QUEUED, repo.runs.getValue(1).status)

        gateway.upserts.clear()
        assertEquals(SyncOutcome.Finished(SyncStatus.SUCCEEDED), engine.run(1))
        assertEquals(listOf("nk2"), gateway.upserts.map { it.eventId })
    }

    @Test
    fun permanentFailureGivesPartialAndTheLastAttemptStopsRetrying() = runTest {
        threeDates()
        gateway.failures["nk1"] = ArrayDeque(listOf(AppError.ApiError(400, "bad")))
        gateway.failures["nk3"] = ArrayDeque(listOf(AppError.RateLimited))
        assertEquals(SyncOutcome.Finished(SyncStatus.PARTIAL), engine.run(1, finalAttempt = true))
        val run = repo.runs.getValue(1)
        assertEquals(1, run.succeeded)
        assertEquals(2, run.failed)
        assertEquals("HTTP 400: bad", repo.events.getValue(1).lastError)

        repo.resetFailed(1)
        assertEquals(SyncOutcome.Finished(SyncStatus.SUCCEEDED), engine.run(1))
    }

    @Test
    fun anExpiredTokenIsRefreshedOnceAndRevokedAccessNeedsReconnect() = runTest {
        threeDates()
        gateway.failures["nk1"] = ArrayDeque(listOf(AppError.AuthRequired))
        auth.nextToken = "token-2"
        assertEquals(SyncOutcome.Finished(SyncStatus.SUCCEEDED), engine.run(1))
        assertEquals(listOf("token-1"), auth.invalidated)
        assertEquals("token-2", gateway.tokensSeen.last())

        repo.addRun(2, listOf(SyncFixtures.occurrence(id = 9)))
        auth.token = AppResult.Failure(AppError.AuthRequired)
        assertEquals(SyncOutcome.Finished(SyncStatus.NEEDS_AUTH), engine.run(2))
        assertEquals(EventSyncStatus.PENDING, repo.events.getValue(9).status)
    }

    @Test
    fun removeDeletesTheRunsEvents() = runTest {
        threeDates()
        engine.run(1)
        assertEquals(SyncOutcome.Finished(SyncStatus.REMOVED), engine.remove(1))
        assertEquals(listOf("nk1", "nk2", "nk3"), gateway.deletes)
        assertTrue(repo.events.values.all { it.status == EventSyncStatus.DELETED })
    }
}
