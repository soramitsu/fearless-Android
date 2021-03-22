package jp.co.soramitsu.feature_staking_api.domain.model

data class StakingStory(
    val titleRes: Int,
    val iconSymbol: String,
    val elements: List<Element>
) {

    data class Element(
        val titleRes: Int,
        val bodyRes: Int,
        val url: String
    )
}
