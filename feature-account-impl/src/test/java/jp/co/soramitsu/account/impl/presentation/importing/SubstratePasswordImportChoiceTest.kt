package jp.co.soramitsu.account.impl.presentation.importing

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.account.impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The import asks which account to open when an earlier browser extension read the path's password differently. */
@OptIn(ExperimentalCoroutinesApi::class)
class SubstratePasswordImportChoiceTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val models = mutableListOf<ImportAccountViewModel>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { interactor.createAccount(any()) } returns Result.success(1L)
        coEvery { interactor.isCodeSet() } returns true
    }

    @After fun teardown() {
        models.forEach { it.coroutineContext.cancel() }
        Dispatchers.resetMain()
    }

    @Test fun asksWhichAccountToOpenAndImportsThePick() = runTest(dispatcher) {
        val vector = vector("default24-unicode")
        val model = model()
        enter(model, vector["mnemonic"].asString, vector["derivationPath"].asString)

        model.nextClicked()
        awaitUntil { model.substratePasswordChooserEvent.value != null }

        val choice: Payload<SubstratePasswordAccountModel> = model.substratePasswordChooserEvent.value!!.peekContent()
        assertEquals(listOf(SubstratePasswordRule.NFKD, SubstratePasswordRule.NFC), choice.data.map { it.rule })
        assertEquals(
            listOf("text-${R.string.substrate_password_choice_nfkd}", "text-${R.string.substrate_password_choice_nfc}"),
            choice.data.map { it.title }
        )
        assertEquals(listOf(address(vector, "nfkd"), address(vector, "nfc")), choice.data.map { it.address })
        assertEquals(choice.data.first(), choice.selected)
        coVerify(exactly = 0) { interactor.createAccount(any()) }

        model.substratePasswordAccountChosen(choice.data.last())
        awaitUntil { runCatching { coVerify(exactly = 1) { interactor.createAccount(any()) } }.isSuccess }

        coVerify(exactly = 1) {
            interactor.createAccount(
                match { it is AddAccountPayload.SubstrateOrEvm && it.substratePasswordRule == SubstratePasswordRule.NFC }
            )
        }
    }

    @Test fun importsWithoutAskingWhenEveryRuleOpensTheSameAccount() = runTest(dispatcher) {
        val vector = vector("import12-ascii")
        val model = model()
        enter(model, vector["mnemonic"].asString, vector["derivationPath"].asString)

        model.nextClicked()
        awaitUntil { runCatching { coVerify(exactly = 1) { interactor.createAccount(any()) } }.isSuccess }

        assertNull(model.substratePasswordChooserEvent.value)
        coVerify(exactly = 1) {
            interactor.createAccount(
                match { it is AddAccountPayload.SubstrateOrEvm && it.substratePasswordRule == SubstratePasswordRule.NFKD }
            )
        }
    }

    // Key derivation runs on Dispatchers.Default and resumes on Main: let both run.
    private fun TestScope.awaitUntil(condition: () -> Boolean) {
        repeat(500) {
            runCurrent()
            if (condition()) return
            Thread.sleep(10)
        }
        error("Timed out waiting for the import")
    }

    private fun enter(model: ImportAccountViewModel, mnemonic: String, path: String) {
        (model.selectedSourceLiveData.value as MnemonicImportSource).mnemonicContentLiveData.value = mnemonic
        model.substrateDerivationPathLiveData.value = path
        model.nameLiveData.value = "Imported"
    }

    private fun model(): ImportAccountViewModel {
        val resources = mockk<ResourceManager> { every { getString(any()) } answers { "text-${firstArg<Int>()}" } }
        // ED25519: JVM tests have no sr25519 library.
        val crypto = mockk<CryptoTypeChooserMixin> {
            every { selectedEncryptionTypeLiveData } returns MutableLiveData(CryptoTypeModel("ED25519", CryptoType.ED25519))
        }
        val saved = SavedStateHandle(mapOf(
            ImportAccountFragment.IMPORT_ACCOUNT_TYPE_KEY to WalletEcosystem.Substrate,
            ImportAccountFragment.IMPORT_MODE_KEY to ImportMode.MnemonicPhrase
        ))
        return ImportAccountViewModel(interactor, router, resources, crypto, mockk(), mockk(), mockk(), mockk(relaxed = true), saved)
            .also(models::add)
    }

    private fun address(vector: JsonObject, rule: String): String =
        vector["accounts"].asJsonObject[rule].asJsonObject["ed25519"].asJsonObject["ss58Generic"].asString

    private fun vector(id: String): JsonObject {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("substrate-password-vectors.json"))
        val document = stream.bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        return document["vectors"].asJsonArray.map { it.asJsonObject }.single { it["id"].asString == id }
    }
}
