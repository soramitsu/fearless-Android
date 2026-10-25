package jp.co.soramitsu.account.impl.domain

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.cache.CachedAssetWithPriceRead
import jp.co.soramitsu.coredb.model.cache.CachedNumericCell
import jp.co.soramitsu.coredb.model.cache.RawCachedAssetRow
import jp.co.soramitsu.coredb.model.cache.RawCachedTokenPriceRow
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetContext
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.core.models.Asset as CoreAsset

internal object AssetBalanceFixtures {
    fun configuration(id: String = "asset", chainId: String = "chain", detected: Boolean = false): CoreAsset = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.chainId } returns chainId
        every { precision } returns 0
        every { symbol } returns "UNIT"
        every { priceId } returns "price-$id"
        every { priceProvider } returns null
        every { type } returns if (detected) ChainAssetType.Unknown else ChainAssetType.Normal
    }
    private fun cell(value: String?) = value?.toByteArray(Charsets.UTF_8)?.let {
        CachedNumericCell("text", it.size.toLong(), it)
    } ?: CachedNumericCell("null", 0, null)

    fun row(id: String = "asset", chainId: String = "chain", free: String? = "4", reserved: String? = "1",
            frozen: String? = "2", currency: String = "cad", rate: String? = "2", change: String? = "10",
            enabled: Boolean? = true) = CachedAssetWithPriceRead(
        RawCachedAssetRow(id, chainId, byteArrayOf(7, 8), 7, "untrusted-old-pointer", 0,
            enabled, false, null, null, cell(free), cell(reserved), cell(frozen), cell("0"),
            cell("0"), cell("0"), cell("0")).decode(),
        RawCachedTokenPriceRow("price-$id", "FOREIGN", cell(rate), cell(change), cell(currency)).decode()
    )

    fun meta(id: Long = 7, name: String = "public-fixture") = MetaAccount(
        id, emptyMap(), emptyMap(), null, CryptoType.SR25519, byteArrayOf(7, 8), byteArrayOf(9, 10),
        null, null, true, true, null, name, true
    )

    fun snapshot(rows: List<CachedAssetWithPriceRead> = listOf(row()),
                 configs: List<CoreAsset> = listOf(configuration()),
                 currency: String = "cad", metadata: FiatCurrency? = null,
                 ready: Boolean = true, account: MetaAccount = meta()): CurrentAccountAssetSnapshot {
        val chain: Chain = mockk(relaxed = true) {
            every { id } returns "chain"
            every { assetsById } returns configs.associateBy { it.id }
            every { minSupportedVersion } returns null
        }
        return CurrentAccountAssetSnapshot(CurrentAccountAssetContext(account, FiatValuationContext(currency, metadata)),
            mapOf("chain" to chain), rows, ready)
    }
}
