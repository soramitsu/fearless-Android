package jp.co.soramitsu.account.impl.presentation.importing.source.view

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.account.impl.presentation.importing.FileReader
import jp.co.soramitsu.account.impl.presentation.importing.ImportAccountViewModel
import jp.co.soramitsu.account.impl.presentation.importing.source.model.ImportError
import jp.co.soramitsu.account.impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.RawSeedImportSource
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class ImportSourceViewsTest {
    private lateinit var context: ContextThemeWrapper
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        context = ContextThemeWrapper(activity, R.style.Theme_Soramitsu_Fearless)
        owner.registry.currentState = Lifecycle.State.RESUMED
    }

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    @Test
    fun `the json view labels the ecosystem, mirrors the json, binds the password and reports errors`() {
        val errors = mutableListOf<ImportError>()
        val clipboard = mockk<ClipboardManager> { every { getFromClipboard() } returns "" }
        val source = JsonImportSource(
            MutableLiveData(), MutableLiveData(), mockk<AccountInteractor>(), VmTestFixtures.resourceManager(), clipboard,
            mockk<FileReader>(), MainScope()
        )
        val view = JsonImportView(context, WalletEcosystem.Ethereum) { errors += it }
        assertEquals(context.getString(R.string.import_ethereum_recovery), view.text(R.id.labeledTextLabel))

        view.observeSource(source, WalletEcosystem.Ethereum, owner)
        assertEquals(WalletEcosystem.Ethereum, source.blockchainTypeFlow.value)
        source.jsonContentLiveData.value = "{json}"
        assertEquals("{json}", view.text(R.id.labeledTextText))

        view.findViewById<InputField>(R.id.importJsonPasswordInput).content.setText("pw")
        assertEquals("pw", source.passwordLiveData.value)

        source.pasteClicked()
        assertEquals(listOf(R.string.import_json_invalid_format_message), errors.map { it.messageRes })

        view.findViewById<View>(R.id.labeledTextAction).performClick()
        assertEquals(101, source.chooseJsonFileEvent.value!!.peekContent())

        view.findViewById<View>(R.id.importJsonContent).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(true, source.showJsonInputOptionsEvent.value!!.hasBeenHandled)
    }

    @Test
    fun `the json view defaults to the substrate label and refuses other sources`() {
        val view = JsonImportView(context)
        assertEquals(context.getString(R.string.import_substrate_recovery), view.text(R.id.labeledTextLabel))
        JsonImportView(context, WalletEcosystem.Ton) {}
        assertThrows(IllegalArgumentException::class.java) {
            view.observeSource(MnemonicImportSource(), WalletEcosystem.Substrate, owner)
        }
    }

    @Test
    fun `the seed view titles the ecosystem and binds the seed field`() {
        val ethereum = SeedImportView(context, WalletEcosystem.Ethereum)
        assertEquals(context.getString(R.string.account_import_ethereum_raw_seed_placeholder), ethereum.text(R.id.importSeedTitle))
        val substrate = SeedImportView(context)
        assertEquals(context.getString(R.string.account_import_substrate_raw_seed_placeholder), substrate.text(R.id.importSeedTitle))
        SeedImportView(context, WalletEcosystem.Bitcoin)

        val source = RawSeedImportSource()
        substrate.observeSource(source, WalletEcosystem.Substrate, owner)
        substrate.findViewById<EditText>(R.id.importSeedContent).setText("0xabc")
        assertEquals("0xabc", source.rawSeedLiveData.value)
        assertNotNull(substrate.findViewById<View>(R.id.importSeedContentContainer).background)
    }

    @Test
    fun `the mnemonic view binds the phrase and the shared name field follows the view model`() {
        val view = MnemonicImportView(context)
        val source = MnemonicImportSource()
        view.observeSource(source, WalletEcosystem.Substrate, owner)
        view.findViewById<EditText>(R.id.importMnemonicContent).setText("abandon about")
        assertEquals("abandon about", source.mnemonicContentLiveData.value)

        val name = MutableLiveData("Restored")
        val rebinding = mockk<ImportAccountViewModel> {
            every { isRebinding } returns true
            every { nameLiveData } returns name
        }
        view.observeCommon(rebinding, owner)
        assertEquals(View.GONE, view.findViewById<View>(R.id.importMnemonicUsernameInput).visibility)

        val fresh = mockk<ImportAccountViewModel> {
            every { isRebinding } returns false
            every { nameLiveData } returns name
        }
        view.observeCommon(fresh, owner)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.importMnemonicUsernameInput).visibility)
    }
}
