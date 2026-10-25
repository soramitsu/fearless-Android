package jp.co.soramitsu.fearless_utils.runtime.definitions

import jp.co.soramitsu.fearless_utils.runtime.RealRuntimeProvider
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.UIntType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonTypesInRegistry {

    private val networks = listOf("sora2" to "_v14", "kintsugi" to "_v14", "polkadot" to "_v14", "polkatrain" to "", "statemine" to "_v14", "westend" to "_v14")

    @Test
    fun test_u() {
        networks.forEach {
            val runtimeSnapshot = RealRuntimeProvider.buildRuntime(it.first, it.second)
            var type = runtimeSnapshot.typeRegistry["u8"]
            assertEquals(1, (type as UIntType).bytes)
            type = runtimeSnapshot.typeRegistry["u16"]
            assertEquals(2, (type as UIntType).bytes)
            type = runtimeSnapshot.typeRegistry["u32"]
            assertEquals(4, (type as UIntType).bytes)
            type = runtimeSnapshot.typeRegistry["u64"]
            assertEquals(8, (type as UIntType).bytes)
            type = runtimeSnapshot.typeRegistry["u128"]
            assertEquals(16, (type as UIntType).bytes)
            type = runtimeSnapshot.typeRegistry["u256"]
            assertEquals(32, (type as UIntType).bytes)
            type = runtimeSnapshot.typeRegistry["bool"]
            assertTrue(type is BooleanType)
            type = runtimeSnapshot.typeRegistry["GenericAccountId"]
            assertTrue(type is FixedByteArray)
        }
    }
}