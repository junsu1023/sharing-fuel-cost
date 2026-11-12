package com.jskim.sharingfuelcost.data.vehicle

import com.jskim.sharingfuelcost.data.kakao.safeLookup
import com.jskim.sharingfuelcost.data.relay.RelayClient
import com.jskim.sharingfuelcost.domain.fuel.FuelEconomy
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.fuel.VehicleFuelEconomy
import com.jskim.sharingfuelcost.domain.fuel.VehicleRepository
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.LookupResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query
import java.math.BigDecimal

private const val PAGE_SIZE = 100
// 2026-11-12 기준 115건이다.
private const val PHEV_PAGE_SIZE = 1000
private const val RESULT_OK = "00"
private const val RESULT_NO_DATA = "03"
private val NUMBER = Regex("""\d+(\.\d+)?""")

internal interface VehicleService {
    @GET("B553530/CAREFF/CAREFF_LIST")
    suspend fun search(
        @Query("q2") modelName: String,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = PAGE_SIZE,
        @Query("apiType") apiType: String = "json",
    ): JsonElement
}

/** 한국에너지공단 자동차 표시연비 정보(odcloud). 등급 조건으로 플러그인 하이브리드만 받는다. */
internal interface PlugInHybridService {
    @GET("api/15083023/v1/uddi:c5d3f252-f55b-469c-8573-e53160dd9e69")
    suspend fun plugInHybrids(
        @Query("cond[등급::EQ]") grade: String = "PHEV",
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = PHEV_PAGE_SIZE,
    ): JsonElement
}

/** 실제 응답은 response.body.items.item[]이다. 결과가 없으면 item이 없고, 한 건일 때를 대비해 단일 객체도 받는다. */
internal fun JsonElement.toVehicles(): LookupResult<List<VehicleFuelEconomy>> {
    val root = this as? JsonObject ?: return LookupResult.Failure(LookupFailure.UNEXPECTED)
    val response = root["response"] as? JsonObject ?: root
    val code = (response["header"] as? JsonObject)?.text("resultCode")
    if (code == RESULT_NO_DATA) return LookupResult.Success(emptyList())
    if (code != null && code != RESULT_OK) return LookupResult.Failure(LookupFailure.UNEXPECTED)

    val body = response["body"] as? JsonObject ?: return LookupResult.Failure(LookupFailure.UNEXPECTED)
    val items = body["items"].let { (it as? JsonObject)?.get("item") ?: it }
    val rows = when (items) {
        is JsonArray -> items.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(items)
        else -> emptyList()
    }
    return LookupResult.Success(rows.mapNotNull { it.toVehicle() })
}

private fun JsonObject.toVehicle(): VehicleFuelEconomy? {
    val fuelName = text("FUEL_NM").orEmpty()
    val fuelTypes = fuelTypesOf(fuelName).ifEmpty { return null }
    val displayEfficiency = text("DISPLAY_EFF")?.toPositiveNumber()
    val economies = if (isPlugInHybrid(fuelName)) {
        // 플러그인 하이브리드의 DISPLAY_EFF는 연료 연비(km/L)다. 전비는 저장소가 odcloud에서 채운다.
        fuelTypes.map { FuelEconomy(it, if (it == FuelType.ELECTRIC) null else displayEfficiency) }
    } else {
        listOf(FuelEconomy(fuelTypes.single(), displayEfficiency ?: return null))
    }
    return VehicleFuelEconomy(
        maker = text("COMP_NM").orEmpty(),
        model = text("MODEL_NM").orEmpty(),
        year = text("YEAR").orEmpty(),
        fuelName = fuelName,
        economies = economies,
    )
}

private fun String.toPositiveNumber(): BigDecimal? = NUMBER.find(this)?.value?.toBigDecimalOrNull()
    ?.takeIf { it.signum() > 0 }
    ?.let { BigDecimal(it.stripTrailingZeros().toPlainString()) }

/** `전기+휘발유`처럼 전기와 다른 연료를 함께 쓰는 차. */
internal fun isPlugInHybrid(fuelName: String): Boolean = fuelName.contains("전기") && fuelName.contains("+")

/**
 * 연료명에 든 연료를 전기, 내연기관 연료 순으로 돌려준다. `전기+휘발유`는 [전기, 휘발유]다.
 * 수소와 값을 모르는 연료는 빈 목록이다.
 */
internal fun fuelTypesOf(fuelName: String): List<FuelType> = buildList {
    if (fuelName.contains("전기")) add(FuelType.ELECTRIC)
    when {
        fuelName.contains("LPG", ignoreCase = true) -> add(FuelType.LPG)
        fuelName.contains("경유") || fuelName.contains("디젤") -> add(FuelType.DIESEL)
        fuelName.contains("휘발유") || fuelName.contains("가솔린") -> add(FuelType.GASOLINE)
    }
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** 두 API의 모델명은 띄어쓰기만 다른 경우가 있어 공백을 빼고 비교한다. */
internal fun modelKey(model: String): String = model.filterNot { it.isWhitespace() }.lowercase()

/**
 * odcloud 응답 data[]에서 모델명 → 전비. 복합_연비는 "2.7 / 7.4"(전비 km/kWh / 휘발유 km/L)라 앞 값을 쓴다.
 * 같은 모델명이 여러 건이면 처음 값을 쓴다.
 */
internal fun JsonElement.toPlugInHybridEfficiencies(): Map<String, BigDecimal>? {
    val rows = ((this as? JsonObject)?.get("data") as? JsonArray) ?: return null
    return rows.mapNotNull { row ->
        val obj = row as? JsonObject ?: return@mapNotNull null
        val model = obj.text("모델명") ?: return@mapNotNull null
        val efficiency = obj.text("복합_연비")?.substringBefore("/")?.toPositiveNumber() ?: return@mapNotNull null
        modelKey(model) to efficiency
    }.distinctBy { it.first }.toMap()
}

internal class DataGoKrVehicleRepository(
    private val service: VehicleService,
    private val plugInHybridService: PlugInHybridService,
    private val isConfigured: Boolean,
) : VehicleRepository {

    // 앱이 떠 있는 동안 한 번만 받는다. 받지 못하면 다음 검색에서 다시 시도한다.
    private var plugInHybridEfficiencies: Map<String, BigDecimal>? = null

    override suspend fun search(modelName: String): LookupResult<List<VehicleFuelEconomy>> {
        if (!isConfigured) return LookupResult.Failure(LookupFailure.NOT_CONFIGURED)
        val result = safeLookup { service.search(modelName).toVehicles() }
        if (result !is LookupResult.Success || result.value.none { isPlugInHybrid(it.fuelName) }) return result
        // 전비를 받지 못해도 검색 결과는 보여 주고, 전비는 사용자가 입력한다.
        val efficiencies = plugInHybridEfficiencies() ?: return result
        return LookupResult.Success(
            result.value.map { vehicle ->
                if (isPlugInHybrid(vehicle.fuelName)) {
                    val electric = efficiencies[modelKey(vehicle.model)]
                    vehicle.copy(
                        economies = vehicle.economies.map {
                            if (it.fuelType == FuelType.ELECTRIC) it.copy(kmPerLiter = electric) else it
                        },
                    )
                } else {
                    vehicle
                }
            },
        )
    }

    private suspend fun plugInHybridEfficiencies(): Map<String, BigDecimal>? {
        plugInHybridEfficiencies?.let { return it }
        val loaded = runCatching { plugInHybridService.plugInHybrids().toPlugInHybridEfficiencies() }.getOrNull()
        plugInHybridEfficiencies = loaded
        return loaded
    }
}

/** 공공데이터포털 표시연비 구현을 만든다. 서비스 키는 중계 서버가 붙인다. */
class VehicleDataSource(relay: RelayClient) {

    val vehicleRepository: VehicleRepository = DataGoKrVehicleRepository(
        relay.retrofit("data-go-kr").create(),
        relay.retrofit("odcloud").create(),
        relay.isConfigured,
    )
}
