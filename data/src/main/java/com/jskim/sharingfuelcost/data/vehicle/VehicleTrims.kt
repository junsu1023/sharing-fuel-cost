package com.jskim.sharingfuelcost.data.vehicle

import com.jskim.sharingfuelcost.domain.fuel.FuelEconomy
import com.jskim.sharingfuelcost.domain.fuel.VehicleFuelEconomy
import java.math.BigDecimal
import java.math.RoundingMode

// 15인치 타이어, 16인치타이어, (16“타이어), 15''/16'' 타이어, 18/19", 14" 처럼 표기가 제각각이다.
private const val INCH = """(?:''+|["“”″]|인치)"""
private val TIRE = Regex("""\d{2}(?:\s*/\s*\d{2})?\s*$INCH(?:\s*/\s*\d{2}\s*$INCH)?\s*(?:타이어)?""")
// (22MY), 25MY, 쏘렌토(19MY), (22), (15)
private val MODEL_YEAR = Regex("""\((?:\d{2}MY|\d{2})\)|\b\d{2}MY\b""")
// (빌트인캠), 빌트인캠 유, 빌트인캠 무, (빌트인캠 미장착), (빌트인캠미적용), 빌트인캠 통합
private val BUILT_IN_CAM = Regex("""\(?빌트인캠\s*(?:미적용|적용|미장착|장착|통합|무|유)?\)?""")
// 5인, 7인승, 11인승, (5seater). 인승만 다른 트림은 표시연비가 같거나 거의 같다.
private val SEATS = Regex("""(?<![\d.])\d{1,2}\s*인승?(?![가-힣A-Za-z])|\(?\b\d{1,2}\s*seater\)?""", RegexOption.IGNORE_CASE)
private val EMPTY_PARENS = Regex("""\(\s*\)""")
private val SPACES = Regex("""\s+""")

/** 모델명에서 타이어 크기, 연식 변경 표기, 빌트인캠 여부, 인승을 지운다. 출시년도는 년식 칸으로 따로 구분한다. */
internal fun trimModelName(model: String): String {
    val trimmed = model.replace('_', ' ')
        .replace(TIRE, " ")
        .replace(MODEL_YEAR, " ")
        .replace(BUILT_IN_CAM, " ")
        .replace(SEATS, " ")
        .replace(EMPTY_PARENS, " ")
        .replace(SPACES, " ")
        .trim()
    return trimmed.ifEmpty { model.trim() }
}

/**
 * 제조사, 다듬은 모델명, 년식, 연료가 같은 트림을 한 줄로 묶는다. 연비는 값이 있는 트림의 평균을
 * 소수 첫째 자리에서 반올림한다. 순서는 묶음이 처음 나온 자리를 따른다.
 */
internal fun groupTrims(vehicles: List<VehicleFuelEconomy>): List<VehicleFuelEconomy> =
    vehicles
        .map { it.copy(model = trimModelName(it.model)) }
        .groupBy { listOf(it.maker, it.model, it.year, it.fuelName) }
        .values
        .map { trims ->
            trims.first().copy(
                economies = trims.first().economies.map { economy ->
                    val values = trims.mapNotNull { it.kmPerLiter(economy.fuelType) }
                    FuelEconomy(economy.fuelType, values.averageOrNull())
                },
            )
        }

private fun List<BigDecimal>.averageOrNull(): BigDecimal? {
    if (isEmpty()) return null
    val average = reduce(BigDecimal::add).divide(BigDecimal(size), 1, RoundingMode.HALF_UP)
    return BigDecimal(average.stripTrailingZeros().toPlainString())
}
