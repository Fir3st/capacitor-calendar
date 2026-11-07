import Foundation

enum CalendarDateUtils {
    private static let dayInMilliseconds: Int64 = 24 * 60 * 60 * 1000

    static func isAllDayEvent(startTimeMillis: Double, endTimeMillis: Double) -> Bool {
        let start = Int64(startTimeMillis.rounded())
        let end = Int64(endTimeMillis.rounded())
        return (end - start) % dayInMilliseconds == 0
    }
}
