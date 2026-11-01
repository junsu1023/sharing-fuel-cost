package com.jskim.sharingfuelcost.data.vehicle

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
    private val noEfficiency = """{"COMP_NM":"현대","MODEL_NM":"아반떼","YEAR":"2024","FUEL_NM":"휘발유","DISPLAY_EFF":""}"""

    @Test
    fun `parses documented fields from item array`() {
        val result = vehicles("""{"header":{"resultCode":"00"},"body":{"items":{"item":[$gasoline]}}}""")
        assertEquals(
            listOf(VehicleFuelEconomy("현대", "아반떼", "2024", "휘발유", FuelType.GASOLINE, BigDecimal("15.2"))),
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
        assertEquals(FuelType.LPG, result.single().fuelType)
        assertEquals(BigDecimal("10.6"), result.single().kmPerLiter)
    }

    @Test
    fun `no results body without item is empty`() {
        assertEquals(0, vehicles("""{"response":{"header":{"resultCode":"00"},"body":{"totalCount":"0","items":""}}}""").size)
    }

    @Test
    fun `reads number from efficiency with unit`() {
        assertEquals(BigDecimal("14.3"), vehicles("""{"body":{"items":[$diesel]}}""").single().kmPerLiter)
    }

    @Test
    fun `excludes electric hydrogen and missing efficiency`() {
        assertEquals(0, vehicles("""{"body":{"items":[$electric,$hydrogen,$noEfficiency]}}""").size)
    }

    @Test
    fun `maps fuel names`() {
        assertEquals(FuelType.GASOLINE, fuelTypeOf("휘발유(하이브리드)"))
        assertEquals(FuelType.DIESEL, fuelTypeOf("경유"))
        assertEquals(FuelType.LPG, fuelTypeOf("LPG"))
        assertEquals(null, fuelTypeOf("전기"))
        assertEquals(null, fuelTypeOf("수소"))
        assertEquals(FuelType.GASOLINE, fuelTypeOf("전기+휘발유"))
        assertEquals(null, fuelTypeOf(""))
    }

    @Test
    fun `no data code is empty and other codes fail`() {
        assertEquals(emptyList<Any>(), vehicles("""{"header":{"resultCode":"03"}}"""))
        assertEquals(LookupResult.Failure(LookupFailure.UNEXPECTED), parse("""{"header":{"resultCode":"99"}}"""))
        assertEquals(LookupResult.Failure(LookupFailure.UNEXPECTED), parse("""{"unknown":1}"""))
    }

    @Test
    fun `missing key does not call api and network error is reported`() {
        val service = object : VehicleService {
            override suspend fun search(
                serviceKey: String,
                modelName: String,
                pageNo: Int,
                numOfRows: Int,
                apiType: String,
            ): JsonElement = throw IOException()
        }
        runBlocking {
            assertEquals(
                LookupResult.Failure(LookupFailure.NOT_CONFIGURED),
                DataGoKrVehicleRepository(service, "").search("아반떼"),
            )
            assertEquals(
                LookupResult.Failure(LookupFailure.NETWORK),
                DataGoKrVehicleRepository(service, "key").search("아반떼"),
            )
        }
    }
}
