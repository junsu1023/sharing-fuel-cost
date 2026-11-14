package com.jskim.sharingfuelcost.data.vehicle

import com.jskim.sharingfuelcost.data.kakao.safeLookup
import com.jskim.sharingfuelcost.data.relay.RelayClient
import com.jskim.sharingfuelcost.domain.fuel.FuelEconomy
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.fuel.VehicleFuelEconomy
import com.jskim.sharingfuelcost.domain.fuel.VehicleRepository
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.LookupResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
private const val GRADE_PHEV = "PHEV"
private const val GRADE_FCEV = "FCEV"
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

/**
 * 한국에너지공단 자동차 표시연비 정보(odcloud, 2026-04-24). CAREFF_LIST에 없는 2025년 이후 전기차와
 * 플러그인 하이브리드 전비를 여기서 찾는다. 모델명 LIKE는 대소문자를 구분하지 않고 띄어쓰기는 구분한다.
 */
internal interface LatestFuelEconomyService {
    @GET("api/15083023/v1/uddi:c5d3f252-f55b-469c-8573-e53160dd9e69")
    suspend fun search(
        @Query("cond[모델명::LIKE]") modelName: String,
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = PAGE_SIZE,
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
 * odcloud 응답 data[]에서 연료를 확실히 알 수 있는 전기차와 플러그인 하이브리드만 고른다.
 * 유종 필드가 없어 내연기관 차는 휘발유, 경유, LPG를 구분할 수 없으므로 제외한다. 출시년도도 없다.
 * - 플러그인 하이브리드: 등급 PHEV, 복합_연비 "2.7 / 7.4"(전비 km/kWh / 휘발유 km/L)
 * - 전기차: 1회충전주행거리가 있고 등급이 PHEV, FCEV(수소)가 아님
 * 형식이 다르면 null이다.
 */
internal fun JsonElement.toLatestVehicles(): List<VehicleFuelEconomy>? {
    val rows = ((this as? JsonObject)?.get("data") as? JsonArray) ?: return null
    return rows.mapNotNull { row ->
        val obj = row as? JsonObject ?: return@mapNotNull null
        val model = obj.text("모델명")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val grade = obj.text("등급")?.trim()
        val combined = obj.text("복합_연비") ?: return@mapNotNull null
        val (fuelName, economies) = when {
            grade == GRADE_PHEV -> "전기+휘발유" to listOf(
                FuelEconomy(FuelType.ELECTRIC, combined.substringBefore("/").toPositiveNumber()),
                FuelEconomy(FuelType.GASOLINE, combined.substringAfter("/", "").toPositiveNumber()),
            )
            grade != GRADE_FCEV && obj.text("1회충전주행거리")?.toPositiveNumber() != null ->
                "전기" to listOf(FuelEconomy(FuelType.ELECTRIC, combined.toPositiveNumber() ?: return@mapNotNull null))
            else -> return@mapNotNull null
        }
        VehicleFuelEconomy(obj.text("제조(수입사)").orEmpty(), model, "", fuelName, economies)
    }.distinct()
}

internal class DataGoKrVehicleRepository(
    private val service: VehicleService,
    private val latestService: LatestFuelEconomyService,
    private val isConfigured: Boolean,
) : VehicleRepository {

    override suspend fun search(modelName: String): LookupResult<List<VehicleFuelEconomy>> {
        if (!isConfigured) return LookupResult.Failure(LookupFailure.NOT_CONFIGURED)
        return coroutineScope {
            val latestJob = async { runCatching { latestService.search(modelName).toLatestVehicles() }.getOrNull() }
            val result = safeLookup { service.search(modelName).toVehicles() }
            // 최신 목록을 받지 못해도 CAREFF 결과는 보여 준다. PHEV 전비만 비고 사용자가 입력한다.
            val latest = latestJob.await()
            if (result !is LookupResult.Success || latest == null) return@coroutineScope result
            LookupResult.Success(mergeVehicles(result.value, latest))
        }
    }
}

/**
 * CAREFF 결과의 PHEV 전비를 최신 목록에서 채우고, CAREFF에 없는 최신 전기차와 PHEV를 뒤에 붙인다.
 * 두 API의 모델명은 띄어쓰기만 다른 경우가 있어 공백을 빼고 비교한다.
 */
internal fun mergeVehicles(
    vehicles: List<VehicleFuelEconomy>,
    latest: List<VehicleFuelEconomy>,
): List<VehicleFuelEconomy> {
    val electricByModel = latest
        .filter { isPlugInHybrid(it.fuelName) }
        .groupBy { modelKey(it.model) }
        .mapValues { (_, same) -> same.firstNotNullOfOrNull { it.kmPerLiter(FuelType.ELECTRIC) } }
    val filled = vehicles.map { vehicle ->
        if (!isPlugInHybrid(vehicle.fuelName)) return@map vehicle
        val electric = electricByModel[modelKey(vehicle.model)]
        vehicle.copy(
            economies = vehicle.economies.map {
                if (it.fuelType == FuelType.ELECTRIC) it.copy(kmPerLiter = electric) else it
            },
        )
    }
    val known = vehicles.map { modelKey(it.model) }.toSet()
    return filled + latest.filter { modelKey(it.model) !in known }
}

/** 공공데이터포털 표시연비 구현을 만든다. 서비스 키는 중계 서버가 붙인다. */
class VehicleDataSource(relay: RelayClient) {

    val vehicleRepository: VehicleRepository = DataGoKrVehicleRepository(
        relay.retrofit("data-go-kr").create(),
        relay.retrofit("odcloud").create(),
        relay.isConfigured,
    )
}
