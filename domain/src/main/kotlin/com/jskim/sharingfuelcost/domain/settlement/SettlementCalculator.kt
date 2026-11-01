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

        val distances = listOf<Pair<String?, Long>>(null to input.totalDistanceMeters) +
            input.riders.map { it.name to it.distanceMeters }
        val distanceSum = distances.sumOf { it.second }

        // 1원 미만은 버리고, 남는 금액은 누구에게도 배정하지 않는다. (D-1)
        val shares = distances.map { (name, meters) ->
            val amount = when (input.method) {
                SettlementMethod.EQUAL -> totalCostWon / distances.size
                SettlementMethod.DISTANCE_PROPORTIONAL -> totalCostWon * meters / distanceSum
            }
            ParticipantShare(name, meters, amount)
        }

        return SettlementOutcome.Success(
            SettlementResult(input.totalDistanceMeters, liters, totalCostWon, input.method, shares),
        )
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
