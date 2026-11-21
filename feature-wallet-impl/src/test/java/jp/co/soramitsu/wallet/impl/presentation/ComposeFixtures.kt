package jp.co.soramitsu.wallet.impl.presentation

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import java.time.Duration
import org.robolectric.shadows.ShadowSystemClock

/** Shared fixtures for the module's Robolectric Compose tests. */
internal object ComposeFixtures {

    val context: Context get() = ApplicationProvider.getApplicationContext()

    fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    fun plural(id: Int, count: Int, vararg args: Any): String = context.resources.getQuantityString(id, count, *args)

    /** Keep the app's supported reduced-motion mode on while asserting UI state and interactions. */
    fun prepareUiTest() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        passDoubleTapGuard()
    }

    /** Moves Robolectric's clock past the 1.5 s double-tap guard of `onSingleClick` buttons. */
    fun passDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    fun assetRow(
        index: Int? = 0,
        symbol: String = "dot",
        name: String = "Polkadot",
        chainName: String = "Polkadot",
        chainId: String = "polkadot-chain",
        chainAssetId: String = "dot-asset",
        balance: String? = "12.5",
        fiat: String? = "$75.00",
        isHidden: Boolean = false,
        isTestnet: Boolean = false,
        isSupported: Boolean = true,
        metadataTrust: String = "verified",
        assetPreference: String = "auto",
        networkFiatSubtotal: String? = null,
        networkIsStale: Boolean = false,
        networkSyncError: String? = null,
        networkLastSuccessMillis: Long? = 1L,
        networkAccountLabel: String? = null
    ) = AssetListItemViewState(
        index = index,
        assetIconUrl = "",
        assetName = name,
        assetChainName = chainName,
        assetSymbol = symbol,
        assetTokenFiat = "$6.00",
        assetTokenRate = "+1.2%",
        assetTransferableBalance = balance,
        assetTransferableBalanceFiat = fiat,
        assetChainUrls = mapOf(chainId to ""),
        chainId = chainId,
        chainAssetId = chainAssetId,
        isSupported = isSupported,
        isHidden = isHidden,
        assetPreference = assetPreference,
        isTestnet = isTestnet,
        metadataTrust = metadataTrust,
        networkFiatSubtotal = networkFiatSubtotal,
        networkIsStale = networkIsStale,
        networkSyncError = networkSyncError,
        networkLastSuccessMillis = networkLastSuccessMillis,
        networkAccountLabel = networkAccountLabel
    )
}
