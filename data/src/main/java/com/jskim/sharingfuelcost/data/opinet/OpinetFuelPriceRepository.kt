package com.jskim.sharingfuelcost.data.opinet

import com.jskim.sharingfuelcost.data.kakao.safeLookup
import com.jskim.sharingfuelcost.domain.fuel.AverageFuelPrice
import com.jskim.sharingfuelcost.domain.fuel.FuelPriceRepository
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.LookupResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
internal data class OpinetResponse(@SerialName("RESULT") val result: OpinetResult = OpinetResult())

@Serializable
internal data class OpinetResult(@SerialName("OIL") val oil: List<OpinetOil> = emptyList())

@Serializable
internal data class OpinetOil(
    @SerialName("TRADE_DT") val tradeDate: JsonPrimitive,
    @SerialName("PRODCD") val productCode: String,
    @SerialName("PRICE") val price: JsonPrimitive,
)

internal interface OpinetService {
    @GET("api/avgAllPrice.do")
    /** 인증키(code)는 중계 서버가 붙인다. */
    suspend fun averagePrices(@Query("out") out: String = "json"): OpinetResponse
}

internal interface FuelPriceCache {
    val fetchedDate: String?
    val response: OpinetResponse?
    fun save(fetchedDate: String, response: OpinetResponse)
}

private val PRODUCT_CODES = mapOf(
    "B027" to FuelType.GASOLINE,
    "D047" to FuelType.DIESEL,
    "K015" to FuelType.LPG,
)

internal fun OpinetResponse.toAveragePrices(): List<AverageFuelPrice> = result.oil.mapNotNull { oil ->
    val type = PRODUCT_CODES[oil.productCode] ?: return@mapNotNull null
    val price = oil.price.content.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return@mapNotNull null
    AverageFuelPrice(type, price, oil.tradeDate.content)
}

internal class OpinetFuelPriceRepository(
    private val service: OpinetService,
    private val cache: FuelPriceCache,
    private val isConfigured: Boolean,
    private val today: () -> String,
) : FuelPriceRepository {

    override suspend fun averagePrices(): LookupResult<List<AverageFuelPrice>> {
        val cached = cache.response?.toAveragePrices().orEmpty()
        val date = today()
        if (cache.fetchedDate == date && cached.isNotEmpty()) return LookupResult.Success(cached)

        val fetched = if (!isConfigured) {
            LookupResult.Failure(LookupFailure.NOT_CONFIGURED)
        } else {
            safeLookup {
                val response = service.averagePrices()
                val prices = response.toAveragePrices()
                if (prices.isEmpty()) {
                    LookupResult.Failure(LookupFailure.UNEXPECTED)
                } else {
                    cache.save(date, response)
                    LookupResult.Success(prices)
                }
            }
        }
        return if (fetched is LookupResult.Failure && cached.isNotEmpty()) LookupResult.Success(cached) else fetched
    }
}
