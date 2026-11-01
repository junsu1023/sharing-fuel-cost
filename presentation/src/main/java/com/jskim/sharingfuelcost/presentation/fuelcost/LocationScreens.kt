package com.jskim.sharingfuelcost.presentation.fuelcost

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jskim.sharingfuelcost.domain.route.GeoPoint
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.Place
import com.jskim.sharingfuelcost.presentation.R
import com.jskim.sharingfuelcost.presentation.theme.SharingFuelCostTheme

@Composable
internal fun AddressSearchScreen(
    query: String,
    search: AddressSearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (Place) -> Unit,
    onUseTyped: () -> Unit,
    onBack: () -> Unit,
) {
    LocationScaffold(stringResource(R.string.address_search_title), onBack) {
        FuelSection(title = null) {
            FuelTextField(
                value = query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.label_address),
                onSearch = onSearch,
            )
            when (search) {
                AddressSearchState.Idle -> Unit
                AddressSearchState.Loading -> CircularProgressIndicator()
                is AddressSearchState.Results -> {
                    if (search.places.isEmpty()) {
                        Text(stringResource(R.string.address_search_empty), style = MaterialTheme.typography.bodyMedium)
                        UseTypedButton(query, onUseTyped)
                    }
                    search.places.forEach { place -> PlaceRow(place) { onSelect(place) } }
                }
                is AddressSearchState.Failed -> {
                    Text(
                        text = stringResource(search.reason.messageRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    UseTypedButton(query, onUseTyped)
                }
            }
        }
    }
}

@Composable
private fun UseTypedButton(query: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = query.isNotBlank(),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.action_use_address)) }
}

@Composable
internal fun PlaceRow(place: Place, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.heightIn(min = 56.dp).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(place.name, style = MaterialTheme.typography.titleMedium)
            if (place.address != place.name) {
                Text(
                    text = place.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

internal fun LookupFailure.messageRes(): Int = when (this) {
    LookupFailure.NOT_CONFIGURED -> R.string.failure_not_configured
    LookupFailure.NO_ROUTE -> R.string.failure_no_route
    LookupFailure.UNAUTHORIZED -> R.string.failure_unauthorized
    LookupFailure.QUOTA_EXCEEDED -> R.string.failure_quota
    LookupFailure.NETWORK -> R.string.failure_network
    LookupFailure.LOCATION_UNAVAILABLE -> R.string.failure_location
    LookupFailure.UNEXPECTED -> R.string.failure_unexpected
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) { content() }
    }
}

@Preview(name = "라이트, 결과")
@Preview(name = "다크, 결과", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddressSearchPreview() {
    SharingFuelCostTheme {
        AddressSearchScreen(
            query = "속초해수욕장",
            search = AddressSearchState.Results(
                listOf(Place("속초해수욕장", "강원 속초시 해오름로 190", GeoPoint(128.6, 38.19))),
            ),
            onQueryChange = {},
            onSearch = {},
            onSelect = {},
            onUseTyped = {},
            onBack = {},
        )
    }
}

@Preview(name = "라이트, 실패")
@Composable
private fun AddressSearchFailedPreview() {
    SharingFuelCostTheme {
        AddressSearchScreen(
            query = "속초해수욕장",
            search = AddressSearchState.Failed(LookupFailure.NETWORK),
            onQueryChange = {},
            onSearch = {},
            onSelect = {},
            onUseTyped = {},
            onBack = {},
        )
    }
}
