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

    /** `https://fearlesswallet.io/ton-connect/...`: the verified app link. */
    TON_CONNECT_LINK,

    /** A `content://` document opened as `application/json`. */
    JSON_DOCUMENT
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
    private const val DOCUMENT_SCHEME = "content"

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

            uri.scheme == TON_CONNECT_SCHEME &&
                uri.encodedAuthority == TON_CONNECT_HOST &&
                uri.encodedPath.orEmpty().startsWith(TON_CONNECT_PATH_PREFIX) ->
                WalletEntryData.TON_CONNECT_LINK

            uri.scheme == DOCUMENT_SCHEME &&
                !uri.encodedAuthority.isNullOrEmpty() &&
                type == JSON_MIME_TYPE ->
                WalletEntryData.JSON_DOCUMENT

            else -> null
        }
    }
}
