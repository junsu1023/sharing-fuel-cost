package com.jskim.sharingfuelcost.domain.fuel

import com.jskim.sharingfuelcost.domain.route.LookupResult
import java.math.BigDecimal

enum class FuelType { GASOLINE, DIESEL, LPG }

data class AverageFuelPrice(
    val type: FuelType,
    val wonPerLiter: BigDecimal,
    /** 가격 기준일, yyyyMMdd */
    val tradeDate: String,
)

interface FuelPriceRepository {
    /** 전국 평균 유가를 하루 한 번 새로 받고, 받지 못하면 마지막으로 저장한 값을 돌려준다. */
    suspend fun averagePrices(): LookupResult<List<AverageFuelPrice>>
}
