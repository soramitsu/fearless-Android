package jp.co.soramitsu.fearless_utils.runtime.definitions.registry.extensions

import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.TypeProvider
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.stub.FakeType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class GenericsExtensionTest {

    private val fakeTypeProvider: TypeProvider = {
        TypeReference(FakeType(it))
    }

    @Test
    fun `should extract raw type`() {
        val typeDef = "AccountInfo<T::Index, T::AccountData>"

        val createdType = GenericsExtension.createType("Test", typeDef, fakeTypeProvider)

        assert(createdType != null)
        assertEquals(createdType!!.name, "AccountInfo")
    }

    @Test
    fun `should return null for plain type`() {
        val typeDef = "AccountInfo"

        val createdType = GenericsExtension.createType("Test", typeDef, fakeTypeProvider)

        assert(createdType == null)
    }
}