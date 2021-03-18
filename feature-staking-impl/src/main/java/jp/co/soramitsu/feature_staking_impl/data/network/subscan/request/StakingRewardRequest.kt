package jp.co.soramitsu.feature_staking_impl.data.network.subscan.request

class StakingRewardRequest(
    val page: Int,
    val address: String,
    val row: Int = ROW_MAX,
) {
    companion object {
        const val ROW_MAX = 100
    }
}
