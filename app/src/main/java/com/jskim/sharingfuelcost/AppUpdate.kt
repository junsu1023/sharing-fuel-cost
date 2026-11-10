package com.jskim.sharingfuelcost

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

private const val TAG = "AppUpdate"

/**
 * Play In-App Updates의 즉시 업데이트. Play에 더 높은 versionCode가 있으면 전체 화면 업데이트를 띄운다.
 * 사용자가 거절하면 앱을 닫고, 다음에 열 때 다시 묻는다. Play에서 설치하지 않은 빌드에서는 아무 일도 하지 않는다.
 * https://developer.android.com/guide/playcore/in-app-updates/kotlin-java
 */
internal class ImmediateAppUpdate(private val activity: ComponentActivity) {
    // Activity 필드에서 만들어지므로 Context가 붙은 뒤(onResume)에 처음 만든다.
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(activity) }

    // 업데이트 자체가 실패하면 같은 프로세스에서는 다시 묻지 않고 앱을 계속 쓰게 한다.
    private var failed = false

    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(StartIntentSenderForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> Unit
                Activity.RESULT_CANCELED -> activity.finish()
                ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                    Log.w(TAG, "업데이트에 실패했습니다")
                    failed = true
                }
            }
        }

    /** onResume에서 호출한다. 중단된 업데이트도 이어서 진행한다. */
    fun check() {
        if (failed) return
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                val availability = info.updateAvailability()
                val shouldStart = availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ||
                    (availability == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE))
                if (shouldStart) {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        launcher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                    )
                }
            }
            .addOnFailureListener { Log.w(TAG, "업데이트 정보를 받지 못했습니다", it) }
    }
}
