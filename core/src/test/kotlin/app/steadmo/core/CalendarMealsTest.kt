package app.steadmo.core

import app.steadmo.core.CalendarMeals.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMealsTest {
    private val min = 60_000L

    @Test fun `events named for a meal`() {
        listOf(
            "Dinner with Sam", "lunch", "Team Lunch", "BRUNCH at Ottolenghi", "Breakfast meeting", "Supper", "Meal prep",
            "Reservation at Dishoom", "Table for 2 at Barrafina", "Restaurant: Hawksmoor",
        )
            .forEach { assertTrue(it, CalendarMeals.isMeal(it)) }
        listOf("Dentist", "Lunchbox repair", "Mealtime check", "Stand-up", "Dinnerware sale", "Hotel reservations call")
            .forEach { assertFalse(it, CalendarMeals.isMeal(it)) }
    }

    @Test fun `reminds when the soonest timed meal starts`() {
        val events = listOf(
            Event(1, "Dentist", 30 * min, false),
            Event(2, "Dinner with Sam", 300 * min, false),
            Event(3, "Lunch", 120 * min, false),
            Event(4, "Team dinner", 60 * min, true),
        )
        assertEquals(Event(3, "Lunch", 120 * min, false) to 120 * min, CalendarMeals.next(events, 0))
    }

    @Test fun `a due reminder that hasn't gone off is kept until 30 minutes after the start`() {
        val lunch = Event(1, "Lunch", 10 * min, false)
        // Moved to just before now (an edit late in the day): due at once.
        assertEquals(lunch to 10 * min, CalendarMeals.next(listOf(lunch), 11 * min))
        assertNull(CalendarMeals.next(listOf(lunch), 11 * min, reminded = setOf(lunch.key)))
        assertEquals(lunch to 10 * min, CalendarMeals.next(listOf(lunch), 39 * min))
        assertNull(CalendarMeals.next(listOf(lunch), 40 * min))
    }

    @Test fun `not beyond a day`() {
        assertNull(CalendarMeals.next(listOf(Event(1, "Lunch", 25 * 60 * min, false)), 0))
    }
}
