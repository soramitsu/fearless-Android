package jp.co.soramitsu.fearless_utils.runtime.definitions.types

import jp.co.soramitsu.fearless_utils.common.assertThrows
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Alias
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeReferenceExtTest {

    @Test
    fun `should return self if not alias`() {
        val typeRef = TypeReference(u8)

        assertEquals(typeRef, typeRef.skipAliases())
    }

    @Test
    fun `should resolve one-level alias`() {
        val typeRef = TypeReference(u8)
        val alias = Alias("A", typeRef)

        assertEquals(typeRef, TypeReference(alias).skipAliases())
    }

    @Test
    fun `should resolve multiple-level alias`() {
        val typeRef = TypeReference(u8)
        val alias1 = Alias("A", typeRef)
        val alias2 = Alias("B", TypeReference(alias1))

        assertEquals(typeRef, TypeReference(alias2).skipAliases())
    }

    @Test
    fun `should throw on cycling-aliasing`() {
        val alias1Ref = TypeReference(null)
        val alias2Ref = TypeReference(null)

        val alias1 = Alias("A", alias2Ref)
        val alias2 = Alias("B", alias1Ref)

        alias1Ref.value = alias1
        alias2Ref.value = alias2

        assertThrows<CyclicAliasingException> { alias1Ref.skipAliases() }
    }

    @Test
    fun `should throw on self-aliasing`() {
        val alias1Ref = TypeReference(null)

        val alias1 = Alias("A", alias1Ref)

        alias1Ref.value = alias1

        assertThrows<CyclicAliasingException> { alias1Ref.skipAliases() }
    }

    @Test
    fun `should return null on cycling-aliasing`() {
        val alias1Ref = TypeReference(null)
        val alias2Ref = TypeReference(null)

        val alias1 = Alias("A", alias2Ref)
        val alias2 = Alias("B", alias1Ref)

        alias1Ref.value = alias1
        alias2Ref.value = alias2

        assertEquals(null, alias1Ref.skipAliasesOrNull())
    }
}