package com.jskim.sharingfuelcost.domain.settlement

import java.math.BigDecimal
import java.math.RoundingMode

private val METERS_PER_KM = BigDecimal(1000)

fun kmToMeters(km: BigDecimal): Long =
    km.multiply(METERS_PER_KM).setScale(0, RoundingMode.HALF_UP).toLong()

class SettlementCalculator {

    operator fun invoke(input: SettlementInput): SettlementOutcome {
        validate(input)?.let { return SettlementOutcome.Failure(it) }

        val liters = BigDecimal(input.totalDistanceMeters)
            .divide(METERS_PER_KM.multiply(input.kmPerLiter), 10, RoundingMode.HALF_UP)
        val totalCostWon = BigDecimal(input.totalDistanceMeters)
            .multiply(input.wonPerLiter)
            .divide(METERS_PER_KM.multiply(input.kmPerLiter), 0, RoundingMode.HALF_UP)
            .toLong()

        val shares = when (input.method) {
            SettlementMethod.EQUAL -> equalShares(input, totalCostWon)
            SettlementMethod.DISTANCE_PROPORTIONAL -> driverResidualShares(input, totalCostWon)
        }

        return SettlementOutcome.Success(
            SettlementResult(input.totalDistanceMeters, liters, totalCostWon, input.method, shares),
        )
    }

    /** 1원 미만은 버리고, 남는 금액은 누구에게도 배정하지 않는다. (D-1) */
    private fun equalShares(input: SettlementInput, totalCostWon: Long): List<ParticipantShare> {
        val amount = totalCostWon / (input.riders.size + 1)
        return listOf(ParticipantShare(null, input.totalDistanceMeters, amount)) +
            input.riders.map { ParticipantShare(it.name, it.distanceMeters, amount) }
    }

    /**
     * 탑승자는 자기 이동 거리의 기름값을 전액 내고, 운전자는 나머지를 낸다.
     * 탑승자 거리 합이 전체 거리보다 길면 실제 기름값보다 더 걷지 않도록 전체 기름값을 탑승자 거리 비율로 나눈다.
     */
    private fun driverResidualShares(input: SettlementInput, totalCostWon: Long): List<ParticipantShare> {
        val basisMeters = maxOf(input.totalDistanceMeters, input.riders.sumOf { it.distanceMeters })
        val riderShares = input.riders.map {
            ParticipantShare(it.name, it.distanceMeters, totalCostWon * it.distanceMeters / basisMeters)
        }
        val driverAmount = totalCostWon - riderShares.sumOf { it.amountWon }
        return listOf(ParticipantShare(null, input.totalDistanceMeters, driverAmount)) + riderShares
    }

    private fun validate(input: SettlementInput): SettlementError? = when {
        input.kmPerLiter.signum() <= 0 -> SettlementError.INVALID_FUEL_ECONOMY
        input.wonPerLiter.signum() <= 0 -> SettlementError.INVALID_FUEL_PRICE
        input.totalDistanceMeters <= 0 || input.riders.any { it.distanceMeters <= 0 } ->
            SettlementError.INVALID_DISTANCE
        input.riders.any { it.distanceMeters > input.totalDistanceMeters } ->
            SettlementError.RIDER_DISTANCE_EXCEEDS_TOTAL
        else -> null
    }
}
