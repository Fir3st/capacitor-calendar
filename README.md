# Capacitor Calendar

Perform various actions on the user's calendar.

This version targets **Capacitor 8** and supports both Swift Package Manager and CocoaPods on iOS.

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

## Requirements

- Capacitor 8 (`@capacitor/core >= 8.0.0`)
- iOS 15 or newer, Xcode 26 or newer
- Android minSdk 24, compileSdk 36, Java 21
- Node.js 22 or newer

## Installation

```bash
npm install capacitor-calendar
npx cap sync
```

### iOS

Add the calendar usage descriptions to your app's `Info.plist`:

```xml
<key>NSCalendarsUsageDescription</key>
<string>This app needs access to your calendar to manage events.</string>
<key>NSCalendarsFullAccessUsageDescription</key>
<string>This app needs full access to your calendar to manage events.</string>
```

### Android

The plugin declares the `READ_CALENDAR` and `WRITE_CALENDAR` permissions. The permissions are requested at runtime by the plugin.

The plugin is not implemented on the web.

## Usage

```ts
import { Capacitor } from '@capacitor/core';
import { CapacitorCalendar } from 'capacitor-calendar';

async function createEvent(durationHours: number): Promise<void> {
  if (!Capacitor.isNativePlatform()) {
    return;
  }

  const result = await CapacitorCalendar.getAvailableCalendars();

  if (result.availableCalendars.length === 0) {
    console.error('Could not add event to calendar - no calendars found');
    return;
  }

  const hours = 3600000 * durationHours;
  await CapacitorCalendar.createEvent({
    id: 'abc',
    title: 'Launch party',
    location: '1 Infinite Loop, Cupertino CA 95014',
    notes: 'A celebration for all our hard work!',
    startDate: Date.now(),
    endDate: Date.now() + hours,
  });
}
```

The first calendar operation prompts for permission. You can also manage permissions explicitly:

```ts
const status = await CapacitorCalendar.checkPermissions();
await CapacitorCalendar.requestPermissions();
```

## Development

```bash
npm install
npm run build
```

Build the plugin for all platforms:

```bash
npm run verify
```

Individual checks:

```bash
npm run verify:web
npm run verify:ios
npm run verify:android
```

`verify:android` requires an Android SDK with platform 36 installed and `JAVA_HOME` pointing to JDK 21. `verify:ios` requires Xcode 26 or newer.

To test the plugin in an app, install the local folder and sync the native projects:

```bash
npm install /path/to/capacitor-calendar
npx cap sync
```

A ready-to-run Capacitor 8 app that exercises every method is in [`example/`](./example).

## Breaking changes in 2.0

- Requires Capacitor 8.
- iOS deployment target is now 15.0.
- Android compile/target SDK is now 36.
- `getAvailableCalendars()` returns an array on Android as well as iOS.
- The legacy Xcode project and CocoaPods development Podfile were replaced by Swift Package Manager and the podspec.
