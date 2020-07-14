package cz.firest.calendar;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Event {
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
