package com.jskim.sharingfuelcost.presentation.fuelcost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jskim.sharingfuelcost.domain.route.CurrentLocationRepository
import com.jskim.sharingfuelcost.domain.route.GeoPoint
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.LookupResult
import com.jskim.sharingfuelcost.domain.route.PlaceSearchRepository
import com.jskim.sharingfuelcost.domain.route.RouteDistanceRepository
import com.jskim.sharingfuelcost.domain.settlement.RiderTrip
import com.jskim.sharingfuelcost.domain.settlement.SettlementCalculator
import com.jskim.sharingfuelcost.domain.settlement.SettlementInput
import com.jskim.sharingfuelcost.domain.settlement.SettlementOutcome
import com.jskim.sharingfuelcost.domain.settlement.kmToMeters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FuelCostViewModel @Inject constructor(
    private val calculator: SettlementCalculator,
    private val routeDistanceRepository: RouteDistanceRepository,
    private val placeSearchRepository: PlaceSearchRepository,
    private val currentLocationRepository: CurrentLocationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FuelCostUiState())
    val uiState: StateFlow<FuelCostUiState> = _uiState.asStateFlow()

    private var nextRiderId = 0L
    private var lookupJob: Job? = null
    private var searchJob: Job? = null
    private var mapJob: Job? = null
    private var locationJob: Job? = null

    fun onIntent(intent: FuelCostIntent) {
        when (intent) {
            FuelCostIntent.DistanceLookupClicked -> lookupDistances()
            FuelCostIntent.SearchSubmitted -> searchPlaces()
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
            FuelCostIntent.Back -> {
                searchJob?.cancel()
                mapJob?.cancel()
                _uiState.update { it.copy(screen = FuelCostScreen.Input) }
            }
            else -> _uiState.update { reduce(it, intent) }
        }
    }

    private fun reduce(state: FuelCostUiState, intent: FuelCostIntent): FuelCostUiState = when (intent) {
        is FuelCostIntent.DriverNameChanged -> state.copy(driverName = intent.value)
        is FuelCostIntent.RiderNameChanged -> state.updateRider(intent.id) { it.copy(name = intent.value) }
        is FuelCostIntent.RiderDistanceChanged ->
            state.updateRider(intent.id) { it.copy(distanceKm = intent.value.decimalOnly()) }.manualDistance()
        is FuelCostIntent.KmPerLiterChanged -> state.copy(kmPerLiter = intent.value.decimalOnly())
        is FuelCostIntent.WonPerLiterChanged -> state.copy(wonPerLiter = intent.value.decimalOnly())
        is FuelCostIntent.MethodSelected -> state.copy(method = intent.method)
        is FuelCostIntent.TotalDistanceChanged ->
            state.copy(totalDistanceKm = intent.value.decimalOnly()).manualDistance()
        is FuelCostIntent.OpenAddressSearch -> state.copy(
            screen = FuelCostScreen.AddressSearch(intent.field),
            searchQuery = state.valueOf(intent.field),
            addressSearch = AddressSearchState.Idle,
        )
        is FuelCostIntent.OpenMapPicker -> state.copy(
            screen = FuelCostScreen.MapPicker(intent.riderId),
            mapPick = MapPickState.Idle,
        )
        is FuelCostIntent.SearchQueryChanged -> state.copy(searchQuery = intent.value)
        FuelCostIntent.LocationPermissionDenied -> state.copy(currentLocation = CurrentLocationState.PermissionDenied)
        FuelCostIntent.Calculate -> state.calculate()
        else -> state
    }

    private fun lookupDistances() {
        val state = _uiState.value
        val origin = state.originPoint
        val destination = state.destinationPoint
        val pickups = state.riders.mapNotNull { it.pickupPoint }
        if (origin == null || destination == null || pickups.size != state.riders.size) {
            _uiState.update { it.copy(distanceLookup = DistanceLookupState.MissingLocation) }
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
                    )
                    is LookupResult.Failure -> current.copy(distanceLookup = DistanceLookupState.Failed(result.reason))
                }
            }
        }
    }

    private fun searchPlaces() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return
        _uiState.update { it.copy(addressSearch = AddressSearchState.Loading) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
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
    }

    private fun resolveMapPoint(point: GeoPoint) {
        _uiState.update { it.copy(mapPick = MapPickState.Loading) }
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
        updateRoute {
            it.withLocation(LocationField.RiderPickup(screen.riderId), place.name, place.point)
                .copy(screen = FuelCostScreen.Input)
        }
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

    /** 경로가 바뀌면 진행 중인 조회를 취소하고, 이전 경로로 조회한 거리를 지운다. */
    private fun updateRoute(transform: (FuelCostUiState) -> FuelCostUiState) {
        lookupJob?.cancel()
        _uiState.update { state ->
            val cleared = if (state.distanceLookup == DistanceLookupState.Success) {
                state.copy(totalDistanceKm = "", riders = state.riders.map { it.copy(distanceKm = "") })
            } else {
                state
            }
            transform(cleared).copy(distanceLookup = DistanceLookupState.Idle)
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
            driverName = driverName.trim(),
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
