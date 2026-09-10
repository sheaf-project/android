package systems.lupine.sheaf.ui.components

import java.time.LocalDate
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The picked day must be the recorded day, wherever the phone is.
 *
 * These assertions are about the device's default zone, so they set it: the
 * whole bug was code that consulted that zone for a value which is defined in
 * UTC, and it only misbehaves at certain offsets. A test that ran in one zone
 * would pass on the broken implementation half the time.
 */
class DatePickerDatesTest {

    private val original: TimeZone = TimeZone.getDefault()

    @AfterTest fun restore() { TimeZone.setDefault(original) }

    private fun inZone(id: String, body: () -> Unit) {
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        body()
    }

    /** UTC midnight of a day, which is what the picker hands back. */
    private fun utcMidnight(date: LocalDate) =
        date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun `a tapped day reads back as that day west of UTC`() {
        // The reported bug: pick the 8th, get the 7th. UTC midnight on the 8th
        // is the 7th at 20:00 in New York, so reading it in the device's zone
        // moved every date back a day for the whole of the Americas.
        inZone("America/New_York") {
            assertEquals(
                LocalDate.of(2026, 9, 8),
                datePickerDate(utcMidnight(LocalDate.of(2026, 9, 8))),
            )
        }
    }

    @Test fun `and east of it`() {
        inZone("Pacific/Auckland") {
            assertEquals(
                LocalDate.of(2026, 9, 8),
                datePickerDate(utcMidnight(LocalDate.of(2026, 9, 8))),
            )
        }
    }

    @Test fun `the picker opens on the date it was given`() {
        // The same mistake the other way round: local midnight east of UTC is
        // the previous day in UTC, so the picker opened on the wrong day.
        listOf("Pacific/Auckland", "Asia/Kolkata", "UTC", "America/New_York", "Pacific/Honolulu")
            .forEach { zone ->
                inZone(zone) {
                    val date = LocalDate.of(2026, 9, 8)
                    assertEquals(date, datePickerDate(datePickerMillis(date)), zone)
                }
            }
    }

    @Test fun `a date survives the round trip on either side of a DST change`() {
        // A zone whose offset changes mid-year is where an off-by-one hides
        // from a test that only ever looks at one date.
        inZone("America/New_York") {
            listOf(
                LocalDate.of(2026, 1, 15),  // EST, UTC-5
                LocalDate.of(2026, 3, 8),   // the spring-forward day itself
                LocalDate.of(2026, 7, 15),  // EDT, UTC-4
                LocalDate.of(2026, 11, 1),  // the fall-back day
            ).forEach { date ->
                assertEquals(date, datePickerDate(datePickerMillis(date)), "$date")
            }
        }
    }

    @Test fun `the conversion does not depend on where the phone is`() {
        // Two devices in different zones must agree about which day a given
        // selection was, because the value they are converting is the same.
        val date = LocalDate.of(2026, 12, 31)
        var west: Long? = null
        var east: Long? = null
        inZone("Pacific/Honolulu") { west = datePickerMillis(date) }
        inZone("Pacific/Auckland") { east = datePickerMillis(date) }
        assertEquals(west, east)
    }
}
