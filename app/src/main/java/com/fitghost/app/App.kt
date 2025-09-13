package com.fitghost.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * FitGhost Application
 * - MobileAds 초기화
 * - UMP 동의 템플릿 부팅(테스트 모드 주석 제공)
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)

        // UMP Consent (테스트 모드 샘플 설정)
        val debugSettings = ConsentDebugSettings.Builder(this)
            // .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            // .addTestDeviceHashedId("TEST-DEVICE-HASH")
            .build()
        val params = ConsentRequestParameters.Builder()
            .setConsentDebugSettings(debugSettings)
            .build()
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        // Consent initialization is moved to MainActivity since it requires Activity context
    }
}
