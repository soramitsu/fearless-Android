package jp.co.soramitsu.app.root.presentation.main

import android.net.ConnectivityManager
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.AlertsAdapter
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.feature_staking_impl.R as StakingR

/** Actual AlertsAdapter, RecyclerView and inflated row in the existing empty debug host.
 * Only public AlertModel content/callback counters are synthetic. No StakingViewModel,
 * wallet navigation, renderer screenshot, public RPC, key or transaction is exercised.
 */
@RunWith(AndroidJUnit4::class)
class StakingAlertAdapterAuthorityInstrumentedTest {
    @get:Rule val testName = TestName()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<PolkaswapNavigationLayoutTestActivity>
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AlertsAdapter
    private lateinit var directory: File
    private var oldCalls = 0
    private var freshCalls = 0
    private var sequence = 0

    @Before fun prepareNativeAdapter() {
        val args = InstrumentationRegistry.getArguments()
        require(args.getString("allowPublicAlertAdapter") == "audit-public-offline-v1")
        val context = instrumentation.targetContext
        check(context.getSystemService(ConnectivityManager::class.java).activeNetwork == null) { "Offline fixture required" }
        val label = requireNotNull(args.getString("alertAdapterEvidence"))
        require(label.matches(Regex("[a-zA-Z0-9_-]+")))
        directory = File(context.getExternalFilesDir(null), "staking-alert-adapter-$label/${testName.methodName}")
        check(!directory.exists() && directory.mkdirs())
        scenario = ActivityScenario.launch(PolkaswapNavigationLayoutTestActivity::class.java)
        scenario.onActivity { activity ->
            adapter = AlertsAdapter()
            recycler = RecyclerView(activity).apply {
                layoutManager = LinearLayoutManager(activity)
                itemAnimator = null
                adapter = this@StakingAlertAdapterAuthorityInstrumentedTest.adapter
            }
            activity.setContentView(recycler)
        }
        instrumentation.waitForIdleSync()
    }

    @After fun closeNativeAdapter() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test fun healthyDifferentTextReplacementInvokesOnlyCurrentCallback() {
        submit(action("Initial", old = true))
        click("initial-current")
        assertEquals(1, onMain { oldCalls })
        submit(action("Replacement", old = false))
        click("different-text-current")
        assertEquals(1, onMain { oldCalls })
        assertEquals(1, onMain { freshCalls })
    }

    @Test fun equalTextReplacementRebindsTheCurrentCallback() {
        submit(action("Equal public title", old = true))
        click("initial-current")
        assertEquals(1, onMain { oldCalls })
        val fresh = action("Equal public title", old = false)
        submit(fresh)
        assertSame(fresh, onMain { adapter.currentList.single() })
        click("equal-text-replacement")
        assertEquals("Equal rendered text must not retain the prior callback", 1, onMain { oldCalls })
        assertEquals(1, onMain { freshCalls })
    }

    @Test fun reusedInfoHolderCannotInvokeItsPreviousAction() {
        submit(action("Action before reuse", old = true))
        click("initial-current")
        assertEquals(1, onMain { oldCalls })
        onMain {
            holder().bind(AlertModel(CommonR.drawable.ic_warning_filled, "Informational state", "No action", AlertModel.Type.Info), true)
            assertEquals(View.GONE, holder().itemView.findViewById<View>(StakingR.id.alertItemGoToFlowIcon).visibility)
        }
        click("same-holder-now-info")
        assertEquals("An info row must clear its reused action listener", 1, onMain { oldCalls })
        assertEquals(0, onMain { freshCalls })
    }

    @Test fun healthyInfoHolderCanBindAndInvokeAFreshAction() {
        submit(AlertModel(CommonR.drawable.ic_warning_filled, "Initial info", "No action", AlertModel.Type.Info))
        click("initial-info")
        assertEquals(0, onMain { oldCalls + freshCalls })
        onMain { holder().bind(action("Fresh action", old = false), true) }
        click("same-holder-fresh-action")
        assertEquals(0, onMain { oldCalls })
        assertEquals(1, onMain { freshCalls })
    }

    private fun action(title: String, old: Boolean) = AlertModel(
        CommonR.drawable.ic_warning_filled, title, "Public synthetic alert body",
        AlertModel.Type.CallToAction { if (old) oldCalls++ else freshCalls++ }
    )

    private fun submit(model: AlertModel) {
        val committed = CountDownLatch(1)
        onMain { adapter.submitList(listOf(model)) { committed.countDown() } }
        assertTrue("Actual ListAdapter commit must finish", committed.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!onMain {
            val h = recycler.findViewHolderForAdapterPosition(0)
            h != null && h.itemView.isAttachedToWindow && h.itemView.isShown &&
                h.itemView.findViewById<TextView>(StakingR.id.alertItemTitle).text.toString() == model.title
        }) {
            check(SystemClock.elapsedRealtime() < deadline) { "Actual native alert row did not bind" }
            SystemClock.sleep(10)
        }
        record("list-committed")
    }

    private fun holder() = requireNotNull(recycler.findViewHolderForAdapterPosition(0)) as AlertsAdapter.AlertViewHolder

    private fun click(phase: String) {
        onMain { holder().itemView.performClick() }
        record(phase)
    }

    private fun record(phase: String) {
        val data = onMain {
            val row = holder().itemView
            JSONObject().put("phase", phase).put("attached", row.isAttachedToWindow).put("shown", row.isShown)
                .put("title", row.findViewById<TextView>(StakingR.id.alertItemTitle).text.toString())
                .put("arrowVisibility", row.findViewById<View>(StakingR.id.alertItemGoToFlowIcon).visibility)
                .put("oldCalls", oldCalls).put("freshCalls", freshCalls)
                .put("adapterModelIdentity", System.identityHashCode(adapter.currentList.single()))
        }
        File(directory, "%02d-%s.json".format(sequence++, phase)).writeText(data.toString(2))
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
