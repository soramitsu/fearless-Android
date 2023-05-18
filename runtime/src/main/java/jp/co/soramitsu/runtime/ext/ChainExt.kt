package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.utils.accountIdFromMapKey
import jp.co.soramitsu.common.utils.ethereumAddressFromMapKey
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.ethereumAddressToHex
import jp.co.soramitsu.core.models.MultiAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainEcosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAddress

val Chain.genesisHash: String
    get() = id

fun Chain.addressOf(accountId: ByteArray): String {
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

fun Chain.fakeAddress(): String {
    return if (isEthereumBased) {
        fakeEthereumAddress().ethereumAddressToHex()
    } else {
        fakeAccountId().toAddress(addressPrefix.toShort())
    }
}

private fun fakeAccountId() = ByteArray(32)

private fun fakeEthereumAddress() = ByteArray(20)

fun Chain.multiAddressOf(address: String): MultiAddress = multiAddressOf(accountIdOf(address))

fun Chain.ecosystem() = when {
    polkadotChainId in listOf(id, parentId) -> ChainEcosystem.POLKADOT
    kusamaChainId in listOf(id, parentId) -> ChainEcosystem.KUSAMA
    else -> ChainEcosystem.STANDALONE
}
