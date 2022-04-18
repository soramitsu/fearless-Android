package jp.co.soramitsu

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import dagger.Component
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.FeatureContainer
import jp.co.soramitsu.commonnetworking.fearless.FearlessChainsBuilder
import jp.co.soramitsu.commonnetworking.networkclient.SoraNetworkClientImpl
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@Component(
    dependencies = [
        CommonApi::class,
    ]
)
interface TestAppComponent {

    fun inject(test: ChainSyncServiceIntegrationTest)
}

@RunWith(AndroidJUnit4::class)
class ChainSyncServiceIntegrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val featureContainer = context as FeatureContainer

    lateinit var chainSyncService: ChainSyncService

    @Before
    fun setup() {
        val component = DaggerTestAppComponent.builder()
            .commonApi(featureContainer.commonApi())
            .build()

        component.inject(this)

        val chainDao = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .build()
            .chainDao()

        val soraNetworkClient = SoraNetworkClientImpl()
        val fearlessChainsBuilder = FearlessChainsBuilder(soraNetworkClient, "")

        chainSyncService = ChainSyncService(chainDao, soraNetworkClient, fearlessChainsBuilder, Gson())
    }

    @Test
    fun shouldFetchAndStoreRealChains() = runBlocking {
        chainSyncService.syncUp()
    }
}
