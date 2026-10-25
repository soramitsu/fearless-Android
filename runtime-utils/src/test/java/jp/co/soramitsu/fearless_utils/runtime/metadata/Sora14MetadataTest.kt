package jp.co.soramitsu.fearless_utils.runtime.metadata

import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.hash.Hasher.xxHash64
import jp.co.soramitsu.fearless_utils.hash.hashConcat
import jp.co.soramitsu.fearless_utils.runtime.RealRuntimeProvider
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class Sora14MetadataTest {

    private val soraRuntime = RealRuntimeProvider.buildRuntime("sora2", "_v14")

    private val soraKeypair = BaseKeypair(
        privateKey = "fb981a1fe2aeb2c6f3eaf50af5ac2408b6f5ab4b04ae124aa4546d71014d6e0d".fromHex(),
        publicKey = "bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f".fromHex()
    )

    private val t1 =
        "0x0200000000000000000000000000000000000000000000000000000000000000".fromHex().toList()
            .map { it.toInt().toBigInteger() }
    private val t2 =
        "0x0200050000000000000000000000000000000000000000000000000000000000".fromHex().toList()
            .map { it.toInt().toBigInteger() }

    private val hex =
        "0x7c2f67164deafeedd91e34da0331ade460c9ab7384f36f3de79a685fa22b44919d7224862f5243be3cf3be5853f2c810020000000000000000000000000000000000000000000000000000000000000013dd3ce42378c5cfe6a72f2938ba393d0200050000000000000000000000000000000000000000000000000000000000"

    @Test
    fun `sora parse storage to get events for transfer extrinsic`() {
        val storage = soraRuntime.metadata.module("PoolXYK").storage("Reserves")
        val e1 = Struct.Instance(mapOf("code" to t1))
        val e2 = Struct.Instance(mapOf("code" to t2))
        val key = storage.storageKey(soraRuntime, e1, e2)

        assertEquals(hex, key)
    }

    @Test
    fun `dex manager storage 1`() {
        val storage = soraRuntime.metadata.module("DEXManager").storage("DEXInfos")
        val key = storage.storageKey(soraRuntime, BigInteger.ONE)
        val hex = "0xa1bd2c8b755a708aa525cd47c8e225fd49e90400771bdeb88bf8ecd95a3c447d5153cb1f00942ff401000000"
        assertEquals(hex, key)
    }

    @Test
    fun `dex manager storage 0`() {
        val storage = soraRuntime.metadata.module("DEXManager").storage("DEXInfos")
        val key = storage.storageKey(soraRuntime, BigInteger.ZERO)
        val hex = "0xa1bd2c8b755a708aa525cd47c8e225fd49e90400771bdeb88bf8ecd95a3c447db4def25cfda6ef3a00000000"
        assertEquals(hex, key)
    }

    @Test
    fun `dex manager storage 2`() {
        val storage = soraRuntime.metadata.module("DEXManager").storage("DEXInfos")
        val key = storage.storageKey(soraRuntime, BigInteger.TWO)
        val hex = "0xa1bd2c8b755a708aa525cd47c8e225fd49e90400771bdeb88bf8ecd95a3c447d9eb2dcce60f37a2702000000"
        assertEquals(hex, key)
    }

    @Test
    fun `sora2 substrate4 events parsing`() {
        val eventsInHex = "0x380000000000000098e140090000000002000000010000000208bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f00c05773a57c0200000000000000000000000100000012020200050000000000000000000000000000000000000000000000000000000000bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc6649859000064a7b3b6e00d00000000000000000000010000001501bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f84bdc405d139399bba3ccea5d3de23316c9deeab661f57e2f4d1720cc66498590200050000000000000000000000000000000000000000000000000000000000000064a7b3b6e00d00000000000000000000010000000900bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f00c05773a57c0200000000000000000000000100000000001804b70400000000000000000200000002081ac86a66220905edee92dde6f0f86a2816066cd28ee935162a8272b219020d6800c05773a57c0200000000000000000000000200000002021ac86a66220905edee92dde6f0f86a2816066cd28ee935162a8272b219020d6854734f90f971a02c609b2d684e61b557a3ffc178c2c4665f9f81fe701bbe1f9e00c02be5d642380f000000000000000000000200000002021ac86a66220905edee92dde6f0f86a2816066cd28ee935162a8272b219020d6854734f90f971a02c609b2d684e61b557da6b7fe224d6f6eb9aa214d8fe219b4d0040c21f55b90b0000000000000000000000020000001202020007000000000000000000000000000000000000000000000000000000000054734f90f971a02c609b2d684e61b557a3ffc178c2c4665f9f81fe701bbe1f9e1ac86a66220905edee92dde6f0f86a2816066cd28ee935162a8272b219020d68379e77f03e5d1700000000000000000000000200000018041ac86a66220905edee92dde6f0f86a2816066cd28ee935162a8272b219020d680000020000001a001ac86a66220905edee92dde6f0f86a2816066cd28ee935162a8272b219020d6800000000020000000000000000000000000000000000000000000000000000000000000002000700000000000000000000000000000000000000000000000000000000000000ee042cfc430f0000000000000000379e77f03e5d170000000000000000000040c21f55b90b000000000000000000080000000000000000000200000200000009001ac86a66220905edee92dde6f0f86a2816066cd28ee935162a8272b219020d6800c05773a57c02000000000000000000000002000000000040e1619f00000000000000"
        val eventType = soraRuntime.metadata.module("System").storage("Events").type.value!!
        val events = eventType.fromHex(soraRuntime, eventsInHex)
        assertTrue(events is List<*>)
        assertEquals(14, (events as? List<*>)?.size)
    }
}