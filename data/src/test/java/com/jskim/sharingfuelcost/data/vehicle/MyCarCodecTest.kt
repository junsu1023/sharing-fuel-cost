package com.jskim.sharingfuelcost.data.vehicle

import com.jskim.sharingfuelcost.domain.fuel.FuelEconomy
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.fuel.MyCar
import com.jskim.sharingfuelcost.domain.fuel.VehicleFuelEconomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyCarCodecTest {

    @Test
    fun `searched vehicle survives encode and decode with exact fuel economy`() {
        val vehicle = VehicleFuelEconomy("기아", "쏘렌토", "2023", "경유", listOf(FuelEconomy(FuelType.DIESEL, "14.30".toBigDecimal())))
        val car = MyCar(vehicle, "14.30".toBigDecimal(), FuelType.DIESEL)

        assertEquals(car, decodeMyCar(encodeMyCar(car)))
    }

    @Test
    fun `typed fuel economy without vehicle survives encode and decode`() {
        val car = MyCar(null, "12.5".toBigDecimal(), FuelType.LPG)

        assertEquals(car, decodeMyCar(encodeMyCar(car)))
    }

    @Test
    fun `plug in hybrid keeps both fuels and missing electric efficiency`() {
        val vehicle = VehicleFuelEconomy(
            "기아",
            "니로 PHEV",
            "2022",
            "전기+휘발유",
            listOf(FuelEconomy(FuelType.ELECTRIC, null), FuelEconomy(FuelType.GASOLINE, "18.6".toBigDecimal())),
        )
        val car = MyCar(vehicle, "4.6".toBigDecimal(), FuelType.ELECTRIC)

        assertEquals(car, decodeMyCar(encodeMyCar(car)))
    }

    @Test
    fun `car saved before economies existed is read as single fuel vehicle`() {
        val stored = """{"kmPerLiter":"14.3","fuelType":"DIESEL","vehicle":{"maker":"기아","model":"쏘렌토","year":"2023",""" +
            """"fuelName":"경유","fuelType":"DIESEL","kmPerLiter":"14.3"}}"""
        val vehicle = VehicleFuelEconomy("기아", "쏘렌토", "2023", "경유", listOf(FuelEconomy(FuelType.DIESEL, "14.3".toBigDecimal())))

        assertEquals(MyCar(vehicle, "14.3".toBigDecimal(), FuelType.DIESEL), decodeMyCar(stored))
    }

    @Test
    fun `broken or unknown stored value is treated as no saved car`() {
        assertNull(decodeMyCar("not json"))
        assertNull(decodeMyCar("""{"kmPerLiter":"12.5","fuelType":"HYDROGEN"}"""))
        assertNull(decodeMyCar("""{"kmPerLiter":"abc","fuelType":"GASOLINE"}"""))
    }
}
