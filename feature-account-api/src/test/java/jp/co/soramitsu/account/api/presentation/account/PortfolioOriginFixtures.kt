package jp.co.soramitsu.account.api.presentation.account

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

internal fun originMeta(name: String = "Public wallet", key: ByteArray? = ByteArray(32) { 1 }) = MetaAccount(
    7, emptyMap(), emptyMap(), key, CryptoType.SR25519, key, null, null, null,
    true, true, null, name, true
)

internal fun originChain(prefix: Int = 0): Chain = mockk {
    every { id } returns "captured-chain"
    every { ecosystem } returns Ecosystem.Substrate
    every { externalApi } returns null
    every { isEthereumBased } returns false
    every { addressPrefix } returns prefix
}

internal fun originReady(
    meta: MetaAccount = originMeta(),
    chains: Map<String, Chain> = mapOf("captured-chain" to originChain()),
    currency: String = "cad",
    amount: String? = "10",
    subtotal: String? = amount,
    quotesReady: Boolean = true
): TotalBalance.Ready {
    val shown = PortfolioBalance(amount?.toBigDecimal(), subtotal?.toBigDecimal(), null, null,
        1, if (subtotal == null) 0 else 1, if (amount == null) 1 else 0, 0)
    val hidden = PortfolioBalance("0".toBigDecimal(), "0".toBigDecimal(), null, null, 0, 0, 0, 0)
    return TotalBalance.Ready(meta, chains, currency, "$", quotesReady, shown, hidden, shown)
}
