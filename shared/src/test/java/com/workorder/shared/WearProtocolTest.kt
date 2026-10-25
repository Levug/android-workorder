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

    @Test
    fun quickAmountsAreOptionalForOlderPhones() {
        val legacy = """{"schemaVersion":1,"updatedAt":5,"operations":[]}"""
        val catalog = WearProtocol.decodeCatalog(legacy.encodeToByteArray())
        assertEquals(WearProtocol.DEFAULT_QUICK_AMOUNTS, WearProtocol.quickAmounts(catalog))

        val current = WearCatalogDto(updatedAt = 6, operations = emptyList(), quickAmounts = listOf(2, 4, 8))
        val decoded = WearProtocol.decodeCatalog(WearProtocol.encodeCatalog(current))
        assertEquals(current, decoded)
        assertEquals(listOf(2, 4, 8), WearProtocol.quickAmounts(decoded))
        assertEquals(WearProtocol.DEFAULT_QUICK_AMOUNTS, WearProtocol.quickAmounts(current.copy(quickAmounts = listOf(1, 1))))
        assertEquals(WearProtocol.DEFAULT_QUICK_AMOUNTS, WearProtocol.quickAmounts(current.copy(quickAmounts = emptyList())))
    }

    @Test
    fun watchStatusRoundTrips() {
        val status = WearWatchStatusDto(appVersion = "3.5-beta", catalogUpdatedAt = 7, pendingEvents = 2, reportedAt = 8)
        assertEquals(status, WearProtocol.decodeWatchStatus(WearProtocol.encodeWatchStatus(status)))
    }

    @Test
    fun watchAcceptsUpToSixQuickAmounts() {
        val six = WearCatalogDto(updatedAt = 1, operations = emptyList(), quickAmounts = listOf(1, 2, 5, 10, 17, 25))
        assertEquals(listOf(1, 2, 5, 10, 17, 25), WearProtocol.quickAmounts(six))
        assertEquals(WearProtocol.DEFAULT_QUICK_AMOUNTS, WearProtocol.quickAmounts(six.copy(quickAmounts = (1..7).toList())))
    }
}
