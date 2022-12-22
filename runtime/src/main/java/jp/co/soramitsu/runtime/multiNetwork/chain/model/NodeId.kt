package jp.co.soramitsu.runtime.multiNetwork.chain.model

// TODO: Move - OK

@JvmInline
value class NodeId(private val pair: Pair<String, String>) {

    val chainId: String
        get() = pair.first

    val nodeUrl: String
        get() = pair.second
}
