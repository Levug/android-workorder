package com.workorder.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    private val nbsp = '\u00A0'

    @Test fun shortWordsAndUnitsAreGluedToTheirNeighbours() {
        assertEquals("Оплата по${nbsp}графику 5/2 или${nbsp}2/2", "Оплата по графику 5/2 или 2/2".noOrphans())
        assertEquals("Обычная оплата и${nbsp}в${nbsp}выходной", "Обычная оплата и в выходной".noOrphans())
        assertEquals("Основа: 209,5${nbsp}ч за${nbsp}месяц ×${nbsp}1,15", "Основа: 209,5 ч за месяц × 1,15".noOrphans())
        assertEquals("есть${nbsp}16${nbsp}ч", "есть 16 ч".noOrphans())
        assertEquals("Записи${nbsp}— в${nbsp}наряд", "Записи — в наряд".noOrphans())
    }

    @Test fun longWordsStayBreakable() {
        assertEquals("Выработка выросла сегодня", "Выработка выросла сегодня".noOrphans())
        assertEquals("", "".noOrphans())
    }
}
