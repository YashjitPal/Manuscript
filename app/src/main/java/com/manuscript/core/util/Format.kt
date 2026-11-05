package com.manuscript.core.util

import com.manuscript.core.model.NDate
import com.manuscript.core.model.parseInstant
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

object Fmt {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dayMonth = DateTimeFormatter.ofPattern("MMM d")
    private val dayMonthYear = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val weekday = DateTimeFormatter.ofPattern("EEEE")

    fun relative(iso: String?, now: Instant = Instant.now()): String {
        val instant = parseInstant(iso) ?: return ""
        val d = Duration.between(instant, now)
        return when {
            d.isNegative -> absolute(instant)
            d.toMinutes() < 1 -> "Just now"
            d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
            d.toHours() < 24 -> "${d.toHours()}h ago"
            d.toDays() < 2 -> "Yesterday"
            d.toDays() < 7 -> "${d.toDays()}d ago"
            else -> absolute(instant)
        }
    }

    fun absolute(instant: Instant): String {
        val date = instant.atZone(zone).toLocalDate()
        return if (date.year == LocalDate.now().year) dayMonth.format(date) else dayMonthYear.format(date)
    }

    fun dateTime(iso: String?): String {
        val instant = parseInstant(iso) ?: return ""
        val z = instant.atZone(zone)
        return "${dayMonthYear.format(z)} at ${time.format(z)}"
    }

    fun friendlyDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        in 2L..6L -> weekday.format(date)
        else -> if (date.year == today.year) dayMonth.format(date) else dayMonthYear.format(date)
    }

    fun date(d: NDate?, relativeDays: Boolean = true): String {
        if (d == null) return ""
        val tz = d.timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
        fun one(value: String): String {
            if (value.length <= 10) {
                val ld = NDate.parseDate(value) ?: return value
                return if (relativeDays) friendlyDay(ld) else dayMonthYear.format(ld)
            }
            val z = NDate.parseDateTime(value, tz) ?: return value
            val day = if (relativeDays) friendlyDay(z.toLocalDate()) else dayMonthYear.format(z)
            return "$day ${time.format(z)}"
        }
        val start = one(d.start)
        return d.end?.let { "$start → ${one(it)}" } ?: start
    }

    fun greeting(now: java.time.LocalTime = java.time.LocalTime.now()): String = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }

    private val currencyByFormat = mapOf(
        "dollar" to "USD", "canadian_dollar" to "CAD", "singapore_dollar" to "SGD", "euro" to "EUR", "pound" to "GBP",
        "yen" to "JPY", "ruble" to "RUB", "rupee" to "INR", "won" to "KRW", "yuan" to "CNY", "real" to "BRL",
        "lira" to "TRY", "rupiah" to "IDR", "franc" to "CHF", "hong_kong_dollar" to "HKD", "new_zealand_dollar" to "NZD",
        "krona" to "SEK", "norwegian_krone" to "NOK", "mexican_peso" to "MXN", "rand" to "ZAR", "new_taiwan_dollar" to "TWD",
        "danish_krone" to "DKK", "zloty" to "PLN", "baht" to "THB", "forint" to "HUF", "koruna" to "CZK",
        "shekel" to "ILS", "chilean_peso" to "CLP", "philippine_peso" to "PHP", "dirham" to "AED",
        "colombian_peso" to "COP", "riyal" to "SAR", "ringgit" to "MYR", "leu" to "RON", "argentine_peso" to "ARS",
        "uruguayan_peso" to "UYU", "peruvian_sol" to "PEN", "australian_dollar" to "AUD",
    )

    val numberFormats: List<Pair<String, String>> = listOf(
        "number" to "Number", "number_with_commas" to "Number with commas", "percent" to "Percent",
    ) + currencyByFormat.keys.map { key ->
        key to key.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    fun number(value: Double?, format: String?): String {
        if (value == null) return ""
        return when (format) {
            null, "number" -> plainNumber(value)
            "number_with_commas" -> NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 8 }.format(value)
            "percent" -> NumberFormat.getPercentInstance().apply { maximumFractionDigits = 2 }.format(value)
            else -> currencyByFormat[format]?.let { code ->
                runCatching {
                    NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(code) }.format(value)
                }.getOrNull()
            } ?: plainNumber(value)
        }
    }

    fun plainNumber(value: Double): String =
        if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) value.toLong().toString()
        else value.toBigDecimal().stripTrailingZeros().toPlainString()

    fun bytes(size: Long): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
        size < 1024L * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024))
        else -> "%.2f GB".format(size / (1024.0 * 1024 * 1024))
    }

    fun duration(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val hours = total / 3600
        return if (hours > 0) "%d:%02d:%02d".format(hours, (total / 60) % 60, total % 60)
        else "%d:%02d".format(total / 60, total % 60)
    }
}
