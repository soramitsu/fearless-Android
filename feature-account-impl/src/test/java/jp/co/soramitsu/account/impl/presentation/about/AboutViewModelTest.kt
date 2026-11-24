package jp.co.soramitsu.account.impl.presentation.about

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.account.impl.presentation.about.model.AboutItemModel
import jp.co.soramitsu.account.impl.presentation.about.model.AboutSectionModel
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AboutViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val router = mockk<AccountRouter>(relaxed = true)
    private val links = AppLinksProvider("https://terms.example", "https://privacy.example", "", "", "", "")
    private lateinit var vm: AboutViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        vm = AboutViewModel(router, ApplicationProvider.getApplicationContext(), links, VmTestFixtures.resourceManager())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun item(title: Int) = vm.list.filterIsInstance<AboutItemModel>().single { it.titleResId == title }

    @Test
    fun `the list groups the links into four titled sections`() {
        val sections = vm.list.filterIsInstance<AboutSectionModel>().map { it.titleResId }
        assertEquals(listOf(R.string.fearless_wallet, R.string.community, R.string.social_media, R.string.support_and_feedback), sections)
        assertEquals(17, vm.list.size)
        assertSame(vm.list, vm.uiState.value.items)
        assertEquals(BuildConfig.WEBSITE_URL.removePrefix("https://"), item(R.string.about_website).text)
        assertFalse(BuildConfig.WEBSITE_URL.removePrefix("https://").startsWith("https://"))
        assertTrue(item(R.string.about_github).text!!.startsWith("s${R.string.about_version} "))
        assertNull(item(R.string.about_terms).text)
        assertFalse(item(R.string.about_privacy).showDivider)
        assertEquals(BuildConfig.EMAIL, item(R.string.about_contact_us).text)
    }

    @Test
    fun `each link opens its own address in the browser`() {
        val expected = mapOf(
            R.string.about_website to BuildConfig.WEBSITE_URL,
            R.string.about_wiki to BuildConfig.WIKI_URL,
            R.string.about_github to BuildConfig.GITHUB_URL,
            R.string.about_terms to "https://terms.example",
            R.string.about_privacy to "https://privacy.example",
            R.string.about_telegram to BuildConfig.TELEGRAM_URL,
            R.string.about_medium to BuildConfig.MEDIUM_URL,
            R.string.about_instagram to BuildConfig.INSTAGRAM_URL,
            R.string.about_twitter to BuildConfig.TWITTER_URL,
            R.string.about_youtube to BuildConfig.YOUTUBE_URL,
            R.string.about_announcement to BuildConfig.ANNOUNCEMENT_URL,
            R.string.about_support to BuildConfig.SUPPORT_URL
        )
        expected.forEach { (title, url) ->
            item(title).onClick()
            assertEquals(url, vm.openBrowserEvent.value!!.getContentIfNotHandled())
        }
    }

    @Test
    fun `contact us opens an email instead of the browser and back returns to the profile`() {
        item(R.string.about_contact_us).onClick()
        assertEquals(BuildConfig.EMAIL, vm.openSendEmailEvent.value!!.getContentIfNotHandled())
        assertNull(vm.openBrowserEvent.value)

        vm.backButtonPressed()
        verify { router.backToProfileScreen() }
    }
}
