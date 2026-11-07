package cz.firest.calendar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CalendarDateUtilsTest {

    private static final long HOUR_IN_MILLIS = 60 * 60 * 1000L;

    @Test
    public void oneHourIsNotAllDay() {
        assertFalse(CalendarDateUtils.isAllDayEvent(0, HOUR_IN_MILLIS));
    }

    @Test
    public void exactlyOneDayIsAllDay() {
        assertTrue(CalendarDateUtils.isAllDayEvent(0, 24 * HOUR_IN_MILLIS));
    }

    @Test
    public void exactlyTwoDaysIsAllDay() {
        assertTrue(CalendarDateUtils.isAllDayEvent(0, 48 * HOUR_IN_MILLIS));
    }

    @Test
    public void twentyThreeHoursIsNotAllDay() {
        assertFalse(CalendarDateUtils.isAllDayEvent(0, 23 * HOUR_IN_MILLIS));
    }
}
