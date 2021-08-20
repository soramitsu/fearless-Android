package jp.co.soramitsu

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.FeatureContainer
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject


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

    @Inject
    lateinit var networkApiCreator: NetworkApiCreator

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

        chainSyncService = ChainSyncService(chainDao, networkApiCreator.create(ChainFetcher::class.java))
    }

    @Test
    fun shouldFetchAndStoreRealChains() = runBlocking {
        chainSyncService.syncUp()
    }
}
