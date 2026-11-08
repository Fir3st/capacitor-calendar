import { CapacitorCalendar } from '../src';

import type {
  CalendarEventOptions,
  CreateEventOptions,
  CreateEventResult,
  UpdateEventOptions,
} from '../src';

const event = {
  title: 'Launch party',
  startDate: 1,
  endDate: 2,
};

const createOptions: CreateEventOptions = event;
void createOptions;

void CapacitorCalendar.createEvent(event);

// @ts-expect-error createEvent does not accept an id
const createOptionsWithId: CreateEventOptions = { ...event, id: 'abc' };
void createOptionsWithId;

// @ts-expect-error createEvent does not accept an id
void CapacitorCalendar.createEvent({ ...event, id: 'abc' });

const updateOptions: UpdateEventOptions = { ...event, id: 'abc' };
void updateOptions;

// @ts-expect-error updateEvent requires an id
const updateOptionsWithoutId: UpdateEventOptions = event;
void updateOptionsWithoutId;

// @ts-expect-error updateEvent requires an id
void CapacitorCalendar.updateEvent(event);

void CapacitorCalendar.updateEvent({ ...event, id: 'abc' });

const findOptions: CalendarEventOptions = { id: 'abc' };
void findOptions;

void CapacitorCalendar.findEvent({ id: 'abc' });

// @ts-expect-error createEvent does not accept options with an id
void CapacitorCalendar.createEvent(findOptions);

async function getCreatedEventId(): Promise<string> {
  const result: CreateEventResult = await CapacitorCalendar.createEvent(event);
  return result.id;
}

void getCreatedEventId;
