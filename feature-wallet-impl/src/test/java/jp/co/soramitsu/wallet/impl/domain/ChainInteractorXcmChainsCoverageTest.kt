package jp.co.soramitsu.wallet.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import jp.co.soramitsu.xcm.domain.CrossChainRouteProvider
import jp.co.soramitsu.xcm.domain.CrossChainRouteProviderRegistry
import jp.co.soramitsu.xcm.domain.CrossChainRouteQuery
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cross-chain network lists merge approved XCM chains with the networks of available provider routes. */
class ChainInteractorXcmChainsCoverageTest {

    private val fetcher = mockk<XcmEntitiesFetcher>()
    private val available = RecordingProvider(
        providerId = "available",
        hasRoute = true,
        origins = setOf("liberland", "polkadot"),
        destinations = setOf("sora")
    )
    private val unavailable = RecordingProvider(
        providerId = "unavailable",
        hasRoute = false,
        origins = setOf("hidden-origin"),
        destinations = setOf("hidden-destination")
    )
    private val interactor = ChainInteractor(
        mockk<ChainDao>(),
        fetcher,
        CrossChainRouteProviderRegistry(listOf(available, unavailable))
    )

    @Test
    fun `origin networks add available provider origins without duplicates`() = runBlocking {
        coEvery { fetcher.getAvailableOriginChains(any(), any(), any()) } returns listOf("polkadot", "kusama")

        val chainIds = interactor.getXcmChainIdsFlow(XcmChainType.Origin).first()

        assertEquals(listOf("polkadot", "kusama", "liberland"), chainIds)
        assertEquals(CrossChainRouteQuery(), available.queries.single())
        coVerify(exactly = 1) { fetcher.getAvailableOriginChains(null, null, null) }
    }

    @Test
    fun `destination networks follow the origin, asset and symbol`() = runBlocking {
        coEvery { fetcher.getAvailableDestinationChains(any(), any(), any()) } returns listOf("moonbeam")

        val chainIds = interactor.getXcmChainIdsFlow(
            type = XcmChainType.Destination,
            originChainId = "liberland",
            originAssetId = "lld",
            assetSymbol = "LLD"
        ).first()

        assertEquals(listOf("moonbeam", "sora"), chainIds)
        assertEquals(
            CrossChainRouteQuery(originNetworkId = "liberland", originAssetId = "lld", assetSymbol = "LLD"),
            available.queries.single()
        )
        coVerify(exactly = 1) {
            fetcher.getAvailableDestinationChains(originChainId = "liberland", assetSymbol = "LLD", originAssetId = "lld")
        }
    }

    private class RecordingProvider(
        override val providerId: String,
        private val hasRoute: Boolean,
        private val origins: Set<String>,
        private val destinations: Set<String>
    ) : CrossChainRouteProvider {
        override val protocol: CrossChainProtocol = CrossChainProtocol.Liberland
        val queries = mutableListOf<CrossChainRouteQuery>()

        override suspend fun capability(query: CrossChainRouteQuery): CrossChainRouteCapability {
            queries += query
            return CrossChainRouteCapability(
                providerId = providerId,
                protocol = protocol,
                availability = if (hasRoute) CrossChainRouteAvailability.Available else CrossChainRouteAvailability.Unavailable,
                actionsEnabled = hasRoute,
                requiresAccount = true,
                requiresSigning = true,
                supportedOriginNetworkIds = origins,
                supportedDestinationNetworkIds = destinations,
                userFacingReason = if (hasRoute) null else "Not delivering"
            )
        }
    }
}
