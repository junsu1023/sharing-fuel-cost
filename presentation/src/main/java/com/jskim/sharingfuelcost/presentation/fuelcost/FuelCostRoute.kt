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
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jskim.sharingfuelcost.domain.route.GeoPoint

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

/** 출발지를 모를 때 지도에 처음 보여 줄 위치(서울시청) */
private val DEFAULT_MAP_POINT = GeoPoint(longitude = 126.9784, latitude = 37.5665)

private fun Context.hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
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

    // 이미 권한이 있으면 출발지를 현재 위치로 채운다. 권한 요청은 사용자가 버튼을 누를 때 한다.
    LaunchedEffect(Unit) {
        val current = viewModel.uiState.value
        if (current.origin.isBlank() && current.currentLocation == CurrentLocationState.Idle &&
            context.hasLocationPermission()
        ) {
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
            initialPoint = state.riders.firstOrNull { it.id == screen.riderId }?.pickupPoint
                ?: state.originPoint
                ?: state.destinationPoint
                ?: DEFAULT_MAP_POINT,
            otherPickups = state.riders.filter { it.id != screen.riderId }.mapNotNull { it.pickupPoint },
            pick = state.mapPick,
            onCameraIdle = { onIntent(FuelCostIntent.MapCameraIdle(it)) },
            onConfirm = { onIntent(FuelCostIntent.MapPickConfirmed) },
            onUseAddress = {
                onIntent(FuelCostIntent.OpenAddressSearch(LocationField.RiderPickup(screen.riderId)))
            },
            onBack = onBack,
        )
    }
}
