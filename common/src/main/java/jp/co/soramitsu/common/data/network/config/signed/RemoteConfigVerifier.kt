package jp.co.soramitsu.common.data.network.config.signed

/**
 * Decides which content of a remote configuration file the app uses.
 *
 * A fetched file is used only when the newest signed manifest lists exactly its SHA-256. Otherwise (no network, an
 * unsigned or changed file, a missing or invalid manifest) the app keeps the last copy that verified, or else the
 * snapshot bundled into the APK. A manifest replaces the known one only when its signature verifies and its sequence is
 * not lower, so an old signed manifest cannot bring back old files either.
 */
class RemoteConfigVerifier(
    /** Fetches the published signed manifest; null when it cannot be fetched. */
    private val fetchManifest: () -> ByteArray?,
    private val store: RemoteConfigStore,
    private val snapshot: RemoteConfigSnapshot,
    private val verify: (ByteArray) -> SignedRemoteConfigManifest = { SignedRemoteConfigManifest.verify(it) },
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Where the content [resolve] returned came from. */
    enum class Source { NETWORK, LAST_VERIFIED, BUNDLED }

    class Resolved(val content: ByteArray, val source: Source)

    private var manifest: SignedRemoteConfigManifest? = null
    private var manifestFetchedAt = Long.MIN_VALUE
    private var lastFetchAttempt = Long.MIN_VALUE
    private var loaded = false

    /**
     * The content to use for [path] after [fetched] arrived from the network (null when the fetch failed), or null when
     * nothing verified is available.
     */
    @Synchronized
    fun resolve(path: String, fetched: ByteArray?): Resolved? {
        if (fetched != null) {
            // A file newer than the known manifest is checked once more against a freshly fetched manifest.
            if (verifiedAgainst(currentManifest(refresh = false), path, fetched) ||
                verifiedAgainst(currentManifest(refresh = true), path, fetched)
            ) {
                runCatching { store.saveVerified(path, fetched) }
                return Resolved(fetched, Source.NETWORK)
            }
        }
        store.lastVerified(path)?.let { return Resolved(it, Source.LAST_VERIFIED) }
        return snapshot.read(path)?.let { Resolved(it, Source.BUNDLED) }
    }

    private fun verifiedAgainst(manifest: SignedRemoteConfigManifest?, path: String, content: ByteArray) =
        manifest?.lists(path, content) == true

    private fun manifestIsFresh() = manifestFetchedAt != Long.MIN_VALUE && clock() - manifestFetchedAt < MANIFEST_TTL_MILLIS

    private fun currentManifest(refresh: Boolean): SignedRemoteConfigManifest? {
        if (!loaded) {
            loaded = true
            manifest = newest(store.lastManifest(verify), snapshot.manifestEnvelope()?.let { runCatching { verify(it) }.getOrNull() })
        }
        val now = clock()
        val due = refresh || !manifestIsFresh()
        val throttled = lastFetchAttempt != Long.MIN_VALUE && now - lastFetchAttempt < RETRY_MILLIS
        if (due && !throttled) {
            lastFetchAttempt = now
            val fetched = runCatching { fetchManifest()?.let(verify) }.getOrNull()
            val known = manifest
            if (fetched != null && (known == null || fetched.sequence >= known.sequence)) {
                manifest = fetched
                manifestFetchedAt = now
                runCatching { store.saveManifest(fetched) }
            }
        }
        return manifest
    }

    private fun newest(vararg manifests: SignedRemoteConfigManifest?) = manifests.filterNotNull().maxByOrNull { it.sequence }

    companion object {
        // raw.githubusercontent.com serves files with a five-minute cache lifetime.
        const val MANIFEST_TTL_MILLIS = 5 * 60 * 1000L
        const val RETRY_MILLIS = 30 * 1000L
    }
}
