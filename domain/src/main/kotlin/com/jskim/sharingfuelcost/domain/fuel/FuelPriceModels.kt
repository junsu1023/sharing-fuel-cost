package com.jskim.sharingfuelcost.domain.fuel

import com.jskim.sharingfuelcost.domain.route.LookupResult
import java.math.BigDecimal

/** 전기는 연비 대신 전비(km/kWh), 유가 대신 충전 단가(원/kWh)를 쓴다. 계산식은 같다. */
enum class FuelType { GASOLINE, DIESEL, LPG, ELECTRIC }

data class AverageFuelPrice(
    val type: FuelType,
    val wonPerLiter: BigDecimal,
    /** 가격 기준일, yyyyMMdd */
    val tradeDate: String,
)

/**
 * 전기차 충전 단가. 평균 충전 단가를 주는 API가 없어 공공 급속충전기(50~100kW) 요금을 기본값으로 쓴다.
 * 요금이 바뀌면 이 값을 고친다. (docs/decisions.md 2026-11-12)
 */
val PUBLIC_CHARGE_PRICE = AverageFuelPrice(FuelType.ELECTRIC, BigDecimal("325.6"), "20260801")

interface FuelPriceRepository {
    /** 전국 평균 유가를 하루 한 번 새로 받고, 받지 못하면 마지막으로 저장한 값을 돌려준다. */
    suspend fun averagePrices(): LookupResult<List<AverageFuelPrice>>
}
