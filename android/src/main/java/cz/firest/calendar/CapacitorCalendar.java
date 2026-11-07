package cz.firest.calendar;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

@CapacitorPlugin(
    name = "CapacitorCalendar",
    permissions = {
        @Permission(strings = { Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR }, alias = "calendar")
    }
)
public class CapacitorCalendar extends Plugin {
    public static final String LOG_TAG = "Calendar";

    private EnumMap<KeyIndex, String> keys;

    protected enum KeyIndex {
        CALENDARS_ID,
        IS_PRIMARY,
        CALENDARS_NAME,
        CALENDARS_VISIBLE,
        CALENDARS_PRIMARY,
        CALENDARS_DISPLAY_NAME,
        EVENTS_ID,
        EVENTS_CALENDAR_ID,
        EVENTS_DESCRIPTION,
        EVENTS_LOCATION,
        EVENTS_SUMMARY,
        EVENTS_START,
        EVENTS_END,
        EVENTS_RRULE,
        EVENTS_ALL_DAY,
        INSTANCES_ID,
        INSTANCES_EVENT_ID,
        INSTANCES_BEGIN,
        INSTANCES_END,
        ATTENDEES_ID,
        ATTENDEES_EVENT_ID,
        ATTENDEES_NAME,
        ATTENDEES_EMAIL,
        ATTENDEES_STATUS
    }

    private boolean hasCalendarPermission() {
        return getPermissionState("calendar") == PermissionState.GRANTED;
    }

    private boolean requestCalendarPermissionIfNeeded(PluginCall call) {
        if (hasCalendarPermission()) {
            return true;
        }
        requestPermissionForAlias("calendar", call, "calendarPermissionsCallback");
        return false;
    }

    @PermissionCallback
    private void calendarPermissionsCallback(PluginCall call) {
        if (!hasCalendarPermission()) {
            call.reject("Calendar permission is required");
            return;
        }

        String method = call.getMethodName();
        if (method == null) {
            call.reject("Unknown plugin method");
            return;
        }

        switch (method) {
            case "openCalendar":
                openCalendar(call);
                break;
            case "createEvent":
                createEvent(call);
                break;
            case "findEvent":
                findEvent(call);
                break;
            case "deleteEvent":
                deleteEvent(call);
                break;
            case "deleteEventById":
                deleteEventById(call);
                break;
            case "updateEvent":
                updateEvent(call);
                break;
            case "getAvailableCalendars":
                getAvailableCalendars(call);
                break;
            default:
                call.reject("Unknown plugin method: " + method);
        }
    }

    @PluginMethod()
    public void openCalendar(PluginCall call) {
        if (!requestCalendarPermissionIfNeeded(call)) {
            return;
        }

        try {
            JSObject data = call.getData();
            long millis = data.has("date") ? data.getLong("date") : new Date().getTime();

            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            ContentUris.appendId(builder, millis);
            Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
            getActivity().startActivity(intent);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("No calendar app found", e);
        } catch (JSONException e) {
            call.reject("Invalid date", e);
        }
    }

    @PluginMethod()
    public void createEvent(PluginCall call) {
        if (!requestCalendarPermissionIfNeeded(call)) {
            return;
        }
        createCalendarEvent(call);
    }

    protected void createCalendarEvent(PluginCall call) {
        ContentResolver cr = getActivity().getContentResolver();
        ContentValues values = new ContentValues();
        JSObject data = call.getData();

        try {
            long startTime = data.has("startDate") ? data.getLong("startDate") : new Date().getTime();
            long endTime = data.has("endDate") ? data.getLong("endDate") : startTime;
            boolean hasAllDayConfig = data.has("allDay");
            boolean allDay = hasAllDayConfig && data.getBoolean("allDay");
            putEventTimes(values, startTime, endTime, hasAllDayConfig, allDay);

            String selectedCalendarId = data.has("calendarId") ? data.getString("calendarId") : null;
            if (selectedCalendarId != null) {
                selectedCalendarId = selectedCalendarId.replace("\"", "");
            }
            List<String> activeCalendars = Arrays.asList(getActiveCalendarIds());
            int calendarId = selectedCalendarId != null && activeCalendars.contains(selectedCalendarId)
                    ? Integer.parseInt(selectedCalendarId)
                    : getDefaultCalendarId();

            values.put(Events.TITLE, call.getString("title", ""));
            values.put(Events.DESCRIPTION, call.getString("notes", ""));
            values.put(Events.EVENT_LOCATION, call.getString("location", ""));
            values.put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY);
            values.put(Events.CALENDAR_ID, calendarId);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to parse data", e);
            call.reject(e.getMessage(), e);
            return;
        }

        try {
            Uri uri = cr.insert(Events.CONTENT_URI, values);
            if (uri == null) {
                call.reject("Failed to create event");
                return;
            }

            String createdEventID = uri.getLastPathSegment();
            Log.d(LOG_TAG, "Created event with ID " + createdEventID);

            JSObject ret = new JSObject();
            ret.put("id", createdEventID);
            call.resolve(ret);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Permission denied", e);
            call.reject("Permission denied", e);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to create an event", e);
            call.reject(e.getMessage(), e);
        }
    }

    protected int getDefaultCalendarId() throws Exception {
        List<Calendar> calendars = getAvailableCalendarsList();

        if (calendars == null || calendars.isEmpty()) {
            throw new Exception("No calendars found.");
        }

        for (Calendar calendar : calendars) {
            if (calendar.defaultCalendar) {
                return Integer.parseInt(calendar.id.trim());
            }
        }

        return Integer.parseInt(calendars.get(0).id.trim());
    }

    @PluginMethod()
    public void findEvent(PluginCall call) {
        if (!requestCalendarPermissionIfNeeded(call)) {
            return;
        }
        findCalendarEvents(call);
    }

    protected void findCalendarEvents(PluginCall call) {
        JSObject data = call.getData();
        JSObject ret = new JSObject();
        long now = new Date().getTime();

        String eventId;
        String title;
        String location;
        String notes;
        long startFrom;
        long startTo;

        try {
            eventId = data.getString("id", null);
            title = data.getString("title", null);
            location = data.getString("location", null);
            notes = data.getString("notes", null);
            startFrom = data.has("startDate") ? data.getLong("startDate") : now - DateUtils.DAY_IN_MILLIS * 1000;
            startTo = data.has("endDate") ? data.getLong("endDate") : now + DateUtils.DAY_IN_MILLIS * 1000;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to parse data", e);
            call.reject(e.getMessage(), e);
            return;
        }

        Event[] instances = fetchEventInstances(eventId, title, location, notes, startFrom, startTo);
        JSArray result = new JSArray();

        if (instances != null && instances.length > 0) {
            Map<String, Event> eventMap = fetchEventsAsMap(instances);
            for (Event instance : instances) {
                Event event = eventMap.get(instance.eventId);
                if (event == null) {
                    continue;
                }

                instance.message = event.message;
                instance.location = event.location;
                instance.title = event.title;
                if (!event.recurring) {
                    instance.startDate = event.startDate;
                    instance.endDate = event.endDate;
                }

                instance.recurring = event.recurring;
                instance.recurrenceFreq = event.recurrenceFreq;
                instance.recurrenceInterval = event.recurrenceInterval;
                instance.recurrenceWeekstart = event.recurrenceWeekstart;
                instance.recurrenceByDay = event.recurrenceByDay;
                instance.recurrenceByMonthDay = event.recurrenceByMonthDay;
                instance.recurrenceUntil = event.recurrenceUntil;
                instance.recurrenceCount = event.recurrenceCount;
                instance.allDay = event.allDay;

                result.put(instance.toJSONObject());
            }
        }

        ret.put("events", result);
        call.resolve(ret);
    }

    @PluginMethod()
    public void deleteEvent(PluginCall call) {
        if (!requestCalendarPermissionIfNeeded(call)) {
            return;
        }
        deleteCalendarEvents(call);
    }

    protected void deleteCalendarEvents(PluginCall call) {
        ContentResolver cr = getActivity().getContentResolver();
        JSObject data = call.getData();
        JSObject ret = new JSObject();
        long now = new Date().getTime();

        String eventId;
        String title;
        String location;
        String notes;
        long startFrom;
        long startTo;

        try {
            eventId = data.getString("id", null);
            title = data.getString("title", null);
            location = data.getString("location", null);
            notes = data.getString("notes", null);
            startFrom = data.has("startDate") ? data.getLong("startDate") : now - DateUtils.DAY_IN_MILLIS * 1000;
            startTo = data.has("endDate") ? data.getLong("endDate") : now + DateUtils.DAY_IN_MILLIS * 1000;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to parse data", e);
            call.reject(e.getMessage(), e);
            return;
        }

        Event[] events = fetchEventInstances(eventId, title, location, notes, startFrom, startTo);
        int nrDeletedRecords = 0;

        if (events != null) {
            for (Event event : events) {
                if (event.eventId == null) {
                    continue;
                }
                try {
                    Uri eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, Long.parseLong(event.eventId));
                    nrDeletedRecords += cr.delete(eventUri, null, null);
                } catch (NumberFormatException e) {
                    Log.e(LOG_TAG, "Invalid event id", e);
                }
            }
        }

        ret.put("result", nrDeletedRecords > 0);
        call.resolve(ret);
    }

    @PluginMethod()
    public void deleteEventById(PluginCall call) {
        if (!requestCalendarPermissionIfNeeded(call)) {
            return;
        }
        deleteCalendarEventById(call);
    }

    protected void deleteCalendarEventById(PluginCall call) {
        JSObject data = call.getData();
        JSObject ret = new JSObject();
        String id = data.getString("id", null);

        if (id == null) {
            call.reject("Event id not specified.");
            return;
        }

        boolean eventFound = false;
        Cursor cursor = queryEvents(
                new String[] { Events.DTSTART },
                Events._ID + " = ?",
                new String[] { id },
                Events.DTSTART);

        if (cursor != null) {
            try {
                eventFound = cursor.moveToNext();
            } finally {
                cursor.close();
            }
        }

        if (!eventFound) {
            call.reject("Could not find event.");
            return;
        }

        try {
            int deleted = getActivity().getContentResolver()
                    .delete(ContentUris.withAppendedId(Events.CONTENT_URI, Long.parseLong(id)), null, null);
            ret.put("result", deleted > 0);
            call.resolve(ret);
        } catch (NumberFormatException e) {
            call.reject("Invalid event id.", e);
        } catch (SecurityException e) {
            call.reject("Permission denied", e);
        }
    }

    @PluginMethod()
    public void updateEvent(PluginCall call) {
        if (!requestCalendarPermissionIfNeeded(call)) {
            return;
        }
        updateCalendarEvent(call);
    }

    protected void updateCalendarEvent(PluginCall call) {
        JSObject data = call.getData();
        JSObject ret = new JSObject();
        String id = data.getString("id", null);

        if (id == null) {
            call.reject("Event id not specified.");
            return;
        }

        long evDtStart = -1;
        long evDtEnd = -1;
        String title = null;
        String description = null;
        String location = null;

        Cursor cursor = queryEvents(
                new String[] { Events.DTSTART, Events.DTEND, Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION },
                Events._ID + " = ?",
                new String[] { id },
                Events.DTSTART);

        if (cursor != null) {
            try {
                if (cursor.moveToNext()) {
                    evDtStart = cursor.getLong(0);
                    evDtEnd = cursor.getLong(1);
                    title = cursor.getString(2);
                    description = cursor.getString(3);
                    location = cursor.getString(4);
                }
            } finally {
                cursor.close();
            }
        }

        if (evDtStart == -1) {
            call.reject("Could not find event.");
            return;
        }

        ContentValues values = new ContentValues();
        try {
            long startTime = data.has("startDate") ? data.getLong("startDate") : evDtStart;
            long endTime = data.has("endDate") ? data.getLong("endDate") : evDtEnd;
            boolean hasAllDayConfig = data.has("allDay");
            boolean allDay = hasAllDayConfig && data.getBoolean("allDay");
            putEventTimes(values, startTime, endTime, hasAllDayConfig, allDay);

            values.put(Events.TITLE, data.has("title") ? data.getString("title") : title);
            values.put(Events.DESCRIPTION, data.has("notes") ? data.getString("notes") : description);
            values.put(Events.EVENT_LOCATION, data.has("location") ? data.getString("location") : location);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to parse data", e);
            call.reject(e.getMessage(), e);
            return;
        }

        try {
            int updated = getActivity().getContentResolver()
                    .update(ContentUris.withAppendedId(Events.CONTENT_URI, Long.parseLong(id)), values, null, null);
            ret.put("result", updated > 0);
            call.resolve(ret);
        } catch (NumberFormatException e) {
            call.reject("Invalid event id.", e);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Permission denied", e);
            call.reject("Permission denied", e);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to update an event", e);
            call.reject(e.getMessage(), e);
        }
    }

    private void putEventTimes(ContentValues values, long startTime, long endTime, boolean hasAllDayConfig, boolean allDay) {
        if (hasAllDayConfig) {
            values.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            values.put(Events.ALL_DAY, allDay);
            values.put(Events.DTSTART, startTime);
            values.put(Events.DTEND, endTime);
        } else if (CalendarDateUtils.isAllDayEvent(startTime, endTime)) {
            values.put(Events.EVENT_TIMEZONE, "UTC");
            values.put(Events.ALL_DAY, true);
            values.put(Events.DTSTART, startTime + TimeZone.getDefault().getOffset(startTime));
            values.put(Events.DTEND, endTime + TimeZone.getDefault().getOffset(endTime));
        } else {
            values.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            values.put(Events.ALL_DAY, false);
            values.put(Events.DTSTART, startTime);
            values.put(Events.DTEND, endTime);
        }
    }

    protected EnumMap<KeyIndex, String> initContentProviderKeys() {
        EnumMap<KeyIndex, String> result = new EnumMap<KeyIndex, String>(KeyIndex.class);
        result.put(KeyIndex.CALENDARS_ID, Calendars._ID);
        result.put(KeyIndex.IS_PRIMARY, Calendars.IS_PRIMARY);
        result.put(KeyIndex.CALENDARS_NAME, Calendars.NAME);
        result.put(KeyIndex.CALENDARS_DISPLAY_NAME, Calendars.CALENDAR_DISPLAY_NAME);
        result.put(KeyIndex.CALENDARS_VISIBLE, Calendars.VISIBLE);
        result.put(KeyIndex.CALENDARS_PRIMARY, Calendars.IS_PRIMARY);
        result.put(KeyIndex.EVENTS_ID, Events._ID);
        result.put(KeyIndex.EVENTS_CALENDAR_ID, Events.CALENDAR_ID);
        result.put(KeyIndex.EVENTS_DESCRIPTION, Events.DESCRIPTION);
        result.put(KeyIndex.EVENTS_LOCATION, Events.EVENT_LOCATION);
        result.put(KeyIndex.EVENTS_SUMMARY, Events.TITLE);
        result.put(KeyIndex.EVENTS_START, Events.DTSTART);
        result.put(KeyIndex.EVENTS_END, Events.DTEND);
        result.put(KeyIndex.EVENTS_RRULE, Events.RRULE);
        result.put(KeyIndex.EVENTS_ALL_DAY, Events.ALL_DAY);
        result.put(KeyIndex.INSTANCES_ID, Instances._ID);
        result.put(KeyIndex.INSTANCES_EVENT_ID, Instances.EVENT_ID);
        result.put(KeyIndex.INSTANCES_BEGIN, Instances.BEGIN);
        result.put(KeyIndex.INSTANCES_END, Instances.END);
        return result;
    }

    protected Event[] fetchEventInstances(String eventId, String title, String location, String notes, long startFrom, long startTo) {
        String[] projection = {
                this.getKey(KeyIndex.INSTANCES_ID),
                this.getKey(KeyIndex.INSTANCES_EVENT_ID),
                this.getKey(KeyIndex.INSTANCES_BEGIN),
                this.getKey(KeyIndex.INSTANCES_END)
        };

        String sortOrder = this.getKey(KeyIndex.INSTANCES_BEGIN) + " ASC, " + this.getKey(KeyIndex.INSTANCES_END) + " ASC";
        List<String> selectionList = new ArrayList<String>();
        List<String> clauses = new ArrayList<String>();

        if (eventId != null && !"".equals(eventId)) {
            clauses.add(Instances.EVENT_ID + " = ?");
            selectionList.add(eventId);
        } else {
            if (title != null && !"".equals(title)) {
                clauses.add(Events.TITLE + " LIKE ?");
                selectionList.add("%" + title + "%");
            }
            if (location != null && !"".equals(location)) {
                clauses.add(Events.EVENT_LOCATION + " LIKE ?");
                selectionList.add("%" + location + "%");
            }
            if (notes != null && !"".equals(notes)) {
                clauses.add(Events.DESCRIPTION + " LIKE ?");
                selectionList.add("%" + notes + "%");
            }
        }

        String selection = TextUtils.join(" AND ", clauses);
        String[] selectionArgs = selectionList.toArray(new String[0]);
        Cursor cursor = queryEventInstances(startFrom, startTo, projection, selection, selectionArgs, sortOrder);

        if (cursor == null) {
            return null;
        }

        try {
            if (!cursor.moveToFirst()) {
                return null;
            }

            int idCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_ID));
            int eventIdCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_EVENT_ID));
            int beginCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_BEGIN));
            int endCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_END));
            int count = cursor.getCount();
            int i = 0;
            Event[] instances = new Event[count];

            do {
                instances[i] = new Event();
                instances[i].id = cursor.getString(idCol);
                instances[i].eventId = cursor.getString(eventIdCol);
                instances[i].startDate = cursor.getString(beginCol);
                instances[i].endDate = cursor.getString(endCol);
                i += 1;
            } while (cursor.moveToNext());

            return instances;
        } finally {
            cursor.close();
        }
    }

    protected String getKey(KeyIndex index) {
        if (this.keys == null) {
            this.keys = this.initContentProviderKeys();
        }
        return this.keys.get(index);
    }

    protected Map<String, Event> fetchEventsAsMap(Event[] instances) {
        Map<String, Event> eventsMap = new HashMap<String, Event>();
        if (instances == null || instances.length == 0) {
            return eventsMap;
        }

        List<String> activeCalendarIds = Arrays.asList(getActiveCalendarIds());
        if (activeCalendarIds.isEmpty()) {
            return eventsMap;
        }

        Set<String> eventIds = new LinkedHashSet<String>();
        for (Event instance : instances) {
            if (instance.eventId != null) {
                eventIds.add(instance.eventId);
            }
        }
        if (eventIds.isEmpty()) {
            return eventsMap;
        }

        String[] projection = new String[] {
                this.getKey(KeyIndex.EVENTS_ID),
                this.getKey(KeyIndex.EVENTS_DESCRIPTION),
                this.getKey(KeyIndex.EVENTS_LOCATION),
                this.getKey(KeyIndex.EVENTS_SUMMARY),
                this.getKey(KeyIndex.EVENTS_START),
                this.getKey(KeyIndex.EVENTS_END),
                this.getKey(KeyIndex.EVENTS_RRULE),
                this.getKey(KeyIndex.EVENTS_ALL_DAY)
        };

        String eventPlaceholders = TextUtils.join(",", Collections.nCopies(eventIds.size(), "?"));
        String calendarPlaceholders = TextUtils.join(",", Collections.nCopies(activeCalendarIds.size(), "?"));
        String selection = this.getKey(KeyIndex.EVENTS_ID) + " IN (" + eventPlaceholders + ")"
                + " AND " + this.getKey(KeyIndex.EVENTS_CALENDAR_ID) + " IN (" + calendarPlaceholders + ")";

        List<String> selectionArgs = new ArrayList<String>(eventIds);
        selectionArgs.addAll(activeCalendarIds);
        Cursor cursor = queryEvents(projection, selection, selectionArgs.toArray(new String[0]), null);

        if (cursor == null) {
            return eventsMap;
        }

        try {
            if (!cursor.moveToFirst()) {
                return eventsMap;
            }

            int[] cols = new int[projection.length];
            for (int i = 0; i < cols.length; i++) {
                cols[i] = cursor.getColumnIndex(projection[i]);
            }

            do {
                Event event = new Event();
                event.id = cursor.getString(cols[0]);
                event.message = cursor.getString(cols[1]);
                event.location = cursor.getString(cols[2]);
                event.title = cursor.getString(cols[3]);
                event.startDate = cursor.getString(cols[4]);
                event.endDate = cursor.getString(cols[5]);

                String rrule = cursor.getString(cols[6]);
                if (!TextUtils.isEmpty(rrule)) {
                    event.recurring = true;
                    String[] rruleRules = rrule.split(";");
                    for (String rule : rruleRules) {
                        String[] parts = rule.split("=", 2);
                        if (parts.length < 2) {
                            continue;
                        }
                        String ruleType = parts[0];
                        String ruleValue = parts[1];
                        switch (ruleType) {
                            case "FREQ":
                                event.recurrenceFreq = ruleValue;
                                break;
                            case "INTERVAL":
                                event.recurrenceInterval = ruleValue;
                                break;
                            case "WKST":
                                event.recurrenceWeekstart = ruleValue;
                                break;
                            case "BYDAY":
                                event.recurrenceByDay = ruleValue;
                                break;
                            case "BYMONTHDAY":
                                event.recurrenceByMonthDay = ruleValue;
                                break;
                            case "UNTIL":
                                event.recurrenceUntil = ruleValue;
                                break;
                            case "COUNT":
                                event.recurrenceCount = ruleValue;
                                break;
                            default:
                                Log.d(LOG_TAG, "Missing handler for " + rule);
                        }
                    }
                } else {
                    event.recurring = false;
                }

                event.allDay = cursor.getInt(cols[7]) != 0;
                eventsMap.put(event.id, event);
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }

        return eventsMap;
    }

    @PluginMethod()
    public void getAvailableCalendars(PluginCall call) {
        if (!requestCalendarPermissionIfNeeded(call)) {
            return;
        }

        List<Calendar> availableCalendars = getAvailableCalendarsList();
        JSArray calendars = new JSArray();
        for (Calendar calendar : availableCalendars) {
            JSObject item = new JSObject();
            item.put("id", calendar.id);
            item.put("name", calendar.name);
            item.put("displayName", calendar.displayName);
            item.put("defaultCalendar", calendar.defaultCalendar);
            calendars.put(item);
        }

        JSObject ret = new JSObject();
        ret.put("availableCalendars", calendars);
        call.resolve(ret);
    }

    protected List<Calendar> getAvailableCalendarsList() {
        Cursor cursor = queryCalendars(new String[] {
                        this.getKey(KeyIndex.CALENDARS_ID),
                        this.getKey(KeyIndex.CALENDARS_PRIMARY),
                        this.getKey(KeyIndex.CALENDARS_NAME),
                        this.getKey(KeyIndex.CALENDARS_DISPLAY_NAME)
                },
                this.getKey(KeyIndex.CALENDARS_VISIBLE) + "=1", null, null);

        List<Calendar> availableCalendars = new ArrayList<Calendar>();
        if (cursor == null) {
            return availableCalendars;
        }

        boolean defaultSelected = false;
        try {
            if (!cursor.moveToFirst()) {
                return availableCalendars;
            }

            do {
                int col = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_ID));
                int primaryCol = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_PRIMARY));
                int nameCol = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_NAME));
                int displayNameCol = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_DISPLAY_NAME));

                if (primaryCol != -1) {
                    boolean defaultCalendar = false;
                    if (!defaultSelected && cursor.getInt(primaryCol) == 1) {
                        defaultSelected = true;
                        defaultCalendar = true;
                    }

                    Calendar data = new Calendar(
                            cursor.getString(col),
                            cursor.getString(nameCol),
                            cursor.getString(displayNameCol),
                            defaultCalendar);
                    availableCalendars.add(data);
                }
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }

        return availableCalendars;
    }

    protected String[] getActiveCalendarIds() {
        Cursor cursor = queryCalendars(new String[] {
                        this.getKey(KeyIndex.CALENDARS_ID),
                        this.getKey(KeyIndex.CALENDARS_PRIMARY)
                },
                this.getKey(KeyIndex.CALENDARS_VISIBLE) + "=1", null, null);

        if (cursor == null) {
            return new String[0];
        }

        List<String> calendarIds = new ArrayList<String>();
        try {
            int col = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_ID));
            int primaryCol = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_PRIMARY));
            if (primaryCol != -1) {
                while (cursor.moveToNext()) {
                    calendarIds.add(cursor.getString(col));
                }
            }
        } finally {
            cursor.close();
        }

        return calendarIds.toArray(new String[0]);
    }

    protected Cursor queryEventInstances(long startFrom, long startTo, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, startFrom);
        ContentUris.appendId(builder, startTo);

        return this.getActivity().getContentResolver().query(
                builder.build(), projection, selection, selectionArgs, sortOrder);
    }

    protected Cursor queryCalendars(String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            return this.getActivity().getContentResolver().query(
                    CalendarContract.Calendars.CONTENT_URI, projection, selection, selectionArgs, sortOrder);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Permission denied", e);
            return null;
        }
    }

    protected Cursor queryEvents(String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            return this.getActivity().getContentResolver().query(
                    Events.CONTENT_URI, projection, selection, selectionArgs, sortOrder);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Permission denied", e);
            return null;
        }
    }

    public static boolean isAllDayEvent(final Date startDate, final Date endDate) {
        return CalendarDateUtils.isAllDayEvent(startDate.getTime(), endDate.getTime());
    }
}
