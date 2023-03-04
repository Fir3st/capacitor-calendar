# Capacitor Calendar

Perform various actions on the user's calendar:

```ts
interface CapacitorCalendar {
  openCalendar(options?: { date: number }): Promise<any>
  createEvent(options: CalendarEventOptions): Promise<any>
  findEvent(options: CalendarEventOptions): Promise<any>
  deleteEvent(options: DeleteEventOptions): Promise<any>
  deleteEventById(options: { id: string }): Promise<any>
  updateEvent(options: CalendarEventOptions): Promise<any>
  getAvailableCalendars(): Promise<any>
}
```

## Usage

For iOS, you must add a `NSCalendarsUsageDescription` key to Info.plist with a usage description.

This plugin is not yet implemented on web.


**Create event simple example**

```ts
import { Capacitor } from '@capacitor/core';
import { CapacitorCalendar } from 'capacitor-calendar';

async function createEvent(durationHours: number): Promise<void> {
  if (Capacitor.isNativePlatform()) {
    // create calendar event on mobile
    let result: { availableCalendars: { id: string; name: string }[] };
    try {
      // the first time, the user will be prompted to grant permission
      result = await CapacitorCalendar.getAvailableCalendars();
    } catch(e) {}

    if (result?.availableCalendars.length) {
      try {
        const hours = 3600000 * durationHours;
        // use default calendar
        await CapacitorCalendar.createEvent({
          id: 'abc',
          title: 'Launch party',
          location: '1 Infinite Loop, Cupertino CA 95014',
          notes: 'A celebration for all our hard work!',
          startDate: 1677883222055, // start date milliseconds
          endDate: 1677883222055 + hours,
        });
        // show success message here...
      } catch (e) {
        console.error('Error creating calendar event', e);
        // show error message here...
      }
    } else {
      // show error: 'Could not add event to calendar - no calendars found';
    }
  }
}
```
