package com.jskim.sharingfuelcost.data.opinet

import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.LookupResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

class OpinetFuelPriceRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val sample = json.decodeFromString<OpinetResponse>(
        """{"RESULT":{"OIL":[
        {"TRADE_DT":"20250723","PRODCD":"B034","PRODNM":"고급휘발유","PRICE":"1900.10","DIFF":"0.1"},
        {"TRADE_DT":"20250723","PRODCD":"B027","PRODNM":"휘발유","PRICE":"1667.33","DIFF":"-0.23"},
        {"TRADE_DT":20250723,"PRODCD":"D047","PRODNM":"자동차용경유","PRICE":1500.5,"DIFF":0.0},
        {"TRADE_DT":"20250723","PRODCD":"K015","PRODNM":"자동차용부탄","PRICE":"","DIFF":""}
        ]}}""",
    )

    private class FakeCache(
        override var fetchedDate: String? = null,
        override var response: OpinetResponse? = null,
    ) : FuelPriceCache {
        override fun save(fetchedDate: String, response: OpinetResponse) {
            this.fetchedDate = fetchedDate
            this.response = response
        }
    }

    private var calls = 0

    private fun repository(cache: FakeCache, configured: Boolean = true, answer: () -> OpinetResponse = { sample }) =
        OpinetFuelPriceRepository(
            service = object : OpinetService {
                override suspend fun averagePrices(out: String): OpinetResponse {
                    calls++
                    return answer()
                }
            },
            cache = cache,
            isConfigured = configured,
            today = { "2026-11-02" },
        )

    @Test
    fun `maps known products and skips unknown or invalid prices`() {
        val prices = sample.toAveragePrices()
        assertEquals(listOf(FuelType.GASOLINE, FuelType.DIESEL), prices.map { it.type })
        assertEquals(BigDecimal("1667.33"), prices[0].wonPerLiter)
        assertEquals("20250723", prices[1].tradeDate)
    }

    @Test
    fun `fetches once a day and saves`() {
        val cache = FakeCache()
        val repository = repository(cache)
        runBlocking {
            assertTrue(repository.averagePrices() is LookupResult.Success)
            assertTrue(repository.averagePrices() is LookupResult.Success)
        }
        assertEquals(1, calls)
        assertEquals("2026-11-02", cache.fetchedDate)
    }

    @Test
    fun `stale cache is refreshed`() {
        runBlocking { repository(FakeCache("2026-11-01", sample)).averagePrices() }
        assertEquals(1, calls)
    }

    @Test
    fun `failure falls back to stale cache`() {
        val result = runBlocking {
            repository(FakeCache("2026-11-01", sample)) { throw IOException() }.averagePrices()
        }
        assertEquals(2, (result as LookupResult.Success).value.size)
    }

    @Test
    fun `failure without cache is reported`() {
        val result = runBlocking { repository(FakeCache()) { throw IOException() }.averagePrices() }
        assertEquals(LookupResult.Failure(LookupFailure.NETWORK), result)
    }

    @Test
    fun `unconfigured relay does not call api`() {
        val result = runBlocking { repository(FakeCache(), configured = false).averagePrices() }
        assertEquals(LookupResult.Failure(LookupFailure.NOT_CONFIGURED), result)
        assertEquals(0, calls)
    }

    @Test
    fun `empty response is unexpected and not saved`() {
        val cache = FakeCache()
        val result = runBlocking { repository(cache) { OpinetResponse() }.averagePrices() }
        assertEquals(LookupResult.Failure(LookupFailure.UNEXPECTED), result)
        assertEquals(null, cache.fetchedDate)
    }
}
