#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(CapacitorCalendar, "CapacitorCalendar",
           CAP_PLUGIN_METHOD(createEvent, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(findEvent, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(deleteEvent, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(deleteEventById, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(updateEvent, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(openCalendar, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getAvailableCalendars, CAPPluginReturnPromise);
)
