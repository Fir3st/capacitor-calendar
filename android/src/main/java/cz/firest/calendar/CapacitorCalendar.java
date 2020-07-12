package cz.firest.calendar;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.provider.CalendarContract.Calendars;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

class Event {
    String id;
    String message;
    String location;
    String title;
    String startDate;
    String endDate;
    String recurrenceFreq;
    String recurrenceInterval;
    String recurrenceWeekstart;
    String recurrenceByDay;
    String recurrenceByMonthDay;
    String recurrenceUntil;
    String recurrenceCount;

    String eventId;
    boolean recurring = false;
    boolean allDay;

    public JSONObject toJSONObject() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", this.eventId);
            obj.putOpt("message", this.message);
            obj.putOpt("location", this.location);
            obj.putOpt("title", this.title);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sdf.setTimeZone(TimeZone.getDefault());
            if (this.startDate != null) {
                obj.put("startDate", sdf.format(new Date(Long.parseLong(this.startDate))));
            }
            if (this.endDate != null) {
                obj.put("endDate", sdf.format(new Date(Long.parseLong(this.endDate))));
            }
            obj.put("allday", this.allDay);
            if (this.recurring) {
                JSONObject objRecurrence = new JSONObject();

                objRecurrence.putOpt("freq", this.recurrenceFreq);
                objRecurrence.putOpt("interval", this.recurrenceInterval);
                objRecurrence.putOpt("wkst", this.recurrenceWeekstart);
                objRecurrence.putOpt("byday", this.recurrenceByDay);
                objRecurrence.putOpt("bymonthday", this.recurrenceByMonthDay);
                objRecurrence.putOpt("until", this.recurrenceUntil);
                objRecurrence.putOpt("count", this.recurrenceCount);

                obj.put("recurrence", objRecurrence);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return obj;
    }
}

@NativePlugin()
public class CapacitorCalendar extends Plugin {
    public static final String LOG_TAG = "Calendar";

    protected enum KeyIndex {
        CALENDARS_ID,
        IS_PRIMARY,
        CALENDARS_NAME,
        CALENDARS_VISIBLE,
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

    @PluginMethod()
    public void createEvent(PluginCall call) {
        ContentResolver cr = this.getActivity().getContentResolver();
        ContentValues values = new ContentValues();
        JSObject data = call.getData();

        try {
            values.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            values.put(Events.DTSTART, data.has("startDate") ? data.getLong("startDate") : new Date().getTime());
            values.put(Events.DTEND, data.has("endDate") ? data.getLong("endDate") : new Date().getTime());
            values.put(Events.TITLE, call.getString("title", ""));
            values.put(Events.DESCRIPTION, call.getString("notes", ""));
            values.put(Events.EVENT_LOCATION, call.getString("location", ""));
            values.put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY);
            values.put(CalendarContract.Events.CALENDAR_ID, 1);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to parse data", e);
            call.error(e.getMessage());
        }

        try {
            Uri uri = cr.insert(Events.CONTENT_URI, values);
            String createdEventID = uri.getLastPathSegment();
            Log.d(LOG_TAG, "Created event with ID " + createdEventID);

            JSObject ret = new JSObject();
            ret.put("id", createdEventID);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fail to create an event", e);
            call.error(e.getMessage());
        }
    }

    @PluginMethod()
    public void findEvent(PluginCall call) {
        JSObject data = call.getData();
        JSObject ret = new JSObject();

        String eventId = data.getString("id", null);
        String title = data.getString("title", null);
        String location = data.getString("location", null);
        String notes = data.getString("notes", null);
        Long now = new Date().getTime();
        Long startFrom = now - DateUtils.DAY_IN_MILLIS * 10000;
        Long startTo = now + DateUtils.DAY_IN_MILLIS * 10000;

        Event[] instances = fetchEventInstances(eventId, title, location, notes, startFrom, startTo);

        if (instances == null) {
            ret.put("events", new JSONArray());
            call.resolve(ret);
        }

        JSONArray result = new JSONArray();
        Map<String, Event> eventMap = fetchEventsAsMap(instances, "1");

        for (Event instance : instances) {
            Event event = eventMap.get(instance.eventId);
            if (event != null) {
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
        ContentResolver cr = this.getActivity().getContentResolver();
        JSObject data = call.getData();
        JSObject ret = new JSObject();
        String title = data.getString("title", null);
        String location = data.getString("location", null);
        String notes = data.getString("notes", null);
        Long now = new Date().getTime();
        Long startFrom = now - DateUtils.DAY_IN_MILLIS * 10000;
        Long startTo = now + DateUtils.DAY_IN_MILLIS * 10000;

        Event[] events = fetchEventInstances(null, title, location, notes, startFrom, startTo);
        int nrDeletedRecords = 0;
        if (events != null) {
            for (Event event : events) {
                Uri eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, Integer.parseInt(event.eventId));
                nrDeletedRecords = cr.delete(eventUri, null, null);
            }
        }

        ret.put("result", nrDeletedRecords > 0);
        call.resolve(ret);
    }

    protected EnumMap<KeyIndex, String> initContentProviderKeys() {
        EnumMap<KeyIndex, String> keys = new EnumMap<KeyIndex, String>(KeyIndex.class);
        keys.put(KeyIndex.CALENDARS_ID, Calendars._ID);
        keys.put(KeyIndex.IS_PRIMARY, Calendars.IS_PRIMARY);
        keys.put(KeyIndex.CALENDARS_NAME, Calendars.NAME);
        keys.put(KeyIndex.CALENDARS_DISPLAY_NAME, Calendars.CALENDAR_DISPLAY_NAME);
        keys.put(KeyIndex.CALENDARS_VISIBLE, Calendars.VISIBLE);
        keys.put(KeyIndex.EVENTS_ID, Events._ID);
        keys.put(KeyIndex.EVENTS_CALENDAR_ID, Events.CALENDAR_ID);
        keys.put(KeyIndex.EVENTS_DESCRIPTION, Events.DESCRIPTION);
        keys.put(KeyIndex.EVENTS_LOCATION, Events.EVENT_LOCATION);
        keys.put(KeyIndex.EVENTS_SUMMARY, Events.TITLE);
        keys.put(KeyIndex.EVENTS_START, Events.DTSTART);
        keys.put(KeyIndex.EVENTS_END, Events.DTEND);
        keys.put(KeyIndex.EVENTS_RRULE, Events.RRULE);
        keys.put(KeyIndex.EVENTS_ALL_DAY, Events.ALL_DAY);
        keys.put(KeyIndex.INSTANCES_ID, Instances._ID);
        keys.put(KeyIndex.INSTANCES_EVENT_ID, Instances.EVENT_ID);
        keys.put(KeyIndex.INSTANCES_BEGIN, Instances.BEGIN);
        keys.put(KeyIndex.INSTANCES_END, Instances.END);
        return keys;
    }

    protected Event[] fetchEventInstances(String eventId, String title, String location, String notes, long startFrom, long startTo) {
        String[] projection = {
                this.getKey(KeyIndex.INSTANCES_ID),
                this.getKey(KeyIndex.INSTANCES_EVENT_ID),
                this.getKey(KeyIndex.INSTANCES_BEGIN),
                this.getKey(KeyIndex.INSTANCES_END)
        };

        String sortOrder = this.getKey(KeyIndex.INSTANCES_BEGIN) + " ASC, " + this.getKey(KeyIndex.INSTANCES_END) + " ASC";
        String selection = "";
        List<String> selectionList = new ArrayList<String>();

        if (eventId != null && !"".equals(eventId)) {
            selection += CalendarContract.Instances.EVENT_ID + " = ?";
            selectionList.add(eventId);
        } else {
            if (title != null && !"".equals(title)) {
                selection += Events.TITLE + " LIKE ?";
                selectionList.add("%" + title + "%");
            }
            if (location != null  && !"".equals(location)) {
                selection += " AND " + Events.EVENT_LOCATION + " LIKE ?";
                selectionList.add("%" + location + "%");
            }
            if (notes != null  && !"".equals(notes)) {
                selection += " AND " + Events.DESCRIPTION + " LIKE ?";
                selectionList.add("%" + notes + "%");
            }
        }

        String[] selectionArgs = new String[selectionList.size()];
        Cursor cursor = queryEventInstances(startFrom, startTo, projection, selection, selectionList.toArray(selectionArgs), sortOrder);

        if (cursor == null) {
            return null;
        }
        Event[] instances = null;
        if (cursor.moveToFirst()) {
            int idCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_ID));
            int eventIdCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_EVENT_ID));
            int beginCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_BEGIN));
            int endCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_END));
            int count = cursor.getCount();
            int i = 0;
            instances = new Event[count];
            do {
                instances[i] = new Event();
                instances[i].id = cursor.getString(idCol);
                instances[i].eventId = cursor.getString(eventIdCol);
                instances[i].startDate = cursor.getString(beginCol);
                instances[i].endDate = cursor.getString(endCol);
                i += 1;
            } while (cursor.moveToNext());
        }

        if ((instances == null || instances.length == 0) && eventId != null) {
            return fetchEventInstances(null, title, location, notes, startFrom, startTo);
        } else {
            return instances;
        }
    }

    protected String getKey(KeyIndex index) {
        this.initContentProviderKeys();
        return this.initContentProviderKeys().get(index);
    }

    private Map<String, Event> fetchEventsAsMap(Event[] instances, String calendarId) {
        // Only selecting from active calendars, no active calendars = no events.
        List<String> activeCalendarIds = Arrays.asList(getActiveCalendarIds());
        if (activeCalendarIds.isEmpty()) {
            return null;
        }

        List<String> calendarsToSearch;

        if(calendarId!=null){
            calendarsToSearch = new ArrayList<String>();
            if(activeCalendarIds.contains(calendarId)){
                calendarsToSearch.add(calendarId);
            }

        }else{
            calendarsToSearch = activeCalendarIds;
        }

        if(calendarsToSearch.isEmpty()){
            return null;
        }


        String[] projection = new String[]{
                this.getKey(KeyIndex.EVENTS_ID),
                this.getKey(KeyIndex.EVENTS_DESCRIPTION),
                this.getKey(KeyIndex.EVENTS_LOCATION),
                this.getKey(KeyIndex.EVENTS_SUMMARY),
                this.getKey(KeyIndex.EVENTS_START),
                this.getKey(KeyIndex.EVENTS_END),
                this.getKey(KeyIndex.EVENTS_RRULE),
                this.getKey(KeyIndex.EVENTS_ALL_DAY)
        };
        // Get all the ids at once from active calendars.
        StringBuffer select = new StringBuffer();
        select.append(this.getKey(KeyIndex.EVENTS_ID) + " IN (");
        select.append(instances[0].eventId);
        for (int i = 1; i < instances.length; i++) {
            select.append(",");
            select.append(instances[i].eventId);
        }
        select.append(") AND " + this.getKey(KeyIndex.EVENTS_CALENDAR_ID) + " IN (");

        String prefix ="";
        for (String calendarToFilterId:calendarsToSearch) {
            select.append(prefix);
            prefix = ",";
            select.append(calendarToFilterId);
        }

        select.append(")");
        Cursor cursor = queryEvents(projection, select.toString(), null, null);
        Map<String, Event> eventsMap = new HashMap<String, Event>();
        if (cursor.moveToFirst()) {
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
                    String[] rrule_rules = cursor.getString(cols[6]).split(";");
                    for (String rule : rrule_rules) {
                        String rule_type = rule.split("=")[0];
                        if (rule_type.equals("FREQ")) {
                            event.recurrenceFreq = rule.split("=")[1];
                        } else if (rule_type.equals("INTERVAL")) {
                            event.recurrenceInterval = rule.split("=")[1];
                        } else if (rule_type.equals("WKST")) {
                            event.recurrenceWeekstart = rule.split("=")[1];
                        } else if (rule_type.equals("BYDAY")) {
                            event.recurrenceByDay = rule.split("=")[1];
                        } else if (rule_type.equals("BYMONTHDAY")) {
                            event.recurrenceByMonthDay = rule.split("=")[1];
                        } else if (rule_type.equals("UNTIL")) {
                            event.recurrenceUntil = rule.split("=")[1];
                        } else if (rule_type.equals("COUNT")) {
                            event.recurrenceCount = rule.split("=")[1];
                        } else {
                            Log.d(LOG_TAG, "Missing handler for " + rule);
                        }
                    }
                } else {
                    event.recurring = false;
                }
                event.allDay = cursor.getInt(cols[7]) != 0;
                eventsMap.put(event.id, event);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return eventsMap;
    }

    private String[] getActiveCalendarIds() {
        Cursor cursor = queryCalendars(new String[]{
                        this.getKey(KeyIndex.CALENDARS_ID)
                },
                this.getKey(KeyIndex.CALENDARS_VISIBLE) + "=1", null, null);
        String[] calendarIds = null;
        if (cursor.moveToFirst()) {
            calendarIds = new String[cursor.getCount()];
            int i = 0;
            do {
                int col = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_ID));
                calendarIds[i] = cursor.getString(col);
                i += 1;
            } while (cursor.moveToNext());
            cursor.close();
        }
        return calendarIds;
    }

    protected Cursor queryEventInstances(long startFrom, long startTo, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, startFrom);
            ContentUris.appendId(builder, startTo);

        return this.getActivity().getContentResolver().query(
                builder.build(), projection, selection, selectionArgs, sortOrder);
    }

    protected Cursor queryCalendars(String[] projection, String selection,
                                    String[] selectionArgs, String sortOrder) {
        return this.getActivity().getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI, projection, selection, selectionArgs,
                sortOrder);
    }

    protected Cursor queryEvents(String[] projection, String selection,
                                 String[] selectionArgs, String sortOrder) {
        return this.getActivity().getContentResolver().query(
                Events.CONTENT_URI, projection, selection, selectionArgs, sortOrder);
    }
}
