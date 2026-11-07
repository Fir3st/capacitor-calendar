package cz.firest.calendar;

public final class CalendarDateUtils {

    private static final long DAY_IN_MILLIS = 24 * 60 * 60 * 1000L;

    private CalendarDateUtils() {}

    public static boolean isAllDayEvent(long startTime, long endTime) {
        return (endTime - startTime) % DAY_IN_MILLIS == 0;
    }
}
