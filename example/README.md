# Capacitor Calendar example

A small Vite app that exercises every method of the plugin:

- `checkPermissions` / `requestPermissions`
- `getAvailableCalendars`
- `createEvent`
- `findEvent`
- `updateEvent`
- `deleteEventById`
- `openCalendar`

## Setup

Build the plugin first, then install the example dependencies:

```bash
cd ..
npm install
npm run build

cd example
npm install
npm run build
npx cap sync
```

## Run

```bash
npx cap open ios
# Select a signing team, then run on a device or simulator.

npx cap open android
# Run on an emulator or a connected device.
```

The iOS project already contains `NSCalendarsUsageDescription` and
`NSCalendarsFullAccessUsageDescription`. Android permissions are merged from
the plugin manifest.

The plugin is not implemented on the web, so permission prompts and calendar
operations only work in the native apps.
