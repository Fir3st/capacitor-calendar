declare module "@capacitor/core" {
  interface PluginRegistry {
    CapacitorCalendar: CapacitorCalendarPlugin;
  }
}

export interface CapacitorCalendarPlugin {
  createEvent(options: { title?: string, location?: string, notes?: string, startDate?: number, endDate?: number }): Promise<any>
}
