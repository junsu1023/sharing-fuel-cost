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
import com.jskim.sharingfuelcost.domain.fuel.FuelEconomy
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
                text = vehicleDetailText(vehicle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

internal fun VehicleFuelEconomy.displayName(): String = "$maker $model".trim()

/** "2024 · 휘발유 · 15.2 km/L". 최신 목록에서 온 차는 출시년도가 없어 빼고 표시한다. */
@Composable
internal fun vehicleDetailText(vehicle: VehicleFuelEconomy): String =
    listOf(vehicle.year, vehicle.fuelName, vehicleEfficiencyText(vehicle))
        .filter { it.isNotBlank() }
        .joinToString(" · ")

/** "15.2 km/L", 플러그인 하이브리드는 "3.5 km/kWh / 11.7 km/L". 찾지 못한 전비는 정보 없음으로 표시한다. */
@Composable
internal fun vehicleEfficiencyText(vehicle: VehicleFuelEconomy): String {
    val kmPerKwh = stringResource(R.string.unit_km_per_kwh)
    val kmPerLiter = stringResource(R.string.unit_km_per_liter)
    val missing = stringResource(R.string.vehicle_efficiency_missing)
    val template = stringResource(R.string.vehicle_efficiency)
    return vehicle.economies.joinToString(" / ") { economy ->
        val efficiency = economy.kmPerLiter ?: return@joinToString missing
        val unit = if (economy.fuelType == FuelType.ELECTRIC) kmPerKwh else kmPerLiter
        template.format(efficiency.toPlainString(), unit)
    }
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
                    VehicleFuelEconomy(
                        "현대", "아반떼", "2024", "휘발유",
                        listOf(FuelEconomy(FuelType.GASOLINE, "15.2".toBigDecimal())),
                    ),
                    VehicleFuelEconomy(
                        "볼보", "XC60T8 AWD", "2024", "전기+휘발유",
                        listOf(
                            FuelEconomy(FuelType.ELECTRIC, "3.5".toBigDecimal()),
                            FuelEconomy(FuelType.GASOLINE, "11.7".toBigDecimal()),
                        ),
                    ),
                    VehicleFuelEconomy(
                        "BMW", "BMW i8", "2019", "전기+휘발유",
                        listOf(FuelEconomy(FuelType.ELECTRIC, null), FuelEconomy(FuelType.GASOLINE, "13.6".toBigDecimal())),
                    ),
                ),
            ),
            onQueryChange = {},
            onSearch = {},
            onSelect = {},
            onBack = {},
        )
    }
}
