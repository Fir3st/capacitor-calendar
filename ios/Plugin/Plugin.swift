import Foundation
import Capacitor
import EventKit

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(CapacitorCalendar)
public class CapacitorCalendar: CAPPlugin {
    
    let SEARCH_LIMIT_INTERVAL = 1000 * 24 * 60 * 60;
    
    let store = EKEventStore()
    
    @objc func createEvent(_ call: CAPPluginCall) {
        
        guard let title = call.getString("title"), !title.isEmpty else {
            let msg = "Must provide title property"
            print(msg)
            call.reject(msg)
            return
        }
        
        let location = call.getString("location") ?? ""
        let notes = call.getString("notes") ?? ""

        guard let startDate = call.getDouble("startDate"), startDate > 0 else {
           let msg = "Must provide startDate property"
           print(msg)
           call.reject(msg)
           return
       }
        
        guard let endDate = call.getDouble("endDate"), endDate > 0 else {
            let msg = "Must provide endDate property"
            print(msg)
            call.reject(msg)
            return
        }

       

        
        let eventStartDate = Date(timeIntervalSince1970: startDate / 1000);
        
        store.requestAccess(to: .event) { (accessGranted: Bool, error: Error?) in
            if accessGranted && error == nil {
                var calendar = self.store.defaultCalendarForNewEvents
                if let identifier = call.getString("calendarId") {
                    if let selectedCalendar = self.store.calendar(withIdentifier: identifier) {
                        calendar = selectedCalendar
                    }
                }

                let event = EKEvent.init(eventStore: self.store)
                event.title = title
                event.location = location
                event.notes = notes
                event.calendar = calendar
                event.startDate = eventStartDate

                if let allDay = call.getBool("allDay") {
                    event.endDate = Date(timeIntervalSince1970: endDate / 1000)
                    event.isAllDay = allDay
                } else {
                    let duration = Int(endDate - startDate);
                    let moduloDay = (duration / 1000) % (60 * 60 * 24);
                    if (moduloDay == 0) {
                        event.isAllDay = true;
                        event.endDate = Date(timeIntervalSince1970: (endDate / 1000) - 1)
                    } else {
                        event.endDate = Date(timeIntervalSince1970: endDate / 1000)
                    }
                }


                do {
                    try self.store.save(event, span: .thisEvent)
                    call.resolve()
                } catch let error as NSError {
                    let msg = "Failed to save event with error: \(error)"
                    print(msg)
                    call.reject(msg)
                    return
                }
            } else {
                let msg = "EK access denied: \(String(describing: error?.localizedDescription))"
                print(msg)
                call.reject(msg)
            }
        }
    }
    
    @objc func updateEvent(_ call: CAPPluginCall) {

        guard let id = call.getString("id"), !id.isEmpty else {
            let msg = "Must provide id property"
            print(msg)
            call.reject(msg)
            return
        }
        
         guard let title = call.getString("title"), !title.isEmpty else {
             let msg = "Must provide title property"
             print(msg)
             call.reject(msg)
             return
         }
         
         let location = call.getString("location") ?? ""
         let notes = call.getString("notes") ?? ""

         guard let startDate = call.getDouble("startDate"), startDate > 0 else {
            let msg = "Must provide startDate property"
            print(msg)
            call.reject(msg)
            return
        }
         
         guard let endDate = call.getDouble("endDate"), endDate > 0 else {
             let msg = "Must provide endDate property"
             print(msg)
             call.reject(msg)
             return
        }

        let eventStartDate = Date(timeIntervalSince1970: startDate / 1000);
         
        store.requestAccess(to: .event) { (accessGranted: Bool, error: Error?) in
            if accessGranted && error == nil {
                
                guard let event = self.store.event(withIdentifier: id) else {
                    let msg = "Event has not been found"
                    print(msg)
                    call.reject(msg)
                    return
                }
                
                event.title = title
                event.location = location
                event.notes = notes

                event.startDate = eventStartDate
                if let allDay = call.getBool("allDay") {
                    event.endDate = Date(timeIntervalSince1970: endDate / 1000)
                    event.isAllDay = allDay
                } else {
                    let duration = Int(endDate - startDate);
                    let moduloDay = (duration / 1000) % (60 * 60 * 24);
                    if (moduloDay == 0) {
                        event.isAllDay = true;
                        event.endDate = Date(timeIntervalSince1970: (endDate / 1000) - 1)
                    } else {
                        event.endDate = Date(timeIntervalSince1970: endDate / 1000)
                    }
                }

                do {
                    try self.store.save(event, span: .thisEvent)
                    call.resolve()
                } catch let error as NSError {
                    let msg = "Failed to save event with error: \(error)"
                    print(msg)
                    call.reject(msg)
                    return
                }
            } else {
                let msg = "EK access denied \(String(describing: error?.localizedDescription))"
                print(msg)
                call.reject(msg)
            }
        }
    }
    
    @objc func findEvent(_ call: CAPPluginCall) {
        var title = call.getString("title") ?? ""
        var location = call.getString("location") ?? ""
        var notes = call.getString("notes") ?? ""

        let startDate:Double = call.getDouble("startDate") ?? Date().addingTimeInterval(TimeInterval(-1 * self.SEARCH_LIMIT_INTERVAL)).timeIntervalSince1970 * 1000;
        
        let endDate:Double = call.getDouble("endDate") ?? Date().addingTimeInterval(TimeInterval(self.SEARCH_LIMIT_INTERVAL)).timeIntervalSince1970 * 1000
        
        let eventStartDate = Date(timeIntervalSince1970: startDate / 1000)
        let eventEndDate = Date(timeIntervalSince1970: endDate / 1000)
        
        store.requestAccess(to: .event) { (accessGranted: Bool, error: Error?) in
            if accessGranted && error == nil {
                let calendars = self.store.calendars(for: EKEntityType.event)
                let predicate = self.store.predicateForEvents(withStart: eventStartDate, end: eventEndDate, calendars: calendars)
                
                var predicateStrings = [String]();
                
                if (!title.isEmpty) {
                    title = title.replacingOccurrences(of: "'", with: "\\'")
                    predicateStrings.append(String(format: "title contains[c] '%@'", title))
                }
                
                if (!location.isEmpty) {
                    location = location.replacingOccurrences(of: "'", with: "\\'")
                    predicateStrings.append(String(format: "location contains[c] '%@'", location))
                }

                if (!notes.isEmpty) {
                    notes = notes.replacingOccurrences(of: "'", with: "\\'")
                    predicateStrings.append(String(format: "notes contains[c] '%@'", notes))
                }

                let predicateString = predicateStrings.joined(separator: " AND ")
                
                let datedEvents = NSArray(array: self.store.events(matching: predicate))
                                
                if (!predicateString.isEmpty) {
                    let matches = NSPredicate(format: predicateString)
                    let matchingEvents = datedEvents.filtered(using: matches)
                    
                    let events = matchingEvents.map {
                        (event: Any) -> [String: String?] in
                        
                        return [
                            "title": (event as! EKEvent).title,
                            "location": (event as! EKEvent).location,
                            "id": (event as! EKEvent).eventIdentifier,
                            "notes": (event as! EKEvent).notes,
                        ]
                    }
                    
                    call.resolve(["events": events])
                } else {
                    let events = datedEvents.map{
                        (event: Any) -> [String: String?] in
                        
                        return [
                            "title": (event as! EKEvent).title,
                            "location": (event as! EKEvent).location,
                            "id": (event as! EKEvent).eventIdentifier,
                            "notes": (event as! EKEvent).notes,
                        ]
                    }
                                          
                    call.resolve(["events": events])
                }

            } else {
                let msg = "EK access denied"
                print(msg)
                call.reject(msg)
            }
        }
    }

    @objc func deleteEventById(_ call: CAPPluginCall) {
        deleteEvent(call)
    }
    
    @objc func deleteEvent(_ call: CAPPluginCall) {
        guard let id = call.getString("id"), !id.isEmpty else {
            let error = "Must provide id property"
            call.reject(error)
            return
        }
        
        store.requestAccess(to: .event) { (accessGranted: Bool, error: Error?) in
            if accessGranted && error == nil {
                guard let event = self.store.event(withIdentifier: id) else {
                    let msg = "Event \(id) has not been found"
                    print(msg)
                    call.reject(msg)
                    return
                }
                
                do {
                    try self.store.remove(event, span: .thisEvent);
                    call.resolve()
                } catch let error as NSError {
                    let msg = "Failed to remove event with error: \(error)"
                    print(msg)
                    call.reject(msg)
                    return
                }
            } else {
                let msg = "EK access denied"
                print(msg)
                call.reject(msg)
            }
        }
    }
    
    @objc func openCalendar(_ call: CAPPluginCall) {
        guard let url = URL(string: "calshow://") else {
            call.reject("Unknown error")
            return
        }
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
        call.resolve()
    }
    
    @objc func getAvailableCalendars(_ call: CAPPluginCall) {
        store.requestAccess(to: .event) { (accessGranted: Bool, error: Error?) in
            if accessGranted && error == nil {
                let defaultCalendar = self.store.defaultCalendarForNewEvents
                
                var calendars = self.store.calendars(for: EKEntityType.event)
                    .filter { $0.calendarIdentifier != defaultCalendar?.calendarIdentifier }
                    .filter { $0.allowsContentModifications }
                    .map {[
                        "id": $0.calendarIdentifier,
                        "name": $0.title,
                        "displayName": $0.title,
                        "defaultCalendar": false,
                    ]}
                
                calendars.insert([
                    "id": defaultCalendar!.calendarIdentifier,
                    "name": defaultCalendar!.title,
                    "displayName": defaultCalendar!.title,
                    "defaultCalendar": true,
                ], at: 0)

                call.resolve(["availableCalendars": calendars]);
            } else {
                let msg = "EK access denied"
                print(msg)
                call.reject(msg)
            }
        }
    }

}
