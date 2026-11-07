package jp.co.soramitsu.walletconnect.impl.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts WalletConnect at app start: connects the client to the app's screens and lets the relay
 * connection follow the app between foreground and background. The client opens no connection
 * unless a pairing or session is stored. What the old SDK stored is left on the device.
 */
@Singleton
class WalletConnectStarter internal constructor(
    private val client: WalletConnectClient,
    private val processLifecycle: () -> Lifecycle
) {
    @Volatile
    private var started = false

    @Inject
    constructor(client: WalletConnectClient) : this(
        client = client,
        processLifecycle = { ProcessLifecycleOwner.get().lifecycle }
    )

    /** Call on the main thread from `Application.onCreate`. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        WCDelegate.attach(client)
        client.start()
        processLifecycle().addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = client.setForeground(true)

            override fun onStop(owner: LifecycleOwner) = client.setForeground(false)
        })
    }
}
