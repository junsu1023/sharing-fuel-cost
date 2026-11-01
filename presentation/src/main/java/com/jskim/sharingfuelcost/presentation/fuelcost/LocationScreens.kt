package com.jskim.sharingfuelcost.presentation.fuelcost

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.jskim.sharingfuelcost.presentation.R
import com.jskim.sharingfuelcost.presentation.theme.SharingFuelCostTheme

/** 주소 검색 API는 미연동이다. 연결 전까지 입력한 주소를 그대로 사용한다. */
@Composable
internal fun AddressSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    LocationScaffold(stringResource(R.string.address_search_title), onBack) {
        FuelSection(title = null) {
            FuelTextField(
                value = query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.label_address),
            )
            Text(
                text = stringResource(R.string.address_search_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onConfirm,
                enabled = query.isNotBlank(),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.action_use_address)) }
        }
    }
}

/** 지도 SDK는 미연동이다. 연결 전까지 주소 입력으로 대체한다. */
@Composable
internal fun MapPickerScreen(onUseAddress: () -> Unit, onBack: () -> Unit) {
    LocationScaffold(stringResource(R.string.map_picker_title), onBack) {
        FuelSection(title = null) {
            Text(
                text = stringResource(R.string.map_unavailable),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = onUseAddress,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.action_enter_by_address)) }
        }
    }
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) { content() }
    }
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddressSearchPreview() {
    SharingFuelCostTheme { AddressSearchScreen("속초해수욕장", {}, {}, {}) }
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MapPickerPreview() {
    SharingFuelCostTheme { MapPickerScreen({}, {}) }
}
