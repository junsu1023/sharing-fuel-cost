package com.jskim.sharingfuelcost.data.history

import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.history.SettlementRecord
import com.jskim.sharingfuelcost.domain.settlement.ParticipantShare
import com.jskim.sharingfuelcost.domain.settlement.SettlementMethod
import com.jskim.sharingfuelcost.domain.settlement.SettlementResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SettlementRecordCodecTest {

    private val record = SettlementRecord(
        createdAtMillis = 1_762_170_000_000,
        places = listOf("강남역 2번 출구", "사당역", "속초해수욕장"),
        kmPerLiter = BigDecimal("12.50"),
        wonPerLiter = BigDecimal("1650"),
        result = SettlementResult(
            totalDistanceMeters = 250_000,
            fuelLiters = BigDecimal("20.0000"),
            totalCostWon = 33_000,
            method = SettlementMethod.DISTANCE_PROPORTIONAL,
            shares = listOf(ParticipantShare(null, 250_000, 1), ParticipantShare("민지", 240_000, 32_999)),
        ),
    )

    @Test
    fun `records survive encode and decode with driver without name and exact decimals`() {
        assertEquals(listOf(record), decodeRecords(encodeRecords(listOf(record))))
    }

    @Test
    fun `electric record keeps fuel type`() {
        val electric = record.copy(fuelType = FuelType.ELECTRIC)
        assertEquals(listOf(electric), decodeRecords(encodeRecords(listOf(electric))))
    }

    @Test
    fun `record saved before fuel type existed is read as gasoline`() {
        val stored = encodeRecords(listOf(record)).replace(""","fuelType":"GASOLINE"""", "")
        assertEquals(false, stored.contains("fuelType"))
        assertEquals(listOf(record), decodeRecords(stored))
    }

    @Test
    fun `broken stored value is treated as empty history`() {
        assertEquals(emptyList<SettlementRecord>(), decodeRecords("not json"))
    }

    @Test
    fun `record with unknown method is skipped and others are kept`() {
        val stored = encodeRecords(listOf(record, record.copy(createdAtMillis = 1)))
            .replaceFirst("DISTANCE_PROPORTIONAL", "UNKNOWN")
        assertEquals(listOf(record.copy(createdAtMillis = 1)), decodeRecords(stored))
    }
}
