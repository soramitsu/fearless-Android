package jp.co.soramitsu.account.impl.presentation.importing.source.model

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.account.impl.presentation.importing.FileReader
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecodingException.IncorrectPasswordException
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecodingException.InvalidJsonException
import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.bouncycastle.util.encoders.DecoderException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportSourceModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val interactor = mockk<AccountInteractor>()
    private val clipboard = mockk<ClipboardManager>()
    private val fileReader = mockk<FileReader>()
    private val name = MutableLiveData<String>()
    private val cryptoType = MutableLiveData<CryptoTypeModel>()

    private fun json(scope: CoroutineScope, recoveryMode: Boolean = false) = JsonImportSource(
        name, cryptoType, interactor, VmTestFixtures.resourceManager(), clipboard, fileReader, scope, recoveryMode
    ).also { source -> source.validationLiveData.observeForever { } }

    private fun JsonImportSource.lastError() = showImportErrorEvent.value?.getContentIfNotHandled()

    @Test
    fun `a pasted json is parsed, then fills the name and encryption and validates with a password`() = runTest {
        coEvery { interactor.processAccountJson(JSON) } returns Result.success(ImportJsonData("Imported", CryptoType.ED25519))
        every { clipboard.getFromClipboard() } returns JSON
        val source = json(this)
        assertEquals(false, source.validationLiveData.value)

        source.pasteClicked()
        runCurrent()

        assertEquals(JSON, source.jsonContentLiveData.value)
        assertEquals("Imported", name.value)
        assertEquals(CryptoType.ED25519, cryptoType.value!!.cryptoType)
        assertEquals(false, source.validationLiveData.value)
        source.passwordLiveData.value = "secret"
        assertEquals(true, source.validationLiveData.value)
    }

    @Test
    fun `an ecdsa json is refused for a substrate import and a substrate json for an ethereum one`() = runTest {
        val source = json(this)
        coEvery { interactor.processAccountJson(JSON) } returns Result.success(ImportJsonData(null, CryptoType.ECDSA))
        every { clipboard.getFromClipboard() } returns JSON

        source.blockchainTypeFlow.value = WalletEcosystem.Substrate
        source.pasteClicked(); runCurrent()
        val wrongType = source.lastError()!!
        assertEquals(R.string.import_json_invalid_format_title, wrongType.titleRes)
        assertEquals(R.string.import_json_invalid_import_type_message, wrongType.messageRes)
        assertNull(source.jsonContentLiveData.value)

        coEvery { interactor.processAccountJson(JSON) } returns Result.success(ImportJsonData(null, CryptoType.SR25519))
        source.blockchainTypeFlow.value = WalletEcosystem.Ethereum
        source.pasteClicked(); runCurrent()
        assertEquals(R.string.import_json_invalid_format_message, source.lastError()!!.messageRes)
        assertNull(source.jsonContentLiveData.value)
        assertNull(name.value)
    }

    @Test
    fun `matching or unchecked ecosystems accept the json`() = runTest {
        every { clipboard.getFromClipboard() } returns JSON
        val cases = listOf(
            WalletEcosystem.Substrate to CryptoType.SR25519,
            WalletEcosystem.Ethereum to CryptoType.ECDSA,
            WalletEcosystem.Ton to CryptoType.ED25519,
            WalletEcosystem.Bitcoin to CryptoType.ECDSA,
            WalletEcosystem.Solana to CryptoType.ED25519,
            WalletEcosystem.Iroha to CryptoType.ED25519
        )
        cases.forEach { (ecosystem, type) ->
            coEvery { interactor.processAccountJson(JSON) } returns Result.success(ImportJsonData(null, type))
            val source = json(this)
            source.blockchainTypeFlow.value = ecosystem
            source.pasteClicked(); runCurrent()
            assertEquals("$ecosystem accepts $type", JSON, source.jsonContentLiveData.value)
        }
    }

    @Test
    fun `decoding failures are translated into import errors`() = runTest {
        every { clipboard.getFromClipboard() } returns JSON
        val source = json(this)

        coEvery { interactor.processAccountJson(JSON) } returns Result.failure(IncorrectPasswordException())
        source.pasteClicked(); runCurrent()
        assertEquals(R.string.import_json_invalid_password, source.lastError()!!.messageRes)

        coEvery { interactor.processAccountJson(JSON) } returns Result.failure(IllegalStateException("other"))
        source.pasteClicked(); runCurrent()
        assertNull(source.lastError())
        assertNull(source.handleError(RuntimeException()))
    }

    @Test
    fun `empty, oversized and unreadable input is refused before parsing`() = runTest {
        val source = json(this)
        every { clipboard.getFromClipboard() } returns ""
        source.pasteClicked()
        assertEquals(R.string.import_json_invalid_format_message, source.lastError()!!.messageRes)

        every { clipboard.getFromClipboard() } returns "x".repeat(FileReader.MAX_JSON_CHARS + 1)
        source.pasteClicked()
        assertEquals(R.string.import_json_invalid_format_message, source.lastError()!!.messageRes)

        every { clipboard.getFromClipboard() } returns null
        source.pasteClicked()
        assertNull(source.lastError())

        val uri = mockk<Uri>()
        coEvery { fileReader.readFile(uri) } returns null
        source.fileChosen(uri); runCurrent()
        assertEquals(R.string.import_json_invalid_format_message, source.lastError()!!.messageRes)
        coVerify(exactly = 0) { interactor.processAccountJson(any()) }
    }

    @Test
    fun `recovery mode keeps the file content without parsing, needs no password and can be cleared`() = runTest {
        val source = json(this, recoveryMode = true)
        val uri = mockk<Uri>()
        coEvery { fileReader.readFile(uri) } returns JSON

        source.fileChosen(uri); runCurrent()

        assertEquals(JSON, source.jsonContentLiveData.value)
        assertEquals(true, source.validationLiveData.value)
        coVerify(exactly = 0) { interactor.processAccountJson(any()) }

        source.clearRecoveryInput()
        assertEquals("", source.jsonContentLiveData.value)
        assertEquals("", source.passwordLiveData.value)
        assertEquals(false, source.validationLiveData.value)
    }

    @Test
    fun `clearing outside recovery keeps the input, and the json buttons emit their events`() = runTest {
        val source = json(this)
        source.jsonContentLiveData.value = JSON
        source.clearRecoveryInput()
        assertEquals(JSON, source.jsonContentLiveData.value)

        source.jsonClicked()
        assertEquals(Unit, source.showJsonInputOptionsEvent.value!!.getContentIfNotHandled())
        source.chooseFileClicked()
        assertEquals(101, source.chooseJsonFileEvent.value!!.getContentIfNotHandled())
        assertEquals(R.string.import_json_invalid_password_title, source.handleError(IncorrectPasswordException())!!.titleRes)
        assertEquals(R.string.import_json_invalid_format_message, source.handleError(InvalidJsonException())!!.messageRes)
    }

    @Test
    fun `mnemonic and seed sources validate on content and explain their own errors`() {
        val mnemonic = MnemonicImportSource().also { it.validationLiveData.observeForever { } }
        assertEquals(false, mnemonic.validationLiveData.value)
        mnemonic.mnemonicContentLiveData.value = "abandon about"
        assertEquals(true, mnemonic.validationLiveData.value)
        assertEquals(R.string.mnemonic_error_try_another_one, mnemonic.handleError(Bip39Exception())!!.messageRes)
        assertNull(mnemonic.handleError(IllegalStateException()))

        val seed = RawSeedImportSource().also { it.validationLiveData.observeForever { } }
        seed.rawSeedLiveData.value = "0x00"
        assertEquals(true, seed.validationLiveData.value)
        assertEquals(R.string.account_import_invalid_seed, seed.handleError(IllegalArgumentException())!!.messageRes)
        val decodingError = org.junit.Assert.assertThrows(DecoderException::class.java) {
            org.bouncycastle.util.encoders.Hex.decode("not hexadecimal")
        }
        assertEquals(R.string.import_seed_invalid_title, seed.handleError(decodingError)!!.titleRes)
        assertNull(seed.handleError(IllegalStateException()))
        assertTrue(ImportError().titleRes == R.string.common_error_general_title)
    }

    private companion object {
        const val JSON = "{\"encoded\":\"x\",\"encoding\":{}}"
    }
}
