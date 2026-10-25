package jp.co.soramitsu.xcm.domain

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XcmExecutionSpecTest {

    private val relay = chain("relay")
    private val assetHub = chain("asset-hub", paraId = "1000", parentId = "relay")
    private val sibling = chain("sibling", paraId = "2000", parentId = "relay")

    @Test
    fun `a parachain is reached from its relay chain one level down`() {
        assertEquals(XcmChainPath(0, listOf(parachain("1000"))), xcmDestinationPath(relay, assetHub))
    }

    @Test
    fun `the relay chain is reached from its parachain one level up`() {
        assertEquals(XcmChainPath(1, emptyList()), xcmDestinationPath(assetHub, relay))
    }

    @Test
    fun `a sibling parachain is reached through the shared relay chain`() {
        assertEquals(XcmChainPath(1, listOf(parachain("2000"))), xcmDestinationPath(assetHub, sibling))
        assertEquals(XcmChainPath(1, listOf(parachain("1000"))), xcmDestinationPath(sibling, assetHub))
    }

    @Test
    fun `chains without a reviewed path between them have none`() {
        val otherRelay = chain("other-relay")
        val foreignParachain = chain("foreign", paraId = "2000", parentId = "other-relay")
        val unregisteredParachain = chain("unregistered", paraId = null, parentId = "relay")
        val paddedParaId = chain("padded", paraId = "02000", parentId = "relay")
        val namedParaId = chain("named", paraId = "two-thousand", parentId = "relay")

        assertNull(xcmDestinationPath(relay, otherRelay))
        assertNull(xcmDestinationPath(otherRelay, assetHub))
        assertNull(xcmDestinationPath(assetHub, foreignParachain))
        assertNull(xcmDestinationPath(foreignParachain, relay))
        assertNull(xcmDestinationPath(relay, unregisteredParachain))
        assertNull(xcmDestinationPath(assetHub, paddedParaId))
        assertNull(xcmDestinationPath(assetHub, namedParaId))
        // A chain without a parachain id cannot claim the relay chain as its parent.
        assertNull(xcmDestinationPath(unregisteredParachain, relay))
    }

    @Test
    fun `an xTokens recipient is the destination location followed by the account`() {
        val destination = location(1, "X1(Parachain(2000))")

        assertTrue(location(1, "X2(Parachain(2000), AccountId32({network: Any, id: <account>}))").isRecipientAt(destination))
        assertTrue(location(1, "X2(Parachain(2000), AccountKey20({network: Any, key: <account>}))").isRecipientAt(destination))
        assertTrue(location(1, "X1(AccountId32({network: Any, id: <account>}))").isRecipientAt(location(1, "Here")))
    }

    @Test
    fun `an xTokens recipient anywhere else is refused`() {
        val destination = location(1, "X1(Parachain(2000))")

        listOf(
            location(0, "X1(AccountId32({network: Any, id: <account>}))"),
            location(2, "X2(Parachain(2000), AccountId32({network: Any, id: <account>}))"),
            location(1, "X2(Parachain(3000), AccountId32({network: Any, id: <account>}))"),
            location(1, "X3(Parachain(2000), PalletInstance(50), AccountId32({network: Any, id: <account>}))"),
            location(1, "X2(Parachain(2000), GeneralIndex(7))"),
            location(1, "X1(Parachain(2000))")
        ).forEach { beneficiary ->
            assertFalse(beneficiary.interior, beneficiary.isRecipientAt(destination))
        }
    }

    private fun parachain(id: String) = XcmJunctionSpec(XcmJunctionType.PARACHAIN, id)

    private fun location(parents: Int, interior: String) = XcmMultiLocationSpec(
        parents = parents,
        interior = interior,
        junctions = XcmMultiLocationParser.requireValidInterior(interior, "test")
    )

    private fun chain(
        id: String,
        paraId: String? = null,
        parentId: String? = null
    ) = Chain(
        id = id,
        paraId = paraId,
        rank = null,
        name = id,
        minSupportedVersion = null,
        assets = emptyList(),
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = null,
        icon = "",
        addressPrefix = 0,
        isEthereumBased = false,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = parentId,
        supportStakingPool = false,
        isEthereumChain = false,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.Substrate,
        androidMinAppVersion = null,
        remoteAssetsSource = null,
        tonBridgeUrl = null,
        xcm = null
    )
}
