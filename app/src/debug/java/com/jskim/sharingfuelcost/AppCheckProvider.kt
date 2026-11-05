package com.jskim.sharingfuelcost

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** 에뮬레이터와 개발 기기용. 처음 실행하면 logcat(DebugAppCheckProvider)에 나오는 토큰을 Firebase 콘솔에 등록한다. */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
