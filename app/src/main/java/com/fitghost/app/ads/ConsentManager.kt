package com.fitghost.app.ads

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * UMP 동의 플로우 관리
 * - 앱 시작 시 호출하여 동의 상태 갱신 및 필요시 폼 표시
 */
object ConsentManager {
    fun requestConsent(activity: Activity, onFinished: () -> Unit = {}) {
        val params = ConsentRequestParameters.Builder()
            .build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadConsentForm(
                    activity,
                    { form ->
                        if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                            form.show(activity) { onFinished() }
                        } else {
                            onFinished()
                        }
                    },
                    { _ -> onFinished() }
                )
            },
            { _ -> onFinished() }
        )
    }
}