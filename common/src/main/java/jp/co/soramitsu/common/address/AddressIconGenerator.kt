package jp.co.soramitsu.common.address

import android.graphics.drawable.PictureDrawable
import androidx.annotation.ColorRes
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.shared_utils.exceptions.AddressFormatException
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.icon.IconGenerator
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap


interface AddressIconGenerator {

    companion object {

        const val SIZE_SMALL = 18
        const val SIZE_MEDIUM = 24
        const val SIZE_BIG = 32
    }

    suspend fun createAddressIcon(
        accountId: AccountId,
        sizeInDp: Int,
        @ColorRes backgroundColorRes: Int = R.color.account_icon_light
    ): PictureDrawable

    suspend fun createEthereumAddressIcon(
        accountId: AccountId,
        sizeInDp: Int,
        @ColorRes backgroundColorRes: Int = R.color.account_icon_light
    ): PictureDrawable

    suspend fun createWalletIcon(ecosystem: WalletEcosystem, sizeInDp: Int): PictureDrawable
}

@Throws(AddressFormatException::class)
suspend fun AddressIconGenerator.createAddressModel(
    isEthereumBased: Boolean,
    accountAddress: String,
    sizeInDp: Int,
    accountName: String? = null
): AddressModel {
    val icon = createAddressIcon(isEthereumBased, accountAddress, sizeInDp)

    return AddressModel(accountAddress, icon, accountName)
}

private suspend fun AddressIconGenerator.createAddressIcon(
    isEthereumBased: Boolean,
    accountAddress: String,
    sizeInDp: Int
) = when {
    isEthereumBased -> createEthereumAddressIcon(accountAddress, sizeInDp)
    else -> createAddressIcon(accountAddress, sizeInDp)
}

@Throws(AddressFormatException::class)
suspend fun AddressIconGenerator.createAddressIcon(
    isEthereumBased: Boolean,
    accountAddress: String,
    sizeInDp: Int,
    @ColorRes backgroundColorRes: Int = R.color.account_icon_light
) = withContext(Dispatchers.Default) {
    val accountId = if (isEthereumBased) {
        runCatching { accountAddress.toByteArray() }.getOrDefault(ByteArray(32))
    } else {
        runCatching { accountAddress.toAccountId() }.getOrDefault(ByteArray(32))
    }
    if (isEthereumBased) {
        createEthereumAddressIcon(accountId, sizeInDp)
    } else {
        createAddressIcon(accountId, sizeInDp, backgroundColorRes)
    }
}

@Throws(AddressFormatException::class)
suspend fun AddressIconGenerator.createAddressModel(accountAddress: String, sizeInDp: Int, accountName: String? = null): AddressModel {
    val icon = createAddressIcon(accountAddress, sizeInDp)

    return AddressModel(accountAddress, icon, accountName)
}

@Throws(AddressFormatException::class)
suspend fun AddressIconGenerator.createAddressModel(supportedEcosystemWithAddress: Map<WalletEcosystem, String>, accountAddress: String, sizeInDp: Int, accountName: String? = null): AddressModel {
    val icon = createAddressIcon(supportedEcosystemWithAddress, sizeInDp)

    return AddressModel(accountAddress, icon, accountName)
}

@Throws(AddressFormatException::class)
suspend fun AddressIconGenerator.createAddressIcon(supportedEcosystemWithAddress: Map<WalletEcosystem, String>, sizeInDp: Int, accountName: String? = null): PictureDrawable {
    return if (supportedEcosystemWithAddress.size == 1) {
        val ecosystem = supportedEcosystemWithAddress.toList()[0].first
        createWalletIcon(ecosystem, sizeInDp)
    } else {
        val address = supportedEcosystemWithAddress.toList().sortedBy {
            when (it.first) {
                WalletEcosystem.Substrate -> 1
                WalletEcosystem.Ethereum -> 2
                WalletEcosystem.Ton -> 3
            }
        }[0].second

        createAddressIcon(address, sizeInDp)
    }
}

@Throws(AddressFormatException::class)
suspend fun AddressIconGenerator.createAddressModel(supportedEcosystemWithAddress: Map<WalletEcosystem, String>, sizeInDp: Int, accountName: String? = null): AddressModel {
    if (supportedEcosystemWithAddress.size == 1) {
        val (ecosystem, address) = supportedEcosystemWithAddress.toList()[0]
        val icon = createWalletIcon(ecosystem, sizeInDp)
        return AddressModel(address, icon, accountName)
    } else {
        val address = supportedEcosystemWithAddress.toList().sortedBy {
            when (it.first) {
                WalletEcosystem.Substrate -> 1
                WalletEcosystem.Ethereum -> 2
                WalletEcosystem.Ton -> 3
            }
        }[0].second

        val icon = createAddressIcon(address, sizeInDp)
        return AddressModel(address, icon, accountName)
    }
}

@Throws(AddressFormatException::class)
suspend fun AddressIconGenerator.createEthereumAddressModel(accountAddress: String, sizeInDp: Int, accountName: String? = null): AddressModel {
    val icon = createEthereumAddressIcon(accountAddress.requireHexPrefix(), sizeInDp)
    return AddressModel(accountAddress, icon, accountName)
}

@Throws(AddressFormatException::class)
suspend fun AddressIconGenerator.createAddressIcon(accountAddress: String, sizeInDp: Int) = withContext(Dispatchers.Default) {
    val addressId = runCatching { accountAddress.toAccountId() }.getOrDefault(ByteArray(32))

    createAddressIcon(addressId, sizeInDp)
}

@Throws(AddressFormatException::class)
suspend fun AddressIconGenerator.createEthereumAddressIcon(accountAddress: String, sizeInDp: Int) = withContext(Dispatchers.Default) {
    val addressId = runCatching { accountAddress.toByteArray() }.getOrDefault(ByteArray(32))
    createEthereumAddressIcon(addressId, sizeInDp)
}

class CachingAddressIconGenerator(
    private val delegate: AddressIconGenerator
) : AddressIconGenerator {

    val cache = ConcurrentHashMap<String, PictureDrawable>()

    override suspend fun createAddressIcon(
        accountId: AccountId,
        sizeInDp: Int,
        @ColorRes backgroundColorRes: Int
    ): PictureDrawable = withContext(Dispatchers.Default) {
        val key = "${accountId.toHexString()}:$sizeInDp:$backgroundColorRes"

        cache.getOrPut(key) {
            delegate.createAddressIcon(accountId, sizeInDp, backgroundColorRes)
        }
    }

    override suspend fun createEthereumAddressIcon(accountId: AccountId, sizeInDp: Int, backgroundColorRes: Int): PictureDrawable =
        withContext(Dispatchers.Default) {
            val key = "${accountId.toHexString()}:$sizeInDp:$backgroundColorRes"

            cache.getOrPut(key) {
                delegate.createEthereumAddressIcon(accountId, sizeInDp, backgroundColorRes)
            }
        }

    override suspend fun createWalletIcon(ecosystem: WalletEcosystem, sizeInDp: Int): PictureDrawable =
        withContext(Dispatchers.Default) {
            val key = "${ecosystem.name}:$sizeInDp"

            cache.getOrPut(key) {
                delegate.createWalletIcon(ecosystem, sizeInDp)
            }
        }
}

class StatelessAddressIconGenerator(
    private val iconGenerator: IconGenerator,
    private val resourceManager: ResourceManager
) : AddressIconGenerator {

    override suspend fun createAddressIcon(
        accountId: AccountId,
        sizeInDp: Int,
        @ColorRes backgroundColorRes: Int
    ) = withContext(Dispatchers.Default) {
        val sizeInPx = resourceManager.measureInPx(sizeInDp)
        val backgroundColor = resourceManager.getColor(backgroundColorRes)

        iconGenerator.getSvgImage(accountId, sizeInPx, backgroundColor = backgroundColor)
    }

    override suspend fun createEthereumAddressIcon(accountId: AccountId, sizeInDp: Int, backgroundColorRes: Int) = withContext(Dispatchers.Default) {
        val sizeInPx = resourceManager.measureInPx(sizeInDp)
        val backgroundColor = resourceManager.getColor(backgroundColorRes)
        iconGenerator.generateEthereumAddressIcon(accountId, sizeInPx, backgroundColor = backgroundColor)
    }

    override suspend fun createWalletIcon(ecosystem: WalletEcosystem, sizeInDp: Int): PictureDrawable = withContext(Dispatchers.Default) {
        val sizeInPx = resourceManager.measureInPx(sizeInDp)
        val icon = when (ecosystem) {
            WalletEcosystem.Substrate -> iconGenerator.getSubstrateWalletIcon(sizeInPx)
            WalletEcosystem.Ethereum -> iconGenerator.getEvmWalletIcon(sizeInPx)
            WalletEcosystem.Ton -> iconGenerator.getTonWalletIcon(sizeInPx)
        }
        icon
    }
}
