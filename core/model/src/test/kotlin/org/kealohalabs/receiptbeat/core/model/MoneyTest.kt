package org.kealohalabs.receiptbeat.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesCommonShapes() {
        assertEquals(1250L, Money.parse("12.50"))
        assertEquals(1250L, Money.parse("$12.50"))
        assertEquals(123450L, Money.parse("1,234.50"))
        assertEquals(850L, Money.parse("8,50"))
        assertEquals(123456L, Money.parse("1.234,56"))
        assertEquals(800L, Money.parse("8"))
        assertEquals(-500L, Money.parse("-5.00"))
        assertNull(Money.parse("total"))
    }

    @Test fun formatsAndSpeaks() {
        assertEquals("$42.50", Money.format(4250))
        assertEquals("42 dollars and 50 cents", Money.spoken(4250))
    }
}
