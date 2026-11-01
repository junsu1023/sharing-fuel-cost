package com.jskim.sharingfuelcost.presentation.fuelcost

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FuelCostRoute(viewModel: FuelCostViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onIntent = viewModel::onIntent
    val onBack = { onIntent(FuelCostIntent.Back) }

    BackHandler(enabled = state.screen != FuelCostScreen.Input, onBack = onBack)

    when (val screen = state.screen) {
        FuelCostScreen.Input -> InputScreen(state, onIntent)
        FuelCostScreen.Result -> state.result?.let { ResultScreen(state, it, onBack) }
        is FuelCostScreen.AddressSearch -> AddressSearchScreen(
            query = state.searchQuery,
            onQueryChange = { onIntent(FuelCostIntent.SearchQueryChanged(it)) },
            onConfirm = { onIntent(FuelCostIntent.ConfirmAddress) },
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
