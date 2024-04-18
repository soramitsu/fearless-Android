package jp.co.soramitsu.common.data.network.subquery

data class SoraEraInfoValidatorResponse(val stakingEraNominators: List<Nominator>) {
    data class Nominator(val nominations: List<Nomination>) {
        data class Nomination(val validator: Validator) {
            data class Validator(val id: String? = null)
        }
    }
}