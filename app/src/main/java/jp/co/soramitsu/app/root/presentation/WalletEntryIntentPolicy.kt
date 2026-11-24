package jp.co.soramitsu.app.root.presentation

/**
 * The parts of an entry Intent's data URI that [WalletEntryIntentPolicy]
 * inspects, exactly as android.net.Uri reports them. The encoded authority
 * and path are compared, so percent-decoding can never widen a match.
 */
internal data class WalletEntryUri(
    val scheme: String?,
    val encodedAuthority: String?,
    val encodedPath: String?
)

/** The only kinds of data an external caller can hand to the wallet. */
internal enum class WalletEntryData {
    /** `fearless://buy-success`: a buy provider returning to the wallet. */
    BUY_PROVIDER_REDIRECT,

    /**
     * A dApp's TON Connect connect link: `https://fearlesswallet.io/ton-connect/...` (the verified app link) or
     * `tc://?...`. Only RootViewModel acts on it, after the PIN, and parses every field strictly first.
     */
    TON_CONNECT_LINK,

    /** A `content://` document opened as `application/json`. */
    JSON_DOCUMENT,

    /**
     * `irohaconnect://connect?…` or `iroha://connect?…`: a dApp's Iroha Connect wallet invitation. Only the
     * session screen acts on it, after the PIN, and parses every field strictly before joining any relay.
     */
    IROHA_CONNECT_LINK
}

/**
 * Decides what an Intent delivered to the exported launcher may carry into
 * WalletRootActivity.
 *
 * WalletRootActivity's NavHostFragment handles getIntent() as a deep link
 * when its graph is created: Navigation's deep-link extras, or a URI that
 * matches a nav-graph `<deepLink>`, would open that destination without
 * passing the splash and PIN screens. Only the VIEW data advertised by the
 * manifest's intent filters is admitted; the caller drops everything else.
 */
internal object WalletEntryIntentPolicy {

    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val JSON_MIME_TYPE = "application/json"

    private const val BUY_REDIRECT_SCHEME = "fearless"
    private const val BUY_REDIRECT_HOST = "buy-success"
    private const val TON_CONNECT_SCHEME = "https"
    private const val TON_CONNECT_HOST = "fearlesswallet.io"
    private const val TON_CONNECT_PATH_PREFIX = "/ton-connect/"
    private const val TON_CONNECT_LINK_SCHEME = "tc"
    private const val DOCUMENT_SCHEME = "content"
    private val IROHA_CONNECT_SCHEMES = setOf("irohaconnect", "iroha")
    private const val IROHA_CONNECT_HOST = "connect"

    /**
     * Returns the admitted kind of entry data, or null when the Intent must
     * reach the wallet as a plain launch without any data.
     */
    fun admit(
        action: String?,
        uri: WalletEntryUri?,
        type: String?
    ): WalletEntryData? {
        if (action != ACTION_VIEW || uri == null) return null

        return when {
            uri.scheme == BUY_REDIRECT_SCHEME &&
                uri.encodedAuthority == BUY_REDIRECT_HOST ->
                WalletEntryData.BUY_PROVIDER_REDIRECT

            isTonConnectLink(uri) ->
                WalletEntryData.TON_CONNECT_LINK

            uri.scheme in IROHA_CONNECT_SCHEMES &&
                uri.encodedAuthority == IROHA_CONNECT_HOST &&
                uri.encodedPath.isNullOrEmpty() ->
                WalletEntryData.IROHA_CONNECT_LINK

            uri.scheme == DOCUMENT_SCHEME &&
                !uri.encodedAuthority.isNullOrEmpty() &&
                type == JSON_MIME_TYPE ->
                WalletEntryData.JSON_DOCUMENT

            else -> null
        }
    }

    // The verified app link under /ton-connect/, or `tc://?v=2&id=…&r=…` with nothing between the scheme and the query.
    private fun isTonConnectLink(uri: WalletEntryUri): Boolean {
        val appLink = uri.scheme == TON_CONNECT_SCHEME &&
            uri.encodedAuthority == TON_CONNECT_HOST &&
            uri.encodedPath.orEmpty().startsWith(TON_CONNECT_PATH_PREFIX)
        val tcLink = uri.scheme == TON_CONNECT_LINK_SCHEME &&
            uri.encodedAuthority.isNullOrEmpty() &&
            uri.encodedPath.isNullOrEmpty()
        return appLink || tcLink
    }
}
