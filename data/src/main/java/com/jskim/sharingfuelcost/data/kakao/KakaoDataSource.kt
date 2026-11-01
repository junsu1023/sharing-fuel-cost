package com.jskim.sharingfuelcost.data.kakao

import com.jskim.sharingfuelcost.domain.route.PlaceSearchRepository
import com.jskim.sharingfuelcost.domain.route.RouteDistanceRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

/** 카카오 REST API 구현을 만든다. 키가 비어 있으면 NOT_CONFIGURED로 실패한다. */
class KakaoDataSource(apiKey: String) {

    private val isConfigured = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", "KakaoAK $apiKey")
                    .build(),
            )
        }
        .build()

    private val converter = Json { ignoreUnknownKeys = true }
        .asConverterFactory("application/json".toMediaType())

    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(converter)
        .build()

    val routeDistanceRepository: RouteDistanceRepository = KakaoRouteDistanceRepository(
        retrofit("https://apis-navi.kakaomobility.com/").create(),
        isConfigured,
    )

    val placeSearchRepository: PlaceSearchRepository = KakaoPlaceSearchRepository(
        retrofit("https://dapi.kakao.com/").create(),
        isConfigured,
    )
}
