package systems.lupine.sheaf.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Converting between a calendar date and what Material's date picker speaks.
 *
 * `DatePickerState.selectedDateMillis` is **UTC midnight of the day tapped**,
 * not a local instant, and the picker likewise reads the millis it is seeded
 * with as a UTC day. Converting either through the device's own zone is off by
 * a day for half the planet, in opposite directions:
 *
 * - Reading a selection west of UTC. Tapping the 8th gives 8th 00:00Z, which in
 *   UTC-4 is the 7th at 20:00 local, so the local date is the 7th. This is the
 *   one that was reported: picking a date for a front history entry recorded
 *   the day before, and only for people in the Americas, which is why it
 *   survived so long.
 * - Seeding east of UTC. Local midnight on the 8th in UTC+13 is the 7th at
 *   11:00Z, so the picker opens on the 7th.
 *
 * Both directions therefore use UTC and nothing else. What zone the resulting
 * date is later interpreted in is a separate question, and the caller's: a
 * front history entry combines the date with a time in the display zone, and a
 * date custom field stores a bare ISO date with no zone at all.
 */

/** Seed value for `rememberDatePickerState`, as the picker reads it. */
fun datePickerMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The day the user actually tapped, from `selectedDateMillis`. */
fun datePickerDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
