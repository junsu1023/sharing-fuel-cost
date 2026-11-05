package com.jskim.sharingfuelcost.presentation.fuelcost

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jskim.sharingfuelcost.domain.history.MAX_SETTLEMENT_RECORDS
import com.jskim.sharingfuelcost.domain.history.SettlementRecord
import com.jskim.sharingfuelcost.domain.settlement.ParticipantShare
import com.jskim.sharingfuelcost.domain.settlement.SettlementMethod
import com.jskim.sharingfuelcost.domain.settlement.SettlementResult
import com.jskim.sharingfuelcost.presentation.R
import com.jskim.sharingfuelcost.presentation.theme.SharingFuelCostTheme
import java.math.BigDecimal

/** [records]가 null이면 아직 읽는 중이다. */
@Composable
internal fun HistoryScreen(
    records: List<SettlementRecord>?,
    onOpen: (SettlementRecord) -> Unit,
    onBack: () -> Unit,
) {
    LocationScaffold(stringResource(R.string.history_title), onBack) {
        FuelSection(title = null) {
            when {
                records == null -> CircularProgressIndicator()
                records.isEmpty() -> Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    records.forEach { record -> RecordRow(record) { onOpen(record) } }
                    Text(
                        text = stringResource(R.string.history_limit, MAX_SETTLEMENT_RECORDS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: SettlementRecord, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatRecordTime(record.createdAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatRoute(listOf(record.places.first(), record.places.last())),
                    style = MaterialTheme.typography.titleMedium,
                )
                val riders = record.riderNames
                Text(
                    text = if (riders.isEmpty()) {
                        stringResource(R.string.history_no_riders)
                    } else {
                        stringResource(R.string.history_riders, riders.joinToString(", "))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.amount_won, formatWon(record.result.totalCostWon)),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

private val previewRecord = SettlementRecord(
    createdAtMillis = 1_762_170_000_000,
    places = listOf("강남역 2번 출구", "사당역", "속초해수욕장"),
    kmPerLiter = BigDecimal("12.5"),
    wonPerLiter = BigDecimal("1650"),
    result = SettlementResult(
        totalDistanceMeters = 250_000,
        fuelLiters = BigDecimal("20"),
        totalCostWon = 33_000,
        method = SettlementMethod.EQUAL,
        shares = listOf(ParticipantShare(null, 250_000, 16_500), ParticipantShare("민지", 240_000, 16_500)),
    ),
)

@Preview(name = "라이트, 기록")
@Preview(name = "다크, 기록", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryScreenPreview() {
    SharingFuelCostTheme {
        HistoryScreen(
            records = listOf(previewRecord, previewRecord.copy(createdAtMillis = 1_761_900_000_000, places = listOf("집", "회사"))),
            onOpen = {},
            onBack = {},
        )
    }
}

@Preview(name = "라이트, 비어 있음")
@Composable
private fun HistoryScreenEmptyPreview() {
    SharingFuelCostTheme { HistoryScreen(records = emptyList(), onOpen = {}, onBack = {}) }
}
