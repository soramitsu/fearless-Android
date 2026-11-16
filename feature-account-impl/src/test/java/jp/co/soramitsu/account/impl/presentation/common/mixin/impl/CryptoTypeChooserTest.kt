package jp.co.soramitsu.account.impl.presentation.common.mixin.impl

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CryptoTypeChooserTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the preferred type is preselected and the chooser lists every supported type`() = runTest(dispatcher) {
        every { interactor.getCryptoTypes() } returns listOf(CryptoType.SR25519, CryptoType.ED25519, CryptoType.ECDSA)
        coEvery { interactor.getPreferredCryptoType() } returns CryptoType.ED25519
        val chooser = CryptoTypeChooser(interactor, VmTestFixtures.resourceManager())

        chooser.chooseEncryptionClicked()
        assertNull(chooser.encryptionTypeChooserEvent.value)

        chooser.selectedEncryptionTypeLiveData.observeForever { }
        runCurrent()
        val selected = chooser.selectedEncryptionTypeLiveData.value!!
        assertEquals(CryptoType.ED25519, selected.cryptoType)
        assertTrue(selected.name.contains("s${R.string.ed25519_selection_title}"))

        chooser.chooseEncryptionClicked()
        val payload = chooser.encryptionTypeChooserEvent.value!!.getContentIfNotHandled()!!
        assertEquals(listOf(CryptoType.SR25519, CryptoType.ED25519, CryptoType.ECDSA), payload.data.map { it.cryptoType })
        assertEquals(selected, payload.selected)
    }
}
