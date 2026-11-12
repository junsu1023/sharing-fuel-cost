package com.jskim.sharingfuelcost.presentation.fuelcost

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.fuel.VehicleFuelEconomy
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.presentation.R
import com.jskim.sharingfuelcost.presentation.theme.SharingFuelCostTheme

@Composable
internal fun VehicleSearchScreen(
    query: String,
    search: VehicleSearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (VehicleFuelEconomy) -> Unit,
    onBack: () -> Unit,
) {
    LocationScaffold(stringResource(R.string.vehicle_search_title), onBack) {
        FuelSection(title = null) {
            FuelTextField(
                value = query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.label_vehicle_model),
                onSearch = onSearch,
            )
            when (search) {
                VehicleSearchState.Idle -> Text(
                    text = stringResource(R.string.vehicle_search_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VehicleSearchState.Loading -> CircularProgressIndicator()
                is VehicleSearchState.Results -> {
                    if (search.vehicles.isEmpty()) {
                        Text(
                            text = stringResource(R.string.vehicle_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    search.vehicles.forEach { vehicle -> VehicleRow(vehicle) { onSelect(vehicle) } }
                }
                is VehicleSearchState.Failed -> Text(
                    text = stringResource(
                        if (search.reason == LookupFailure.NOT_CONFIGURED) {
                            R.string.vehicle_not_configured
                        } else {
                            search.reason.messageRes()
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun VehicleRow(vehicle: VehicleFuelEconomy, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.heightIn(min = 56.dp).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(vehicle.displayName(), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    R.string.vehicle_row_detail,
                    vehicle.year,
                    vehicle.fuelName,
                    vehicleEfficiencyText(vehicle),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

internal fun VehicleFuelEconomy.displayName(): String = "$maker $model".trim()

/** "15.2 km/L", "5.3 km/kWh". 전비를 찾지 못한 플러그인 하이브리드는 직접 입력을 안내한다. */
@Composable
internal fun vehicleEfficiencyText(vehicle: VehicleFuelEconomy): String {
    val efficiency = vehicle.kmPerLiter ?: return stringResource(R.string.vehicle_efficiency_missing)
    val unit = if (vehicle.fuelType == FuelType.ELECTRIC) R.string.unit_km_per_kwh else R.string.unit_km_per_liter
    return stringResource(R.string.vehicle_efficiency, efficiency.toPlainString(), stringResource(unit))
}

@Preview(name = "라이트, 결과")
@Preview(name = "다크, 결과", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VehicleSearchPreview() {
    SharingFuelCostTheme {
        VehicleSearchScreen(
            query = "아반떼",
            search = VehicleSearchState.Results(
                listOf(
                    VehicleFuelEconomy("현대", "아반떼", "2024", "휘발유", FuelType.GASOLINE, "15.2".toBigDecimal()),
                    VehicleFuelEconomy("기아", "EV6 기본형 4WD 19인치", "2024", "전기", FuelType.ELECTRIC, "5.3".toBigDecimal()),
                    VehicleFuelEconomy("BMW", "BMW i8", "2019", "전기+휘발유", FuelType.ELECTRIC, null),
                ),
            ),
            onQueryChange = {},
            onSearch = {},
            onSelect = {},
            onBack = {},
        )
    }
}
