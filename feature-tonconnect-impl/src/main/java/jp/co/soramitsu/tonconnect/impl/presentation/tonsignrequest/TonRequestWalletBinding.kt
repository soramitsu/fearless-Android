package jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest

import java.net.URL

/** Match the existing request rule: explicit dApp wallet first, then a connected dApp host. */
internal fun tonRequestWalletId(
    explicitMetaId: Long?,
    requestUrl: String?,
    connectedWallets: List<Pair<String?, Long?>>
): Long? {
    if (explicitMetaId != null) return explicitMetaId
    val host = runCatching { URL(requestUrl).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
    return connectedWallets.firstOrNull { (url, _) ->
        runCatching { URL(url).host }.getOrNull() == host
    }?.second
}
