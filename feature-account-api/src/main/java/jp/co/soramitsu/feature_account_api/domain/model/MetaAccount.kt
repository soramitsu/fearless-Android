package jp.co.soramitsu.feature_account_api.domain.model

import jp.co.soramitsu.common.utils.ethereumAddressToHex
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class MetaAccountOrdering(
    val id: Long,
    val position: Int,
)

interface LightMetaAccount {
    val id: Long
    val substratePublicKey: ByteArray
    val substrateCryptoType: CryptoType
    val substrateAccountId: ByteArray
    val ethereumAddress: ByteArray?
    val ethereumPublicKey: ByteArray?
    val isSelected: Boolean
    val name: String
}

fun LightMetaAccount(
    id: Long,
    substratePublicKey: ByteArray,
    substrateCryptoType: CryptoType,
    substrateAccountId: ByteArray,
    ethereumAddress: ByteArray?,
    ethereumPublicKey: ByteArray?,
    isSelected: Boolean,
    name: String,
) = object : LightMetaAccount {
    override val id: Long = id
    override val substratePublicKey: ByteArray = substratePublicKey
    override val substrateCryptoType: CryptoType = substrateCryptoType
    override val substrateAccountId: ByteArray = substrateAccountId
    override val ethereumAddress: ByteArray? = ethereumAddress
    override val ethereumPublicKey: ByteArray? = ethereumPublicKey
    override val isSelected: Boolean = isSelected
    override val name: String = name
}

class MetaAccount(
    override val id: Long,
    val chainAccounts: Map<ChainId, ChainAccount>,
    override val substratePublicKey: ByteArray,
    override val substrateCryptoType: CryptoType,
    override val substrateAccountId: ByteArray,
    override val ethereumAddress: ByteArray?,
    override val ethereumPublicKey: ByteArray?,
    override val isSelected: Boolean,
    override val name: String,
) : LightMetaAccount {

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
