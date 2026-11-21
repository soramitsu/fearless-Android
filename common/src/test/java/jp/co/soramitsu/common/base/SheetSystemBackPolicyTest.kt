package jp.co.soramitsu.common.base

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What system Back does on every sheet on [BaseComposeBottomSheetDialogFragment], read from source. Whether Material and
 * a BackHandler really behave this way takes a real dialog: SheetSystemBackInstrumentedTest in app checks that.
 */
class SheetSystemBackPolicyTest {

    private val sheetClass = Regex(""":\s*BaseComposeBottomSheetDialogFragment<""")

    private val sheets: Map<String, String> by lazy {
        val modules = File("..").listFiles().orEmpty().map { File(it, "src/main") }.filter { it.isDirectory }
        modules.flatMap { main -> main.walk().filter { it.extension == "kt" }.toList() }
            .map { it.nameWithoutExtension to it.readText() }
            .filter { (_, source) -> sheetClass.containsMatchIn(source) }
            .toMap()
    }

    private val base by lazy { File("src/main/java/jp/co/soramitsu/common/base/BaseComposeBottomSheetDialogFragment.kt").readText() }

    /** Back is the toolbar's action, or Material's close for all of these once the base class lets it hide them. */
    private val closedByMaterial = listOf(
        "ScoreDetailsFragment",
        "SelectPoolFragment",
        "EditPoolFragment",
        "PoolInfoOptionsFragment",
        "SetupStakingPoolFragment",
        "StartStakingPoolFragment",
        "CreatePoolSetupFragment",
        "ValidatorsSettingsFragment",
        "SelectCustomValidatorsFragment",
        "SelectValidatorsFragment",
        "StartSelectValidatorsFragment",
        "SelectedValidatorsFragment"
    )

    private val staysWhileSubmitting = mapOf(
        "ConfirmSendFragment" to "KeepSheetOpenOnBack(submitting = state.isLoading)",
        "ClaimRewardsFragment" to "KeepSheetOpenOnBack(submitting = isClaiming)",
        "ConfirmJoinPoolFragment" to "KeepSheetOpenOnBack(submitting = state.value.isLoading)",
        "EditPoolConfirmFragment" to "KeepSheetOpenOnBack(submitting = state.isLoading)",
        "ConfirmSelectValidatorsFragment" to "KeepSheetOpenOnBack(submitting = state.isLoading)"
    )

    private val ownBack = mapOf(
        "DappScreenFragment" to "BackHandler(onBack = ::back)",
        "ImportRemoteWalletDialog" to "BackHandler(onBack = viewModel::backClicked)",
        "TonSignRequestFragment" to "BackHandler(onBack = viewModel::onClose)",
        "WalletConnectSignMessageFragment" to "BackHandler(onBack = viewModel::onClose)",
        "ChainChooseFragment" to "BackHandler { dialog?.cancel() }",
        "PoolsFlowFragment" to "BackHandler(onBack = viewModel::onNavigationClick)",
        "NFTFlowFragment" to "BackHandler(onBack = viewModel::onNavigationClick)"
    )

    private fun source(sheet: String) = sheets[sheet] ?: error("$sheet is not a sheet on the base class any more")

    @Test
    fun `the base class lets Material hide a sheet, which is what closes it on Back`() {
        val behavior = base.substringAfter("protected open fun setupBehavior")
        assertTrue(behavior.contains("behavior.isHideable = true"))
        assertFalse(behavior.contains("behavior.isHideable = false"))
        // Dragging stays off, so long lists and forms scroll without closing the sheet.
        assertTrue(behavior.contains("behavior.isDraggable = false"))
    }

    @Test
    fun `no sheet waits for Back on the activity's dispatcher, which a dialog never reaches`() {
        assertFalse(base.contains("onBackPressedDispatcher"))
        assertEquals(emptyList<String>(), sheets.filterValues { it.contains("onBackPressed") }.keys.toList())
    }

    @Test
    fun `a sheet that can't always be hidden answers Back itself`() {
        val notAlwaysHideable = sheets.filterValues { source ->
            Regex("""behavior\.isHideable\s*=\s*(.+)""").findAll(source).any { it.groupValues[1].trim() != "true" }
        }.keys
        // Once its wallet exists this one is not cancelable, so Material leaves Back alone and nothing collapses it.
        val cancelableInstead = "CreateBackupPasswordDialog"
        assertTrue(source(cancelableInstead).contains("isCancelable = !isWalletCreated"))

        val silent = (notAlwaysHideable - cancelableInstead).filterNot { sheets.getValue(it).contains("BackHandler") }
        assertEquals(emptyList<String>(), silent)
    }

    @Test
    fun `sheets with nothing to do on Back close through Material`() {
        closedByMaterial.forEach { sheet ->
            val text = source(sheet)
            assertFalse("$sheet must not replace the base behaviour", text.contains("setupBehavior"))
            assertFalse("$sheet has no Back of its own", text.contains("BackHandler"))
        }
    }

    @Test
    fun `a confirm sheet stays while its submission runs`() {
        staysWhileSubmitting.forEach { (sheet, rule) ->
            val text = source(sheet)
            assertTrue("$sheet: $rule", text.contains(rule))
            assertFalse("$sheet must not replace the base behaviour", text.contains("setupBehavior"))
        }
    }

    @Test
    fun `sheets whose Back does something else take that path ahead of Material`() {
        ownBack.forEach { (sheet, handler) ->
            assertTrue("$sheet: $handler", source(sheet).contains(handler))
        }
    }

    @Test
    fun `signing a TON request keeps the sheet, and otherwise Back declines it`() {
        val text = source("TonSignRequestFragment")
        val declines = text.indexOf("BackHandler(onBack = viewModel::onClose)")
        val stays = text.indexOf("KeepSheetOpenOnBack(submitting = (state as? TonSignPreviewViewState)?.loading == true)")
        // The dispatcher runs the enabled callback added last, and composition adds them in source order.
        assertTrue(declines >= 0)
        assertTrue(stays > declines)
    }

    @Test
    fun `the dApp browser and its toolbar icon take one path`() {
        val text = source("DappScreenFragment")
        assertTrue(text.contains("onNavigationClick = ::back"))
        assertTrue(text.contains("currentWebView.goBack()"))
    }
}
