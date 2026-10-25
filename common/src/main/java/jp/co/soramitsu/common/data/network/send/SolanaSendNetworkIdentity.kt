package jp.co.soramitsu.common.data.network.send

import jp.co.soramitsu.common.data.network.solana.SolanaPublicKeyCodec

/** Original registry cluster plus the explicitly selected execution cluster (also supports private validators). */
data class SolanaSendNetworkIdentity(val registryGenesisHash: String, val executionGenesisHash: String) {
    init {
        SolanaPublicKeyCodec.decode(registryGenesisHash)
        SolanaPublicKeyCodec.decode(executionGenesisHash)
    }
}
