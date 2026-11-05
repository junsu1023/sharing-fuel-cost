package com.jskim.sharingfuelcost

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import java.util.concurrent.TimeUnit

private const val TAG = "AppCheck"
private const val TOKEN_TIMEOUT_SECONDS = 10L

/**
 * google-services.json이 있을 때만 Firebase를 켜고 App Check provider를 설치한다.
 * provider는 빌드 종류별 소스(src/debug, src/release)의 [appCheckProviderFactory]가 정한다.
 */
internal fun installAppCheck(context: Context): Boolean {
    val app = FirebaseApp.initializeApp(context) ?: run {
        Log.w(TAG, "google-services.json이 없어 App Check를 켜지 않았습니다")
        return false
    }
    FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(appCheckProviderFactory())
    return true
}

/** OkHttp 작업 스레드에서 호출한다. 토큰은 라이브러리가 캐시하고 필요할 때 갱신한다. */
internal fun currentAppCheckToken(): String? = runCatching {
    Tasks.await(FirebaseAppCheck.getInstance().getAppCheckToken(false), TOKEN_TIMEOUT_SECONDS, TimeUnit.SECONDS).token
}.onFailure { Log.w(TAG, "App Check 토큰을 받지 못했습니다", it) }.getOrNull()
