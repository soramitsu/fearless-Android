package jp.co.soramitsu.account.impl.data.mappers

import java.text.SimpleDateFormat
import java.util.Locale
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.impl.presentation.node.model.NodeModel
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.common.data.network.nomis.NomisResponse
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.NomisWalletScoreLocal
import jp.co.soramitsu.coredb.model.chain.ChainAccountLocal
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal
import jp.co.soramitsu.coredb.model.chain.JoinedMetaAccountInfo
import jp.co.soramitsu.coredb.model.chain.MetaAccountLocal
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.ext.hexAccountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.extensions.toHexString

fun mapCryptoTypeToCryptoTypeModel(
    resourceManager: ResourceManager,
    encryptionType: CryptoType
): CryptoTypeModel {
    val name = when (encryptionType) {
        CryptoType.SR25519 -> "${resourceManager.getString(R.string.sr25519_selection_title)} ${
            resourceManager.getString(
                R.string.sr25519_selection_subtitle
            )
        }"
        CryptoType.ED25519 -> "${resourceManager.getString(R.string.ed25519_selection_title)} ${
            resourceManager.getString(
                R.string.ed25519_selection_subtitle
            )
        }"
        CryptoType.ECDSA -> "${resourceManager.getString(R.string.ecdsa_selection_title)} ${
            resourceManager.getString(
                R.string.ecdsa_selection_subtitle
            )
        }"
    }

    return CryptoTypeModel(name, encryptionType)
}

fun mapNodeToNodeModel(node: ChainNode): NodeModel {
    return with(node) {
        NodeModel(
            name = name,
            link = url,
            isDefault = isDefault,
            isActive = isActive
        )
    }
}

fun mapMetaAccountLocalToLightMetaAccount(
    metaAccountLocal: MetaAccountLocal
): LightMetaAccount = with(metaAccountLocal) {
    LightMetaAccount(
        id = id,
        substratePublicKey = substratePublicKey,
        substrateCryptoType = substrateCryptoType,
        substrateAccountId = substrateAccountId,
        ethereumAddress = ethereumAddress,
        ethereumPublicKey = ethereumPublicKey,
        isSelected = isSelected,
        name = name,
        isBackedUp = isBackedUp,
        initialized = initialized,
    )
}

fun mapMetaAccountLocalToMetaAccount(
    chainsById: Map<ChainId, Chain>,
    joinedMetaAccountInfo: JoinedMetaAccountInfo
): MetaAccount {
    val chainAccounts = joinedMetaAccountInfo.chainAccounts.associateBy(
        keySelector = ChainAccountLocal::chainId,
        valueTransform = {
            MetaAccount.ChainAccount(
                metaId = joinedMetaAccountInfo.metaAccount.id,
                chain = chainsById[it.chainId],
                publicKey = it.publicKey,
                accountId = it.accountId,
                cryptoType = it.cryptoType,
                accountName = it.name
            )
        }
    )

    val favoriteChains = joinedMetaAccountInfo.favoriteChains.associateBy(
        keySelector = FavoriteChainLocal::chainId,
        valueTransform = {
            MetaAccount.FavoriteChain(
                chain =  chainsById[it.chainId],
                isFavorite = it.isFavorite
            )
        }
    )

    val metaAccount = with(joinedMetaAccountInfo.metaAccount) {
        MetaAccount(
            id = id,
            chainAccounts = chainAccounts,
            favoriteChains = favoriteChains,
            substratePublicKey = substratePublicKey,
            substrateCryptoType = substrateCryptoType,
            substrateAccountId = substrateAccountId,
            ethereumAddress = ethereumAddress,
            ethereumPublicKey = ethereumPublicKey,
            isSelected = isSelected,
            name = name,
            isBackedUp = isBackedUp,
            googleBackupAddress = googleBackupAddress,
            initialized = initialized,
        )
    }
    return metaAccount
}

fun mapMetaAccountToAccount(chain: Chain, metaAccount: MetaAccount): Account? {
    return metaAccount.address(chain)?.let { address ->

        val accountId = chain.hexAccountIdOf(address)

        Account(
            address = address,
            name = metaAccount.name,
            accountIdHex = accountId,
            cryptoType = metaAccount.substrateCryptoType,
            position = 0
        )
    }
}

fun mapChainAccountToAccount(
    parent: MetaAccount,
    chainAccount: MetaAccount.ChainAccount
): Account {
    val chain = chainAccount.chain

    return Account(
        address = chain?.let { parent.address(chain) } ?: "Invalid chain (removed)",
        name = parent.name,
        accountIdHex = chainAccount.accountId.toHexString(),
        cryptoType = chainAccount.cryptoType,
        position = 0
    )
}

fun NomisResponse.toLocal(metaId: Long): NomisWalletScoreLocal {
    val score = (data.score * 100).toInt()
    return NomisWalletScoreLocal(
        metaId = metaId,
        score = score,
        updated = System.currentTimeMillis(),
        nativeBalanceUsd = data.stats.nativeBalanceUSD.toBigDecimal(),
        holdTokensUsd = data.stats.holdTokensBalanceUSD.toBigDecimal(),
        walletAgeInMonths = data.stats.walletAgeInMonths,
        totalTransactions = data.stats.totalTransactions,
        rejectedTransactions = data.stats.totalRejectedTransactions,
        avgTransactionTimeInHours = data.stats.averageTransactionTimeInHours,
        maxTransactionTimeInHours = data.stats.maxTransactionTimeInHours,
        minTransactionTimeInHours = data.stats.minTransactionTimeInHours,
        scoredAt = data.stats.scoredAt
    )
}

fun NomisWalletScoreLocal.toDomain(): NomisScoreData {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
//    sdf.timeZone = TimeZone.getTimeZone("UTC")
    val scoredAtMillis = runCatching { formatter.parse(scoredAt)?.time }.getOrNull()

    return NomisScoreData(
        metaId = metaId,
        score = score,
        updated = updated,
        nativeBalanceUsd = nativeBalanceUsd,
        holdTokensUsd = holdTokensUsd,
        walletAgeInMonths = walletAgeInMonths,
        totalTransactions = totalTransactions,
        rejectedTransactions = rejectedTransactions,
        avgTransactionTimeInHours = avgTransactionTimeInHours,
        maxTransactionTimeInHours = maxTransactionTimeInHours,
        minTransactionTimeInHours = minTransactionTimeInHours,
        scoredAt = scoredAtMillis
    )
}