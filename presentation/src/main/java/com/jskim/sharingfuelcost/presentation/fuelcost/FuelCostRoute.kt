package com.jskim.sharingfuelcost.presentation.fuelcost

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun FuelCostRoute(viewModel: FuelCostViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onIntent = viewModel::onIntent
    val onBack = { onIntent(FuelCostIntent.Back) }

    BackHandler(enabled = state.screen != FuelCostScreen.Input, onBack = onBack)

    when (val screen = state.screen) {
        FuelCostScreen.Input -> InputScreen(state, onIntent)
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
            onUseAddress = {
                onIntent(FuelCostIntent.OpenAddressSearch(LocationField.RiderPickup(screen.riderId)))
            },
            onBack = onBack,
        )
    }
}
