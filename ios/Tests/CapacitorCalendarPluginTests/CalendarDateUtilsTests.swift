import XCTest
@testable import CapacitorCalendarPlugin

final class CalendarDateUtilsTests: XCTestCase {
    private let hourInMilliseconds: Double = 60 * 60 * 1000

    func testOneHourIsNotAllDay() {
        XCTAssertFalse(CalendarDateUtils.isAllDayEvent(
            startTimeMillis: 0,
            endTimeMillis: hourInMilliseconds
        ))
    }

    func testExactlyOneDayIsAllDay() {
        XCTAssertTrue(CalendarDateUtils.isAllDayEvent(
            startTimeMillis: 0,
            endTimeMillis: 24 * hourInMilliseconds
        ))
    }

    func testExactlyTwoDaysIsAllDay() {
        XCTAssertTrue(CalendarDateUtils.isAllDayEvent(
            startTimeMillis: 0,
            endTimeMillis: 48 * hourInMilliseconds
        ))
    }

    func testTwentyThreeHoursIsNotAllDay() {
        XCTAssertFalse(CalendarDateUtils.isAllDayEvent(
            startTimeMillis: 0,
            endTimeMillis: 23 * hourInMilliseconds
        ))
    }
}
