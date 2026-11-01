package com.jskim.sharingfuelcost.presentation.fuelcost

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.settlement.SettlementMethod
import com.jskim.sharingfuelcost.presentation.R
import com.jskim.sharingfuelcost.presentation.theme.SharingFuelCostTheme
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InputScreen(
    state: FuelCostUiState,
    onIntent: (FuelCostIntent) -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.input_title)) }) },
        bottomBar = {
            Column(modifier = Modifier.imePadding()) {
                BottomActions(
                    tonalLabel = stringResource(R.string.action_lookup_distance),
                    onTonal = { onIntent(FuelCostIntent.DistanceLookupClicked) },
                    tonalEnabled = state.distanceLookup != DistanceLookupState.Loading,
                    filledLabel = stringResource(R.string.action_calculate),
                    onFilled = { onIntent(FuelCostIntent.Calculate) },
                    filledEnabled = state.canCalculate,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RouteSection(state, onIntent, onUseCurrentLocation)
            ParticipantsSection(state, onIntent)
            FuelSection(stringResource(R.string.section_fuel)) {
                FuelTypeSelector(state.fuelType, onIntent)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    FuelTextField(
                        value = state.kmPerLiter,
                        onValueChange = { onIntent(FuelCostIntent.KmPerLiterChanged(it)) },
                        label = stringResource(R.string.label_fuel_economy),
                        suffix = stringResource(R.string.unit_km_per_liter),
                        errorText = if (state.kmPerLiterInvalid) stringResource(R.string.error_positive) else null,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    FuelTextField(
                        value = state.wonPerLiter,
                        onValueChange = { onIntent(FuelCostIntent.WonPerLiterChanged(it)) },
                        label = stringResource(R.string.label_fuel_price),
                        suffix = stringResource(R.string.unit_won_per_liter),
                        errorText = if (state.wonPerLiterInvalid) stringResource(R.string.error_positive) else null,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                FuelPriceInfo(state, onIntent)
            }
            MethodSection(state.method, onIntent)
            DistanceSection(state, onIntent)
        }
    }
}

@Composable
private fun RouteSection(
    state: FuelCostUiState,
    onIntent: (FuelCostIntent) -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    FuelSection(stringResource(R.string.section_route)) {
        LocationRow(
            label = stringResource(R.string.label_origin),
            value = state.origin,
            placeholder = stringResource(R.string.placeholder_address),
            action = stringResource(R.string.action_search_address),
            onAction = { onIntent(FuelCostIntent.OpenAddressSearch(LocationField.Origin)) },
            iconRes = R.drawable.ic_my_location,
        )
        CurrentLocationRow(state.currentLocation, onUseCurrentLocation)
        LocationRow(
            label = stringResource(R.string.label_destination),
            value = state.destination,
            placeholder = stringResource(R.string.placeholder_address),
            action = stringResource(R.string.action_search_address),
            onAction = { onIntent(FuelCostIntent.OpenAddressSearch(LocationField.Destination)) },
            iconRes = R.drawable.ic_flag,
        )
    }
}

@Composable
private fun CurrentLocationRow(location: CurrentLocationState, onUse: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val isError = location is CurrentLocationState.Failed || location == CurrentLocationState.PermissionDenied
        Text(
            text = stringResource(
                when (location) {
                    CurrentLocationState.Idle -> R.string.current_location_reason
                    CurrentLocationState.Loading -> R.string.current_location_loading
                    CurrentLocationState.Success -> R.string.current_location_success
                    CurrentLocationState.PermissionDenied -> R.string.current_location_denied
                    is CurrentLocationState.Failed -> location.reason.messageRes()
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (location == CurrentLocationState.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            TextButton(onClick = onUse, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_use_current_location))
            }
        }
    }
}

@Composable
private fun ParticipantsSection(state: FuelCostUiState, onIntent: (FuelCostIntent) -> Unit) {
    FuelSection(stringResource(R.string.section_participants)) {
        state.riders.forEachIndexed { index, rider ->
            RiderItem(
                rider = rider,
                canMoveUp = index > 0,
                canMoveDown = index < state.riders.lastIndex,
                onIntent = onIntent,
            )
        }
        FilledTonalButton(
            onClick = { onIntent(FuelCostIntent.AddRider) },
            enabled = state.canAddRider,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.action_add_rider), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun RiderItem(
    rider: RiderInput,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onIntent: (FuelCostIntent) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NameAvatar(rider.name)
            FuelTextField(
                value = rider.name,
                onValueChange = { onIntent(FuelCostIntent.RiderNameChanged(rider.id, it)) },
                label = stringResource(R.string.label_rider),
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            IconButton(onClick = { onIntent(FuelCostIntent.MoveRider(rider.id, -1)) }, enabled = canMoveUp) {
                Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.cd_move_up))
            }
            IconButton(onClick = { onIntent(FuelCostIntent.MoveRider(rider.id, 1)) }, enabled = canMoveDown) {
                Icon(painterResource(R.drawable.ic_arrow_down), stringResource(R.string.cd_move_down))
            }
            IconButton(onClick = { onIntent(FuelCostIntent.RemoveRider(rider.id)) }) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cd_remove_rider))
            }
        }
        LocationRow(
            label = stringResource(R.string.label_pickup),
            value = rider.pickup,
            placeholder = stringResource(R.string.placeholder_pickup),
            action = stringResource(R.string.action_pick_on_map),
            onAction = { onIntent(FuelCostIntent.OpenMapPicker(rider.id)) },
            modifier = Modifier.padding(start = 52.dp),
        )
    }
}

@Composable
private fun FuelTypeSelector(selected: FuelType, onIntent: (FuelCostIntent) -> Unit) {
    val types = listOf(
        FuelType.GASOLINE to R.string.fuel_gasoline,
        FuelType.DIESEL to R.string.fuel_diesel,
        FuelType.LPG to R.string.fuel_lpg,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onIntent(FuelCostIntent.FuelTypeSelected(type)) },
                shape = SegmentedButtonDefaults.itemShape(index, types.size, MaterialTheme.shapes.medium),
                colors = methodColors(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

@Composable
private fun FuelPriceInfo(state: FuelCostUiState, onIntent: (FuelCostIntent) -> Unit) {
    val average = state.averageFuelPrice
    val fuelName = stringResource(
        when (state.fuelType) {
            FuelType.GASOLINE -> R.string.fuel_gasoline
            FuelType.DIESEL -> R.string.fuel_diesel
            FuelType.LPG -> R.string.fuel_lpg
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    average != null -> stringResource(
                        R.string.fuel_average_applied,
                        fuelName,
                        average.wonPerLiter.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                        formatTradeDate(average.tradeDate),
                    )
                    state.fuelPrice == FuelPriceState.Loading -> stringResource(R.string.fuel_price_loading)
                    state.fuelPrice == FuelPriceState.Failed(LookupFailure.NOT_CONFIGURED) ->
                        stringResource(R.string.fuel_price_not_configured)
                    else -> stringResource(R.string.fuel_price_failed)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (average != null) {
                Text(
                    text = stringResource(R.string.fuel_price_source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (average != null && state.wonPerLiterEdited) {
            TextButton(
                onClick = { onIntent(FuelCostIntent.ApplyAverageFuelPrice) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.action_apply_average_price)) }
        }
    }
}

@Composable
private fun MethodSection(method: SettlementMethod, onIntent: (FuelCostIntent) -> Unit) {
    FuelSection(stringResource(R.string.section_method)) {
        val shapes = MaterialTheme.shapes.medium
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = method == SettlementMethod.EQUAL,
                onClick = { onIntent(FuelCostIntent.MethodSelected(SettlementMethod.EQUAL)) },
                shape = SegmentedButtonDefaults.itemShape(0, 2, shapes),
                colors = methodColors(),
                modifier = Modifier.weight(5f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.method_equal), maxLines = 1) }
            SegmentedButton(
                selected = method == SettlementMethod.DISTANCE_PROPORTIONAL,
                onClick = { onIntent(FuelCostIntent.MethodSelected(SettlementMethod.DISTANCE_PROPORTIONAL)) },
                shape = SegmentedButtonDefaults.itemShape(1, 2, shapes),
                colors = methodColors(),
                modifier = Modifier.weight(7f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.method_proportional), maxLines = 1) }
        }
    }
}

@Composable
private fun methodColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@Composable
private fun DistanceSection(state: FuelCostUiState, onIntent: (FuelCostIntent) -> Unit) {
    FuelSection(stringResource(R.string.section_distance)) {
        val lookup = state.distanceLookup
        if (lookup == DistanceLookupState.Loading) {
            CircularProgressIndicator()
        } else {
            val isError = lookup is DistanceLookupState.Failed || lookup == DistanceLookupState.MissingLocation
            Text(
                text = stringResource(
                    when (lookup) {
                        is DistanceLookupState.Failed -> lookup.reason.messageRes()
                        DistanceLookupState.MissingLocation -> R.string.distance_missing_location
                        DistanceLookupState.Success -> R.string.distance_auto_filled
                        else -> R.string.distance_manual_hint
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FuelTextField(
            value = state.totalDistanceKm,
            onValueChange = { onIntent(FuelCostIntent.TotalDistanceChanged(it)) },
            label = stringResource(R.string.label_total_distance),
            suffix = stringResource(R.string.unit_km),
            errorText = if (state.totalDistanceInvalid) stringResource(R.string.error_positive) else null,
            keyboardType = KeyboardType.Decimal,
        )
        state.riders.forEach { rider ->
            FuelTextField(
                value = rider.distanceKm,
                onValueChange = { onIntent(FuelCostIntent.RiderDistanceChanged(rider.id, it)) },
                label = stringResource(
                    R.string.label_rider_distance,
                    rider.name.ifBlank { stringResource(R.string.label_rider) },
                ),
                suffix = stringResource(R.string.unit_km),
                errorText = if (state.riderDistanceInvalid(rider)) stringResource(R.string.error_rider_distance) else null,
                keyboardType = KeyboardType.Decimal,
            )
        }
    }
}

private val previewState = FuelCostUiState(
    origin = "강남역 2번 출구",
    destination = "속초해수욕장",
    riders = listOf(
        RiderInput(0, "민지", "사당역", "240"),
        RiderInput(1, "현우", "수원역", "300"),
    ),
    kmPerLiter = "12.5",
    wonPerLiter = "1650",
    method = SettlementMethod.DISTANCE_PROPORTIONAL,
    totalDistanceKm = "250",
    distanceLookup = DistanceLookupState.Failed(LookupFailure.NETWORK),
)

@Preview(name = "라이트, 오류", heightDp = 1400)
@Preview(name = "다크, 오류", heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InputScreenPreview() {
    SharingFuelCostTheme { InputScreen(previewState, {}, {}) }
}
