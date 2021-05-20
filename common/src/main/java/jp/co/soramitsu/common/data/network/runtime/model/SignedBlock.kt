package jp.co.soramitsu.common.data.network.runtime.model

class SignedBlock(val block: Block, val justification: Any?) {
    class Block(val extrinsics: List<String>, val header: Header) {
        class Header(val number: String)
    }
}
