package jp.co.soramitsu.soracard.api.util

import jp.co.soramitsu.feature_soracard_api.BuildConfig
import java.util.Locale
import jp.co.soramitsu.oauth.base.sdk.SoraCardEnvironmentType
import jp.co.soramitsu.oauth.base.sdk.SoraCardKycCredentials
import jp.co.soramitsu.oauth.base.sdk.contract.IbanStatus
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardBasicContractData
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardFlow
import jp.co.soramitsu.oauth.uiscreens.clientsui.UiStyle

fun IbanStatus?.readyToStartGatehubOnboarding(): Boolean {
    return (this != null) && (this == IbanStatus.ACTIVE)
}

val soraCardBackendUrl = BuildConfig.SORA_CARD_BACKEND.let { url ->
    if (url.endsWith("/")) url else "$url/"
}

private const val header = "Fearless/${BuildConfig.BUILD_TYPE}/${BuildConfig.SORA_CARD_VERSION_CODE}/${BuildConfig.SORA_CARD_VERSION_NAME}"

fun createSoraCardBasicContract() = SoraCardBasicContractData(
    apiKey = BuildConfig.SORA_CARD_API_KEY,
    domain = BuildConfig.SORA_CARD_DOMAIN,
    environment = if (BuildConfig.DEBUG) SoraCardEnvironmentType.TEST else SoraCardEnvironmentType.PRODUCTION,
    platform = BuildConfig.SORA_CARD_PLATFORM,
    recaptcha = BuildConfig.SORA_CARD_RECAPTCHA,
)

fun createSoraCardGateHubContract(): SoraCardContractData {
    return SoraCardContractData(
        basic = createSoraCardBasicContract(),
        locale = Locale.ENGLISH,
        soraBackEndUrl = soraCardBackendUrl,
        client = header,
        clientDark = true,
        flow = SoraCardFlow.SoraCardGateHubFlow,
        clientCase = UiStyle.FW,
    )
}

fun createSoraCardContract(
    userAvailableXorAmount: Double,
    isEnoughXorAvailable: Boolean,
): SoraCardContractData {
    return SoraCardContractData(
        basic = createSoraCardBasicContract(),
        locale = Locale.ENGLISH,
        soraBackEndUrl = soraCardBackendUrl,
        client = header,
        clientDark = true,
        clientCase = UiStyle.FW,
        flow = SoraCardFlow.SoraCardKycFlow(
            kycCredentials = SoraCardKycCredentials(
                endpointUrl = BuildConfig.SORA_CARD_KYC_ENDPOINT_URL,
                username = BuildConfig.SORA_CARD_KYC_USERNAME,
                password = BuildConfig.SORA_CARD_KYC_PASSWORD,
            ),
            userAvailableXorAmount = userAvailableXorAmount,
//        will be available in Phase 2
            areAttemptsPaidSuccessfully = false,
            isEnoughXorAvailable = isEnoughXorAvailable,
//        will be available in Phase 2
            isIssuancePaid = false,
            logIn = false,
        ),
    )
}