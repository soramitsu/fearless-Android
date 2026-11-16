package jp.co.soramitsu.app.root.presentation.main.hub

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.rules.ExternalResource

/**
 * Applies global snapshot writes on the main looper for the duration of a Compose test; put it outside the compose rule.
 *
 * Compose applies writes made outside composition (a ScrollState's range set during layout, for one) through
 * GlobalSnapshotManager, which runs on AndroidUiDispatcher.Main. In one Robolectric sandbox that dispatcher can stop for
 * good when an earlier test ends with its handler message pending (the looper is reset between tests). From then on the
 * writes stay pending, the compose rule keeps pumping frames for them, and a scrolling screen never becomes idle
 * ("Compose did not get idle after N attempts"). This rule clears what an earlier test left and keeps applying writes
 * from an ordinary main-looper handler, which the compose rule idles.
 */
class GlobalSnapshotApplyRule : ExternalResource() {
    private var observer: ObserverHandle? = null

    override fun before() {
        Snapshot.sendApplyNotifications()
        val handler = Handler(Looper.getMainLooper())
        val scheduled = AtomicBoolean(false)
        observer = Snapshot.registerGlobalWriteObserver {
            if (scheduled.compareAndSet(false, true)) {
                handler.post {
                    scheduled.set(false)
                    Snapshot.sendApplyNotifications()
                }
            }
        }
    }

    override fun after() {
        observer?.dispose()
        observer = null
    }
}
