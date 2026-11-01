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

internal fun formatLiters(liters: BigDecimal): String =
    liters.setScale(1, RoundingMode.HALF_UP).toPlainString()
