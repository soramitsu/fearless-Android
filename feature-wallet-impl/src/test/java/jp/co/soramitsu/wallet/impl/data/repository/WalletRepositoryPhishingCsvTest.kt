package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.coredb.dao.PhishingDao
import jp.co.soramitsu.coredb.model.PhishingLocal
import jp.co.soramitsu.wallet.impl.data.network.phishing.PhishingApi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The phishing list goes through opencsv's header-aware reader, the only opencsv (and so
 * commons-beanutils) consumer in the app. Guards the parsing path across their version changes.
 */
class WalletRepositoryPhishingCsvTest {

    @Test
    fun `phishing list rows are stored by column after the header row`() = runBlocking {
        val csv = "name,address,type,subtype\n" +
            "Fake exchange,5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY,scam,impersonation\n" +
            "\"Quoted, name\",0x000000000000000000000000000000000000dead,exchange,\n"
        val phishingApi = mockk<PhishingApi> {
            coEvery { getPhishingAddresses() } returns csv.toResponseBody("text/csv".toMediaType())
        }
        val stored = slot<List<PhishingLocal>>()
        val phishingDao = mockk<PhishingDao>(relaxed = true) {
            coEvery { insert(capture(stored)) } returns Unit
        }

        repository(phishingApi, phishingDao).updatePhishingAddresses()

        coVerifyOrder {
            phishingDao.clearTable()
            phishingDao.insert(any())
        }
        assertEquals(
            listOf(
                PhishingLocal(
                    address = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY",
                    name = "Fake exchange",
                    type = "scam",
                    subtype = "impersonation"
                ),
                PhishingLocal(
                    address = "0x000000000000000000000000000000000000dead",
                    name = "Quoted, name",
                    type = "exchange",
                    subtype = ""
                )
            ),
            stored.captured
        )
    }

    private fun repository(phishingApi: PhishingApi, phishingDao: PhishingDao) = WalletRepositoryImpl(
        substrateSource = mockk(),
        ethereumSource = mockk(),
        operationDao = mockk(),
        httpExceptionHandler = mockk(),
        phishingApi = phishingApi,
        assetDao = mockk(),
        walletConstants = mockk(),
        phishingDao = phishingDao,
        coingeckoApi = mockk(),
        chainRegistry = mockk(),
        remoteConfigFetcher = mockk(),
        accountRepository = mockk(),
        chainsRepository = mockk(),
        extrinsicService = mockk(),
        remoteStorageSource = mockk(),
        pricesSyncService = mockk(),
        transferServiceProvider = mockk(),
        tonRemoteSource = mockk(),
        portfolioVisibility = mockk(),
        currentAccountAssetReader = mockk()
    )
}
