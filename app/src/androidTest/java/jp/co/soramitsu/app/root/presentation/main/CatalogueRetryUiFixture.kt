package jp.co.soramitsu.app.root.presentation.main

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.drawable.PictureDrawable
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.view.doOnPreDraw
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.app.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSearchCustomValidatorsBinding
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.CustomBlockProducersAdapter
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchBlockProducerModel
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchBlockProducersState
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.renderValidatorSearchState
import org.junit.Assert.assertTrue
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class CatalogueRetryUiFixture(locale: Locale = Locale.US) : AutoCloseable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scenario = ActivityScenario.launch<PolkaswapNavigationLayoutTestActivity>(
        Intent(instrumentation.targetContext, PolkaswapNavigationLayoutTestActivity::class.java)
    )
    lateinit var binding: FragmentSearchCustomValidatorsBinding
        private set
    val retries = CopyOnWriteArrayList<String>()
    val selected = CopyOnWriteArrayList<String>()
    val inspected = CopyOnWriteArrayList<String>()
    val adapter = CustomBlockProducersAdapter(object : CustomBlockProducersAdapter.ItemHandler {
        override fun blockProducerClicked(blockProducerModel: SearchBlockProducerModel) {
            selected += blockProducerModel.address
        }
        override fun blockProducerInfoClicked(blockProducerModel: SearchBlockProducerModel) {
            inspected += blockProducerModel.address
        }
    })
    val rows = listOf(
        SearchBlockProducerModel("Alice", "alice-address", true, "12.5%", PictureDrawable(Picture())),
        SearchBlockProducerModel("ALPINE", "alpine-address", false, "8%", PictureDrawable(Picture()))
    )

    init {
        scenario.onActivity { activity ->
            val configuration = Configuration(activity.resources.configuration).apply { setLocale(locale) }
            val context = ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.Theme_Soramitsu_Fearless)
            binding = FragmentSearchCustomValidatorsBinding.inflate(LayoutInflater.from(context))
            binding.searchCustomValidatorsList.adapter = adapter
            binding.searchCustomValidatorsList.setHasFixedSize(true)
            binding.root.isFocusableInTouchMode = true
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            activity.setContentView(binding.root)
            binding.root.requestFocus()
        }
        instrumentation.waitForIdleSync()
    }

    fun render(state: SearchBlockProducersState, retry: () -> Unit = { retries += "retry" }) {
        val drawn = CountDownLatch(1)
        scenario.onActivity {
            renderOnMain(state, retry) {
                binding.root.doOnPreDraw { drawn.countDown() }
                binding.root.invalidate()
            }
        }
        assertTrue("The production layout must draw the submitted state", drawn.await(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    fun renderOnMain(
        state: SearchBlockProducersState,
        retry: () -> Unit = { retries += "retry" },
        submitted: () -> Unit = {}
    ) {
        binding.renderValidatorSearchState(state, retry) { adapter.submitList(it) { submitted() } }
        if (state !is SearchBlockProducersState.Success) submitted()
    }

    fun inspect(check: (FragmentSearchCustomValidatorsBinding) -> Unit) {
        scenario.onActivity { check(binding) }
    }

    fun tapPlaceholder() {
        val bounds = Rect()
        val position = IntArray(2)
        val center = FloatArray(2)
        scenario.onActivity {
            assertTrue("The actual retry/empty placeholder must be visible before touch", binding.searchCustomValidatorsPlaceholder.getGlobalVisibleRect(bounds))
            assertTrue(bounds.width() > 0 && bounds.height() > 0)
            binding.searchCustomValidatorsPlaceholder.getLocationOnScreen(position)
            center[0] = position[0] + binding.searchCustomValidatorsPlaceholder.width / 2f
            center[1] = position[1] + binding.searchCustomValidatorsPlaceholder.height / 2f
        }
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, center[0], center[1], 0
            )
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
        instrumentation.waitForIdleSync()
    }

    override fun close() {
        scenario.onActivity { binding.searchCustomValidatorsList.adapter = null }
        scenario.close()
    }
    private companion object {
        const val FRAME_TIMEOUT_SECONDS = 5L
    }
}
