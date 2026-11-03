package com.jskim.sharingfuelcost.presentation.fuelcost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jskim.sharingfuelcost.domain.fuel.FuelPriceRepository
import com.jskim.sharingfuelcost.domain.fuel.MyCar
import com.jskim.sharingfuelcost.domain.fuel.MyCarRepository
import com.jskim.sharingfuelcost.domain.fuel.VehicleFuelEconomy
import com.jskim.sharingfuelcost.domain.fuel.VehicleRepository
import com.jskim.sharingfuelcost.domain.route.CurrentLocationRepository
import com.jskim.sharingfuelcost.domain.route.GeoPoint
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.LookupResult
import com.jskim.sharingfuelcost.domain.route.Place
import com.jskim.sharingfuelcost.domain.route.PlaceSearchRepository
import com.jskim.sharingfuelcost.domain.route.RouteDistanceRepository
import com.jskim.sharingfuelcost.domain.settlement.RiderTrip
import com.jskim.sharingfuelcost.domain.settlement.SettlementCalculator
import com.jskim.sharingfuelcost.domain.settlement.SettlementInput
import com.jskim.sharingfuelcost.domain.settlement.SettlementOutcome
import com.jskim.sharingfuelcost.domain.settlement.kmToMeters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.abs

private const val SEARCH_DEBOUNCE_MILLIS = 300L

/** 카메라 좌표에 생기는 작은 오차를 무시하는 범위(약 20m) */
private const val SAME_POINT_DEGREES = 0.0002

@HiltViewModel
class FuelCostViewModel @Inject constructor(
    private val calculator: SettlementCalculator,
    private val routeDistanceRepository: RouteDistanceRepository,
    private val placeSearchRepository: PlaceSearchRepository,
    private val currentLocationRepository: CurrentLocationRepository,
    private val fuelPriceRepository: FuelPriceRepository,
    private val vehicleRepository: VehicleRepository,
    private val myCarRepository: MyCarRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FuelCostUiState())
    val uiState: StateFlow<FuelCostUiState> = _uiState.asStateFlow()

    private var nextRiderId = 0L
    private var lookupJob: Job? = null
    private var searchJob: Job? = null
    private var mapJob: Job? = null
    private var locationJob: Job? = null
    private var vehicleJob: Job? = null

    /** 검색으로 고른 장소. 카메라 이동이 끝나도 이름을 주소로 덮어쓰지 않게 기억한다. */
    private var pendingMapPlace: Place? = null

    init {
        viewModelScope.launch {
            val result = fuelPriceRepository.averagePrices()
            _uiState.update { state ->
                when (result) {
                    is LookupResult.Success ->
                        state.copy(fuelPrice = FuelPriceState.Loaded(result.value)).withAveragePriceIfNotEdited()
                    is LookupResult.Failure -> state.copy(fuelPrice = FuelPriceState.Failed(result.reason))
                }
            }
        }
        viewModelScope.launch {
            val car = myCarRepository.load() ?: return@launch
            _uiState.update { state ->
                // 불러오기 전에 사용자가 연비를 입력했으면 덮어쓰지 않는다.
                if (state.kmPerLiter.isNotEmpty() || state.selectedVehicle != null) {
                    state.copy(savedCar = car)
                } else {
                    state.withCar(car).copy(savedCar = car)
                }
            }
        }
    }

    fun onIntent(intent: FuelCostIntent) {
        when (intent) {
            FuelCostIntent.DistanceLookupClicked -> lookupDistances()
            FuelCostIntent.SearchSubmitted -> searchPlaces()
            is FuelCostIntent.SearchQueryChanged -> onSearchQueryChanged(intent.value)
            is FuelCostIntent.VehicleQueryChanged -> onVehicleQueryChanged(intent.value)
            FuelCostIntent.VehicleSearchSubmitted -> searchVehicles()
            is FuelCostIntent.VehicleSelected -> selectVehicle(intent.vehicle)
            is FuelCostIntent.MapPlaceSelected -> selectMapPlace(intent.place)
            FuelCostIntent.AddRider -> if (_uiState.value.canAddRider) {
                val rider = RiderInput(nextRiderId++)
                updateRoute { it.copy(riders = it.riders + rider) }
            }
            is FuelCostIntent.RemoveRider ->
                updateRoute { state -> state.copy(riders = state.riders.filterNot { it.id == intent.id }) }
            is FuelCostIntent.MoveRider ->
                updateRoute { it.copy(riders = it.riders.moved(intent.id, intent.offset)) }
            is FuelCostIntent.PlaceSelected ->
                applySearchedLocation(intent.place.name, intent.place.point)
            FuelCostIntent.ConfirmAddress -> {
                val address = _uiState.value.searchQuery.trim()
                if (address.isNotEmpty()) applySearchedLocation(address, null)
            }
            is FuelCostIntent.MapCameraIdle -> resolveMapPoint(intent.point)
            FuelCostIntent.MapPickConfirmed -> confirmMapPick()
            FuelCostIntent.UseCurrentLocation -> useCurrentLocation()
            FuelCostIntent.SaveMyCar -> {
                val car = _uiState.value.currentCar ?: return
                _uiState.update { it.copy(savedCar = car) }
                viewModelScope.launch { myCarRepository.save(car) }
            }
            FuelCostIntent.ClearMyCar -> {
                _uiState.update { it.copy(savedCar = null) }
                viewModelScope.launch { myCarRepository.clear() }
            }
            FuelCostIntent.Back -> {
                searchJob?.cancel()
                mapJob?.cancel()
                vehicleJob?.cancel()
                _uiState.update { it.copy(screen = FuelCostScreen.Input) }
            }
            else -> _uiState.update { reduce(it, intent) }
        }
    }

    private fun reduce(state: FuelCostUiState, intent: FuelCostIntent): FuelCostUiState = when (intent) {
        is FuelCostIntent.RiderNameChanged -> state.updateRider(intent.id) { it.copy(name = intent.value) }
        is FuelCostIntent.RiderDistanceChanged ->
            state.updateRider(intent.id) { it.copy(distanceKm = intent.value.decimalOnly()) }.manualDistance()
        is FuelCostIntent.KmPerLiterChanged ->
            state.copy(kmPerLiter = intent.value.decimalOnly(), selectedVehicle = null)
        FuelCostIntent.OpenVehicleSearch -> state.copy(
            screen = FuelCostScreen.VehicleSearch,
            vehicleQuery = "",
            vehicleSearch = VehicleSearchState.Idle,
        )
        is FuelCostIntent.WonPerLiterChanged ->
            state.copy(wonPerLiter = intent.value.decimalOnly(), wonPerLiterEdited = true)
        is FuelCostIntent.FuelTypeSelected -> state.copy(fuelType = intent.type).withAveragePriceIfNotEdited()
        FuelCostIntent.ApplyAverageFuelPrice -> state.copy(wonPerLiterEdited = false).withAveragePriceIfNotEdited()
        is FuelCostIntent.MethodSelected -> state.copy(method = intent.method)
        is FuelCostIntent.TotalDistanceChanged ->
            state.copy(totalDistanceKm = intent.value.decimalOnly()).manualDistance()
        is FuelCostIntent.OpenAddressSearch -> state.copy(
            screen = FuelCostScreen.AddressSearch(intent.field),
            searchQuery = state.valueOf(intent.field),
            addressSearch = AddressSearchState.Idle,
        )
        is FuelCostIntent.OpenMapPicker -> state.copy(
            screen = FuelCostScreen.MapPicker(intent.field),
            mapPick = MapPickState.Idle,
            mapCameraTarget = null,
            searchQuery = "",
            addressSearch = AddressSearchState.Idle,
        )
        FuelCostIntent.LocationPermissionDenied -> state.copy(currentLocation = CurrentLocationState.PermissionDenied)
        FuelCostIntent.Calculate -> state.calculate()
        else -> state
    }

    /** 위치가 모두 정해졌으면 조회한다. 아직 비어 있으면 대기하고, 좌표 없는 위치가 있으면 직접 입력을 안내한다. */
    private fun lookupDistances() {
        val state = _uiState.value
        val origin = state.originPoint
        val destination = state.destinationPoint
        val pickups = state.riders.mapNotNull { it.pickupPoint }
        if (origin == null || destination == null || pickups.size != state.riders.size) {
            val incomplete = state.origin.isBlank() || state.destination.isBlank() ||
                state.riders.any { it.pickup.isBlank() }
            _uiState.update {
                it.copy(
                    distanceLookup = if (incomplete) DistanceLookupState.Idle else DistanceLookupState.MissingLocation,
                )
            }
            return
        }
        _uiState.update { it.copy(distanceLookup = DistanceLookupState.Loading) }
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val result = routeDistanceRepository.fetchDistances(origin, destination, pickups)
            _uiState.update { current ->
                when (result) {
                    is LookupResult.Success -> current.copy(
                        totalDistanceKm = metersToKmInput(result.value.totalMeters),
                        riders = current.riders.mapIndexed { index, rider ->
                            rider.copy(distanceKm = metersToKmInput(result.value.riderMeters[index]))
                        },
                        distanceLookup = DistanceLookupState.Success,
                        distanceFromLookup = true,
                    )
                    is LookupResult.Failure -> current.copy(distanceLookup = DistanceLookupState.Failed(result.reason))
                }
            }
        }
    }

    private fun onSearchQueryChanged(value: String) {
        if (value == _uiState.value.searchQuery) return
        searchJob?.cancel()
        _uiState.update { state ->
            state.copy(
                searchQuery = value,
                addressSearch = if (value.isBlank()) AddressSearchState.Idle else state.addressSearch,
            )
        }
        if (value.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            runSearch(value.trim())
        }
    }

    private fun searchPlaces() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(query) }
    }

    /** 입력 중에는 이전 결과를 유지해 목록이 깜빡이지 않게 한다. */
    private suspend fun runSearch(query: String) {
        _uiState.update { state ->
            if (state.addressSearch is AddressSearchState.Results) state
            else state.copy(addressSearch = AddressSearchState.Loading)
        }
        val result = placeSearchRepository.search(query)
        _uiState.update {
            it.copy(
                addressSearch = when (result) {
                    is LookupResult.Success -> AddressSearchState.Results(result.value)
                    is LookupResult.Failure -> AddressSearchState.Failed(result.reason)
                },
            )
        }
    }

    private fun onVehicleQueryChanged(value: String) {
        if (value == _uiState.value.vehicleQuery) return
        vehicleJob?.cancel()
        _uiState.update { state ->
            state.copy(
                vehicleQuery = value,
                vehicleSearch = if (value.isBlank()) VehicleSearchState.Idle else state.vehicleSearch,
            )
        }
        if (value.isBlank()) return
        vehicleJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            runVehicleSearch(value.trim())
        }
    }

    private fun searchVehicles() {
        val query = _uiState.value.vehicleQuery.trim()
        if (query.isEmpty()) return
        vehicleJob?.cancel()
        vehicleJob = viewModelScope.launch { runVehicleSearch(query) }
    }

    private suspend fun runVehicleSearch(query: String) {
        _uiState.update { state ->
            if (state.vehicleSearch is VehicleSearchState.Results) state
            else state.copy(vehicleSearch = VehicleSearchState.Loading)
        }
        val result = vehicleRepository.search(query)
        _uiState.update {
            it.copy(
                vehicleSearch = when (result) {
                    is LookupResult.Success -> VehicleSearchState.Results(result.value)
                    is LookupResult.Failure -> VehicleSearchState.Failed(result.reason)
                },
            )
        }
    }

    private fun selectVehicle(vehicle: VehicleFuelEconomy) {
        vehicleJob?.cancel()
        _uiState.update { state ->
            state.withCar(MyCar(vehicle, vehicle.kmPerLiter, vehicle.fuelType)).copy(screen = FuelCostScreen.Input)
        }
    }

    private fun FuelCostUiState.withCar(car: MyCar) = copy(
        selectedVehicle = car.vehicle,
        kmPerLiter = car.kmPerLiter.toPlainString(),
        fuelType = car.fuelType,
    ).withAveragePriceIfNotEdited()

    private fun selectMapPlace(place: Place) {
        searchJob?.cancel()
        mapJob?.cancel()
        pendingMapPlace = place
        _uiState.update {
            it.copy(
                mapPick = MapPickState.Resolved(place),
                mapCameraTarget = place.point,
                searchQuery = "",
                addressSearch = AddressSearchState.Idle,
            )
        }
    }

    private fun resolveMapPoint(point: GeoPoint) {
        val pending = pendingMapPlace
        pendingMapPlace = null
        if (pending != null && pending.point.isNear(point)) return
        _uiState.update { it.copy(mapPick = MapPickState.Loading, addressSearch = AddressSearchState.Idle) }
        mapJob?.cancel()
        mapJob = viewModelScope.launch {
            val result = placeSearchRepository.reverseGeocode(point)
            _uiState.update {
                it.copy(
                    mapPick = when (result) {
                        is LookupResult.Success -> result.value?.let(MapPickState::Resolved) ?: MapPickState.NoAddress
                        is LookupResult.Failure -> MapPickState.Failed(result.reason)
                    },
                )
            }
        }
    }

    private fun confirmMapPick() {
        val state = _uiState.value
        val screen = state.screen as? FuelCostScreen.MapPicker ?: return
        val place = (state.mapPick as? MapPickState.Resolved)?.place ?: return
        confirmLocation(screen.field, place.name, place.point)
    }

    private fun useCurrentLocation() {
        _uiState.update { it.copy(currentLocation = CurrentLocationState.Loading) }
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            val failed: (LookupFailure) -> Unit = { reason ->
                _uiState.update { it.copy(currentLocation = CurrentLocationState.Failed(reason)) }
            }
            when (val located = currentLocationRepository.currentPoint()) {
                is LookupResult.Failure -> failed(located.reason)
                is LookupResult.Success -> when (val found = placeSearchRepository.reverseGeocode(located.value)) {
                    is LookupResult.Failure -> failed(found.reason)
                    is LookupResult.Success -> {
                        val place = found.value
                        if (place == null) {
                            failed(LookupFailure.LOCATION_UNAVAILABLE)
                        } else {
                            updateRoute {
                                it.withLocation(LocationField.Origin, place.name, place.point)
                                    .copy(currentLocation = CurrentLocationState.Success)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applySearchedLocation(label: String, point: GeoPoint?) {
        searchJob?.cancel()
        val field = (_uiState.value.screen as? FuelCostScreen.AddressSearch)?.field ?: return
        confirmLocation(field, label, point)
    }

    /** 사용자가 고른 위치를 입력에 넣고 입력 화면으로 돌아간다. */
    private fun confirmLocation(field: LocationField, label: String, point: GeoPoint?) {
        if (field == LocationField.Origin) locationJob?.cancel()
        updateRoute { state ->
            val updated = state.withLocation(field, label, point).copy(screen = FuelCostScreen.Input)
            if (field == LocationField.Origin) updated.copy(currentLocation = CurrentLocationState.Idle) else updated
        }
    }

    private fun FuelCostUiState.withLocation(field: LocationField, label: String, point: GeoPoint?) = when (field) {
        LocationField.Origin -> copy(origin = label, originPoint = point)
        LocationField.Destination -> copy(destination = label, destinationPoint = point)
        is LocationField.RiderPickup -> updateRider(field.riderId) { it.copy(pickup = label, pickupPoint = point) }
    }

    /** 경로가 바뀌면 진행 중인 조회를 취소하고, 이전 경로로 조회한 거리를 지운 뒤 다시 조회한다. */
    private fun updateRoute(transform: (FuelCostUiState) -> FuelCostUiState) {
        lookupJob?.cancel()
        _uiState.update { state ->
            val cleared = if (state.distanceFromLookup) {
                state.copy(
                    totalDistanceKm = "",
                    riders = state.riders.map { it.copy(distanceKm = "") },
                    distanceFromLookup = false,
                )
            } else {
                state
            }
            transform(cleared).copy(distanceLookup = DistanceLookupState.Idle)
        }
        lookupDistances()
    }

    private fun GeoPoint.isNear(other: GeoPoint): Boolean =
        abs(longitude - other.longitude) < SAME_POINT_DEGREES && abs(latitude - other.latitude) < SAME_POINT_DEGREES

    private fun FuelCostUiState.withAveragePriceIfNotEdited(): FuelCostUiState {
        val average = averageFuelPrice
        return if (wonPerLiterEdited || average == null) {
            this
        } else {
            copy(wonPerLiter = average.wonPerLiter.setScale(0, RoundingMode.HALF_UP).toPlainString())
        }
    }

    private fun FuelCostUiState.manualDistance() = copy(distanceLookup = DistanceLookupState.Idle)

    private fun FuelCostUiState.calculate(): FuelCostUiState {
        if (!canCalculate) return this
        val input = SettlementInput(
            totalDistanceMeters = kmToMeters(totalDistanceKm.toBigDecimal()),
            kmPerLiter = kmPerLiter.toBigDecimal(),
            wonPerLiter = wonPerLiter.toBigDecimal(),
            method = method,
            riders = riders.map { RiderTrip(it.name.trim(), kmToMeters(it.distanceKm.toBigDecimal())) },
        )
        return when (val outcome = calculator(input)) {
            is SettlementOutcome.Success -> copy(screen = FuelCostScreen.Result, result = outcome.result)
            is SettlementOutcome.Failure -> this
        }
    }

    private fun FuelCostUiState.valueOf(field: LocationField): String = when (field) {
        LocationField.Origin -> origin
        LocationField.Destination -> destination
        is LocationField.RiderPickup -> riders.firstOrNull { it.id == field.riderId }?.pickup.orEmpty()
    }

    private fun FuelCostUiState.updateRider(id: Long, transform: (RiderInput) -> RiderInput) =
        copy(riders = riders.map { if (it.id == id) transform(it) else it })

    private fun List<RiderInput>.moved(id: Long, offset: Int): List<RiderInput> {
        val from = indexOfFirst { it.id == id }
        val to = from + offset
        if (from < 0 || to !in indices) return this
        return toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun String.decimalOnly(): String = filter { it.isDigit() || it == '.' }
}
