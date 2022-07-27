package jp.co.soramitsu.common.data.network

class AppLinksProvider(
    val termsUrl: String,
    val privacyUrl: String,
    val payoutsLearnMore: String,
    val twitterAccountTemplate: String,
    val setControllerLearnMore: String,
    val moonbeamStakingLearnMore: String
) {

    fun getTwitterAccountUrl(
        accountName: String
    ): String = twitterAccountTemplate.format(accountName)
}
