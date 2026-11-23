package dev.valkhes.mealplanner.infrastructure

import dev.valkhes.mealplanner.application.assistant.InterpretContext
import dev.valkhes.mealplanner.application.assistant.Interpretation
import dev.valkhes.mealplanner.infrastructure.assistant.RuleInterpreter
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AbsenceInterpretationTest {
    private val today = LocalDate(2026, 11, 20)
    private val interpreter = RuleInterpreter()

    private suspend fun absence(text: String): Interpretation.Absence =
        assertIs<Interpretation.Absence>(interpreter.interpret(text, InterpretContext(emptyList(), today, hour = 12)), text)

    private fun d(
        month: Int,
        day: Int,
        year: Int = 2026,
    ): LocalDate = LocalDate(year, month, day)

    @Test
    fun french() =
        runTest {
            assertEquals(Interpretation.Absence(d(12, 3), d(12, 10)), absence("On part du 3 au 10"))
            assertEquals(Interpretation.Absence(d(11, 22), d(11, 30)), absence("absents du 22 au 30 novembre"))
            assertEquals(Interpretation.Absence(d(12, 20), d(1, 2, 2027)), absence("Vacances du 20 décembre au 2 janvier"))
            assertEquals(Interpretation.Absence(d(11, 28), d(12, 2)), absence("on part du 28 au 2"))
        }

    @Test
    fun english() =
        runTest {
            assertEquals(Interpretation.Absence(d(11, 28), d(12, 2)), absence("Away from the 28th to the 2nd"))
            assertEquals(Interpretation.Absence(d(12, 20), d(1, 2, 2027)), absence("holidays from 20 december to 2 january"))
        }

    @Test
    fun a_past_sentence_means_the_days_just_gone() =
        runTest {
            assertEquals(Interpretation.Absence(d(11, 3), d(11, 10)), absence("On était absents du 3 au 10"))
            assertEquals(Interpretation.Absence(d(11, 3), d(11, 10)), absence("we were away from the 3rd to the 10th"))
        }

    @Test
    fun without_a_marker_or_with_impossible_days_it_is_not_an_absence() =
        runTest {
            val context = InterpretContext(emptyList(), today, hour = 12)
            assertIs<Interpretation.Meal>(interpreter.interpret("du 3 au 10 des pâtes", context))
            assertIs<Interpretation.Meal>(interpreter.interpret("away from the 31st to the 2nd", context))
        }
}
