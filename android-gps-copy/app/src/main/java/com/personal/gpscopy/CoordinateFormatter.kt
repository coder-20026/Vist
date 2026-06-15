package com.personal.gpscopy

import java.util.Locale

/**
 * Produces the EXACT output format required:  "22.1234,71.5628"
 *  - Latitude,Longitude
 *  - exactly 4 decimal places
 *  - no space after the comma
 *  - Locale.US so the decimal separator is always '.' (never ',')
 *
 * 4-decimal precision (~11 m) keeps the output consistent with the values
 * embedded by typical "GPS Camera" apps, so the same spot maps to the same point.
 */
object CoordinateFormatter {
    fun format(latitude: Double, longitude: Double): String {
        return String.format(Locale.US, "%.4f,%.4f", latitude, longitude)
    }
}
