package jp.co.soramitsu.fearless_utils.runtime.definitions.registry.extensions

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import jp.co.soramitsu.fearless_utils.common.assertInstance
import jp.co.soramitsu.fearless_utils.getResourceReader
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.TypeProvider
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.HashMapExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.stub.FakeType
import org.junit.Assert
import org.junit.Test

class HashMapExtensionTest {

    private val fakeTypeProvider: TypeProvider = {
        TypeReference(FakeType(it))
    }

    @Test
    fun `should parse hashmap`() {
        val type = HashMapExtension.createType("HashMap<Text, Text>", "HashMap<Text, Text>", fakeTypeProvider)
        assertInstance<Vec>(type)
        Assert.assertEquals(type.typeReference.value?.name, "(Text,Text)" )
    }

    @Test
    fun `should parse hashmap with tuple`() {
        val gson = Gson()
        val defaultReader = JsonReader(getResourceReader("default.json"))
        val defaultTree =
            gson.fromJson<TypeDefinitionsTree>(defaultReader, TypeDefinitionsTree::class.java)
        val defaultParsed = TypeDefinitionParser.parseBaseDefinitions(defaultTree, v13Preset())
        val defaultRegistry = TypeRegistry(defaultParsed.typePreset, DynamicTypeResolver.defaultCompoundResolver())
        val type = defaultRegistry["HashMap<Text, Text>"]
        assertInstance<Vec>(type)
        assertInstance<Tuple>(type.typeReference.value)
    }
}
