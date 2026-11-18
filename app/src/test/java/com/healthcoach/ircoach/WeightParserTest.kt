package com.healthcoach.ircoach

import com.healthcoach.ircoach.coach.WeightParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightParserTest {
    @Test fun withUnit() = assertEquals(72.4, WeightParser.parse("72.4 kg", false)!!, 1e-9)
    @Test fun commaDecimal() = assertEquals(72.4, WeightParser.parse("72,4kg", false)!!, 1e-9)
    @Test fun pounds() = assertEquals(72.57, WeightParser.parse("160 lbs today", false)!!, 1e-9)
    @Test fun weightWord() = assertEquals(71.0, WeightParser.parse("my weight is 71", false)!!, 1e-9)
    @Test fun bareWhenExpecting() = assertEquals(70.8, WeightParser.parse("70.8", true)!!, 1e-9)
    @Test fun bareIgnoredOtherwise() = assertNull(WeightParser.parse("70.8", false))
    @Test fun outOfRange() = assertNull(WeightParser.parse("12 kg", false))
    @Test fun normalChat() = assertNull(WeightParser.parse("I had 2 rotis and dal for lunch", true))
    @Test fun longSentenceNotBare() = assertNull(WeightParser.parse("I walked for 45 minutes this morning and felt great", true))
}
