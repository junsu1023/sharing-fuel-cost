package com.jskim.sharingfuelcost.data.vehicle

import com.jskim.sharingfuelcost.domain.fuel.FuelEconomy
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.fuel.VehicleFuelEconomy
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.LookupResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

class VehicleDataSourceTest {

    private fun parse(text: String) = Json.parseToJsonElement(text).toVehicles()

    private fun vehicles(text: String) = (parse(text) as LookupResult.Success).value

    private val gasoline = """{"COMP_NM":"현대","MODEL_NM":"아반떼","YEAR":"2024","FUEL_NM":"휘발유","DISPLAY_EFF":"15.20"}"""
    private val diesel = """{"COMP_NM":"기아","MODEL_NM":"쏘렌토","YEAR":"2023","FUEL_NM":"경유","DISPLAY_EFF":"14.3km/L"}"""
    private val electric = """{"COMP_NM":"현대","MODEL_NM":"아이오닉","YEAR":"2024","FUEL_NM":"전기","DISPLAY_EFF":"5.1"}"""
    private val hydrogen = """{"COMP_NM":"현대","MODEL_NM":"넥쏘","YEAR":"2024","FUEL_NM":"수소","DISPLAY_EFF":"96.2"}"""
    private val plugIn = """{"COMP_NM":"기아","MODEL_NM":"니로(DE) 1.6GDI 플러그인 하이브리드_6DCT (15)","YEAR":"2022","FUEL_NM":"전기+휘발유","DISPLAY_EFF":"18.6","GRADE":"PHEV"}"""
    private val noEfficiency = """{"COMP_NM":"현대","MODEL_NM":"아반떼","YEAR":"2024","FUEL_NM":"휘발유","DISPLAY_EFF":""}"""

    @Test
    fun `parses documented fields from item array`() {
        val result = vehicles("""{"header":{"resultCode":"00"},"body":{"items":{"item":[$gasoline]}}}""")
        assertEquals(
            listOf(
                VehicleFuelEconomy("현대", "아반떼", "2024", "휘발유", listOf(FuelEconomy(FuelType.GASOLINE, BigDecimal("15.2")))),
            ),
            result,
        )
    }

    @Test
    fun `accepts response wrapper single item and plain items array`() {
        assertEquals(1, vehicles("""{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":$gasoline}}}}""").size)
        assertEquals(2, vehicles("""{"body":{"items":[$gasoline,$diesel]}}""").size)
    }

    @Test
    fun `parses captured real response shape`() {
        val result = vehicles(
            """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_CODE"},"body":{"pageNo":"1","totalCount":"46",
            "numOfRows":"3","items":{"item":[{"RANGE_PER_CHARGE":"NULL","URBAN_EFF":"9.4","YEAR":"2020",
            "MODEL_NM":"아반떼 1.6 LPI 15인치 타이어","CAR_TYPE":"내연기관","FUEL_NM":"LPG","COMP_NM":"현대",
            "HIGHWAY_EFF":"12.5","DISPLAY_EFF":"10.6"}]}}}}""",
        )
        assertEquals(listOf(FuelEconomy(FuelType.LPG, BigDecimal("10.6"))), result.single().economies)
    }

    @Test
    fun `no results body without item is empty`() {
        assertEquals(0, vehicles("""{"response":{"header":{"resultCode":"00"},"body":{"totalCount":"0","items":""}}}""").size)
    }

    @Test
    fun `reads number from efficiency with unit`() {
        assertEquals(BigDecimal("14.3"), vehicles("""{"body":{"items":[$diesel]}}""").single().kmPerLiter(FuelType.DIESEL))
    }

    @Test
    fun `excludes hydrogen and missing efficiency`() {
        assertEquals(0, vehicles("""{"body":{"items":[$hydrogen,$noEfficiency]}}""").size)
    }

    @Test
    fun `electric keeps display efficiency as km per kWh`() {
        val result = vehicles("""{"body":{"items":[$electric]}}""").single()
        assertEquals(listOf(FuelEconomy(FuelType.ELECTRIC, BigDecimal("5.1"))), result.economies)
    }

    @Test
    fun `plug in hybrid offers electric first and gasoline with display efficiency`() {
        val result = vehicles("""{"body":{"items":[$plugIn]}}""").single()
        assertEquals(
            listOf(FuelEconomy(FuelType.ELECTRIC, null), FuelEconomy(FuelType.GASOLINE, BigDecimal("18.6"))),
            result.economies,
        )
    }

    private val latestPlugIn = """{"제조(수입사)":"기아","모델명":"니로(DE)1.6GDI 플러그인하이브리드_6DCT (15)","등급":"PHEV","복합_연비":"4.6 / 18.6","1회충전주행거리":"40"}"""
    private val latestElectric = """{"제조(수입사)":"기아","모델명":"EV5(OV1) 항속형 2WD","등급":"1","복합_연비":"5","1회충전주행거리":"460"}"""

    private fun latest(vararg rows: String) = Json.parseToJsonElement("""{"data":[${rows.joinToString(",")}]}""").toLatestVehicles()

    @Test
    fun `latest list keeps electric and plug in hybrid only`() {
        val result = latest(
            latestPlugIn,
            latestElectric,
            latestElectric,
            """{"제조(수입사)":"현대","모델명":"넥쏘","등급":"FCEV","복합_연비":"96.2","1회충전주행거리":"700"}""",
            """{"제조(수입사)":"현대","모델명":"아반떼","등급":"3","복합_연비":"15.2","1회충전주행거리":null}""",
            """{"모델명":"값 없음","등급":"PHEV","복합_연비":null}""",
        )!!
        assertEquals(
            listOf(
                VehicleFuelEconomy(
                    "기아", "니로(DE)1.6GDI 플러그인하이브리드_6DCT (15)", "", "전기+휘발유",
                    listOf(FuelEconomy(FuelType.ELECTRIC, BigDecimal("4.6")), FuelEconomy(FuelType.GASOLINE, BigDecimal("18.6"))),
                ),
                VehicleFuelEconomy("기아", "EV5(OV1) 항속형 2WD", "", "전기", listOf(FuelEconomy(FuelType.ELECTRIC, BigDecimal("5")))),
            ),
            result,
        )
        assertEquals(null, Json.parseToJsonElement("""{"error":1}""").toLatestVehicles())
    }

    @Test
    fun `merge fills plug in hybrid electric by model without spaces and appends new models`() {
        val careff = vehicles("""{"body":{"items":[$plugIn,$gasoline]}}""")
        val merged = mergeVehicles(careff, latest(latestPlugIn, latestElectric)!!)
        assertEquals(3, merged.size)
        assertEquals(BigDecimal("4.6"), merged[0].kmPerLiter(FuelType.ELECTRIC))
        assertEquals(BigDecimal("18.6"), merged[0].kmPerLiter(FuelType.GASOLINE))
        assertEquals("2022", merged[0].year)
        assertEquals(careff[1], merged[1])
        assertEquals("EV5(OV1) 항속형 2WD", merged[2].model)
    }

    @Test
    fun `maps fuel names`() {
        assertEquals(listOf(FuelType.GASOLINE), fuelTypesOf("휘발유(하이브리드)"))
        assertEquals(listOf(FuelType.DIESEL), fuelTypesOf("경유"))
        assertEquals(listOf(FuelType.LPG), fuelTypesOf("LPG"))
        assertEquals(listOf(FuelType.ELECTRIC), fuelTypesOf("전기"))
        assertEquals(emptyList<FuelType>(), fuelTypesOf("수소"))
        assertEquals(listOf(FuelType.ELECTRIC, FuelType.GASOLINE), fuelTypesOf("전기+휘발유"))
        assertEquals(listOf(FuelType.ELECTRIC, FuelType.DIESEL), fuelTypesOf("전기+경유"))
        assertEquals(emptyList<FuelType>(), fuelTypesOf(""))
    }

    @Test
    fun `no data code is empty and other codes fail`() {
        assertEquals(emptyList<Any>(), vehicles("""{"header":{"resultCode":"03"}}"""))
        assertEquals(LookupResult.Failure(LookupFailure.UNEXPECTED), parse("""{"header":{"resultCode":"99"}}"""))
        assertEquals(LookupResult.Failure(LookupFailure.UNEXPECTED), parse("""{"unknown":1}"""))
    }

    private class FakeServices(
        private val items: String,
        private val latest: String?,
    ) : VehicleService, LatestFuelEconomyService {
        override suspend fun search(modelName: String, pageNo: Int, numOfRows: Int, apiType: String): JsonElement =
            Json.parseToJsonElement("""{"body":{"items":[$items]}}""")
        override suspend fun search(modelName: String, page: Int, perPage: Int): JsonElement =
            Json.parseToJsonElement(latest ?: throw IOException())
    }

    @Test
    fun `search finds latest electric vehicle missing from careff`() {
        val services = FakeServices("", """{"data":[$latestElectric]}""")
        val repository = DataGoKrVehicleRepository(services, services, isConfigured = true)
        val result = runBlocking { (repository.search("ev5") as LookupResult.Success).value }
        assertEquals(listOf(FuelEconomy(FuelType.ELECTRIC, BigDecimal("5"))), result.single().economies)
    }

    @Test
    fun `latest list failure keeps careff results with empty plug in hybrid electric`() {
        val services = FakeServices(plugIn, latest = null)
        val repository = DataGoKrVehicleRepository(services, services, isConfigured = true)
        val vehicle = runBlocking { (repository.search("니로") as LookupResult.Success).value.single() }
        assertEquals(null, vehicle.kmPerLiter(FuelType.ELECTRIC))
        assertEquals(BigDecimal("18.6"), vehicle.kmPerLiter(FuelType.GASOLINE))
    }

    private class PagedService(private val total: Int, private val failingPage: Int? = null) :
        VehicleService, LatestFuelEconomyService {
        val pages = mutableListOf<Int>()
        override suspend fun search(modelName: String, pageNo: Int, numOfRows: Int, apiType: String): JsonElement {
            synchronized(pages) { pages += pageNo }
            if (pageNo == failingPage) throw IOException()
            val item = """{"COMP_NM":"기아","MODEL_NM":"쏘렌토 $pageNo","YEAR":"2024","FUEL_NM":"경유","DISPLAY_EFF":"14"}"""
            return Json.parseToJsonElement("""{"response":{"header":{"resultCode":"00"},"body":{"totalCount":"$total","items":{"item":[$item]}}}}""")
        }
        override suspend fun search(modelName: String, page: Int, perPage: Int): JsonElement =
            Json.parseToJsonElement("""{"data":[]}""")
    }

    @Test
    fun `results over one page fetch remaining pages in order`() {
        val service = PagedService(total = 250)
        val result = runBlocking { (DataGoKrVehicleRepository(service, service, isConfigured = true).search("쏘렌토") as LookupResult.Success).value }
        assertEquals(listOf("쏘렌토 1", "쏘렌토 2", "쏘렌토 3"), result.map { it.model })
        assertEquals(listOf(1, 2, 3), service.pages.sorted())
    }

    @Test
    fun `page count is capped and single page makes one request`() {
        val many = PagedService(total = 5000)
        runBlocking { DataGoKrVehicleRepository(many, many, isConfigured = true).search("a") }
        assertEquals(10, many.pages.size)
        val one = PagedService(total = 100)
        runBlocking { DataGoKrVehicleRepository(one, one, isConfigured = true).search("a") }
        assertEquals(listOf(1), one.pages)
    }

    @Test
    fun `failure on a later page fails the search`() {
        val service = PagedService(total = 250, failingPage = 3)
        val result = runBlocking { DataGoKrVehicleRepository(service, service, isConfigured = true).search("쏘렌토") }
        assertEquals(LookupResult.Failure(LookupFailure.NETWORK), result)
    }

    @Test
    fun `unconfigured relay does not call api and network error is reported`() {
        val service = object : VehicleService {
            override suspend fun search(
                modelName: String,
                pageNo: Int,
                numOfRows: Int,
                apiType: String,
            ): JsonElement = throw IOException()
        }
        val table = object : LatestFuelEconomyService {
            override suspend fun search(modelName: String, page: Int, perPage: Int): JsonElement = throw IOException()
        }
        runBlocking {
            assertEquals(
                LookupResult.Failure(LookupFailure.NOT_CONFIGURED),
                DataGoKrVehicleRepository(service, table, isConfigured = false).search("아반떼"),
            )
            assertEquals(
                LookupResult.Failure(LookupFailure.NETWORK),
                DataGoKrVehicleRepository(service, table, isConfigured = true).search("아반떼"),
            )
        }
    }
}
