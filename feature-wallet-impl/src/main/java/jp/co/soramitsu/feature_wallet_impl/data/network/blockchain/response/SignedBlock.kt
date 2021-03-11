package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response

class SignedBlock(val block: Block, val justification: Any?) {
    class Block(val extrinsics: List<String>, val header: Any?)
}