package jp.co.soramitsu.soracard.impl.presentation

import java.util.Locale
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.config.BuildConfigWrapper
import jp.co.soramitsu.common.data.network.OptionsProvider
import jp.co.soramitsu.oauth.base.sdk.SoraCardEnvironmentType
import jp.co.soramitsu.oauth.base.sdk.SoraCardKycCredentials
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardBasicContractData
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData

fun createSoraCardBasicContract() = SoraCardBasicContractData(
    apiKey = BuildConfig.SORA_CARD_API_KEY,
    domain = BuildConfig.SORA_CARD_DOMAIN,
    environment = when {
        BuildConfig.DEBUG -> SoraCardEnvironmentType.TEST
        else -> SoraCardEnvironmentType.PRODUCTION
    }
)

fun createSoraCardContract(
    userAvailableXorAmount: Double,
    isEnoughXorAvailable: Boolean,
): SoraCardContractData {
    return SoraCardContractData(
        basic = createSoraCardBasicContract(),
        locale = Locale.ENGLISH,
        kycCredentials = SoraCardKycCredentials(
            endpointUrl = BuildConfig.SORA_CARD_KYC_ENDPOINT_URL,
            username = BuildConfig.SORA_CARD_KYC_USERNAME,
            password = BuildConfig.SORA_CARD_KYC_PASSWORD,
        ),
        client = OptionsProvider.header,
        userAvailableXorAmount = userAvailableXorAmount,
        areAttemptsPaidSuccessfully = false, // will be available in Phase 2
        isEnoughXorAvailable = isEnoughXorAvailable,
        isIssuancePaid = false, // will be available in Phase 2
        soraBackEndUrl = BuildConfigWrapper.soraCardBackEndUrl
    )
}
