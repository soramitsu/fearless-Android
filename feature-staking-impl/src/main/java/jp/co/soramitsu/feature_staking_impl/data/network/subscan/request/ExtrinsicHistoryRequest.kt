package jp.co.soramitsu.feature_staking_impl.data.network.subscan.request

class ExtrinsicHistoryRequest(
    val page: Int,
    val row: Int,
    val address: String,
    val module: String?,
    val call: String?,
) {

    companion object {
        const val MODULE_UTILITY = "Utility"
        const val CALL_BATCH = "batch"
        const val CALL_BATCH_ALL = "batch_all"

        const val MODULE_STAKING = "Staking"
        const val BOND = "bond"
        const val NOMINATE = "nominate"
        const val SET_CONTROLLER = "set_controller"
    }
}
