package com.jskim.sharingfuelcost.presentation.fuelcost

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jskim.sharingfuelcost.domain.settlement.ParticipantShare
import com.jskim.sharingfuelcost.domain.settlement.SettlementMethod
import com.jskim.sharingfuelcost.domain.settlement.SettlementResult
import com.jskim.sharingfuelcost.presentation.R
import com.jskim.sharingfuelcost.presentation.theme.SharingFuelCostTheme
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultScreen(
    places: List<String>,
    kmPerLiter: String,
    wonPerLiter: String,
    result: SettlementResult,
    onBack: () -> Unit,
    title: String = stringResource(R.string.result_title),
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied)
    val methodName = stringResource(result.method.labelRes())
    val shareText = buildShareText(context, places, result, methodName)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.cd_delete_record))
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomActions(
                tonalLabel = stringResource(R.string.action_copy),
                onTonal = {
                    copyToClipboard(context, shareText)
                    // Android 13부터는 시스템이 복사 확인을 띄우므로 겹치지 않게 12L 이하에서만 알린다.
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    }
                },
                filledLabel = stringResource(R.string.action_share),
                onFilled = { share(context, shareText) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FuelSection(title = null) {
                Column {
                    Text(
                        text = formatRoute(places),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.result_total_label),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Text(
                        text = stringResource(R.string.amount_won, formatWon(result.totalCostWon)),
                        style = MaterialTheme.typography.displayLarge,
                    )
                }
            }
            FuelSection(stringResource(result.method.headingRes())) {
                result.shares.forEach { share -> ShareRow(share) }
            }
            FuelSection(title = null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailLine(
                        stringResource(R.string.label_total_distance),
                        stringResource(R.string.distance_km, formatKm(result.totalDistanceMeters)),
                    )
                    DetailLine(
                        stringResource(R.string.label_fuel_used),
                        stringResource(R.string.fuel_liters, formatLiters(result.fuelLiters)),
                    )
                    DetailLine(stringResource(R.string.label_method), methodName)
                    Text(
                        text = stringResource(
                            R.string.result_formula,
                            formatKm(result.totalDistanceMeters),
                            kmPerLiter,
                            wonPerLiter,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareRow(share: ParticipantShare) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val name = share.name ?: stringResource(R.string.role_driver)
        NameAvatar(name)
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (share.name == null) {
                    stringResource(R.string.distance_km, formatKm(share.distanceMeters))
                } else {
                    stringResource(
                        R.string.result_participant_meta,
                        stringResource(R.string.role_rider),
                        formatKm(share.distanceMeters),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.amount_won, formatWon(share.amountWon)),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun SettlementMethod.labelRes(): Int = when (this) {
    SettlementMethod.EQUAL -> R.string.method_equal
    SettlementMethod.DISTANCE_PROPORTIONAL -> R.string.method_proportional
}

private fun SettlementMethod.headingRes(): Int = when (this) {
    SettlementMethod.EQUAL -> R.string.result_heading_equal
    SettlementMethod.DISTANCE_PROPORTIONAL -> R.string.result_heading_proportional
}

private fun buildShareText(
    context: Context,
    places: List<String>,
    result: SettlementResult,
    methodName: String,
): String = buildList {
    add(formatRoute(places))
    add(context.getString(R.string.share_total_distance, formatKm(result.totalDistanceMeters)))
    add(context.getString(R.string.share_fuel_used, formatLiters(result.fuelLiters)))
    add(context.getString(R.string.share_total_cost, formatWon(result.totalCostWon)))
    add(context.getString(R.string.share_method, methodName))
    result.shares.forEach {
        val name = it.name ?: context.getString(R.string.role_driver)
        add(context.getString(R.string.share_participant, name, formatKm(it.distanceMeters), formatWon(it.amountWon)))
    }
}.joinToString("\n")

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.result_title), text))
}

private fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

@Preview(name = "라이트", heightDp = 800)
@Preview(name = "다크", heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResultScreenPreview() {
    SharingFuelCostTheme {
        ResultScreen(
            places = listOf("강남역 2번 출구", "사당역", "수원역", "속초해수욕장"),
            kmPerLiter = "12.5",
            wonPerLiter = "1650",
            result = SettlementResult(
                totalDistanceMeters = 250_000,
                fuelLiters = BigDecimal("20"),
                totalCostWon = 33_000,
                method = SettlementMethod.DISTANCE_PROPORTIONAL,
                shares = listOf(
                    ParticipantShare(null, 250_000, 13_750),
                    ParticipantShare("민지", 240_000, 13_200),
                    ParticipantShare("현우", 110_000, 6_050),
                ),
            ),
            onBack = {},
        )
    }
}
