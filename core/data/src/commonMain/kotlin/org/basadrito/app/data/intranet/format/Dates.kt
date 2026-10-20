
package org.basadrito.app.data.intranet.format

private val MONTH_DAYS = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

fun dayOfWeekIndex(isoDate: String): Int {
    val (y, m, d) = parseIsoDate(isoDate) ?: return -1
    return ((toJulianDay(y, m, d) + 1) % 7).toInt()
}

fun weekdayNameEs(index: Int): String = when (index) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    0 -> "Domingo"
    else -> ""
}

fun weekdayNameEs(isoDate: String): String = weekdayNameEs(dayOfWeekIndex(isoDate))

fun parseIsoDate(isoDate: String): Triple<Int, Int, Int>? {
    if (isoDate.length < 10) return null
    val y = isoDate.substring(0, 4).toIntOrNull() ?: return null
    val m = isoDate.substring(5, 7).toIntOrNull() ?: return null
    val d = isoDate.substring(8, 10).toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    return Triple(y, m, d)
}

fun addDays(isoDate: String, days: Int): String {
    val (y, m, d) = parseIsoDate(isoDate) ?: return isoDate
    val (ny, nm, nd) = fromJulianDay(toJulianDay(y, m, d) + days)
    return formatIsoDate(ny, nm, nd)
}

fun isoWeekRange(isoDate: String): Pair<String, String> {
    val index = dayOfWeekIndex(isoDate)
    if (index < 0) return isoDate to isoDate
    // Lunes = índice 1. Cuántos días retroceder para llegar al lunes:
    val backToMonday = if (index == 0) 6 else index - 1
    val monday = addDays(isoDate, -backToMonday)
    return monday to addDays(monday, 6)
}

fun formatIsoDate(year: Int, month: Int, day: Int): String =
    "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

fun isoTimePart(isoDateTime: String): String =
    if (isoDateTime.length >= 16) isoDateTime.substring(11, 16) else ""

fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

fun daysInMonth(year: Int, month: Int): Int =
    if (month == 2 && isLeapYear(year)) 29 else MONTH_DAYS.getOrElse(month - 1) { 30 }

internal fun toJulianDay(year: Int, month: Int, day: Int): Long {
    val a = (14 - month) / 12
    val y = year + 4800 - a
    val m = month + 12 * a - 3
    return day + (153L * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045
}

internal fun fromJulianDay(jdn: Long): Triple<Int, Int, Int> {
    val a = jdn + 32044
    val b = (4 * a + 3) / 146097
    val c = a - (146097 * b) / 4
    val d = (4 * c + 3) / 1461
    val e = c - (1461 * d) / 4
    val m = (5 * e + 2) / 153
    val day = (e - (153 * m + 2) / 5 + 1).toInt()
    val month = (m + 3 - 12 * (m / 10)).toInt()
    val year = (100 * b + d - 4800 + m / 10).toInt()
    return Triple(year, month, day)
}
