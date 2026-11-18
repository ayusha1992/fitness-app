package com.healthcoach.ircoach.coach

import com.healthcoach.ircoach.coach.WeightCheck.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightCheckTest {
    private fun weigh(kg: Double, prev: Double?) = WeightCheck.weighIn(kg, prev, 1, prev, null, false, 0)

    @Test fun firstWeight() = assertEquals(Trend.FIRST, weigh(72.0, null).trend)

    @Test fun decreasePraises() {
        val r = weigh(71.5, 72.0)
        assertEquals(Trend.DECREASED, r.trend)
        assertTrue(r.message.contains("Great job"))
        assertTrue(r.message.contains("0.5 kg"))
    }

    @Test fun smallChangeIsSame() = assertEquals(Trend.SAME, weigh(72.05, 72.0).trend)

    @Test fun increaseStartsChecklist() {
        val r = weigh(72.6, 72.0)
        assertEquals(Trend.INCREASED, r.trend)
        assertEquals(0.6, r.delta, 1e-9)
    }

    @Test fun matchesAnswers() {
        val q = WeightCheck.QUESTIONS
        assertEquals("After 10 pm", WeightCheck.matchAnswer(q[0], "3"))
        assertEquals("After 10 pm", WeightCheck.matchAnswer(q[0], "after 10 pm"))
        assertEquals("Yes", WeightCheck.matchAnswer(q[1], "haan"))
        assertEquals("Not yet", WeightCheck.matchAnswer(q[5], "no"))
        assertEquals("Stressed", WeightCheck.matchAnswer(q[6], "stress"))
        assertNull(WeightCheck.matchAnswer(q[1], "what?"))
    }

    @Test fun conclusionListsCauses() {
        val c = WeightCheck.conclusion(mapOf("salt" to "Yes", "water" to "Under 1.5 L", "sleep" to "7 h or more"), 0.6)
        assertTrue(c.contains("salt"))
        assertTrue(c.contains("Too little water"))
        assertTrue(!c.contains("Short sleep"))
    }

    @Test fun conclusionNoCause() =
        assertTrue(WeightCheck.conclusion(emptyMap(), 0.3).contains("normal water fluctuation"))
}
