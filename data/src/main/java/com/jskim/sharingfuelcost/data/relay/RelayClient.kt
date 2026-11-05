package com.jskim.sharingfuelcost.data.relay

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal const val APP_CHECK_HEADER = "X-Firebase-AppCheck"

/**
 * 외부 API는 키를 가진 중계 서버(relay/)로만 호출한다. 앱은 키 대신 App Check 토큰을 보낸다.
 * [appCheckToken]은 OkHttp 작업 스레드에서 호출되며, 받지 못하면 null을 돌려준다. 그때 중계 서버는 401로 거절한다.
 * [baseUrl]이 비어 있으면 미연동으로 보고 호출하지 않는다.
 */
class RelayClient(baseUrl: String, appCheckToken: () -> String?) {

    private val root = baseUrl.trim().let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }

    val isConfigured: Boolean = root.isNotEmpty()

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val token = appCheckToken()
            val request = chain.request().newBuilder()
            if (token != null) request.header(APP_CHECK_HEADER, token)
            chain.proceed(request.build())
        }
        .build()

    private val converter = Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType())

    /** 중계 서버의 경로 접두사([prefix])별 Retrofit. 미연동이면 쓰이지 않는 자리 표시 주소를 쓴다. */
    internal fun retrofit(prefix: String): Retrofit = Retrofit.Builder()
        .baseUrl(if (isConfigured) "$root$prefix/" else "https://relay.invalid/$prefix/")
        .client(client)
        .addConverterFactory(converter)
        .build()
}
