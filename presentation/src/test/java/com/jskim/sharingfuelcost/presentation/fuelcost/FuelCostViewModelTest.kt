package com.jskim.sharingfuelcost.presentation.fuelcost

import com.jskim.sharingfuelcost.domain.fuel.AverageFuelPrice
import com.jskim.sharingfuelcost.domain.fuel.FuelPriceRepository
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.fuel.VehicleFuelEconomy
import com.jskim.sharingfuelcost.domain.fuel.VehicleRepository
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

    private var lastPickups: List<GeoPoint> = emptyList()
    private var searchCalls = 0
    private var vehicleCalls = 0
    private val diesel = VehicleFuelEconomy("기아", "쏘렌토", "2023", "경유", FuelType.DIESEL, "14.3".toBigDecimal())
    private var vehicleResult: LookupResult<List<VehicleFuelEconomy>> = LookupResult.Success(listOf(diesel))
    private val dispatcher = UnconfinedTestDispatcher()
    private var fuelPriceResult: LookupResult<List<AverageFuelPrice>> =
        LookupResult.Failure(LookupFailure.NOT_CONFIGURED)

    private val viewModel by lazy {
        FuelCostViewModel(
            SettlementCalculator(),
            object : RouteDistanceRepository {
                override suspend fun fetchDistances(origin: GeoPoint, destination: GeoPoint, pickups: List<GeoPoint>) =
                    routeResult.also {
                        lastPickups = pickups
                        routeGate?.await()
                    }
            },
            object : PlaceSearchRepository {
                override suspend fun search(query: String) = searchResult.also { searchCalls++ }
                override suspend fun reverseGeocode(point: GeoPoint) = reverseResult
            },
            object : CurrentLocationRepository {
                override suspend fun currentPoint() = locationResult
            },
            object : FuelPriceRepository {
                override suspend fun averagePrices() = fuelPriceResult
            },
            object : VehicleRepository {
                override suspend fun search(modelName: String) = vehicleResult.also { vehicleCalls++ }
            },
        )
    }
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
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
        viewModel.onIntent(FuelCostIntent.OpenMapPicker(LocationField.RiderPickup(id)))
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
        viewModel.onIntent(FuelCostIntent.OpenMapPicker(LocationField.RiderPickup(id)))
        viewModel.onIntent(FuelCostIntent.MapCameraIdle(point))
        assertEquals(MapPickState.NoAddress, state.mapPick)

        viewModel.onIntent(FuelCostIntent.MapPickConfirmed)
        assertEquals(FuelCostScreen.MapPicker(LocationField.RiderPickup(id)), state.screen)
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
    fun `lookup sends pickups in rider order and fills distances by that order`() {
        selectPlace(LocationField.Origin)
        selectPlace(LocationField.Destination)
        val first = GeoPoint(1.0, 1.0)
        val second = GeoPoint(2.0, 2.0)
        listOf(first, second).forEach { pickup ->
            viewModel.onIntent(FuelCostIntent.AddRider)
            val id = state.riders.last().id
            viewModel.onIntent(FuelCostIntent.OpenAddressSearch(LocationField.RiderPickup(id)))
            viewModel.onIntent(FuelCostIntent.PlaceSelected(Place("위치", "주소", pickup)))
        }
        viewModel.onIntent(FuelCostIntent.MoveRider(state.riders.last().id, -1))
        routeResult = LookupResult.Success(RouteDistances(3_000, listOf(2_000, 1_000)))

        viewModel.onIntent(FuelCostIntent.DistanceLookupClicked)

        assertEquals(listOf(second, first), lastPickups)
        assertEquals(second, state.riders[0].pickupPoint)
        assertEquals("2", state.riders[0].distanceKm)
        assertEquals("1", state.riders[1].distanceKm)
    }

    @Test
    fun `typing searches after debounce and only for the latest text`() {
        viewModel.onIntent(FuelCostIntent.OpenAddressSearch(LocationField.Destination))
        viewModel.onIntent(FuelCostIntent.SearchQueryChanged("강"))
        dispatcher.scheduler.advanceTimeBy(100)
        viewModel.onIntent(FuelCostIntent.SearchQueryChanged("강남"))
        dispatcher.scheduler.advanceTimeBy(299)
        dispatcher.scheduler.runCurrent()
        assertEquals(0, searchCalls)

        dispatcher.scheduler.advanceTimeBy(2)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, searchCalls)
        assertTrue(state.addressSearch is AddressSearchState.Results)
    }

    @Test
    fun `clearing text cancels search and resets results`() {
        viewModel.onIntent(FuelCostIntent.OpenAddressSearch(LocationField.Destination))
        viewModel.onIntent(FuelCostIntent.SearchQueryChanged("강남"))
        viewModel.onIntent(FuelCostIntent.SearchQueryChanged(""))
        dispatcher.scheduler.advanceTimeBy(1_000)
        dispatcher.scheduler.runCurrent()
        assertEquals(0, searchCalls)
        assertEquals(AddressSearchState.Idle, state.addressSearch)
    }

    @Test
    fun `map search selection keeps place name after camera arrives and confirms`() {
        viewModel.onIntent(FuelCostIntent.AddRider)
        val id = state.riders.single().id
        viewModel.onIntent(FuelCostIntent.OpenMapPicker(LocationField.RiderPickup(id)))
        val place = Place("강남역", "서울 강남구", GeoPoint(127.0276, 37.4979))
        viewModel.onIntent(FuelCostIntent.SearchQueryChanged("강남"))

        viewModel.onIntent(FuelCostIntent.MapPlaceSelected(place))
        assertEquals(place.point, state.mapCameraTarget)
        assertEquals("", state.searchQuery)

        viewModel.onIntent(FuelCostIntent.MapCameraIdle(GeoPoint(127.02761, 37.49791)))
        assertEquals(MapPickState.Resolved(place), state.mapPick)

        viewModel.onIntent(FuelCostIntent.MapPickConfirmed)
        assertEquals("강남역", state.riders.single().pickup)
        assertEquals(place.point, state.riders.single().pickupPoint)
    }

    @Test
    fun `moving map after search selection resolves new address`() {
        viewModel.onIntent(FuelCostIntent.AddRider)
        viewModel.onIntent(FuelCostIntent.OpenMapPicker(LocationField.RiderPickup(state.riders.single().id)))
        viewModel.onIntent(FuelCostIntent.MapPlaceSelected(Place("강남역", "주소", GeoPoint(127.0, 37.0))))
        viewModel.onIntent(FuelCostIntent.MapCameraIdle(GeoPoint(127.5, 37.5)))
        assertEquals(MapPickState.Resolved(Place("지도 건물", "지도 주소", point)), state.mapPick)
    }

    private fun prices() = LookupResult.Success(
        listOf(
            AverageFuelPrice(FuelType.GASOLINE, "1667.5".toBigDecimal(), "20261101"),
            AverageFuelPrice(FuelType.DIESEL, "1500.2".toBigDecimal(), "20261101"),
        ),
    )

    @Test
    fun `average price fills field rounded and follows fuel type`() {
        fuelPriceResult = prices()
        assertEquals("1668", state.wonPerLiter)
        viewModel.onIntent(FuelCostIntent.FuelTypeSelected(FuelType.DIESEL))
        assertEquals("1500", state.wonPerLiter)
    }

    @Test
    fun `manual price is kept until average is applied again`() {
        fuelPriceResult = prices()
        viewModel.onIntent(FuelCostIntent.WonPerLiterChanged("1700"))
        viewModel.onIntent(FuelCostIntent.FuelTypeSelected(FuelType.DIESEL))
        assertEquals("1700", state.wonPerLiter)

        viewModel.onIntent(FuelCostIntent.ApplyAverageFuelPrice)
        assertEquals("1500", state.wonPerLiter)
    }

    @Test
    fun `average price failure leaves field empty for manual input`() {
        assertEquals(FuelPriceState.Failed(LookupFailure.NOT_CONFIGURED), state.fuelPrice)
        assertEquals("", state.wonPerLiter)
    }

    @Test
    fun `driver needs no input so result lists driver first without name`() {
        fillValidInput()
        assertTrue(state.canCalculate)
        addRider("민지", "사당역", "100")
        viewModel.onIntent(FuelCostIntent.Calculate)
        assertEquals(listOf(null, "민지"), state.result?.shares?.map { it.name })
    }

    @Test
    fun `origin and destination are confirmed on map with place name and coordinates`() {
        val origin = Place("강남역", "서울 강남구", GeoPoint(127.0, 37.0))
        val destination = Place("속초해수욕장", "강원 속초시", GeoPoint(128.6, 38.2))

        viewModel.onIntent(FuelCostIntent.OpenMapPicker(LocationField.Origin))
        viewModel.onIntent(FuelCostIntent.MapPlaceSelected(origin))
        viewModel.onIntent(FuelCostIntent.MapCameraIdle(origin.point))
        viewModel.onIntent(FuelCostIntent.MapPickConfirmed)
        assertEquals("강남역", state.origin)
        assertEquals(origin.point, state.originPoint)
        assertEquals(CurrentLocationState.Idle, state.currentLocation)

        viewModel.onIntent(FuelCostIntent.OpenMapPicker(LocationField.Destination))
        viewModel.onIntent(FuelCostIntent.MapPlaceSelected(destination))
        viewModel.onIntent(FuelCostIntent.MapCameraIdle(destination.point))
        viewModel.onIntent(FuelCostIntent.MapPickConfirmed)
        assertEquals("속초해수욕장", state.destination)
        assertEquals(destination.point, state.destinationPoint)
        assertEquals(FuelCostScreen.Input, state.screen)
    }

    @Test
    fun `leaving map without confirming keeps previous location`() {
        selectPlace(LocationField.Destination)
        val before = state.destination
        viewModel.onIntent(FuelCostIntent.OpenMapPicker(LocationField.Destination))
        viewModel.onIntent(FuelCostIntent.MapCameraIdle(GeoPoint(128.0, 38.0)))
        viewModel.onIntent(FuelCostIntent.Back)
        assertEquals(before, state.destination)
        assertEquals(FuelCostScreen.Input, state.screen)
    }

    @Test
    fun `vehicle search debounces and selection fills economy fuel type and average price`() {
        fuelPriceResult = prices()
        assertEquals("1668", state.wonPerLiter)
        viewModel.onIntent(FuelCostIntent.OpenVehicleSearch)
        viewModel.onIntent(FuelCostIntent.VehicleQueryChanged("쏘"))
        viewModel.onIntent(FuelCostIntent.VehicleQueryChanged("쏘렌토"))
        dispatcher.scheduler.advanceTimeBy(301)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, vehicleCalls)
        assertEquals(VehicleSearchState.Results(listOf(diesel)), state.vehicleSearch)

        viewModel.onIntent(FuelCostIntent.VehicleSelected(diesel))
        assertEquals(FuelCostScreen.Input, state.screen)
        assertEquals("14.3", state.kmPerLiter)
        assertEquals(FuelType.DIESEL, state.fuelType)
        assertEquals("1500", state.wonPerLiter)
        assertEquals(diesel, state.selectedVehicle)
    }

    @Test
    fun `selecting vehicle keeps manually edited price`() {
        fuelPriceResult = prices()
        viewModel.onIntent(FuelCostIntent.WonPerLiterChanged("1700"))
        viewModel.onIntent(FuelCostIntent.VehicleSelected(diesel))
        assertEquals("1700", state.wonPerLiter)
    }

    @Test
    fun `editing economy clears selected vehicle`() {
        viewModel.onIntent(FuelCostIntent.VehicleSelected(diesel))
        viewModel.onIntent(FuelCostIntent.KmPerLiterChanged("10"))
        assertEquals("10", state.kmPerLiter)
        assertNull(state.selectedVehicle)
    }

    @Test
    fun `vehicle search failure is shown and manual economy still works`() {
        vehicleResult = LookupResult.Failure(LookupFailure.NOT_CONFIGURED)
        viewModel.onIntent(FuelCostIntent.OpenVehicleSearch)
        viewModel.onIntent(FuelCostIntent.VehicleQueryChanged("쏘렌토"))
        viewModel.onIntent(FuelCostIntent.VehicleSearchSubmitted)
        assertEquals(VehicleSearchState.Failed(LookupFailure.NOT_CONFIGURED), state.vehicleSearch)
        viewModel.onIntent(FuelCostIntent.Back)
        viewModel.onIntent(FuelCostIntent.KmPerLiterChanged("12.5"))
        assertEquals("12.5", state.kmPerLiter)
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
        assertEquals("12.5", state.kmPerLiter)
    }

    @Test
    fun `map picker falls back to address search for that rider`() {
        val id = addRider("민지", "사당역", "1")
        viewModel.onIntent(FuelCostIntent.OpenMapPicker(LocationField.RiderPickup(id)))
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
