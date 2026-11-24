package jp.co.soramitsu.app.root.presentation.main

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.math.BigDecimal
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.payouts.list.PayoutAdapter
import jp.co.soramitsu.staking.impl.presentation.payouts.list.model.PendingPayoutModel
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.staking.impl.presentation.staking.balance.model.UnbondingModel
import jp.co.soramitsu.staking.impl.presentation.staking.balance.view.StakingBalanceActions
import jp.co.soramitsu.staking.impl.presentation.staking.balance.view.StakingBalanceView
import jp.co.soramitsu.staking.impl.presentation.staking.balance.view.UnbondingsView
import jp.co.soramitsu.staking.impl.presentation.validators.ValidatorViewHolder
import jp.co.soramitsu.staking.impl.presentation.validators.current.CurrentValidatorsAdapter
import jp.co.soramitsu.staking.impl.presentation.validators.current.model.NominatedValidatorModel
import jp.co.soramitsu.staking.impl.presentation.validators.current.model.NominatedValidatorStatusModel
import jp.co.soramitsu.staking.impl.presentation.view.AlertsView
import jp.co.soramitsu.staking.impl.presentation.view.RewardDestinationView
import jp.co.soramitsu.staking.impl.presentation.view.StakeSummaryView
import jp.co.soramitsu.wallet.api.presentation.model.AmountModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A nominator's staking pages from public fixtures: no wallet, no network and no write. An unfunded wallet cannot
 * reach these states on a real screen. Each page is saved beside the other surfaces' pictures for review.
 */
@RunWith(AndroidJUnit4::class)
class SunlitStakingSurfacesInstrumentedTest {

    @Test fun nominatorPagesAreRaisedAndKeepTheirDividersOutAtBothTextSizes() {
        for ((suffix, scale) in listOf("1x" to 1f, "2x" to 2f)) {
            page("staking-nominator-$suffix", scale) { context -> nominatorPage(context) }
            page("staking-rows-$suffix", scale, ::assertRowsAreRaised) { context -> rowsPage(context) }
        }
    }

    private fun page(name: String, scale: Float, check: (View) -> Unit = {}, build: (Context) -> View) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        ActivityScenario.launch<PolkaswapNavigationLayoutTestActivity>(
            Intent(target, PolkaswapNavigationLayoutTestActivity::class.java)
        ).use { scenario ->
            lateinit var root: View
            scenario.onActivity { activity ->
                val configuration = Configuration(target.resources.configuration).apply { fontScale = scale }
                val context = ContextThemeWrapper(
                    activity.createConfigurationContext(configuration),
                    jp.co.soramitsu.app.R.style.Theme_Soramitsu_Fearless
                )
                activity.window.setBackgroundDrawableResource(jp.co.soramitsu.common.R.drawable.fw_page_background)
                root = build(context)
                activity.setContentView(root)
            }
            instrumentation.waitForIdleSync()
            // The window's first frame follows the idle main thread.
            SystemClock.sleep(800)
            scenario.onActivity { check(root) }
            save(name, requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
        }
    }

    private fun save(name: String, bitmap: Bitmap) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val label = InstrumentationRegistry.getArguments().getString("surfacesEvidenceLabel") ?: "default"
        val directory = target.getExternalFilesDir("sunlit-surfaces-$label")!!.apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun assertRowsAreRaised(root: View) {
        val density = root.resources.displayMetrics.density
        val rows = root.findViewById<RecyclerView>(CURRENT_VALIDATORS).children
            .filter { it.findViewById<View>(R.id.itemCurrentValidatorDivider) != null }
            .toList()
        assertTrue("The current validators must render as rows", rows.isNotEmpty())
        for (row in rows) {
            assertEquals(View.GONE, row.findViewById<View>(R.id.itemCurrentValidatorDivider).visibility)
            assertEquals((16 * density).toInt(), (row.layoutParams as ViewGroup.MarginLayoutParams).marginStart)
        }
    }

    private fun nominatorPage(context: Context): View = scrolling(context) { column ->
        column.addCard(
            StakeSummaryView(context).apply {
                setTitle("Your stake")
                setTotalStaked("12.5 DOT")
                setTotalStakedFiat("$54.12")
                setRewardsApr("14.2%")
                setRewardsAprFiat("$7.68")
                setElectionStatus(StakeSummaryView.Status.Active("Era #4321"))
                hideLoading()
            }
        )
        column.addCard(
            AlertsView(context).apply {
                // The same order as the staking page: the loading state ends before the alerts arrive.
                hideLoading()
                setStatus(
                    listOf(
                        AlertModel(
                            jp.co.soramitsu.common.R.drawable.ic_warning_filled,
                            "Waiting for the next era",
                            "Your nominations start with the next era.",
                            AlertModel.Type.Info
                        )
                    )
                )
            }
        )
        column.addCard(
            StakingBalanceView(context).apply {
                bonded.setTokenAmount("12.5 DOT")
                bonded.setFiatAmount("$54.12")
                unbonding.setTokenAmount("1.5 DOT")
                unbonding.setFiatAmount("$6.50")
                redeemable.setTokenAmount("0 DOT")
                redeemable.setFiatAmount("$0.00")
            }
        )
        column.addCard(StakingBalanceActions(context))
        column.addCard(
            UnbondingsView(context).apply {
                val now = System.currentTimeMillis()
                submitList(
                    listOf(
                        UnbondingModel(0, 2 * DAY_MS, now, AmountModel(BigDecimal("1"), "1 DOT", "$4.33")),
                        UnbondingModel(1, 6 * DAY_MS, now, AmountModel(BigDecimal("0.5"), "0.5 DOT", "$2.17"))
                    )
                )
            }
        )
    }

    private fun rowsPage(context: Context): View = scrolling(context) { column ->
        val icon = validatorIcon(context)
        column.addView(
            RecyclerView(context).apply {
                id = CURRENT_VALIDATORS
                layoutManager = LinearLayoutManager(context)
                isNestedScrollingEnabled = false
                clipChildren = false
                adapter = CurrentValidatorsAdapter(
                    object : CurrentValidatorsAdapter.Handler {
                        override fun infoClicked(validatorModel: NominatedValidatorModel) = Unit
                    }
                ).also { validators ->
                    validators.submitList(
                        listOf(
                            NominatedValidatorStatusModel(
                                NominatedValidatorStatusModel.TitleConfig("Active (2)", R.color.green),
                                "Your stake is allocated to these validators."
                            ),
                            NominatedValidatorModel(AddressModel(ADDRESS, icon, "Public validator"), "Nominated: 10 DOT", false, false),
                            NominatedValidatorModel(AddressModel(ADDRESS + "2", icon, "Oversubscribed validator"), "Nominated: 2.5 DOT", true, true)
                        )
                    )
                }
            },
            wide()
        )
        column.addView(
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                isNestedScrollingEnabled = false
                clipChildren = false
                adapter = PayoutAdapter(
                    object : PayoutAdapter.ItemHandler {
                        override fun payoutClicked(index: Int) = Unit
                    }
                ).also { payouts ->
                    payouts.submitList(
                        listOf(
                            PendingPayoutModel("Public validator", 2 * DAY_MS, System.currentTimeMillis(), R.color.colorGreyText, "+0.5 DOT", "$2.17")
                        )
                    )
                }
            },
            wide()
        )
        val row = LayoutInflater.from(context).inflate(R.layout.item_validator, column, false)
        ValidatorViewHolder(row)
        row.findViewById<TextView>(R.id.itemValidatorName).text = "Recommended validator"
        row.findViewById<android.widget.ImageView>(R.id.itemValidatorIcon).setImageDrawable(icon)
        row.findViewById<TextView>(R.id.itemValidatorScoringPrimary).text = "14.2%"
        column.addView(row)
        column.addCard(
            RewardDestinationView(context).apply {
                setName("Restake")
                setTokenAmount("1.42 DOT")
                setPercentageGain("14.2%")
                setFiatAmount("$6.14")
                setChecked(true)
            }
        )
        column.addCard(
            RewardDestinationView(context).apply {
                setName("Payout")
                setTokenAmount("1.31 DOT")
                setPercentageGain("13.1%")
                setFiatAmount("$5.67")
            }
        )
    }

    private fun scrolling(context: Context, content: (LinearLayout) -> Unit): View = ScrollView(context).apply {
        clipChildren = false
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false
                clipToPadding = false
                val top = (48 * resources.displayMetrics.density).toInt()
                setPadding(0, top, 0, top)
                content(this)
            }
        )
    }

    private fun wide() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun LinearLayout.addCard(card: View) {
        val margin = (16 * resources.displayMetrics.density).toInt()
        addView(card, wide().apply { setMargins(margin, margin / 2, margin, margin / 2) })
    }

    private fun validatorIcon(context: Context): PictureDrawable {
        val size = (32 * context.resources.displayMetrics.density).toInt()
        val picture = Picture()
        val canvas = picture.beginRecording(size, size)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226, 151, 72) })
        picture.endRecording()
        return PictureDrawable(picture)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val ADDRESS = "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp"
        val CURRENT_VALIDATORS = View.generateViewId()
    }
}
