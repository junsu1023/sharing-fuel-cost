package com.jskim.sharingfuelcost.presentation.fuelcost

import com.jskim.sharingfuelcost.domain.fuel.AverageFuelPrice
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.fuel.MyCar
import com.jskim.sharingfuelcost.domain.fuel.VehicleFuelEconomy
import com.jskim.sharingfuelcost.domain.history.SettlementRecord
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
    data class MapPicker(val field: LocationField) : FuelCostScreen
    data object VehicleSearch : FuelCostScreen
    data object History : FuelCostScreen
    data class HistoryDetail(val createdAtMillis: Long) : FuelCostScreen
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

sealed interface VehicleSearchState {
    data object Idle : VehicleSearchState
    data object Loading : VehicleSearchState
    data class Results(val vehicles: List<VehicleFuelEconomy>) : VehicleSearchState
    data class Failed(val reason: LookupFailure) : VehicleSearchState
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
    val selectedVehicle: VehicleFuelEconomy? = null,
    val vehicleQuery: String = "",
    val vehicleSearch: VehicleSearchState = VehicleSearchState.Idle,
    val wonPerLiter: String = "",
    val fuelType: FuelType = FuelType.GASOLINE,
    val fuelPrice: FuelPriceState = FuelPriceState.Loading,
    /** 사용자가 고치지 않았으면 평균 유가로 채운다. */
    val wonPerLiterEdited: Boolean = false,
    val method: SettlementMethod = SettlementMethod.EQUAL,
    val totalDistanceKm: String = "",
    val distanceLookup: DistanceLookupState = DistanceLookupState.Idle,
    /** 거리 칸의 값이 조회 결과에서 왔는지. 직접 고쳐도 유지해, 경로가 바뀌면 이전 거리를 지운다. */
    val distanceFromLookup: Boolean = false,
    val searchQuery: String = "",
    val addressSearch: AddressSearchState = AddressSearchState.Idle,
    val mapPick: MapPickState = MapPickState.Idle,
    val currentLocation: CurrentLocationState = CurrentLocationState.Idle,
    /** 검색 결과를 고르면 지도를 이 위치로 옮긴다. */
    val mapCameraTarget: GeoPoint? = null,
    val result: SettlementResult? = null,
    val savedCar: MyCar? = null,
    /** 기록 화면을 열 때 읽는다. 읽기 전에는 null이다. */
    val history: List<SettlementRecord>? = null,
) {
    /** 출발지, 경유 순서대로의 탑승 위치, 목적지 */
    val routePlaces: List<String> get() = listOf(origin) + riders.map { it.pickup } + destination

    /** 지금 입력한 차. 연비가 유효할 때만 내 차로 저장할 수 있다. */
    val currentCar: MyCar?
        get() = kmPerLiter.toPositiveDecimalOrNull()?.let { MyCar(selectedVehicle, it, fuelType) }

    /** 12.5와 12.50처럼 표기만 다른 연비는 같은 차로 본다. */
    val isCurrentCarSaved: Boolean
        get() {
            val current = currentCar ?: return false
            val saved = savedCar ?: return false
            return current.vehicle == saved.vehicle &&
                current.fuelType == saved.fuelType &&
                current.kmPerLiter.compareTo(saved.kmPerLiter) == 0
        }

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
        get() = distanceLookup != DistanceLookupState.Loading &&
            origin.isNotBlank() &&
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
    data class OpenMapPicker(val field: LocationField) : FuelCostIntent
    data class SearchQueryChanged(val value: String) : FuelCostIntent
    data object SearchSubmitted : FuelCostIntent
    data class PlaceSelected(val place: Place) : FuelCostIntent
    /** 검색 없이 입력한 내용을 쓴다. 좌표가 없어 자동 거리 조회는 할 수 없다. */
    data object ConfirmAddress : FuelCostIntent
    data object OpenVehicleSearch : FuelCostIntent
    data class VehicleQueryChanged(val value: String) : FuelCostIntent
    data object VehicleSearchSubmitted : FuelCostIntent
    data class VehicleSelected(val vehicle: VehicleFuelEconomy) : FuelCostIntent
    data class MapCameraIdle(val point: GeoPoint) : FuelCostIntent
    data class MapPlaceSelected(val place: Place) : FuelCostIntent
    data class FuelTypeSelected(val type: FuelType) : FuelCostIntent
    data object ApplyAverageFuelPrice : FuelCostIntent
    data object SaveMyCar : FuelCostIntent
    data object ClearMyCar : FuelCostIntent
    data object OpenHistory : FuelCostIntent
    data class OpenRecord(val createdAtMillis: Long) : FuelCostIntent
    data class DeleteRecord(val createdAtMillis: Long) : FuelCostIntent
    data object MapPickConfirmed : FuelCostIntent
    /** 위치 권한을 받은 뒤 보낸다. */
    data object UseCurrentLocation : FuelCostIntent
    data object LocationPermissionDenied : FuelCostIntent
    data object Calculate : FuelCostIntent
    data object Back : FuelCostIntent
}
