package com.jskim.sharingfuelcost

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** 출시 빌드는 Play Integrity로 정품 앱인지 확인한다. 디버그 provider는 넣지 않는다. */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
