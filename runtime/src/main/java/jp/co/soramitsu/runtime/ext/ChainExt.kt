package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.utils.accountIdFromMapKey
import jp.co.soramitsu.common.utils.ethereumAddressFromMapKey
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.ethereumAddressToHex
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.models.MultiAddress
import jp.co.soramitsu.core.models.TypesUsage
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.addressByte
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainEcosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId

val Chain.typesUsage: TypesUsage
    get() = when {
        types?.overridesCommon == true -> TypesUsage.ON_CHAIN
        else -> TypesUsage.UNSUPPORTED
    }

val Chain.utilityAsset
    get() = assets.first { it.chainId == this.id }

val Chain.genesisHash: String
    get() = id

fun IChain.addressOf(accountId: ByteArray): String {
    return if (isEthereumBased) {
        accountId.ethereumAddressToHex()
    } else {
        accountId.toAddress(addressPrefix.toShort())
    }
}

fun Chain.accountIdOf(address: String): ByteArray {
    return if (isEthereumBased) {
        address.fromHex()
    } else {
        address.toAccountId()
    }
}

fun Chain.hexAccountIdOf(address: String): String {
    return accountIdOf(address).toHexString()
}

fun Chain.accountFromMapKey(account: String): String =
    if (isEthereumBased) {
        account.ethereumAddressFromMapKey()
    } else {
        account.accountIdFromMapKey()
    }

fun Chain.multiAddressOf(accountId: ByteArray): MultiAddress {
    return if (isEthereumBased) {
        MultiAddress.Address20(accountId)
    } else {
        MultiAddress.Id(accountId)
    }
}

fun Chain.addressFromPublicKey(publicKey: ByteArray): String {
    return if (isEthereumBased) {
        publicKey.ethereumAddressFromPublicKey().ethereumAddressToHex()
    } else {
        publicKey.toAddress(addressPrefix.toShort())
    }
}

fun Chain.isValidAddress(address: String): Boolean {
    return runCatching {
        val tryDecodeAddress = accountIdOf(address)

        if (isEthereumBased) {
            address.fromHex().size == 20
        } else {
            address.addressByte() == addressPrefix.toShort()
        }
    }.getOrDefault(false)
}

fun Chain.multiAddressOf(address: String): MultiAddress = multiAddressOf(accountIdOf(address))

fun IChain.ecosystem() = when {
    polkadotChainId in listOf(id, parentId) -> ChainEcosystem.POLKADOT
    kusamaChainId in listOf(id, parentId) -> ChainEcosystem.KUSAMA
    else -> ChainEcosystem.STANDALONE
}
