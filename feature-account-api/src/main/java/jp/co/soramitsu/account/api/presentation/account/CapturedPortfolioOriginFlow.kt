package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

/** Resolve a Substrate origin only from the captured wallet and catalog; reject unsupported or malformed addresses. */
fun capturedPortfolioOriginFlow(
    balances: Flow<TotalBalance>,
    chainId: ChainId,
    iconGenerator: AddressIconGenerator
): Flow<CapturedPortfolioDetail<AddressModel>> = capturedPortfolioDetailFlow(balances) { source ->
    val chain = source.chainsById[chainId] ?: return@capturedPortfolioDetailFlow null
    if (chain.ecosystem != Ecosystem.Substrate || chain.isEthereumBased) return@capturedPortfolioDetailFlow null
    val address = source.meta.address(chain) ?: return@capturedPortfolioDetailFlow null
    withContext(Dispatchers.Default) {
        // Decode strictly here; the legacy convenience helper substitutes an all-zero account on failure.
        val accountId = address.toAccountId()
        val icon = iconGenerator.createAddressIcon(accountId, AddressIconGenerator.SIZE_SMALL)
        AddressModel(address, icon, source.meta.name)
    }
}
