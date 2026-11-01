package com.jskim.sharingfuelcost.presentation.fuelcost

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

internal fun formatWon(amount: Long): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(amount)

internal fun formatKm(meters: Long): String =
    BigDecimal(meters)
        .divide(BigDecimal(1000), 1, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

/** 입력 필드용 km 값. 미터 값을 잃지 않도록 반올림하지 않는다. */
internal fun metersToKmInput(meters: Long): String =
    BigDecimal(meters).movePointLeft(3).stripTrailingZeros().toPlainString()

/** yyyyMMdd를 yyyy.MM.dd로 바꾼다. 형식이 다르면 그대로 둔다. */
internal fun formatTradeDate(date: String): String =
    if (date.length == 8) "${date.take(4)}.${date.substring(4, 6)}.${date.substring(6)}" else date

/** 출발지, 경유 순서대로의 탑승 위치, 목적지를 잇는다. */
internal fun formatRoute(state: FuelCostUiState): String =
    (listOf(state.origin) + state.riders.map { it.pickup } + state.destination).joinToString(" → ")

internal fun formatLiters(liters: BigDecimal): String =
    liters.setScale(1, RoundingMode.HALF_UP).toPlainString()
