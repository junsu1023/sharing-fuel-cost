package com.jskim.sharingfuelcost.domain.fuel

import com.jskim.sharingfuelcost.domain.route.LookupResult
import java.math.BigDecimal

data class VehicleFuelEconomy(
    val maker: String,
    val model: String,
    val year: String,
    val fuelName: String,
    val fuelType: FuelType,
    /** 전기차와 플러그인 하이브리드는 전비(km/kWh)다. 플러그인 하이브리드의 전비를 찾지 못하면 null이다. */
    val kmPerLiter: BigDecimal?,
)

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
