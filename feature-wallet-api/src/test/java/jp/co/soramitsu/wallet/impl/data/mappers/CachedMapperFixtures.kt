package jp.co.soramitsu.wallet.impl.data.mappers

import io.mockk.*
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.model.cache.*
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.core.models.Asset as CoreAsset

internal object CachedMapperFixtures {
    val a get() = byteArrayOf(7, 8)
    val b get() = byteArrayOf(9, 10)
    fun meta(id: Long = 7, substrate: ByteArray? = a, ton: ByteArray? = null,
             overrides: Map<String, MetaAccount.ChainAccount> = emptyMap()) = MetaAccount(
        id, overrides, emptyMap(), null, CryptoType.SR25519, substrate, b, null, ton,
        true, true, null, "public-fixture", true
    )
    fun config(id: String = "asset", chainId: String = "chain", precision: Int = 2,
               priceId: String? = "catalog-price",
               provider: CoreAsset.PriceProvider? = CoreAsset.PriceProvider("provider-price", CoreAsset.PriceProviderType.Chainlink)): CoreAsset = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.chainId } returns chainId
        every { this@mockk.precision } returns precision
        every { this@mockk.priceId } returns priceId
        every { this@mockk.priceProvider } returns provider
    }
    fun chain(id: String = "chain", ecosystem: Ecosystem = Ecosystem.Substrate,
              configs: List<CoreAsset> = listOf(config(), config("sibling")), version: String? = "7.0"): Chain = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.ecosystem } returns ecosystem
        every { externalApi } returns null
        every { isTestNet } returns false
        every { assetsById } returns configs.associateBy { it.id }
        every { minSupportedVersion } returns version
    }
    fun cell(value: String?) = value?.toByteArray()?.let { CachedNumericCell("text", it.size.toLong(), it) }
        ?: CachedNumericCell("null", 0, null)
    fun raw(account: ByteArray = a, id: String = "asset", chain: String = "chain", meta: Long = 7,
            free: String? = "1200", reserved: String? = "30", enabled: Boolean? = false,
            sort: Int = 17, status: String? = null, storedPriceId: String? = "provider-price",
            joinedPriceId: String? = storedPriceId, price: String? = "2.50", recent: String? = "-0.125") = RawCachedAssetWithPriceRow(
        RawCachedAssetRow(id, chain, account, meta, storedPriceId, sort, enabled, true, "retained-name", status,
            cell(free), cell(reserved), cell("10"), cell("20"), cell("40"), cell("50"), cell("60")),
        joinedPriceId, "USD", cell(price), cell(recent)
    )
}
