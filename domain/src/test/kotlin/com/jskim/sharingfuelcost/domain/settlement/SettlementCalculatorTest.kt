package com.jskim.sharingfuelcost.domain.settlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SettlementCalculatorTest {

    private val calculator = SettlementCalculator()

    private fun input(
        totalMeters: Long = 35_000,
        kmPerLiter: String = "10",
        wonPerLiter: String = "1600",
        method: SettlementMethod = SettlementMethod.EQUAL,
        riders: List<Long> = listOf(25_000, 20_000),
    ) = SettlementInput(
        totalDistanceMeters = totalMeters,
        kmPerLiter = BigDecimal(kmPerLiter),
        wonPerLiter = BigDecimal(wonPerLiter),
        method = method,
        riders = riders.mapIndexed { index, meters -> RiderTrip("탑승자$index", meters) },
    )

    private fun success(input: SettlementInput) =
        (calculator(input) as SettlementOutcome.Success).result

    private fun failure(input: SettlementInput) =
        (calculator(input) as SettlementOutcome.Failure).error

    @Test
    fun `fuel and total cost follow the formula`() {
        val result = success(input())
        assertEquals(0, BigDecimal("3.5").compareTo(result.fuelLiters))
        assertEquals(5_600, result.totalCostWon)
    }

    @Test
    fun `decimal fuel economy and large distance are calculated exactly`() {
        val result = success(input(1_000_000, "12.5", "1650", riders = emptyList()))
        assertEquals(132_000, result.totalCostWon)
    }

    @Test
    fun `total cost rounds down below half won`() {
        assertEquals(3_333, success(input(10_000, "3", "1000", riders = emptyList())).totalCostWon)
    }

    @Test
    fun `total cost rounds up at or above half won`() {
        assertEquals(6_667, success(input(20_000, "3", "1000", riders = emptyList())).totalCostWon)
        assertEquals(1, success(input(1_000, "1", "0.5", riders = emptyList())).totalCostWon)
    }

    @Test
    fun `km converts to meters`() {
        assertEquals(12_346, kmToMeters(BigDecimal("12.3456")))
    }

    @Test
    fun `equal split without remainder`() {
        val result = success(input(riders = listOf(10_000)))
        assertEquals(listOf(2_800L, 2_800L), result.shares.map { it.amountWon })
    }

    @Test
    fun `equal split discards remainder`() {
        // 10km / 1km/L * 1000원/L = 10,000원
        val result = success(input(10_000, "1", "1000", riders = listOf(5_000, 5_000)))
        assertEquals(listOf(3_333L, 3_333L, 3_333L), result.shares.map { it.amountWon })
    }

    @Test
    fun `driver pays everything without riders`() {
        val result = success(input(riders = emptyList()))
        assertEquals(listOf(5_600L), result.shares.map { it.amountWon })
    }

    @Test
    fun `proportional riders pay own distance cost and driver pays the rest`() {
        // 100km, 10,000원. 탑승자 60km는 6,000원, 20km는 2,000원, 운전자는 나머지 2,000원
        val result = success(
            input(100_000, "10", "1000", SettlementMethod.DISTANCE_PROPORTIONAL, listOf(60_000, 20_000)),
        )
        assertEquals(listOf(2_000L, 6_000L, 2_000L), result.shares.map { it.amountWon })
        assertEquals(listOf(100_000L, 60_000L, 20_000L), result.shares.map { it.distanceMeters })
    }

    @Test
    fun `proportional rider riding the whole route pays everything`() {
        val result = success(input(method = SettlementMethod.DISTANCE_PROPORTIONAL, riders = listOf(35_000)))
        assertEquals(listOf(0L, 5_600L), result.shares.map { it.amountWon })
    }

    @Test
    fun `proportional without riders charges driver everything`() {
        val result = success(input(method = SettlementMethod.DISTANCE_PROPORTIONAL, riders = emptyList()))
        assertEquals(listOf(5_600L), result.shares.map { it.amountWon })
    }

    @Test
    fun `proportional driver takes won left by rider rounding down`() {
        // 30km, 10,000원. 20km는 6,666.67원 -> 6,666원, 10km는 3,333.33원 -> 3,333원, 운전자 1원
        val result = success(
            input(30_000, "3", "1000", SettlementMethod.DISTANCE_PROPORTIONAL, listOf(20_000, 10_000)),
        )
        assertEquals(listOf(1L, 6_666L, 3_333L), result.shares.map { it.amountWon })
    }

    @Test
    fun `proportional never collects more than total when rider distances exceed route`() {
        // 100km, 10,000원. 탑승자 합 170km이므로 10,000원을 90:80으로 나눈다.
        // 90/170 -> 5,294.11원 -> 5,294원, 80/170 -> 4,705.88원 -> 4,705원, 운전자 1원
        val result = success(
            input(100_000, "10", "1000", SettlementMethod.DISTANCE_PROPORTIONAL, listOf(90_000, 80_000)),
        )
        assertEquals(listOf(1L, 5_294L, 4_705L), result.shares.map { it.amountWon })
    }

    @Test
    fun `proportional shares always add up to total and are never negative`() {
        listOf(
            listOf(11_111L, 7_777L),
            listOf(33_333L, 33_333L, 33_333L),
            listOf(1L, 1L, 1L, 1L, 1L),
            emptyList(),
        ).forEach { riders ->
            val result = success(input(33_333, "7.7", "1777", SettlementMethod.DISTANCE_PROPORTIONAL, riders))
            assertEquals(result.totalCostWon, result.shares.sumOf { it.amountWon })
            assertTrue(result.shares.all { it.amountWon >= 0 })
        }
    }

    @Test
    fun `equal shares are below total by less than participant count`() {
        val result = success(input(33_333, "7.7", "1777", SettlementMethod.EQUAL, listOf(11_111, 7_777)))
        val difference = result.totalCostWon - result.shares.sumOf { it.amountWon }
        assertTrue(difference in 0 until result.shares.size)
    }

    @Test
    fun `invalid inputs fail`() {
        assertEquals(SettlementError.INVALID_FUEL_ECONOMY, failure(input(kmPerLiter = "0")))
        assertEquals(SettlementError.INVALID_FUEL_PRICE, failure(input(wonPerLiter = "-1")))
        assertEquals(SettlementError.INVALID_DISTANCE, failure(input(totalMeters = 0)))
        assertEquals(SettlementError.INVALID_DISTANCE, failure(input(riders = listOf(0))))
        assertEquals(SettlementError.RIDER_DISTANCE_EXCEEDS_TOTAL, failure(input(riders = listOf(35_001))))
    }
}
