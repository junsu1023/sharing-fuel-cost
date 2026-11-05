package com.jskim.sharingfuelcost.data.vehicle

import com.jskim.sharingfuelcost.data.kakao.safeLookup
import com.jskim.sharingfuelcost.data.relay.RelayClient
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
    val fuelType = fuelTypeOf(fuelName) ?: return null
    val efficiency = text("DISPLAY_EFF")?.let { NUMBER.find(it)?.value }?.toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 } ?: return null
    return VehicleFuelEconomy(
        maker = text("COMP_NM").orEmpty(),
        model = text("MODEL_NM").orEmpty(),
        year = text("YEAR").orEmpty(),
        fuelName = fuelName,
        fuelType = fuelType,
        kmPerLiter = BigDecimal(efficiency.stripTrailingZeros().toPlainString()),
    )
}

/** 표시연비 단위가 km/L가 아닌 전기, 수소와 값을 모르는 연료는 null이다. */
internal fun fuelTypeOf(fuelName: String): FuelType? = when {
    fuelName.contains("LPG", ignoreCase = true) -> FuelType.LPG
    fuelName.contains("경유") || fuelName.contains("디젤") -> FuelType.DIESEL
    fuelName.contains("휘발유") || fuelName.contains("가솔린") -> FuelType.GASOLINE
    else -> null
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal class DataGoKrVehicleRepository(
    private val service: VehicleService,
    private val isConfigured: Boolean,
) : VehicleRepository {

    override suspend fun search(modelName: String): LookupResult<List<VehicleFuelEconomy>> {
        if (!isConfigured) return LookupResult.Failure(LookupFailure.NOT_CONFIGURED)
        return safeLookup { service.search(modelName).toVehicles() }
    }
}

/** 공공데이터포털 표시연비 구현을 만든다. 서비스 키는 중계 서버가 붙인다. */
class VehicleDataSource(relay: RelayClient) {

    val vehicleRepository: VehicleRepository = DataGoKrVehicleRepository(
        relay.retrofit("data-go-kr").create(),
        relay.isConfigured,
    )
}
