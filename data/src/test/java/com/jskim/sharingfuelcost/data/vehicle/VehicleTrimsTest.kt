package com.jskim.sharingfuelcost.data.vehicle

import com.jskim.sharingfuelcost.domain.fuel.FuelEconomy
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.fuel.VehicleFuelEconomy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class VehicleTrimsTest {

    @Test
    fun `removes tire size written in any form`() {
        assertEquals("아반떼 1.6 LPI", trimModelName("아반떼 1.6 LPI 15인치 타이어"))
        assertEquals("아반떼(PE) 1.6GDI하이브리드", trimModelName("아반떼(PE) 1.6GDI하이브리드 17인치타이어"))
        assertEquals("아반떼 1.6GDI 하이브리드", trimModelName("아반떼 1.6GDI 하이브리드 (16“타이어)"))
        assertEquals("아반떼 1.6LPI", trimModelName("아반떼 1.6LPI (15''/16'' 타이어) (19MY)"))
        assertEquals("아반떼(AD PE) 1.6가솔린", trimModelName("아반떼(AD PE) 1.6가솔린 (15'''타이어)"))
        assertEquals("그랜저(IG개조) 2.5 GDI", trimModelName("그랜저(IG개조) 2.5 GDI_17인치타이어"))
        assertEquals("모닝 1.0가솔린", trimModelName("모닝 25MY 1.0가솔린 14\""))
        assertEquals("카니발(KA4 PE) 2.2디젤 하이리무진", trimModelName(" 카니발(KA4 PE) 2.2디젤 하이리무진 9인 18/19\""))
        assertEquals("모닝(JA PE) 1.0가솔린 밴형", trimModelName("모닝(JA PE)_1.0가솔린_14인치타이어_밴형"))
    }

    @Test
    fun `removes model year marks and built in cam but keeps other options`() {
        assertEquals("아반떼 1.6 가솔린", trimModelName("아반떼 1.6 가솔린 16인치 타이어(22MY)"))
        assertEquals("쏘렌토 2.2디젤 2WD", trimModelName("쏘렌토 2.2디젤 2WD 5인 18인치타이어 (빌트인캠)(22)"))
        assertEquals("쏘렌토 2.0디젤 2WD ISG", trimModelName("쏘렌토(19MY) 2.0디젤 2WD ISG 5인 (18\" 타이어)"))
        assertEquals("쏘렌토(MQ4 PE) 2.2디젤 2WD", trimModelName("쏘렌토(MQ4 PE) 2.2디젤 2WD 5인 18인치 빌트인캠 유 25MY"))
        assertEquals("K5 PE 1.6T-GDI", trimModelName("K5 PE 1.6T-GDI 17인치(빌트인캠 미장착)"))
        assertEquals("EV6 항속형 RWD", trimModelName("EV6 항속형 RWD 19인치(빌트인캠미적용)"))
        assertEquals("아반떼 1.6 T-GDI 6MT", trimModelName("아반떼 1.6 T-GDI 6MT 18인치 타이어"))
        assertEquals("아이오닉5 개조 항속형 RWD (C)", trimModelName("아이오닉5 개조 항속형 RWD 19인치(C)"))
        assertEquals("BMW 520d", trimModelName("BMW 520d"))
    }

    @Test
    fun `removes seat count but keeps similar looking words`() {
        assertEquals("카니발 2.2디젤 2WD", trimModelName("카니발 2.2디젤 2WD 11인승 (17\" 타이어)"))
        assertEquals("쏘렌토 2.2디젤 2WD ISG", trimModelName("쏘렌토(19MY) 2.2디젤 2WD ISG 7인(18\" 타이어)"))
        assertEquals("Mercedes-Benz GLE 300 d 4MATIC", trimModelName("Mercedes-Benz GLE 300 d 4MATIC(5seater)"))
        assertEquals("카니발 2.2디젤", trimModelName("카니발 2.2디젤 7인 19인치(22)"))
        assertEquals("스타리아 3.5 LPi 인스퍼레이션", trimModelName("스타리아 3.5 LPi 인스퍼레이션"))
    }

    private fun car(model: String, year: String, fuelName: String, vararg economies: FuelEconomy) =
        VehicleFuelEconomy("현대", model, year, fuelName, economies.toList())

    private fun gasoline(value: String) = FuelEconomy(FuelType.GASOLINE, BigDecimal(value))

    @Test
    fun `same name year and fuel become one row with rounded average`() {
        val grouped = groupTrims(
            listOf(
                car("아반떼 1.6 가솔린 15인치 타이어", "2020", "휘발유", gasoline("15.4")),
                car("아반떼 1.6 가솔린 15인치 타이어(22MY)", "2022", "휘발유", gasoline("15.4")),
                car("아반떼 1.6 가솔린 16인치 타이어", "2020", "휘발유", gasoline("14.9")),
                car("아반떼 1.6GDI 하이브리드 (16“타이어)", "2020", "휘발유(하이브리드)", gasoline("21.1")),
                car("아반떼 1.6 가솔린 17인치 타이어", "2020", "휘발유", gasoline("14.5")),
            ),
        )
        assertEquals(
            listOf(
                car("아반떼 1.6 가솔린", "2020", "휘발유", gasoline("14.9")),
                car("아반떼 1.6 가솔린", "2022", "휘발유", gasoline("15.4")),
                car("아반떼 1.6GDI 하이브리드", "2020", "휘발유(하이브리드)", gasoline("21.1")),
            ),
            grouped,
        )
    }

    @Test
    fun `average rounds half up and drops trailing zero`() {
        val grouped = groupTrims(
            listOf(car("A 18인치", "2023", "휘발유", gasoline("10.2")), car("A 19인치", "2023", "휘발유", gasoline("10.3"))),
        )
        assertEquals(BigDecimal("10.3"), grouped.single().kmPerLiter(FuelType.GASOLINE))
        val whole = groupTrims(
            listOf(car("A 18인치", "2023", "휘발유", gasoline("14.9")), car("A 19인치", "2023", "휘발유", gasoline("15.1"))),
        )
        assertEquals(BigDecimal("15"), whole.single().kmPerLiter(FuelType.GASOLINE))
    }

    @Test
    fun `plug in hybrid electric average skips trims without value`() {
        val grouped = groupTrims(
            listOf(
                car("X 18인치", "2024", "전기+휘발유", FuelEconomy(FuelType.ELECTRIC, BigDecimal("3.1")), gasoline("11.7")),
                car("X 19인치", "2024", "전기+휘발유", FuelEconomy(FuelType.ELECTRIC, null), gasoline("11.0")),
                car("Y", "2024", "전기+휘발유", FuelEconomy(FuelType.ELECTRIC, null), gasoline("12")),
            ),
        )
        assertEquals(BigDecimal("3.1"), grouped[0].kmPerLiter(FuelType.ELECTRIC))
        assertEquals(BigDecimal("11.4"), grouped[0].kmPerLiter(FuelType.GASOLINE))
        assertEquals(null, grouped[1].kmPerLiter(FuelType.ELECTRIC))
    }
}
