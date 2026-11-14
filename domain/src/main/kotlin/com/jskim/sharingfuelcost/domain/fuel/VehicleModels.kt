package com.jskim.sharingfuelcost.domain.fuel

import com.jskim.sharingfuelcost.domain.route.LookupResult
import java.math.BigDecimal

/** 연료 하나의 표시연비. 전기면 전비(km/kWh)다. 값을 찾지 못하면 [kmPerLiter]가 null이다. */
data class FuelEconomy(
    val fuelType: FuelType,
    val kmPerLiter: BigDecimal?,
)

data class VehicleFuelEconomy(
    val maker: String,
    val model: String,
    val year: String,
    val fuelName: String,
    /** 이 차가 쓸 수 있는 연료. 플러그인 하이브리드는 전기와 연료 두 개이고, 첫 번째를 기본으로 고른다. */
    val economies: List<FuelEconomy>,
) {
    val fuelTypes: List<FuelType> get() = economies.map { it.fuelType }

    fun kmPerLiter(fuelType: FuelType): BigDecimal? = economies.firstOrNull { it.fuelType == fuelType }?.kmPerLiter
}

interface VehicleRepository {
    /** 모델명으로 표시연비를 찾는다. 단위가 맞지 않는 수소차는 제외한다. */
    suspend fun search(modelName: String): LookupResult<List<VehicleFuelEconomy>>
}

/** 저장해 둔 내 차. 차종을 검색하지 않고 연비만 입력했으면 [vehicle]이 없다. */
data class MyCar(
    val vehicle: VehicleFuelEconomy?,
    val kmPerLiter: BigDecimal,
    val fuelType: FuelType,
)

/** 내 차는 한 대만 저장한다. 새로 저장하면 이전 차를 덮어쓴다. */
interface MyCarRepository {
    suspend fun load(): MyCar?
    suspend fun save(car: MyCar)
    suspend fun clear()
}
