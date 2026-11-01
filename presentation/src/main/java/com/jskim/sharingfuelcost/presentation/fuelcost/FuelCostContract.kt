package com.jskim.sharingfuelcost.presentation.fuelcost

import com.jskim.sharingfuelcost.domain.settlement.SettlementMethod
import com.jskim.sharingfuelcost.domain.settlement.SettlementResult
import java.math.BigDecimal

sealed interface LocationField {
    data object Origin : LocationField
    data object Destination : LocationField
    data class RiderPickup(val riderId: Long) : LocationField
}

sealed interface FuelCostScreen {
    data object Input : FuelCostScreen
    data object Result : FuelCostScreen
    data class AddressSearch(val field: LocationField) : FuelCostScreen
    data class MapPicker(val riderId: Long) : FuelCostScreen
}

data class RiderInput(
    val id: Long,
    val name: String = "",
    val pickup: String = "",
    val distanceKm: String = "",
)

data class FuelCostUiState(
    val screen: FuelCostScreen = FuelCostScreen.Input,
    val origin: String = "",
    val destination: String = "",
    val driverName: String = "",
    val riders: List<RiderInput> = emptyList(),
    val kmPerLiter: String = "",
    val wonPerLiter: String = "",
    val method: SettlementMethod = SettlementMethod.EQUAL,
    val totalDistanceKm: String = "",
    val distanceLookupAttempted: Boolean = false,
    val searchQuery: String = "",
    val result: SettlementResult? = null,
) {
    val kmPerLiterInvalid: Boolean get() = isInvalidPositive(kmPerLiter)
    val wonPerLiterInvalid: Boolean get() = isInvalidPositive(wonPerLiter)
    val totalDistanceInvalid: Boolean get() = isInvalidPositive(totalDistanceKm)

    fun riderDistanceInvalid(rider: RiderInput): Boolean {
        if (isInvalidPositive(rider.distanceKm)) return true
        val distance = rider.distanceKm.toPositiveDecimalOrNull() ?: return false
        val total = totalDistanceKm.toPositiveDecimalOrNull() ?: return false
        return distance > total
    }

    val canCalculate: Boolean
        get() = origin.isNotBlank() &&
            destination.isNotBlank() &&
            driverName.isNotBlank() &&
            kmPerLiter.toPositiveDecimalOrNull() != null &&
            wonPerLiter.toPositiveDecimalOrNull() != null &&
            totalDistanceKm.toPositiveDecimalOrNull() != null &&
            riders.all {
                it.name.isNotBlank() &&
                    it.pickup.isNotBlank() &&
                    it.distanceKm.toPositiveDecimalOrNull() != null &&
                    !riderDistanceInvalid(it)
            }
}

internal fun String.toPositiveDecimalOrNull(): BigDecimal? =
    toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

private fun isInvalidPositive(value: String): Boolean =
    value.isNotEmpty() && value.toPositiveDecimalOrNull() == null

sealed interface FuelCostIntent {
    data class DriverNameChanged(val value: String) : FuelCostIntent
    data object AddRider : FuelCostIntent
    data class RemoveRider(val id: Long) : FuelCostIntent
    data class MoveRider(val id: Long, val offset: Int) : FuelCostIntent
    data class RiderNameChanged(val id: Long, val value: String) : FuelCostIntent
    data class RiderDistanceChanged(val id: Long, val value: String) : FuelCostIntent
    data class KmPerLiterChanged(val value: String) : FuelCostIntent
    data class WonPerLiterChanged(val value: String) : FuelCostIntent
    data class MethodSelected(val method: SettlementMethod) : FuelCostIntent
    data class TotalDistanceChanged(val value: String) : FuelCostIntent
    data object DistanceLookupClicked : FuelCostIntent
    data class OpenAddressSearch(val field: LocationField) : FuelCostIntent
    data class OpenMapPicker(val riderId: Long) : FuelCostIntent
    data class SearchQueryChanged(val value: String) : FuelCostIntent
    data object ConfirmAddress : FuelCostIntent
    data object Calculate : FuelCostIntent
    data object Back : FuelCostIntent
}
