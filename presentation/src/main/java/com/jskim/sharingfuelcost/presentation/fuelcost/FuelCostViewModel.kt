package com.jskim.sharingfuelcost.presentation.fuelcost

import androidx.lifecycle.ViewModel
import com.jskim.sharingfuelcost.domain.settlement.RiderTrip
import com.jskim.sharingfuelcost.domain.settlement.SettlementCalculator
import com.jskim.sharingfuelcost.domain.settlement.SettlementInput
import com.jskim.sharingfuelcost.domain.settlement.SettlementOutcome
import com.jskim.sharingfuelcost.domain.settlement.kmToMeters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FuelCostViewModel(
    private val calculator: SettlementCalculator = SettlementCalculator(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(FuelCostUiState())
    val uiState: StateFlow<FuelCostUiState> = _uiState.asStateFlow()

    private var nextRiderId = 0L

    fun onIntent(intent: FuelCostIntent) {
        _uiState.update { state ->
            when (intent) {
                is FuelCostIntent.DriverNameChanged -> state.copy(driverName = intent.value)
                FuelCostIntent.AddRider -> state.copy(riders = state.riders + RiderInput(nextRiderId++))
                is FuelCostIntent.RemoveRider -> state.copy(riders = state.riders.filterNot { it.id == intent.id })
                is FuelCostIntent.MoveRider -> state.copy(riders = state.riders.moved(intent.id, intent.offset))
                is FuelCostIntent.RiderNameChanged -> state.updateRider(intent.id) { it.copy(name = intent.value) }
                is FuelCostIntent.RiderDistanceChanged ->
                    state.updateRider(intent.id) { it.copy(distanceKm = intent.value.decimalOnly()) }
                is FuelCostIntent.KmPerLiterChanged -> state.copy(kmPerLiter = intent.value.decimalOnly())
                is FuelCostIntent.WonPerLiterChanged -> state.copy(wonPerLiter = intent.value.decimalOnly())
                is FuelCostIntent.MethodSelected -> state.copy(method = intent.method)
                is FuelCostIntent.TotalDistanceChanged -> state.copy(totalDistanceKm = intent.value.decimalOnly())
                // 거리 조회는 미연동이다. 카카오모빌리티 검증 후 domain 인터페이스로 연결한다.
                FuelCostIntent.DistanceLookupClicked -> state.copy(distanceLookupAttempted = true)
                is FuelCostIntent.OpenAddressSearch -> state.copy(
                    screen = FuelCostScreen.AddressSearch(intent.field),
                    searchQuery = state.valueOf(intent.field),
                )
                is FuelCostIntent.OpenMapPicker -> state.copy(screen = FuelCostScreen.MapPicker(intent.riderId))
                is FuelCostIntent.SearchQueryChanged -> state.copy(searchQuery = intent.value)
                FuelCostIntent.ConfirmAddress -> state.confirmAddress()
                FuelCostIntent.Calculate -> state.calculate()
                FuelCostIntent.Back -> state.copy(screen = FuelCostScreen.Input)
            }
        }
    }

    private fun FuelCostUiState.confirmAddress(): FuelCostUiState {
        val field = (screen as? FuelCostScreen.AddressSearch)?.field ?: return this
        val address = searchQuery.trim()
        if (address.isEmpty()) return this
        val updated = when (field) {
            LocationField.Origin -> copy(origin = address)
            LocationField.Destination -> copy(destination = address)
            is LocationField.RiderPickup -> updateRider(field.riderId) { it.copy(pickup = address) }
        }
        return updated.copy(screen = FuelCostScreen.Input)
    }

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
