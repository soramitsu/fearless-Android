package jp.co.soramitsu.runtime.multiNetwork.chain.model

// todo change to value class when ktlint will be fixed https://github.com/pinterest/ktlint/issues/1114
inline class NodeId(private val pair: Pair<String, String>) {

    val chainId: String
        get() = pair.first

    val nodeUrl: String
        get() = pair.second
}
