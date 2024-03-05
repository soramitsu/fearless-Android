package jp.co.soramitsu.onboarding.impl.data

import jp.co.soramitsu.feature_onboarding_impl.BuildConfig
import jp.co.soramitsu.onboarding.api.data.OnboardingConfig
import retrofit2.http.GET

interface OnboardingConfigApi {

    @GET(BuildConfig.ONBOARDING_CONFIG)
    suspend fun getConfig(): OnboardingConfig

}