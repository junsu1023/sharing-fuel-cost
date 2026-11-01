package com.jskim.sharingfuelcost.presentation.fuelcost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jskim.sharingfuelcost.domain.route.GeoPoint
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(FuelCostUiState())
    val uiState: StateFlow<FuelCostUiState> = _uiState.asStateFlow()

    private var nextRiderId = 0L
    private var lookupJob: Job? = null
    private var searchJob: Job? = null

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
                applyLocation(intent.place.name, intent.place.point)
            FuelCostIntent.ConfirmAddress -> {
                val address = _uiState.value.searchQuery.trim()
                if (address.isNotEmpty()) applyLocation(address, null)
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
        is FuelCostIntent.OpenMapPicker -> state.copy(screen = FuelCostScreen.MapPicker(intent.riderId))
        is FuelCostIntent.SearchQueryChanged -> state.copy(searchQuery = intent.value)
        FuelCostIntent.Calculate -> state.calculate()
        FuelCostIntent.Back -> state.copy(screen = FuelCostScreen.Input)
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

    private fun applyLocation(label: String, point: GeoPoint?) {
        searchJob?.cancel()
        updateRoute { state ->
            val field = (state.screen as? FuelCostScreen.AddressSearch)?.field ?: return@updateRoute state
            val updated = when (field) {
                LocationField.Origin -> state.copy(origin = label, originPoint = point)
                LocationField.Destination -> state.copy(destination = label, destinationPoint = point)
                is LocationField.RiderPickup -> state.updateRider(field.riderId) {
                    it.copy(pickup = label, pickupPoint = point)
                }
            }
            updated.copy(screen = FuelCostScreen.Input)
        }
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
