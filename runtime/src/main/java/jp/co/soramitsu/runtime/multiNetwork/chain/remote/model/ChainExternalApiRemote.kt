package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

class ChainExternalApiRemote(
    val staking: Section?,
    val history: Section?,
    val crowdloans: Section?,
) {

    class Section(val type: String, val url: String)
}
