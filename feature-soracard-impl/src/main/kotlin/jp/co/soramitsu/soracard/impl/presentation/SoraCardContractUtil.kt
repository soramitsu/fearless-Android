package jp.co.soramitsu.soracard.impl.presentation

import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.OptionsProvider
import jp.co.soramitsu.oauth.base.sdk.SoraCardEnvironmentType
import jp.co.soramitsu.oauth.base.sdk.SoraCardInfo
import jp.co.soramitsu.oauth.base.sdk.SoraCardKycCredentials
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import java.util.Locale

fun createSoraCardContract(soraCardInfo: SoraCardInfo?): SoraCardContractData {
    return SoraCardContractData(
        locale = Locale.ENGLISH,
        apiKey = BuildConfig.SORA_CARD_API_KEY,
        domain = BuildConfig.SORA_CARD_DOMAIN,
        kycCredentials = SoraCardKycCredentials(
            endpointUrl = BuildConfig.SORA_CARD_KYC_ENDPOINT_URL,
            username = BuildConfig.SORA_CARD_KYC_USERNAME,
            password = BuildConfig.SORA_CARD_KYC_PASSWORD,
        ),
        environment = when {
            BuildConfig.DEBUG -> SoraCardEnvironmentType.TEST
            else -> SoraCardEnvironmentType.PRODUCTION
        },
        soraCardInfo = soraCardInfo,
        client = OptionsProvider.header
    )
}
