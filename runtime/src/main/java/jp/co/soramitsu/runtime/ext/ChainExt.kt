package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.utils.accountIdFromMapKey
import jp.co.soramitsu.common.utils.ethereumAddressFromMapKey
import jp.co.soramitsu.common.utils.ethereumAddressToHex
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.MultiAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.V4R2WalletContract
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAddress

fun Chain.addressOf(accountId: ByteArray): String {
    return when (ecosystem) {
        Ecosystem.Substrate -> accountId.toAddress(addressPrefix.toShort())
        Ecosystem.EthereumBased,
        Ecosystem.Ethereum -> accountId.ethereumAddressToHex()
        Ecosystem.Ton -> V4R2WalletContract(accountId).getAddress(isTestNet)
    }
}

fun Chain.accountIdOf(address: String): ByteArray {
    return when (ecosystem) {
        Ecosystem.Substrate -> address.toAccountId()
        Ecosystem.EthereumBased,
        Ecosystem.Ethereum -> address.fromHex()
        Ecosystem.Ton -> {
            throw IllegalStateException("can't get ton account id from ton address")
        }
    }
}

fun Chain.hexAccountIdOf(address: String): String {
    return when (ecosystem) {
        Ecosystem.Substrate,
        Ecosystem.EthereumBased,
        Ecosystem.Ethereum -> accountIdOf(address).toHexString()
        Ecosystem.Ton -> address
    }
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
