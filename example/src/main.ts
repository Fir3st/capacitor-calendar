import { CapacitorCalendar } from 'capacitor-calendar';

const output = document.querySelector<HTMLPreElement>('#log')!;

function log(label: string, value: unknown) {
  const text = value instanceof Error ? `${value.name}: ${value.message}` : JSON.stringify(value, null, 2);
  output.textContent += `\n[${new Date().toLocaleTimeString()}] ${label}\n${text}\n`;
}

async function run(label: string, action: () => Promise<unknown>) {
  try {
    log(label, await action());
  } catch (error) {
    log(`${label} failed`, error);
  }
}

function eventId(): string {
  return localStorage.getItem('calendarDemoEventId') ?? '';
}

function rememberEventId(id: string | undefined) {
  if (id) {
    localStorage.setItem('calendarDemoEventId', id);
  }
}

document.querySelector('#permissions')!.addEventListener('click', () =>
  run('checkPermissions', () => CapacitorCalendar.checkPermissions())
);

document.querySelector('#request')!.addEventListener('click', () =>
  run('requestPermissions', () => CapacitorCalendar.requestPermissions())
);

document.querySelector('#calendars')!.addEventListener('click', () =>
  run('getAvailableCalendars', () => CapacitorCalendar.getAvailableCalendars())
);

document.querySelector('#create')!.addEventListener('click', () =>
  run('createEvent', async () => {
    const now = Date.now();
    const result = await CapacitorCalendar.createEvent({
      title: 'Capacitor Calendar demo',
      location: 'Demo',
      notes: 'Created by the Capacitor Calendar demo app',
      startDate: now + 60 * 60 * 1000,
      endDate: now + 2 * 60 * 60 * 1000,
    });
    rememberEventId(result?.id);
    return result;
  })
);

document.querySelector('#find')!.addEventListener('click', () =>
  run('findEvent', () => CapacitorCalendar.findEvent({ title: 'Capacitor Calendar demo' }))
);

document.querySelector('#update')!.addEventListener('click', () =>
  run('updateEvent', async () => {
    const id = eventId();
    if (!id) {
      throw new Error('Create an event first');
    }
    const now = Date.now();
    return CapacitorCalendar.updateEvent({
      id,
      title: 'Capacitor Calendar demo (updated)',
      location: 'Demo',
      notes: 'Updated by the Capacitor Calendar demo app',
      startDate: now + 2 * 60 * 60 * 1000,
      endDate: now + 3 * 60 * 60 * 1000,
    });
  })
);

document.querySelector('#delete')!.addEventListener('click', () =>
  run('deleteEventById', async () => {
    const id = eventId();
    if (!id) {
      throw new Error('Create an event first');
    }
    const result = await CapacitorCalendar.deleteEventById({ id });
    localStorage.removeItem('calendarDemoEventId');
    return result;
  })
);

document.querySelector('#open')!.addEventListener('click', () =>
  run('openCalendar', () => CapacitorCalendar.openCalendar({ date: Date.now() }))
);
