package com.sixmonths.app.notifications

import com.sixmonths.app.data.prefs.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderSchedulerTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(date: LocalDate, time: String): ZonedDateTime =
        date.atTime(LocalTime.parse(time)).atZone(zone)

    @Test
    fun `same-day reminder is returned when the time is still ahead`() {
        val monday = LocalDate.of(2026, 10, 26)
        val settings = Settings(reminderDays = setOf(1), reminderTime = "19:00")

        val next = ReminderScheduler.nextOccurrence(settings, at(monday, "18:00"))

        assertEquals(at(monday, "19:00"), next)
    }

    @Test
    fun `reminder rolls to the next week after the time passed`() {
        val monday = LocalDate.of(2026, 10, 26)
        val settings = Settings(reminderDays = setOf(1), reminderTime = "19:00")

        val next = ReminderScheduler.nextOccurrence(settings, at(monday, "20:00"))

        assertEquals(at(monday.plusWeeks(1), "19:00"), next)
    }

    @Test
    fun `the nearest of several reminder days wins`() {
        // Wednesday 27 Oct 2026, 18:00; Monday and Friday are selected.
        val wednesday = LocalDate.of(2026, 10, 28)
        val settings = Settings(reminderDays = setOf(1, 5), reminderTime = "07:30")

        val next = ReminderScheduler.nextOccurrence(settings, at(wednesday, "18:00"))

        assertEquals(at(LocalDate.of(2026, 10, 30), "07:30"), next)
    }

    @Test
    fun `no reminder days means no work scheduled`() {
        val settings = Settings(reminderDays = emptySet())

        assertNull(ReminderScheduler.nextOccurrence(settings, at(LocalDate.of(2026, 10, 26), "18:00")))
    }

    @Test
    fun `the reminder window opens at the set time and repeats from there`() {
        val monday = LocalDate.of(2026, 10, 26)
        val settings = Settings(reminderDays = setOf(1), reminderTime = "19:00")

        assertFalse(ReminderScheduler.reminderWindowOpen(settings, at(monday, "18:59")))
        // Opened: every run from now on posts again until the workout is started.
        assertTrue(ReminderScheduler.reminderWindowOpen(settings, at(monday, "19:00")))
        assertTrue(ReminderScheduler.reminderWindowOpen(settings, at(monday, "21:47")))

        // A day that is not a reminder day never posts.
        assertFalse(ReminderScheduler.reminderWindowOpen(settings, at(monday.plusDays(1), "19:30")))
    }

    @Test
    fun `malformed reminder time falls back to 19 00 instead of crashing`() {
        val monday = LocalDate.of(2026, 10, 26)
        val settings = Settings(reminderDays = setOf(1), reminderTime = "not-a-time")

        val next = ReminderScheduler.nextOccurrence(settings, at(monday, "18:00"))

        assertEquals(at(monday, "19:00"), next)
    }
}
