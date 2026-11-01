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

internal fun formatLiters(liters: BigDecimal): String =
    liters.setScale(1, RoundingMode.HALF_UP).toPlainString()
