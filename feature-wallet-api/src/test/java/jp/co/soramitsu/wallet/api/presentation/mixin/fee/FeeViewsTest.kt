package jp.co.soramitsu.wallet.api.presentation.mixin.fee

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.wallet.api.presentation.model.FeeModel
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Token
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import jp.co.soramitsu.core.models.Asset as CoreAsset

@RunWith(RobolectricTestRunner::class)
class FeeViewsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val views = FeeViews(ProgressBar(context), TextView(context), TextView(context))

    @Test
    fun loadingShowsOnlyTheProgress() {
        displayFeeStatus(FeeStatus.Loading, views)

        assertEquals(View.VISIBLE, views.progress.visibility)
        assertEquals(View.GONE, views.fiat.visibility)
        assertEquals(View.GONE, views.token.visibility)
    }

    @Test
    fun loadedFeeShowsTokenAndFiatAmounts() {
        displayFeeStatus(FeeStatus.Loading, views)
        val model = FeeModel(BigDecimal("0.0125"), token())

        displayFeeStatus(FeeStatus.Loaded(model), views)

        assertEquals(View.GONE, views.progress.visibility)
        assertEquals(View.VISIBLE, views.token.visibility)
        assertEquals(model.displayToken, views.token.text.toString())
        assertEquals(model.displayFiat, views.fiat.text.toString())
        assertEquals(true, views.token.text.contains("DOT"))
    }

    @Test
    fun failedFeeShowsTheErrorTitleAndNoFiat() {
        displayFeeStatus(FeeStatus.Error, views)

        assertEquals(context.getString(R.string.common_error_general_title), views.token.text.toString())
        assertEquals("", views.fiat.text.toString())
        assertEquals(View.GONE, views.progress.visibility)
    }

    private fun token() = Token(
        configuration = CoreAsset(
            id = "dot",
            name = "Polkadot",
            symbol = "DOT",
            iconUrl = "",
            chainId = "polkadot",
            chainName = "Polkadot",
            chainIcon = null,
            isTestNet = false,
            priceId = "polkadot",
            precision = 10,
            staking = CoreAsset.StakingType.UNSUPPORTED,
            purchaseProviders = null,
            supportStakingPool = false,
            isUtility = true,
            type = ChainAssetType.Normal,
            currencyId = null,
            existentialDeposit = null,
            color = null,
            isNative = true
        ),
        fiatRateInput = AssetAmountState.Known(BigDecimal("4")),
        recentRateChangeInput = AssetAmountState.Known(BigDecimal.ZERO),
        fiatContext = FiatValuationContext("usd", FiatCurrency("usd", "$", "USD", "")),
        quoteCurrencyId = "usd",
        quotePriceId = "polkadot"
    )
}
