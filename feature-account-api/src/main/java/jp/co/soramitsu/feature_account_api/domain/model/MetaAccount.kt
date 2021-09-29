package jp.co.soramitsu.feature_account_api.domain.model

import jp.co.soramitsu.common.utils.ethereumAddressToHex
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class MetaAccount(
    val id: Long,
    val chainAccounts: Map<ChainId, ChainAccount>,
    val substratePublicKey: ByteArray,
    val substrateCryptoType: CryptoType,
    val substrateAccountId: ByteArray,
    val ethereumAddress: ByteArray?,
    val ethereumPublicKey: ByteArray?,
    val isSelected: Boolean,
    val name: String,
) {

    class ChainAccount(
        val metaId: Long,
        val chain: Chain,
        val publicKey: ByteArray,
        val accountId: ByteArray,
        val cryptoType: CryptoType,
    )
}

fun MetaAccount.hasChainAccountIn(chainId: ChainId) = chainId in chainAccounts

fun MetaAccount.cryptoTypeIn(chain: Chain): CryptoType {
    return when {
        hasChainAccountIn(chain.id) -> chainAccounts.getValue(chain.id).cryptoType
        chain.isEthereumBased -> CryptoType.ECDSA
        else -> substrateCryptoType
    }
}

fun MetaAccount.addressIn(chain: Chain): String? {
    return when {
        hasChainAccountIn(chain.id) -> chain.addressOf(chainAccounts.getValue(chain.id).accountId)
        chain.isEthereumBased -> ethereumAddress?.ethereumAddressToHex()
        else -> substrateAccountId.toAddress(chain.addressPrefix.toByte())
    }
}

fun MetaAccount.accountIdIn(chain: Chain): ByteArray? {
    return when {
        hasChainAccountIn(chain.id) -> chainAccounts.getValue(chain.id).accountId
        chain.isEthereumBased -> ethereumAddress
        else -> substrateAccountId
    }
}
