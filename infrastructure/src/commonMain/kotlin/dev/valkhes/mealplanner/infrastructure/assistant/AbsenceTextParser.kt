package dev.valkhes.mealplanner.infrastructure.assistant

import dev.valkhes.mealplanner.domain.shared.textKey
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * Reads days away in English or French: "on part du 3 au 10", "absents du 22 au 30 novembre",
 * "away from the 28th to the 2nd", "holidays from 20 december to 2 january". Without a month, the
 * next such days are meant, unless the sentence is in the past ("on était absents du 3 au 10").
 */
internal class AbsenceTextParser {
    fun parse(
        sentence: String,
        today: LocalDate,
    ): Pair<LocalDate, LocalDate>? {
        val key = textKey(sentence)
        if (MARKERS.none { Regex("\\b$it\\b").containsMatchIn(key) }) return null
        val match = FRENCH.find(key) ?: ENGLISH.find(key) ?: return null
        val (d1, m1, d2, m2) = match.destructured
        val startDay = d1.toInt()
        val endDay = d2.toInt()
        val past = PAST.any { Regex("\\b$it\\b").containsMatchIn(key) }
        val start = startDate(startDay, month(m1) ?: month(m2)?.takeIf { startDay <= endDay }, today, past) ?: return null
        val end = endDate(start, endDay, month(m2)) ?: return null
        return if (end < start) null else start to end
    }

    private fun startDate(
        day: Int,
        month: Month?,
        today: LocalDate,
        past: Boolean,
    ): LocalDate? {
        if (month != null) {
            val thisYear = date(today.year, month, day) ?: return null
            // An explicit month means the nearest such date, in the past or the future.
            return if (thisYear < today.plus(DatePeriod(days = -HALF_YEAR))) date(today.year + 1, month, day) else thisYear
        }
        val thisMonth = date(today.year, today.month, day) ?: return null
        return if (thisMonth < today && !past) thisMonth.plus(DatePeriod(months = 1)).takeIf { it.day == day } else thisMonth
    }

    private fun endDate(
        start: LocalDate,
        day: Int,
        month: Month?,
    ): LocalDate? {
        if (month != null) {
            val sameYear = date(start.year, month, day) ?: return null
            return if (sameYear < start) date(start.year + 1, month, day) else sameYear
        }
        val sameMonth = date(start.year, start.month, day) ?: return null
        return if (sameMonth < start) sameMonth.plus(DatePeriod(months = 1)).takeIf { it.day == day } else sameMonth
    }

    private fun date(
        year: Int,
        month: Month,
        day: Int,
    ): LocalDate? =
        try {
            LocalDate(year, month, day)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun month(name: String): Month? = if (name.isEmpty()) null else MONTHS.entries.firstOrNull { (k, _) -> name.startsWith(k) }?.value

    private companion object {
        const val HALF_YEAR: Int = 183

        val MARKERS: List<String> =
            listOf(
                "absent",
                "absents",
                "absente",
                "absentes",
                "on part",
                "on partira",
                "partis",
                "pas la",
                "vacances",
                "en deplacement",
                "away",
                "holiday",
                "holidays",
                "vacation",
                "trip",
                "travelling",
                "traveling",
                "out of town",
            )

        val PAST: List<String> = listOf("etait", "etions", "etais", "etaient", "etiez", "were", "was")

        /** Prefixes of month names, accent-free, French then English; the longest prefix that matches wins. */
        val MONTHS: Map<String, Month> =
            linkedMapOf(
                "janv" to Month.JANUARY,
                "fevr" to Month.FEBRUARY,
                "mars" to Month.MARCH,
                "avr" to Month.APRIL,
                "mai" to Month.MAY,
                "juin" to Month.JUNE,
                "juil" to Month.JULY,
                "aout" to Month.AUGUST,
                "sept" to Month.SEPTEMBER,
                "oct" to Month.OCTOBER,
                "nov" to Month.NOVEMBER,
                "dec" to Month.DECEMBER,
                "jan" to Month.JANUARY,
                "feb" to Month.FEBRUARY,
                "mar" to Month.MARCH,
                "apr" to Month.APRIL,
                "may" to Month.MAY,
                "jun" to Month.JUNE,
                "jul" to Month.JULY,
                "aug" to Month.AUGUST,
                "sep" to Month.SEPTEMBER,
            )

        val MONTH_WORD: String =
            listOf(
                "janv\\w*",
                "fevr\\w*",
                "mars",
                "avr\\w*",
                "mai",
                "juin",
                "juil\\w*",
                "aout",
                "sept\\w*",
                "oct\\w*",
                "nov\\w*",
                "dec\\w*",
                "jan\\w*",
                "feb\\w*",
                "mar\\w*",
                "apr\\w*",
                "may",
                "jun\\w*",
                "jul\\w*",
                "aug\\w*",
                "sep\\w*",
            ).joinToString("|", prefix = "(", postfix = ")")

        val FRENCH: Regex = Regex("""\bdu (\d{1,2})(?:er)?(?: $MONTH_WORD)? (?:au|jusqu'au) (\d{1,2})(?:er)?(?: $MONTH_WORD)?""")

        /** "from the 28th to the 2nd", "from 20 december to 2 january". */
        val ENGLISH_DAY: String = "(?:the )?(\\d{1,2})(?:st|nd|rd|th)?(?: (?:of )?$MONTH_WORD)?"

        val ENGLISH: Regex = Regex("\\bfrom $ENGLISH_DAY (?:to|until|till) $ENGLISH_DAY")
    }
}
