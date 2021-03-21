package jp.co.soramitsu.feature_staking_api.domain.model

data class StakingStory(
    val title: String,
    val iconSymbol: String,
    val elements: List<Element>
) {

    data class Element(
        val title: String,
        val body: String,
        val url: String
    )
}
