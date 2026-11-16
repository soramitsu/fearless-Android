package jp.co.soramitsu.account.impl.presentation.language

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Locale
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.language.model.LanguageModel
import jp.co.soramitsu.core.model.Language
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanguagesViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val interactor = mockk<AccountInteractor>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `languages are listed with english and native names and the selected one is loaded`() = runTest(dispatcher) {
        every { interactor.getLanguages() } returns listOf(Language("en"), Language("ja"))
        coEvery { interactor.getSelectedLanguage() } returns Language("ja")
        val vm = LanguagesViewModel(interactor, router)
        vm.selectedLanguageLiveData.observeForever { }
        runCurrent()

        val japanese = Locale("ja")
        assertEquals(listOf("en", "ja"), vm.languagesModels.map { it.iso })
        assertEquals(japanese.displayLanguage.replaceFirstChar { it.uppercase() }, vm.languagesModels[1].displayName)
        assertEquals(japanese.getDisplayLanguage(japanese), vm.languagesModels[1].nativeDisplayName)
        assertEquals("ja", vm.selectedLanguageLiveData.value!!.iso)
    }

    @Test
    fun `selecting a language stores its iso code and then announces the change`() = runTest(dispatcher) {
        every { interactor.getLanguages() } returns emptyList()
        val vm = LanguagesViewModel(interactor, router)
        vm.selectLanguageClicked(LanguageModel("ru", "Russian", "Русский"))
        assertNull(vm.languageChangedEvent.value)
        runCurrent()

        coVerify { interactor.changeSelectedLanguage(Language("ru")) }
        assertEquals(Unit, vm.languageChangedEvent.value!!.getContentIfNotHandled())
        vm.backClicked()
        verify { router.back() }
    }
}
