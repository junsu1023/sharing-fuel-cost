package com.jskim.sharingfuelcost.data.opinet

import android.content.Context
import com.jskim.sharingfuelcost.data.relay.RelayClient
import com.jskim.sharingfuelcost.domain.fuel.FuelPriceRepository
import kotlinx.serialization.json.Json
import retrofit2.create
import java.time.LocalDate

private val json = Json { ignoreUnknownKeys = true }

/** 오피넷 평균 유가 구현을 만든다. 키는 중계 서버가 붙이고, 중계 주소가 없으면 저장값이 없을 때 NOT_CONFIGURED로 실패한다. */
class OpinetDataSource(context: Context, relay: RelayClient) {

    val fuelPriceRepository: FuelPriceRepository = OpinetFuelPriceRepository(
        service = relay.retrofit("opinet").create(),
        cache = SharedPreferencesFuelPriceCache(context),
        isConfigured = relay.isConfigured,
        today = { LocalDate.now().toString() },
    )
}

private class SharedPreferencesFuelPriceCache(context: Context) : FuelPriceCache {
    private val preferences = context.applicationContext.getSharedPreferences("fuel_price", Context.MODE_PRIVATE)

    override val fetchedDate: String? get() = preferences.getString(KEY_DATE, null)

    override val response: OpinetResponse?
        get() = preferences.getString(KEY_RESPONSE, null)?.let {
            runCatching { json.decodeFromString<OpinetResponse>(it) }.getOrNull()
        }

    override fun save(fetchedDate: String, response: OpinetResponse) {
        preferences.edit()
            .putString(KEY_DATE, fetchedDate)
            .putString(KEY_RESPONSE, json.encodeToString(response))
            .apply()
    }

    private companion object {
        const val KEY_DATE = "fetched_date"
        const val KEY_RESPONSE = "response"
    }
}
