package jp.co.soramitsu.walletconnect.impl.data

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import jp.co.soramitsu.walletconnect.impl.data.LegacyWalletConnectImport.Outcome
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClient
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectStateStorage
import jp.co.soramitsu.walletconnect.impl.protocol.runCatchingNonCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts WalletConnect at app start, without blocking the main thread: imports what the old SDK
 * stored ([LegacyWalletConnectImport]) before the client loads its state, starts the client, and
 * deletes the old SDK's files only after an import that succeeded or found nothing to import. The
 * relay connection follows the app between foreground and background; the client opens no
 * connection unless a pairing or session is stored.
 */
@Singleton
class WalletConnectStarter internal constructor(
    private val client: WalletConnectClient,
    private val storage: WalletConnectStateStorage,
    private val legacyImport: LegacyWalletConnectImport,
    private val legacyCleanup: LegacyWalletConnectSdkCleanup,
    private val processLifecycle: () -> Lifecycle,
    private val scope: CoroutineScope,
    private val importTimeoutMillis: Long = IMPORT_TIMEOUT_MILLIS,
    private val log: (String) -> Unit = { message -> Log.i(TAG, message) }
) {
    @Volatile
    private var started = false

    @Volatile
    private var startup: Job? = null

    @Inject
    constructor(
        client: WalletConnectClient,
        storage: WalletConnectStateStorage,
        legacyImport: Optional<LegacyWalletConnectImport>,
        legacyCleanup: LegacyWalletConnectSdkCleanup
    ) : this(
        client = client,
        storage = storage,
        legacyImport = legacyImport.orElse(LegacyWalletConnectImport.NotAttempted),
        legacyCleanup = legacyCleanup,
        processLifecycle = { ProcessLifecycleOwner.get().lifecycle },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    /** Call on the main thread from `Application.onCreate`; the import and the client's start run in the background. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        WCDelegate.attach(client)
        startup = scope.launch {
            // The client loads its state once, so the import must be complete before it starts.
            val outcome = importLegacyState()
            // Only the outcome's name: never topics, keys or URIs. A kept-files case shows in the log.
            log("Legacy WalletConnect import: ${outcome.name}")
            client.start()
            if (outcome == Outcome.IMPORTED || outcome == Outcome.NOTHING_TO_IMPORT) legacyCleanup.run()
        }
        processLifecycle().addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = client.setForeground(true)

            override fun onStop(owner: LifecycleOwner) = client.setForeground(false)
        })
    }

    /** Waits until the import, the client's start and any cleanup are done; for tests. */
    internal suspend fun awaitStartup() {
        startup?.join()
    }

    // A thrown exception, or an import still running after the timeout, counts as FAILED: the old
    // SDK's files are kept and the client starts anyway.
    private suspend fun importLegacyState(): Outcome = runCatchingNonCancellation {
        withTimeoutOrNull(importTimeoutMillis) {
            withContext(Dispatchers.IO) { legacyImport.importInto(storage) }
        }
    }.getOrNull() ?: Outcome.FAILED

    private companion object {
        const val IMPORT_TIMEOUT_MILLIS = 60_000L
        const val TAG = "WalletConnect"
    }
}
