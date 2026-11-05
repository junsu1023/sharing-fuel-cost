package com.jskim.sharingfuelcost.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jskim.sharingfuelcost.presentation.R

/** 가변 글꼴 파일 하나를 화면에서 쓰는 두께마다 등록한다. 라이선스는 assets/licenses의 OFL이다. */
@OptIn(ExperimentalTextApi::class)
private val NotoSansKr = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold, FontWeight.ExtraBold).map { weight ->
        Font(
            resId = R.font.noto_sans_kr,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

private fun style(weight: FontWeight, size: Int, lineHeight: Int) =
    TextStyle(fontFamily = NotoSansKr, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

private val Base = Typography()

private fun TextStyle.noto() = copy(fontFamily = NotoSansKr)

/** 직접 정하지 않은 스타일(버튼 글자 등)도 Noto Sans KR로 바꾼다. */
val Typography = Typography(
    // 전체 기름값
    displayLarge = style(FontWeight.ExtraBold, 52, 64),
    displayMedium = Base.displayMedium.noto(),
    displaySmall = Base.displaySmall.noto(),
    headlineLarge = Base.headlineLarge.noto(),
    headlineMedium = Base.headlineMedium.noto(),
    // 참여자별 부담금
    headlineSmall = style(FontWeight.ExtraBold, 26, 32),
    // 문장형 섹션 제목
    titleLarge = style(FontWeight.ExtraBold, 20, 27),
    titleMedium = style(FontWeight.Bold, 17, 24),
    titleSmall = Base.titleSmall.noto(),
    bodyLarge = style(FontWeight.Normal, 16, 24),
    bodyMedium = style(FontWeight.Normal, 14, 20),
    bodySmall = style(FontWeight.Normal, 13, 18),
    labelLarge = Base.labelLarge.noto(),
    labelMedium = Base.labelMedium.noto(),
    labelSmall = Base.labelSmall.noto(),
)

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
