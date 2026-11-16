package jp.co.soramitsu.onboarding.impl.di

import io.mockk.mockk
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.feature_onboarding_impl.BuildConfig
import jp.co.soramitsu.onboarding.impl.data.OnboardingRepositoryImpl
import jp.co.soramitsu.onboarding.impl.domain.OnboardingInteractorImpl
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class OnboardingFeatureModuleTest {

    private val module = OnboardingFeatureModule()
    private val requests = mutableListOf<Request>()

    private fun creator(body: String, code: Int = 200): NetworkApiCreator {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Server Error")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return NetworkApiCreator(client, "https://example.invalid/")
    }

    @Test
    fun remoteConfigIsReadFromTheBuildsOnboardingAddressAndFullyDeserialized(): Unit = runBlocking {
        val api = module.provideOnboardingConfigApi(creator(FULL_CONFIG))
        val repository = module.provideOnboardingRepository(api)

        val config = repository.getConfig().getOrThrow()

        assertTrue(repository is OnboardingRepositoryImpl)
        assertEquals(BuildConfig.ONBOARDING_CONFIG, requests.single().url.toString())
        val item = config.configs.single()
        assertEquals("4.3.0", item.minVersion)
        assertEquals("https://example.invalid/bg.png", item.background)
        val newSlide = item.enEn.new.single()
        assertEquals("What is new", newSlide.title.text)
        assertEquals("#e6006f", newSlide.title.color)
        assertEquals("New description", newSlide.description)
        assertEquals("new.png", newSlide.image)
        assertEquals(listOf("First", "Second"), item.enEn.regular.map { it.title.text })
        assertEquals(listOf("one.png", "two.png"), item.enEn.regular.map { it.image })
    }

    @Test
    fun missingListsBecomeEmpty(): Unit = runBlocking {
        val noPlatform = module.provideOnboardingConfigApi(creator("""{"iOS":[]}""")).getConfig()
        assertTrue(noPlatform.configs.isEmpty())

        val noSlides = module.provideOnboardingConfigApi(
            creator("""{"Android":[{"minVersion":"1.0.0","background":"","en-EN":{}}]}""")
        ).getConfig()
        val variants = noSlides.configs.single().enEn
        assertTrue(variants.new.isEmpty())
        assertTrue(variants.regular.isEmpty())
    }

    @Test
    fun aFailedDownloadIsAFailedResultNotAnException(): Unit = runBlocking {
        val repository = module.provideOnboardingRepository(module.provideOnboardingConfigApi(creator("{}", code = 500)))

        val result = repository.getConfig()

        assertTrue(result.exceptionOrNull() is HttpException)
    }

    @Test
    fun interactorAndPreinstalledScenarioAreWiredFromTheirDependencies() {
        val repository = module.provideOnboardingRepository(module.provideOnboardingConfigApi(creator("{}")))
        val preferences = mockk<Preferences>(relaxed = true)

        assertTrue(module.provideOnboardingInteractor(repository, preferences) is OnboardingInteractorImpl)
        val scenario = module.provideImportPreInstalledAccount(mockk(relaxed = true), preferences, mockk(relaxed = true), mockk(relaxed = true))
        // A fresh install has never stored the remote toggle, so the pre-installed import stays off.
        assertEquals(false, scenario.isFeatureEnabled())
    }

    private companion object {
        const val FULL_CONFIG = """
            {"Android":[{"minVersion":"4.3.0","background":"https://example.invalid/bg.png","en-EN":{
              "new":[{"title":{"text":"What is new","color":"#e6006f"},"description":"New description","image":"new.png"}],
              "regular":[
                {"title":{"text":"First","color":"#ffffff"},"description":"One","image":"one.png"},
                {"title":{"text":"Second","color":"#000000"},"description":"Two","image":"two.png"}
              ]}}]}
        """
    }
}
