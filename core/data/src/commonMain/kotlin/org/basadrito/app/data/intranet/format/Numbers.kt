package org.basadrito.app.data.intranet.format

import kotlin.math.round


fun formatOneDecimal(value: Double?): String {
    if (value == null || value.isNaN() || value.isInfinite()) return "-"
    val scaled = round(value * 10.0).toLong()
    val negative = scaled < 0
    val abs = if (negative) -scaled else scaled
    return (if (negative) "-" else "") + "${abs / 10}.${abs % 10}"
}

fun formatTwoDecimals(value: Double?): String {
    if (value == null || value.isNaN() || value.isInfinite()) return "-"
    val scaled = round(value * 100.0).toLong()
    val negative = scaled < 0
    val abs = if (negative) -scaled else scaled
    val frac = (abs % 100).toString().padStart(2, '0')
    return (if (negative) "-" else "") + "${abs / 100}.$frac"
}

fun String.toDoubleFlexible(): Double? = trim().replace(',', '.').toDoubleOrNull()

fun String.toFloatFlexible(): Float? = toDoubleFlexible()?.toFloat()
