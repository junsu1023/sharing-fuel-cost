package com.jskim.sharingfuelcost.data.vehicle

import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.fuel.MyCar
import com.jskim.sharingfuelcost.domain.fuel.VehicleFuelEconomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyCarCodecTest {

    @Test
    fun `searched vehicle survives encode and decode with exact fuel economy`() {
        val vehicle = VehicleFuelEconomy("기아", "쏘렌토", "2023", "경유", FuelType.DIESEL, "14.30".toBigDecimal())
        val car = MyCar(vehicle, "14.30".toBigDecimal(), FuelType.DIESEL)

        assertEquals(car, decodeMyCar(encodeMyCar(car)))
    }

    @Test
    fun `typed fuel economy without vehicle survives encode and decode`() {
        val car = MyCar(null, "12.5".toBigDecimal(), FuelType.LPG)

        assertEquals(car, decodeMyCar(encodeMyCar(car)))
    }

    @Test
    fun `plug in hybrid without efficiency survives encode and decode`() {
        val vehicle = VehicleFuelEconomy("기아", "니로 PHEV", "2022", "전기+휘발유", FuelType.ELECTRIC, null)
        val car = MyCar(vehicle, "4.6".toBigDecimal(), FuelType.ELECTRIC)

        assertEquals(car, decodeMyCar(encodeMyCar(car)))
    }

    @Test
    fun `broken or unknown stored value is treated as no saved car`() {
        assertNull(decodeMyCar("not json"))
        assertNull(decodeMyCar("""{"kmPerLiter":"12.5","fuelType":"HYDROGEN"}"""))
        assertNull(decodeMyCar("""{"kmPerLiter":"abc","fuelType":"GASOLINE"}"""))
    }
}
