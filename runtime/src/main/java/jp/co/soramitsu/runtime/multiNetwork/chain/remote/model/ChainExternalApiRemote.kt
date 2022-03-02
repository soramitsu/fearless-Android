package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

class ChainExternalApiRemote(
    val staking: Section?,
    val history: Section?,
    val crowdloans: Section?,
    val explorers: List<Explorer>?
) {

    class Section(val type: String, val url: String)

    class Explorer(val type: String, val types: List<String>, val url: String)
}
