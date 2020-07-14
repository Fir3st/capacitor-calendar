import { WebPlugin } from '@capacitor/core';
import { CapacitorCalendarPlugin } from './definitions';

export class CapacitorCalendarWeb extends WebPlugin implements CapacitorCalendarPlugin {
  constructor() {
    super({
      name: 'CapacitorCalendar',
      platforms: ['web']
    });
  }

  async createEvent(...args: any): Promise<any> {
    console.log(args);
    throw new Error('Method is not implemented for web');
  }

  async findEvent(...args: any): Promise<any> {
    console.log(args);
    throw new Error('Method is not implemented for web');
  }

  async deleteEvent(...args: any): Promise<any> {
    console.log(args);
    throw new Error('Method is not implemented for web');
  }

  async deleteEventById(...args: any): Promise<any> {
    console.log(args);
    throw new Error('Method is not implemented for web');
  }
}

const CapacitorCalendar = new CapacitorCalendarWeb();

export { CapacitorCalendar };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(CapacitorCalendar);
