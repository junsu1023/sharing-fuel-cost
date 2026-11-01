package com.jskim.sharingfuelcost.presentation.fuelcost

import com.jskim.sharingfuelcost.domain.fuel.AverageFuelPrice
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.route.GeoPoint
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.Place
import com.jskim.sharingfuelcost.domain.settlement.SettlementMethod
import com.jskim.sharingfuelcost.domain.settlement.SettlementResult
import java.math.BigDecimal

internal const val MAX_RIDERS = 5

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
    val pickupPoint: GeoPoint? = null,
)

sealed interface DistanceLookupState {
    data object Idle : DistanceLookupState
    data object Loading : DistanceLookupState
    data object Success : DistanceLookupState
    /** 직접 입력한 주소처럼 좌표가 없는 위치가 있어 조회할 수 없다. */
    data object MissingLocation : DistanceLookupState
    data class Failed(val reason: LookupFailure) : DistanceLookupState
}

sealed interface AddressSearchState {
    data object Idle : AddressSearchState
    data object Loading : AddressSearchState
    data class Results(val places: List<Place>) : AddressSearchState
    data class Failed(val reason: LookupFailure) : AddressSearchState
}

sealed interface MapPickState {
    data object Idle : MapPickState
    data object Loading : MapPickState
    data class Resolved(val place: Place) : MapPickState
    data object NoAddress : MapPickState
    data class Failed(val reason: LookupFailure) : MapPickState
}

sealed interface CurrentLocationState {
    data object Idle : CurrentLocationState
    data object Loading : CurrentLocationState
    data object Success : CurrentLocationState
    data object PermissionDenied : CurrentLocationState
    data class Failed(val reason: LookupFailure) : CurrentLocationState
}

sealed interface FuelPriceState {
    data object Loading : FuelPriceState
    data class Loaded(val prices: List<AverageFuelPrice>) : FuelPriceState
    data class Failed(val reason: LookupFailure) : FuelPriceState
}

data class FuelCostUiState(
    val screen: FuelCostScreen = FuelCostScreen.Input,
    val origin: String = "",
    val originPoint: GeoPoint? = null,
    val destination: String = "",
    val destinationPoint: GeoPoint? = null,
    val riders: List<RiderInput> = emptyList(),
    val kmPerLiter: String = "",
    val wonPerLiter: String = "",
    val fuelType: FuelType = FuelType.GASOLINE,
    val fuelPrice: FuelPriceState = FuelPriceState.Loading,
    /** 사용자가 고치지 않았으면 평균 유가로 채운다. */
    val wonPerLiterEdited: Boolean = false,
    val method: SettlementMethod = SettlementMethod.EQUAL,
    val totalDistanceKm: String = "",
    val distanceLookup: DistanceLookupState = DistanceLookupState.Idle,
    val searchQuery: String = "",
    val addressSearch: AddressSearchState = AddressSearchState.Idle,
    val mapPick: MapPickState = MapPickState.Idle,
    val currentLocation: CurrentLocationState = CurrentLocationState.Idle,
    /** 검색 결과를 고르면 지도를 이 위치로 옮긴다. */
    val mapCameraTarget: GeoPoint? = null,
    val result: SettlementResult? = null,
) {
    val kmPerLiterInvalid: Boolean get() = isInvalidPositive(kmPerLiter)
    val wonPerLiterInvalid: Boolean get() = isInvalidPositive(wonPerLiter)
    val totalDistanceInvalid: Boolean get() = isInvalidPositive(totalDistanceKm)
    val canAddRider: Boolean get() = riders.size < MAX_RIDERS

    val averageFuelPrice: AverageFuelPrice?
        get() = (fuelPrice as? FuelPriceState.Loaded)?.prices?.firstOrNull { it.type == fuelType }

    fun riderDistanceInvalid(rider: RiderInput): Boolean {
        if (isInvalidPositive(rider.distanceKm)) return true
        val distance = rider.distanceKm.toPositiveDecimalOrNull() ?: return false
        val total = totalDistanceKm.toPositiveDecimalOrNull() ?: return false
        return distance > total
    }

    val canCalculate: Boolean
        get() = origin.isNotBlank() &&
            destination.isNotBlank() &&
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
    data object SearchSubmitted : FuelCostIntent
    data class PlaceSelected(val place: Place) : FuelCostIntent
    /** 검색 없이 입력한 내용을 쓴다. 좌표가 없어 자동 거리 조회는 할 수 없다. */
    data object ConfirmAddress : FuelCostIntent
    data class MapCameraIdle(val point: GeoPoint) : FuelCostIntent
    data class MapPlaceSelected(val place: Place) : FuelCostIntent
    data class FuelTypeSelected(val type: FuelType) : FuelCostIntent
    data object ApplyAverageFuelPrice : FuelCostIntent
    data object MapPickConfirmed : FuelCostIntent
    /** 위치 권한을 받은 뒤 보낸다. */
    data object UseCurrentLocation : FuelCostIntent
    data object LocationPermissionDenied : FuelCostIntent
    data object Calculate : FuelCostIntent
    data object Back : FuelCostIntent
}
