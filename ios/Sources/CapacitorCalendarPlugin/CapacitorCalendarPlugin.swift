import Foundation
import Capacitor
import EventKit
import UIKit

@objc(CapacitorCalendarPlugin)
public class CapacitorCalendarPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "CapacitorCalendarPlugin"
    public let jsName = "CapacitorCalendar"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "createEvent", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "findEvent", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deleteEvent", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deleteEventById", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "updateEvent", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openCalendar", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getAvailableCalendars", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise)
    ]

    private let store = EKEventStore()
    private let searchLimitInterval: TimeInterval = 1000 * 24 * 60 * 60

    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        call.resolve(["calendar": calendarPermissionState()])
    }

    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        requestCalendarAccess { [weak self] _, _ in
            call.resolve(["calendar": self?.calendarPermissionState() ?? "denied"])
        }
    }

    @objc func createEvent(_ call: CAPPluginCall) {
        guard let title = call.getString("title"), !title.isEmpty else {
            call.reject("Must provide title property")
            return
        }

        guard let startDate = call.getDouble("startDate"), startDate > 0 else {
            call.reject("Must provide startDate property")
            return
        }

        guard let endDate = call.getDouble("endDate"), endDate > 0 else {
            call.reject("Must provide endDate property")
            return
        }

        let location = call.getString("location") ?? ""
        let notes = call.getString("notes") ?? ""

        requestCalendarAccess { [weak self] granted, error in
            guard let self = self else {
                return
            }
            guard granted, error == nil else {
                call.reject("Calendar access denied: \(error?.localizedDescription ?? "unknown error")")
                return
            }

            DispatchQueue.main.async {
                var calendar = self.store.defaultCalendarForNewEvents
                if let identifier = call.getString("calendarId"),
                   let selectedCalendar = self.store.calendar(withIdentifier: identifier) {
                    calendar = selectedCalendar
                }

                guard let eventCalendar = calendar else {
                    call.reject("No calendar available")
                    return
                }

                let event = EKEvent(eventStore: self.store)
                event.title = title
                event.location = location
                event.notes = notes
                event.calendar = eventCalendar
                event.startDate = Date(timeIntervalSince1970: startDate / 1000)
                self.applyEndDate(to: event, startDate: startDate, endDate: endDate, allDay: call.getBool("allDay"))

                do {
                    try self.store.save(event, span: .thisEvent)
                    call.resolve(["id": event.eventIdentifier ?? ""])
                } catch {
                    call.reject("Failed to save event: \(error.localizedDescription)", nil, error)
                }
            }
        }
    }

    @objc func updateEvent(_ call: CAPPluginCall) {
        guard let id = call.getString("id"), !id.isEmpty else {
            call.reject("Must provide id property")
            return
        }

        guard let title = call.getString("title"), !title.isEmpty else {
            call.reject("Must provide title property")
            return
        }

        guard let startDate = call.getDouble("startDate"), startDate > 0 else {
            call.reject("Must provide startDate property")
            return
        }

        guard let endDate = call.getDouble("endDate"), endDate > 0 else {
            call.reject("Must provide endDate property")
            return
        }

        let location = call.getString("location") ?? ""
        let notes = call.getString("notes") ?? ""

        requestCalendarAccess { [weak self] granted, error in
            guard let self = self else {
                return
            }
            guard granted, error == nil else {
                call.reject("Calendar access denied: \(error?.localizedDescription ?? "unknown error")")
                return
            }

            DispatchQueue.main.async {
                guard let event = self.store.event(withIdentifier: id) else {
                    call.reject("Event has not been found")
                    return
                }

                event.title = title
                event.location = location
                event.notes = notes
                event.startDate = Date(timeIntervalSince1970: startDate / 1000)
                self.applyEndDate(to: event, startDate: startDate, endDate: endDate, allDay: call.getBool("allDay"))

                do {
                    try self.store.save(event, span: .thisEvent)
                    call.resolve()
                } catch {
                    call.reject("Failed to save event: \(error.localizedDescription)", nil, error)
                }
            }
        }
    }

    @objc func findEvent(_ call: CAPPluginCall) {
        let startDate = call.getDouble("startDate")
            ?? Date().addingTimeInterval(-searchLimitInterval).timeIntervalSince1970 * 1000
        let endDate = call.getDouble("endDate")
            ?? Date().addingTimeInterval(searchLimitInterval).timeIntervalSince1970 * 1000

        requestCalendarAccess { [weak self] granted, error in
            guard let self = self else {
                return
            }
            guard granted, error == nil else {
                call.reject("Calendar access denied: \(error?.localizedDescription ?? "unknown error")")
                return
            }

            DispatchQueue.main.async {
                let calendars = self.store.calendars(for: .event)
                let predicate = self.store.predicateForEvents(
                    withStart: Date(timeIntervalSince1970: startDate / 1000),
                    end: Date(timeIntervalSince1970: endDate / 1000),
                    calendars: calendars
                )
                var events = self.store.events(matching: predicate)

                if let id = call.getString("id"), !id.isEmpty {
                    events = events.filter { $0.eventIdentifier == id }
                }
                if let title = call.getString("title"), !title.isEmpty {
                    events = events.filter { ($0.title ?? "").localizedCaseInsensitiveContains(title) }
                }
                if let location = call.getString("location"), !location.isEmpty {
                    events = events.filter { ($0.location ?? "").localizedCaseInsensitiveContains(location) }
                }
                if let notes = call.getString("notes"), !notes.isEmpty {
                    events = events.filter { ($0.notes ?? "").localizedCaseInsensitiveContains(notes) }
                }

                let result: [[String: Any]] = events.map { event in
                    [
                        "title": event.title ?? "",
                        "location": event.location ?? "",
                        "id": event.eventIdentifier ?? "",
                        "notes": event.notes ?? ""
                    ]
                }
                call.resolve(["events": result])
            }
        }
    }

    @objc func deleteEventById(_ call: CAPPluginCall) {
        deleteEvent(call)
    }

    @objc func deleteEvent(_ call: CAPPluginCall) {
        guard let id = call.getString("id"), !id.isEmpty else {
            call.reject("Must provide id property")
            return
        }

        requestCalendarAccess { [weak self] granted, error in
            guard let self = self else {
                return
            }
            guard granted, error == nil else {
                call.reject("Calendar access denied: \(error?.localizedDescription ?? "unknown error")")
                return
            }

            DispatchQueue.main.async {
                guard let event = self.store.event(withIdentifier: id) else {
                    call.reject("Event \(id) has not been found")
                    return
                }

                do {
                    try self.store.remove(event, span: .thisEvent)
                    call.resolve()
                } catch {
                    call.reject("Failed to remove event: \(error.localizedDescription)", nil, error)
                }
            }
        }
    }

    @objc func openCalendar(_ call: CAPPluginCall) {
        guard let url = URL(string: "calshow://") else {
            call.reject("Unable to create calendar URL")
            return
        }

        DispatchQueue.main.async {
            UIApplication.shared.open(url, options: [:]) { success in
                if success {
                    call.resolve()
                } else {
                    call.reject("Unable to open the calendar app")
                }
            }
        }
    }

    @objc func getAvailableCalendars(_ call: CAPPluginCall) {
        requestCalendarAccess { [weak self] granted, error in
            guard let self = self else {
                return
            }
            guard granted, error == nil else {
                call.reject("Calendar access denied: \(error?.localizedDescription ?? "unknown error")")
                return
            }

            DispatchQueue.main.async {
                let defaultCalendar = self.store.defaultCalendarForNewEvents
                let calendars = self.store.calendars(for: .event)
                    .filter { $0.allowsContentModifications }

                var result: [[String: Any]] = []
                if let defaultCalendar = defaultCalendar {
                    result.append(self.calendarDictionary(defaultCalendar, isDefault: true))
                }
                for calendar in calendars where calendar.calendarIdentifier != defaultCalendar?.calendarIdentifier {
                    result.append(self.calendarDictionary(calendar, isDefault: false))
                }

                call.resolve(["availableCalendars": result])
            }
        }
    }

    private func applyEndDate(to event: EKEvent, startDate: Double, endDate: Double, allDay: Bool?) {
        let end = Date(timeIntervalSince1970: endDate / 1000)

        if let allDay = allDay {
            event.isAllDay = allDay
            event.endDate = end
        } else if CalendarDateUtils.isAllDayEvent(startTimeMillis: startDate, endTimeMillis: endDate) {
            event.isAllDay = true
            event.endDate = end.addingTimeInterval(-1)
        } else {
            event.isAllDay = false
            event.endDate = end
        }
    }

    private func calendarDictionary(_ calendar: EKCalendar, isDefault: Bool) -> [String: Any] {
        [
            "id": calendar.calendarIdentifier,
            "name": calendar.title,
            "displayName": calendar.title,
            "defaultCalendar": isDefault
        ]
    }

    private func calendarPermissionState() -> String {
        let status = EKEventStore.authorizationStatus(for: .event)

        if #available(iOS 17.0, *) {
            switch status {
            case .fullAccess, .writeOnly:
                return "granted"
            case .notDetermined:
                return "prompt"
            case .restricted, .denied:
                return "denied"
            default:
                return "denied"
            }
        } else {
            switch status {
            case .authorized:
                return "granted"
            case .notDetermined:
                return "prompt"
            case .restricted, .denied:
                return "denied"
            default:
                return "denied"
            }
        }
    }

    private func requestCalendarAccess(completion: @escaping (Bool, Error?) -> Void) {
        if #available(iOS 17.0, *) {
            store.requestFullAccessToEvents { granted, error in
                completion(granted, error)
            }
        } else {
            store.requestAccess(to: .event) { granted, error in
                completion(granted, error)
            }
        }
    }
}
