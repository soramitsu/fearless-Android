package jp.co.soramitsu.featureaccountapi.presentation.importing

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

enum class ImportAccountType {
    Substrate, Ethereum
}

// todo think of replace with MultiChainEncryption.Ethereum or CryptoType.ECDSA
val Chain.importAccountType: ImportAccountType
    get() = when {
        isEthereumBased -> ImportAccountType.Ethereum
        else -> ImportAccountType.Substrate
    }
