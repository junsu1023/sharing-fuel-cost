package com.jskim.sharingfuelcost.presentation.fuelcost

import com.jskim.sharingfuelcost.domain.route.CurrentLocationRepository
import com.jskim.sharingfuelcost.domain.route.GeoPoint
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.LookupResult
import com.jskim.sharingfuelcost.domain.route.Place
import com.jskim.sharingfuelcost.domain.route.PlaceSearchRepository
import com.jskim.sharingfuelcost.domain.route.RouteDistanceRepository
import com.jskim.sharingfuelcost.domain.route.RouteDistances
import com.jskim.sharingfuelcost.domain.settlement.SettlementCalculator
import com.jskim.sharingfuelcost.domain.settlement.SettlementMethod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FuelCostViewModelTest {

    private val point = GeoPoint(127.0, 37.0)
    private var routeResult: LookupResult<RouteDistances> =
        LookupResult.Success(RouteDistances(256_734, listOf(250_316)))
    private var routeGate: CompletableDeferred<Unit>? = null
    private var searchResult: LookupResult<List<Place>> = LookupResult.Success(listOf(Place("강남역", "주소", point)))

    private var reverseResult: LookupResult<Place?> = LookupResult.Success(Place("지도 건물", "지도 주소", point))
    private var locationResult: LookupResult<GeoPoint> = LookupResult.Success(point)

    private val viewModel by lazy {
        FuelCostViewModel(
            SettlementCalculator(),
            object : RouteDistanceRepository {
                override suspend fun fetchDistances(origin: GeoPoint, destination: GeoPoint, pickups: List<GeoPoint>) =
                    routeResult.also { routeGate?.await() }
            },
            object : PlaceSearchRepository {
                override suspend fun search(query: String) = searchResult
                override suspend fun reverseGeocode(point: GeoPoint) = reverseResult
            },
            object : CurrentLocationRepository {
                override suspend fun currentPoint() = locationResult
            },
        )
    }
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun selectPlace(field: LocationField) {
        viewModel.onIntent(FuelCostIntent.OpenAddressSearch(field))
        viewModel.onIntent(FuelCostIntent.SearchQueryChanged("강남역"))
        viewModel.onIntent(FuelCostIntent.SearchSubmitted)
        val places = (state.addressSearch as AddressSearchState.Results).places
        viewModel.onIntent(FuelCostIntent.PlaceSelected(places.first()))
    }

    private fun routeWithOneRider(): Long {
        selectPlace(LocationField.Origin)
        selectPlace(LocationField.Destination)
        viewModel.onIntent(FuelCostIntent.AddRider)
        val id = state.riders.single().id
        selectPlace(LocationField.RiderPickup(id))
        return id
    }

    @Test
    fun `lookup success fills exact distances and manual edit is allowed`() {
        routeWithOneRider()
        viewModel.onIntent(FuelCostIntent.DistanceLookupClicked)
        assertEquals(DistanceLookupState.Success, state.distanceLookup)
        assertEquals("256.734", state.totalDistanceKm)
        assertEquals("250.316", state.riders.single().distanceKm)

        viewModel.onIntent(FuelCostIntent.TotalDistanceChanged("300"))
        assertEquals("300", state.totalDistanceKm)
        assertEquals(DistanceLookupState.Idle, state.distanceLookup)
    }

    @Test
    fun `lookup failure keeps manual input possible`() {
        routeWithOneRider()
        routeResult = LookupResult.Failure(LookupFailure.NETWORK)
        viewModel.onIntent(FuelCostIntent.TotalDistanceChanged("100"))
        viewModel.onIntent(FuelCostIntent.DistanceLookupClicked)
        assertEquals(DistanceLookupState.Failed(LookupFailure.NETWORK), state.distanceLookup)
        assertEquals("100", state.totalDistanceKm)
    }

    @Test
    fun `typed address has no coordinates so lookup reports missing location`() {
        enterAddress(LocationField.Origin, "직접 입력")
        selectPlace(LocationField.Destination)
        viewModel.onIntent(FuelCostIntent.DistanceLookupClicked)
        assertEquals(DistanceLookupState.MissingLocation, state.distanceLookup)
    }

    @Test
    fun `changing route clears looked up distances but not manual ones`() {
        val id = routeWithOneRider()
        viewModel.onIntent(FuelCostIntent.DistanceLookupClicked)
        selectPlace(LocationField.RiderPickup(id))
        assertEquals("", state.totalDistanceKm)
        assertEquals(DistanceLookupState.Idle, state.distanceLookup)

        viewModel.onIntent(FuelCostIntent.TotalDistanceChanged("100"))
        selectPlace(LocationField.Destination)
        assertEquals("100", state.totalDistanceKm)
    }

    @Test
    fun `route change cancels lookup in flight`() {
        routeWithOneRider()
        routeGate = CompletableDeferred()
        viewModel.onIntent(FuelCostIntent.DistanceLookupClicked)
        assertEquals(DistanceLookupState.Loading, state.distanceLookup)

        viewModel.onIntent(FuelCostIntent.AddRider)
        routeGate?.complete(Unit)
        assertEquals(DistanceLookupState.Idle, state.distanceLookup)
        assertEquals("", state.totalDistanceKm)
    }

    @Test
    fun `search failure is shown and typed address can be used`() {
        searchResult = LookupResult.Failure(LookupFailure.QUOTA_EXCEEDED)
        viewModel.onIntent(FuelCostIntent.OpenAddressSearch(LocationField.Origin))
        viewModel.onIntent(FuelCostIntent.SearchQueryChanged("강남역"))
        viewModel.onIntent(FuelCostIntent.SearchSubmitted)
        assertEquals(AddressSearchState.Failed(LookupFailure.QUOTA_EXCEEDED), state.addressSearch)

        viewModel.onIntent(FuelCostIntent.ConfirmAddress)
        assertEquals("강남역", state.origin)
        assertNull(state.originPoint)
    }

    @Test
    fun `map pick resolves address and confirm sets rider pickup with coordinates`() {
        viewModel.onIntent(FuelCostIntent.AddRider)
        val id = state.riders.single().id
        viewModel.onIntent(FuelCostIntent.OpenMapPicker(id))
        viewModel.onIntent(FuelCostIntent.MapCameraIdle(point))
        assertTrue(state.mapPick is MapPickState.Resolved)

        viewModel.onIntent(FuelCostIntent.MapPickConfirmed)
        assertEquals(FuelCostScreen.Input, state.screen)
        assertEquals("지도 건물", state.riders.single().pickup)
        assertEquals(point, state.riders.single().pickupPoint)
    }

    @Test
    fun `map pick without address cannot be confirmed`() {
        reverseResult = LookupResult.Success(null)
        viewModel.onIntent(FuelCostIntent.AddRider)
        val id = state.riders.single().id
        viewModel.onIntent(FuelCostIntent.OpenMapPicker(id))
        viewModel.onIntent(FuelCostIntent.MapCameraIdle(point))
        assertEquals(MapPickState.NoAddress, state.mapPick)

        viewModel.onIntent(FuelCostIntent.MapPickConfirmed)
        assertEquals(FuelCostScreen.MapPicker(id), state.screen)
        assertEquals("", state.riders.single().pickup)
    }

    @Test
    fun `current location fills origin with address and coordinates`() {
        viewModel.onIntent(FuelCostIntent.UseCurrentLocation)
        assertEquals(CurrentLocationState.Success, state.currentLocation)
        assertEquals("지도 건물", state.origin)
        assertEquals(point, state.originPoint)
    }

    @Test
    fun `current location failure and denied permission keep origin editable`() {
        locationResult = LookupResult.Failure(LookupFailure.LOCATION_UNAVAILABLE)
        viewModel.onIntent(FuelCostIntent.UseCurrentLocation)
        assertEquals(CurrentLocationState.Failed(LookupFailure.LOCATION_UNAVAILABLE), state.currentLocation)

        viewModel.onIntent(FuelCostIntent.LocationPermissionDenied)
        assertEquals(CurrentLocationState.PermissionDenied, state.currentLocation)

        enterAddress(LocationField.Origin, "직접 입력")
        assertEquals("직접 입력", state.origin)
        assertEquals(CurrentLocationState.Idle, state.currentLocation)
    }

    @Test
    fun `riders are limited to five`() {
        repeat(6) { viewModel.onIntent(FuelCostIntent.AddRider) }
        assertEquals(MAX_RIDERS, state.riders.size)
        assertFalse(state.canAddRider)
    }

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
    fun `manual input calculates without lookup`() {
        fillValidInput()
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
