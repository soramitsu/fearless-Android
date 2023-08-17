package jp.co.soramitsu.account.api.domain.model

import jp.co.soramitsu.common.utils.ethereumAddressToHex
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAddress

class MetaAccountOrdering(
    val id: Long,
    val position: Int
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
    name: String
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

data class MetaAccount(
    override val id: Long,
    val chainAccounts: Map<ChainId, ChainAccount>,
    override val substratePublicKey: ByteArray,
    override val substrateCryptoType: CryptoType,
    override val substrateAccountId: ByteArray,
    override val ethereumAddress: ByteArray?,
    override val ethereumPublicKey: ByteArray?,
    override val isSelected: Boolean,
    val isBackedUp: Boolean,
    val googleBackupAddress: String?,
    override val name: String
) : LightMetaAccount {

    class ChainAccount(
        val metaId: Long,
        val chain: Chain?,
        val publicKey: ByteArray,
        val accountId: ByteArray,
        val cryptoType: CryptoType,
        val accountName: String
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MetaAccount

        if (id != other.id) return false
        if (chainAccounts != other.chainAccounts) return false
        if (!substratePublicKey.contentEquals(other.substratePublicKey)) return false
        if (substrateCryptoType != other.substrateCryptoType) return false
        if (!substrateAccountId.contentEquals(other.substrateAccountId)) return false
        if (ethereumAddress != null) {
            if (other.ethereumAddress == null) return false
            if (!ethereumAddress.contentEquals(other.ethereumAddress)) return false
        } else if (other.ethereumAddress != null) return false
        if (ethereumPublicKey != null) {
            if (other.ethereumPublicKey == null) return false
            if (!ethereumPublicKey.contentEquals(other.ethereumPublicKey)) return false
        } else if (other.ethereumPublicKey != null) return false
        if (isSelected != other.isSelected) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + chainAccounts.hashCode()
        result = 31 * result + substratePublicKey.contentHashCode()
        result = 31 * result + substrateCryptoType.hashCode()
        result = 31 * result + substrateAccountId.contentHashCode()
        result = 31 * result + (ethereumAddress?.contentHashCode() ?: 0)
        result = 31 * result + (ethereumPublicKey?.contentHashCode() ?: 0)
        result = 31 * result + isSelected.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

fun MetaAccount.hasChainAccount(chainId: ChainId) = chainId in chainAccounts

fun MetaAccount.cryptoType(chain: IChain): CryptoType {
    return when {
        hasChainAccount(chain.id) -> chainAccounts.getValue(chain.id).cryptoType
        chain.isEthereumBased -> CryptoType.ECDSA
        else -> substrateCryptoType
    }
}

fun MetaAccount.address(chain: Chain): String? {
    return when {
        hasChainAccount(chain.id) -> chain.addressOf(chainAccounts.getValue(chain.id).accountId)
        chain.isEthereumBased -> ethereumAddress?.ethereumAddressToHex()
        else -> substrateAccountId.toAddress(chain.addressPrefix.toShort())
    }
}

fun MetaAccount.chainAddress(chain: Chain): String? {
    return when {
        hasChainAccount(chain.id) -> chain.addressOf(chainAccounts.getValue(chain.id).accountId)
        else -> null
    }
}

fun MetaAccount.accountId(chain: IChain): ByteArray? {
    return when {
        hasChainAccount(chain.id) -> chainAccounts.getValue(chain.id).accountId
        chain.isEthereumBased -> ethereumAddress
        else -> substrateAccountId
    }
}
