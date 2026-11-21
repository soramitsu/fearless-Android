package jp.co.soramitsu.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

const val APP_PACKAGE = "jp.co.soramitsu.fearless"
const val PLAY_MARKET_APP_URI = "market://details?id=$APP_PACKAGE"
const val PLAY_MARKET_BROWSER_URI = "https://play.google.com/store/apps/details?id=$APP_PACKAGE"

/**
 * Opens the wallet's store page: the Play Store app when installed, else a browser. Returns false, and opens
 * nothing, on a device with neither; the unguarded browser fallback threw ActivityNotFoundException there.
 */
fun Context.openPlayMarketPage(): Boolean = openPlayMarket { uri ->
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
}

internal fun openPlayMarket(open: (String) -> Unit): Boolean =
    listOf(PLAY_MARKET_APP_URI, PLAY_MARKET_BROWSER_URI).any { uri ->
        try {
            open(uri)
            true
        } catch (ignored: ActivityNotFoundException) {
            false
        }
    }
