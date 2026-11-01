package com.jskim.sharingfuelcost.domain.settlement

import java.math.BigDecimal

enum class SettlementMethod { EQUAL, DISTANCE_PROPORTIONAL }

data class RiderTrip(
    val name: String,
    val distanceMeters: Long,
)

data class SettlementInput(
    val totalDistanceMeters: Long,
    val kmPerLiter: BigDecimal,
    val wonPerLiter: BigDecimal,
    val method: SettlementMethod,
    val driverName: String,
    val riders: List<RiderTrip>,
)

data class ParticipantShare(
    val name: String,
    val distanceMeters: Long,
    val amountWon: Long,
)

data class SettlementResult(
    val totalDistanceMeters: Long,
    val fuelLiters: BigDecimal,
    val totalCostWon: Long,
    val method: SettlementMethod,
    val shares: List<ParticipantShare>,
)

enum class SettlementError {
    INVALID_FUEL_ECONOMY,
    INVALID_FUEL_PRICE,
    INVALID_DISTANCE,
    RIDER_DISTANCE_EXCEEDS_TOTAL,
}

sealed interface SettlementOutcome {
    data class Success(val result: SettlementResult) : SettlementOutcome
    data class Failure(val error: SettlementError) : SettlementOutcome
}
