package jp.co.soramitsu.staking.impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Data
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntryModifier
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageHasher
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Identities over a small Identity pallet: real storage keys, a mocked bulk storage read, and SCALE values bound by
 * the real identity bindings (display names as raw Data, sub-accounts as (parent, Data) pairs).
 */
class IdentityRepositoryImplCoverageTest {

    private val accountId = FixedByteArray("AccountId", 32)
    private val data = Data(mutableMapOf())
    private val registration = Struct(
        "Registration",
        linkedMapOf("info" to TypeReference(Struct("IdentityInfo", linkedMapOf("display" to TypeReference(data)))))
    )
    private val superOf = Tuple("SuperOf", listOf(TypeReference(accountId), TypeReference(data)))
    private val identityOfEntry = entry("IdentityOf", registration)
    private val superOfEntry = entry("SuperOf", superOf)
    private val identityModule = Module(
        "Identity",
        Storage("Identity", mapOf("IdentityOf" to identityOfEntry, "SuperOf" to superOfEntry)),
        emptyMap(),
        emptyMap(),
        emptyMap(),
        emptyMap(),
        BigInteger.valueOf(28)
    )
    private val runtime: RuntimeSnapshot = snapshot(mapOf("Identity" to identityModule))

    private val child = ByteArray(32) { 1 }
    private val parent = ByteArray(32) { 2 }
    private val root = ByteArray(32) { 3 }
    private val unknown = ByteArray(32) { 4 }
    private val chain = StakingLaneFixtures.chain()

    private val values = mutableMapOf<String, String>()
    private val socket = mockk<SocketService>()
    private val connection = mockk<ChainConnection>()
    private val bulk = mockk<BulkRetriever>()
    private val registry = mockk<ChainRegistry>()
    private val repository = IdentityRepositoryImpl(bulk, registry)

    init {
        every { connection.socketService } returns socket
        coEvery { registry.awaitConnection(any()) } returns connection
        coEvery { registry.getRuntime(any()) } returns runtime
        coEvery { bulk.queryKeys(socket, any(), any()) } answers {
            secondArg<List<String>>().associateWith { values[it] }
        }
    }

    private fun entry(name: String, value: Type<*>) = StorageEntry(
        name,
        StorageEntryModifier.Optional,
        StorageEntryType.NMap(listOf(accountId), listOf(StorageHasher.Twox64Concat), value),
        ByteArray(0),
        emptyList(),
        "Identity"
    )

    private fun snapshot(modules: Map<String, Module>) = RuntimeSnapshot(
        mockk(relaxed = true),
        RuntimeMetadata(BigInteger.valueOf(14), modules, ExtrinsicMetadata(BigInteger.valueOf(4), emptyList()))
    )

    /** Raw Data: one byte of length + 1, then the bytes. */
    private fun raw(text: String) = (byteArrayOf((text.length + 1).toByte()) + text.toByteArray()).toHexString()

    private fun identity(account: ByteArray, display: String) {
        values[identityOfEntry.storageKey(runtime, account)] = "0x" + raw(display)
    }

    private fun sub(account: ByteArray, of: ByteArray, name: String) {
        values[superOfEntry.storageKey(runtime, account)] = "0x" + of.toHexString() + raw(name)
    }

    @Test
    fun `sub accounts are named after their parent identity`() = runTest {
        sub(child, parent, "sub")
        identity(parent, "Alice")
        identity(root, "Bob")

        val identities = repository.getIdentitiesFromIds(chain, listOf(child, root, unknown).map { it.toHexString() })

        assertEquals("Alice / sub", identities.getValue(child.toHexString())!!.display)
        assertEquals("Alice", identities.getValue(parent.toHexString())!!.display)
        assertEquals("Bob", identities.getValue(root.toHexString())!!.display)
        assertTrue(identities.containsKey(unknown.toHexString()))
        assertNull(identities.getValue(unknown.toHexString()))
    }

    @Test
    fun `sub account without a parent identity has none`() = runTest {
        sub(child, parent, "orphan")

        val identities = repository.getIdentitiesFromIds(chain, listOf(child.toHexString()))

        assertNull(identities[child.toHexString()])
        assertTrue(identities.containsKey(parent.toHexString()))
    }

    @Test
    fun `identities are also read by bytes and by address`() = runTest {
        identity(root, "Bob")
        val address = chain.addressOf(root)

        assertEquals("Bob", repository.getIdentitiesFromIdsBytes(chain, listOf(root)).getValue(root.toHexString())!!.display)
        assertEquals("Bob", repository.getIdentitiesFromAddresses(chain, listOf(address)).getValue(address)!!.display)
    }

    @Test
    fun `configured identity chain is read instead of the staking chain`() = runTest {
        identity(root, "Bob")

        val identities = repository.getIdentitiesFromIds(chain.copy(identityChain = PEOPLE), listOf(root.toHexString()))

        assertEquals("Bob", identities.getValue(root.toHexString())!!.display)
        coVerify { registry.awaitConnection(PEOPLE) }
        coVerify { registry.getRuntime(PEOPLE) }
        coVerify(exactly = 0) { registry.awaitConnection(chain.id) }
    }

    @Test
    fun `hub without identity pallet reports no identities`() = runTest {
        val hub = StakingLaneFixtures.chain(StakingLaneFixtures.chainAsset(chainId = AssetHubStaking.POLKADOT))
        coEvery { registry.getRuntime(AssetHubStaking.POLKADOT) } returns snapshot(emptyMap())
        val ids = listOf(root.toHexString(), unknown.toHexString())

        assertEquals(ids.associateWith { null }, repository.getIdentitiesFromIds(hub, ids))
        coVerify(exactly = 0) { bulk.queryKeys(any(), any(), any()) }
    }

    private companion object {
        const val PEOPLE = "people-chain"
    }
}
