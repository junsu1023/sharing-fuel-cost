package com.jskim.sharingfuelcost.presentation.fuelcost

import com.jskim.sharingfuelcost.domain.settlement.SettlementMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelCostViewModelTest {

    private val viewModel = FuelCostViewModel()
    private val state get() = viewModel.uiState.value

    private fun enterAddress(field: LocationField, address: String) {
        viewModel.onIntent(FuelCostIntent.OpenAddressSearch(field))
        viewModel.onIntent(FuelCostIntent.SearchQueryChanged(address))
        viewModel.onIntent(FuelCostIntent.ConfirmAddress)
    }

    private fun addRider(name: String, pickup: String, km: String): Long {
        viewModel.onIntent(FuelCostIntent.AddRider)
        val id = state.riders.last().id
        viewModel.onIntent(FuelCostIntent.RiderNameChanged(id, name))
        viewModel.onIntent(FuelCostIntent.RiderDistanceChanged(id, km))
        enterAddress(LocationField.RiderPickup(id), pickup)
        return id
    }

    private fun fillValidInput() {
        enterAddress(LocationField.Origin, "강남역")
        enterAddress(LocationField.Destination, "속초해수욕장")
        viewModel.onIntent(FuelCostIntent.DriverNameChanged("준수"))
        viewModel.onIntent(FuelCostIntent.KmPerLiterChanged("12.5"))
        viewModel.onIntent(FuelCostIntent.WonPerLiterChanged("1650"))
        viewModel.onIntent(FuelCostIntent.TotalDistanceChanged("250"))
    }

    @Test
    fun `cannot calculate with empty input`() {
        assertFalse(state.canCalculate)
        viewModel.onIntent(FuelCostIntent.Calculate)
        assertEquals(FuelCostScreen.Input, state.screen)
    }

    @Test
    fun `calculate shows proportional result`() {
        fillValidInput()
        addRider("민지", "사당역", "240")
        addRider("현우", "수원역", "110")
        viewModel.onIntent(FuelCostIntent.MethodSelected(SettlementMethod.DISTANCE_PROPORTIONAL))

        viewModel.onIntent(FuelCostIntent.Calculate)

        assertEquals(FuelCostScreen.Result, state.screen)
        assertEquals(33_000L, state.result?.totalCostWon)
        assertEquals(listOf(13_750L, 13_200L, 6_050L), state.result?.shares?.map { it.amountWon })
    }

    @Test
    fun `rider longer than total blocks calculation and shows error`() {
        fillValidInput()
        val id = addRider("민지", "사당역", "251")
        assertTrue(state.riderDistanceInvalid(state.riders.single { it.id == id }))
        assertFalse(state.canCalculate)
    }

    @Test
    fun `rider without pickup blocks calculation`() {
        fillValidInput()
        viewModel.onIntent(FuelCostIntent.AddRider)
        val id = state.riders.single().id
        viewModel.onIntent(FuelCostIntent.RiderNameChanged(id, "민지"))
        viewModel.onIntent(FuelCostIntent.RiderDistanceChanged(id, "100"))
        assertFalse(state.canCalculate)
    }

    @Test
    fun `distance lookup reports unavailable and manual input still calculates`() {
        fillValidInput()
        viewModel.onIntent(FuelCostIntent.DistanceLookupClicked)
        assertTrue(state.distanceLookupAttempted)
        assertTrue(state.canCalculate)
    }

    @Test
    fun `numeric fields drop non numeric characters`() {
        viewModel.onIntent(FuelCostIntent.KmPerLiterChanged("12a.5"))
        assertEquals("12.5", state.kmPerLiter)
    }

    @Test
    fun `zero fuel economy is invalid`() {
        viewModel.onIntent(FuelCostIntent.KmPerLiterChanged("0"))
        assertTrue(state.kmPerLiterInvalid)
    }

    @Test
    fun `move rider changes order and ignores out of range`() {
        val first = addRider("민지", "사당역", "1")
        val second = addRider("현우", "수원역", "1")
        viewModel.onIntent(FuelCostIntent.MoveRider(second, -1))
        assertEquals(listOf(second, first), state.riders.map { it.id })
        viewModel.onIntent(FuelCostIntent.MoveRider(second, -1))
        assertEquals(listOf(second, first), state.riders.map { it.id })
    }

    @Test
    fun `back from result keeps input`() {
        fillValidInput()
        viewModel.onIntent(FuelCostIntent.Calculate)
        viewModel.onIntent(FuelCostIntent.Back)
        assertEquals(FuelCostScreen.Input, state.screen)
        assertEquals("준수", state.driverName)
    }

    @Test
    fun `map picker falls back to address search for that rider`() {
        val id = addRider("민지", "사당역", "1")
        viewModel.onIntent(FuelCostIntent.OpenMapPicker(id))
        enterAddress(LocationField.RiderPickup(id), "수원역")
        assertEquals("수원역", state.riders.single().pickup)
        assertEquals(FuelCostScreen.Input, state.screen)
    }

    @Test
    fun `km format drops trailing zeros and liters keep one decimal`() {
        assertEquals("250", formatKm(250_000))
        assertEquals("12.5", formatKm(12_500))
        assertEquals("20.0", formatLiters("20".toBigDecimal()))
        assertEquals("33,000", formatWon(33_000))
    }
}
