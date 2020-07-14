declare module "@capacitor/core" {
  interface PluginRegistry {
    CapacitorCalendar: CapacitorCalendarPlugin;
  }
}

export interface CalendarEventOptions {
  id?: string;
  title?: string;
  location?: string;
  notes?: string;
  startDate?: number;
  endDate?: number;
}

export interface DeleteEventOptions {
  [key: string]: string;
}

export interface CapacitorCalendarPlugin {
  createEvent(options: CalendarEventOptions): Promise<any>
  findEvent(options: CalendarEventOptions): Promise<any>
  deleteEvent(options: DeleteEventOptions): Promise<any>
  deleteEventById(options: { id: string }): Promise<any>
}
