package cz.firest.calendar;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

@NativePlugin()
public class CapacitorCalendar extends Plugin {
    public static final String LOG_TAG = "Calendar";

    @PluginMethod()
    public void createEvent(PluginCall call) {
        ContentResolver cr = this.getActivity().getContentResolver();
        ContentValues values = new ContentValues();
        JSObject data = call.getData();

        try {
            values.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            values.put(Events.DTSTART, data.getLong("startDate"));
            values.put(Events.DTEND, data.getLong("endDate"));
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
}
