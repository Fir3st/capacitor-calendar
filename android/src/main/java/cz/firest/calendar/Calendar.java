package cz.firest.calendar;

public class Calendar {
    String id;
    String name;
    String displayName;
    boolean defaultCalendar;

    public Calendar(String id, String name, String displayName, boolean defaultCalendar) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.defaultCalendar = defaultCalendar;
    }
}
