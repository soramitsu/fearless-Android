package jp.co.soramitsu.soracard.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.api.presentation.models.SoraCardInfo
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal
import javax.inject.Inject

class SoraCardInteractorImpl @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
) : SoraCardInteractor {

    override var soraCardChainId = if (BuildConfig.DEBUG) soraTestChainId else soraMainChainId

    override suspend fun xorAssetFlow(): Flow<Asset> {
        val chain = chainRegistry.getChain(soraCardChainId)
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val xorAsset = chain.assets.firstOrNull { it.isUtility } ?: error("XOR asset not found")

        return walletRepository.assetFlow(metaAccount.id, metaAccount.substrateAccountId, xorAsset, chain.minSupportedVersion)
    }

    override fun subscribeSoraCardInfo(): Flow<SoraCardInfo?> =
        flowOf(null)

    override suspend fun getXorEuroPrice(priceId: String?): BigDecimal? {
        return null
    }

    private suspend fun getCoingeckoXorPerEurRatio(priceId: String?): BigDecimal? = priceId?.let {
        walletRepository.getSingleAssetPriceCoingecko(priceId, "eur")
    }

    override suspend fun updateSoraCardInfo(accessToken: String, refreshToken: String, accessTokenExpirationTime: Long, kycStatus: String) {
    }

    override suspend fun getSoraCardInfo(): SoraCardInfo? {
        return null
    }

    override suspend fun updateSoraCardKycStatus(kycStatus: String) {
    }
}
