package com.jskim.sharingfuelcost.presentation.fuelcost

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jskim.sharingfuelcost.domain.route.GeoPoint
import com.jskim.sharingfuelcost.presentation.R

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

/** 출발지를 모를 때 지도에 처음 보여 줄 위치(서울시청) */
private val DEFAULT_MAP_POINT = GeoPoint(longitude = 126.9784, latitude = 37.5665)

private fun Context.hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

/** 처음 호출일 때만 true다. 자동 권한 요청은 첫 실행에 한 번만 한다. */
private fun Context.markFirstLocationRequest(): Boolean {
    val preferences = getSharedPreferences("location_permission", Context.MODE_PRIVATE)
    if (preferences.getBoolean("requested", false)) return false
    preferences.edit().putBoolean("requested", true).apply()
    return true
}

private fun FuelCostUiState.pointOf(field: LocationField): GeoPoint? = when (field) {
    LocationField.Origin -> originPoint
    LocationField.Destination -> destinationPoint
    is LocationField.RiderPickup -> riders.firstOrNull { it.id == field.riderId }?.pickupPoint
}

@Composable
fun FuelCostRoute(viewModel: FuelCostViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onIntent = viewModel::onIntent
    val onBack = { onIntent(FuelCostIntent.Back) }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        onIntent(
            if (results.values.any { it }) {
                FuelCostIntent.UseCurrentLocation
            } else {
                FuelCostIntent.LocationPermissionDenied
            },
        )
    }

    // 권한이 있으면 출발지를 현재 위치로 채운다. 처음 실행이면 권한을 먼저 요청한다.
    LaunchedEffect(Unit) {
        val current = viewModel.uiState.value
        when {
            !context.hasLocationPermission() -> if (context.markFirstLocationRequest()) {
                permissionLauncher.launch(LOCATION_PERMISSIONS)
            }
            current.origin.isBlank() && current.currentLocation == CurrentLocationState.Idle ->
                onIntent(FuelCostIntent.UseCurrentLocation)
        }
    }

    BackHandler(enabled = state.screen != FuelCostScreen.Input, onBack = onBack)

    when (val screen = state.screen) {
        FuelCostScreen.Input -> InputScreen(
            state = state,
            onIntent = onIntent,
            onUseCurrentLocation = {
                if (context.hasLocationPermission()) {
                    onIntent(FuelCostIntent.UseCurrentLocation)
                } else {
                    permissionLauncher.launch(LOCATION_PERMISSIONS)
                }
            },
        )
        FuelCostScreen.Result -> state.result?.let { ResultScreen(state, it, onBack) }
        FuelCostScreen.VehicleSearch -> VehicleSearchScreen(
            query = state.vehicleQuery,
            search = state.vehicleSearch,
            onQueryChange = { onIntent(FuelCostIntent.VehicleQueryChanged(it)) },
            onSearch = { onIntent(FuelCostIntent.VehicleSearchSubmitted) },
            onSelect = { onIntent(FuelCostIntent.VehicleSelected(it)) },
            onBack = onBack,
        )
        is FuelCostScreen.AddressSearch -> AddressSearchScreen(
            query = state.searchQuery,
            search = state.addressSearch,
            onQueryChange = { onIntent(FuelCostIntent.SearchQueryChanged(it)) },
            onSearch = { onIntent(FuelCostIntent.SearchSubmitted) },
            onSelect = { onIntent(FuelCostIntent.PlaceSelected(it)) },
            onUseTyped = { onIntent(FuelCostIntent.ConfirmAddress) },
            onBack = onBack,
        )
        is FuelCostScreen.MapPicker -> MapPickerScreen(
            title = stringResource(
                when (screen.field) {
                    LocationField.Origin -> R.string.map_picker_origin_title
                    LocationField.Destination -> R.string.map_picker_destination_title
                    is LocationField.RiderPickup -> R.string.map_picker_title
                },
            ),
            // 아직 고르지 않은 출발지, 목적지는 바로 검색할 수 있게 한다.
            focusSearch = when (screen.field) {
                LocationField.Origin -> state.origin.isBlank()
                LocationField.Destination -> state.destination.isBlank()
                is LocationField.RiderPickup -> false
            },
            initialPoint = state.pointOf(screen.field)
                ?: state.originPoint
                ?: state.destinationPoint
                ?: DEFAULT_MAP_POINT,
            otherPickups = state.riders
                .filterNot { LocationField.RiderPickup(it.id) == screen.field }
                .mapNotNull { it.pickupPoint },
            pick = state.mapPick,
            cameraTarget = state.mapCameraTarget,
            query = state.searchQuery,
            search = state.addressSearch,
            onQueryChange = { onIntent(FuelCostIntent.SearchQueryChanged(it)) },
            onSearch = { onIntent(FuelCostIntent.SearchSubmitted) },
            onSelectPlace = { onIntent(FuelCostIntent.MapPlaceSelected(it)) },
            onCameraIdle = { onIntent(FuelCostIntent.MapCameraIdle(it)) },
            onConfirm = { onIntent(FuelCostIntent.MapPickConfirmed) },
            onUseAddress = { onIntent(FuelCostIntent.OpenAddressSearch(screen.field)) },
            onBack = onBack,
        )
    }
}
