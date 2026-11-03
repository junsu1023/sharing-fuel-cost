package com.jskim.sharingfuelcost.domain.history

import com.jskim.sharingfuelcost.domain.settlement.ParticipantShare
import com.jskim.sharingfuelcost.domain.settlement.SettlementMethod
import com.jskim.sharingfuelcost.domain.settlement.SettlementResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SettlementHistoryTest {

    private fun record(createdAt: Long, totalCostWon: Long = 33_000, kmPerLiter: String = "12.5") = SettlementRecord(
        createdAtMillis = createdAt,
        places = listOf("강남역", "사당역", "속초"),
        kmPerLiter = BigDecimal(kmPerLiter),
        wonPerLiter = BigDecimal("1650"),
        result = SettlementResult(
            totalDistanceMeters = 250_000,
            fuelLiters = BigDecimal("20"),
            totalCostWon = totalCostWon,
            method = SettlementMethod.EQUAL,
            shares = listOf(ParticipantShare(null, 250_000, 16_500), ParticipantShare("민지", 240_000, 16_500)),
        ),
    )

    @Test
    fun `new record goes first`() {
        val older = record(1, totalCostWon = 10_000)
        val newer = record(2)
        assertEquals(listOf(newer, older), addSettlementRecord(listOf(older), newer))
    }

    @Test
    fun `same calculation as latest record is not added again`() {
        val latest = record(1)
        assertEquals(listOf(latest), addSettlementRecord(listOf(latest), record(2)))
    }

    @Test
    fun `fuel economy written differently still counts as same calculation`() {
        val latest = record(1, kmPerLiter = "12.5")
        assertEquals(listOf(latest), addSettlementRecord(listOf(latest), record(2, kmPerLiter = "12.50")))
    }

    @Test
    fun `same calculation as an older record is added again`() {
        val first = record(1)
        val other = record(2, totalCostWon = 10_000)
        val again = record(3)
        assertEquals(listOf(again, other, first), addSettlementRecord(listOf(other, first), again))
    }

    @Test
    fun `only the latest 50 records are kept`() {
        val records = (MAX_SETTLEMENT_RECORDS downTo 1).map { record(it.toLong(), totalCostWon = it.toLong()) }
        val added = addSettlementRecord(records, record(100, totalCostWon = 100))
        assertEquals(MAX_SETTLEMENT_RECORDS, added.size)
        assertEquals(100L, added.first().createdAtMillis)
        assertEquals(2L, added.last().createdAtMillis)
    }
}
