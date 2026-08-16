package com.workorder.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearProtocolTest {
    @Test
    fun entryRoundTripPreservesIdentityAndQuantity() {
        val source = WearEntryEventDto(
            eventId = "event-123",
            operationId = 42,
            operationName = "Сборка узла",
            quantity = 17,
            date = "2026-08-01",
            createdAt = 1_754_000_000_000
        )

        val restored = WearProtocol.decodeEntry(WearProtocol.encodeEntry(source))

        assertEquals(source, restored)
        assertEquals("/workorder/v1/entries/event-123", WearProtocol.entryPath(source.eventId))
    }

    @Test
    fun pathMatchingRequiresChildEventId() {
        assertTrue(WearProtocol.isEntryPath("/workorder/v1/entries/abc"))
        assertFalse(WearProtocol.isEntryPath("/workorder/v1/entries"))
        assertFalse(WearProtocol.isEntryPath("/other/entries/abc"))
    }
}
