package jp.co.soramitsu.fearless_utils.runtime.definitions

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import jp.co.soramitsu.fearless_utils.common.assertInstance
import jp.co.soramitsu.fearless_utils.getResourceReader
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypePreset
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.type
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.typePreset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.CollectionEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.FixedArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Option
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.SetType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u64
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.stub.FakeType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4


@RunWith(JUnit4::class)
class TypeDefinitionParserTest {

    val gson = Gson()

    @Test
    fun `should parse typealias`() {
        val A = FakeType("A")

        val initialTypeRegistry = typePreset {
            type(A)
        }

        val definitions = definitions {
            """
            "B": "A"
            """
        }

        val resultRegistry = parseFromJson(initialTypeRegistry, definitions)

        val B = resultRegistry["B"]

        assertEquals(A, B)
    }

    @Test
    fun `should parse struct`() {
        val A = FakeType("A")
        val B = FakeType("B")

        val initialTypeRegistry = typePreset {
            type(A)
            type(B)
        }

        val definitions = definitions {
            """
            "C": {
                "type": "struct",
                "type_mapping": [
                    [
                        "a",
                        "A"
                    ],
                    [
                        "b",
                        "B"
                    ]
                ]
            }
            """
        }

        val resultRegistry = parseFromJson(initialTypeRegistry, definitions)

        val C = resultRegistry["C"]

        assertInstance<Struct>(C)

        assertEquals(C["a"], A)
        assertEquals(C["b"], B)
    }

    @Test
    fun `should parse dict enum`() {
        val A = FakeType("A")
        val B = FakeType("B")

        val initialTypeRegistry = typePreset {
            type(A)
            type(B)
        }

        val definitions = definitions {
            """
            "C": {
                "type": "enum",
                "type_mapping": [
                    [
                        "a",
                        "A"
                    ],
                    [
                        "b",
                        "B"
                    ]
                ]
            }
            """
        }

        val resultRegistry = parseFromJson(initialTypeRegistry, definitions)

        val C = resultRegistry["C"]

        assertInstance<DictEnum>(C)

        assertEquals(C["a"], A)
        assertEquals(C["b"], B)
    }

    @Test
    fun `should parse collection enum`() {
        val A = FakeType("A")
        val B = FakeType("B")

        val initialTypeRegistry = typePreset {
            type(A)
            type(B)
        }

        val definitions = definitions {
            """
            "C": {
                "type": "enum",
                "value_list": [
                    "A",
                    "B"
                ]
            }
            """
        }

        val resultRegistry = parseFromJson(initialTypeRegistry, definitions)

        val C = resultRegistry["C"]

        assertInstance<CollectionEnum>(C)

        assertEquals(C[0], "A")
        assertEquals(C[1], "B")
    }

    @Test
    fun `should parse set`() {

        val initialTypeRegistry = typePreset {
            type(u8)
        }

        val definitions = definitions {
            """
            "C": {
                "type": "set",
                "value_type": "u8",
                "value_list": {
                    "A": 1,
                    "B": 2
                }
            }
            """
        }

        val resultRegistry = parseFromJson(initialTypeRegistry, definitions)

        val C = resultRegistry["C"]

        assertInstance<SetType>(C)

        assertEquals(C["A"], 1.toBigInteger())
        assertEquals(C["B"], 2.toBigInteger())
    }

    @Test
    fun `should parse fixed array`() {

        val initialTypeRegistry = typePreset {
            type(BooleanType)
        }

        val definitions = definitions {
            """
            "C": "[bool; 5]"
            """
        }

        val resultRegistry = parseFromJson(initialTypeRegistry, definitions)

        val C = resultRegistry["C"]

        assertInstance<FixedArray>(C)

        assertEquals(C.length, 5)
        assertEquals(C.typeReference.value, BooleanType)
    }

    @Test
    fun `should parse fixed u8 array optimized`() {

        val initialTypeRegistry = typePreset {
            type(u8)
        }

        val definitions = definitions {
            """
            "C": "[u8; 5]"
            """
        }

        val resultRegistry = parseFromJson(initialTypeRegistry, definitions)

        val C = resultRegistry["C"]

        assertInstance<FixedByteArray>(C)

        assertEquals(C.length, 5)
    }

    @Test
    fun `should parse vector`() {

        val initialTypeRegistry = typePreset {
            type(BooleanType)
        }

        val definitions = definitions {
            """
            "C": "Vec<bool>"
            """
        }

        val resultRegistry = parseFromJson(initialTypeRegistry, definitions)

        val C = resultRegistry["C"]

        assertInstance<Vec>(C)

        assertEquals(C.typeReference.value, BooleanType)
    }

    @Test
    fun `should parse tuple`() {
        val A = FakeType("A")
        val B = FakeType("B")

        val initialTypeRegistry = typePreset {
            type(A)
            type(B)
        }

        val definitions = definitions {
            """
            "C": "(A, B)"
            """
        }

        val resultRegistry = parseFromJson(initialTypeRegistry, definitions)

        val C = resultRegistry["C"]

        assertInstance<Tuple>(C)

        assertEquals(C[0], A)
        assertEquals(C[1], B)
    }

    @Test
    fun `should parse option`() {
        val A = FakeType("A")

        val initialTypeRegistry = typePreset {
            type(A)
        }

        val definitions = definitions {
            """
            "C": "Option<A>"
            """
        }

        val resultRegistry = parseFromJson(initialTypeRegistry, definitions)

        val C = resultRegistry["C"]

        assertInstance<Option>(C)

        assertEquals(C.innerType, A)
    }

    @Test
    fun `should parse complex type`() {
        val A = FakeType("A")
        val B = FakeType("B")

        val initialTypeRegistry = typePreset {
            type(A)
            type(B)
        }

        val definitions = definitions {
            """
            "C": "Vec<(A, Option<(A, B)>)>"
            """
        }

        val resultRegistry = parseFromJson(initialTypeRegistry, definitions)

        val C = resultRegistry["C"]

        assertInstance<Vec>(C)

        val innerTuple = C.innerType
        assertInstance<Tuple>(innerTuple)

        assertEquals(A, innerTuple[0])

        val innerOption = innerTuple[1]
        assertInstance<Option>(innerOption)

        val leafTuple = innerOption.innerType
        assertInstance<Tuple>(leafTuple)

        assertEquals(A, leafTuple[0])
        assertEquals(B, leafTuple[1])
    }

    @Test
    fun `should put unresolved type to unknown`() {
        val A = FakeType("A")
        val B = FakeType("B")

        val initialTypeRegistry = typePreset {
            type(A)
            type(B)
        }

        val definitions = definitions {
            """
            "C": "F"
            """
        }

        val tree = gson.fromJson(definitions, TypeDefinitionsTree::class.java)

        val unknown =
            TypeDefinitionParser.parseBaseDefinitions(tree = tree, typePreset = initialTypeRegistry, getUnknownTypes = true).unknownTypes

        assert("F" in unknown)
    }

    @Test
    fun `should parse substrate data`() {
        val gson = Gson()
        val reader = JsonReader(getResourceReader("default.json"))
        val tree = gson.fromJson<TypeDefinitionsTree>(reader, TypeDefinitionsTree::class.java)

        val result = TypeDefinitionParser.parseBaseDefinitions(tree, v13Preset())

        print(result.unknownTypes)

        assertEquals(0, result.unknownTypes.size)
    }

    @Test
    fun `should parse network-specific patches`() {
        val gson = Gson()

        val defaultReader = JsonReader(getResourceReader("default.json"))
        val kusamaReader = JsonReader(getResourceReader("kusama.json"))

        val defaultTree =
            gson.fromJson<TypeDefinitionsTree>(defaultReader, TypeDefinitionsTree::class.java)
        val kusamaTree =
            gson.fromJson<TypeDefinitionsTree>(kusamaReader, TypeDefinitionsTree::class.java)

        val defaultParsed = TypeDefinitionParser.parseBaseDefinitions(defaultTree, v13Preset())
        val defaultRegistry = TypeRegistry(defaultParsed.typePreset, DynamicTypeResolver.defaultCompoundResolver())

        val keysDefault = defaultRegistry["Keys"]
        assertEquals("Keys", keysDefault?.name)
        assertEquals(3, (keysDefault as Struct).mapping.size)

        val kusamaParsed = TypeDefinitionParser.parseNetworkVersioning(
            kusamaTree,
            defaultParsed.typePreset,
            1057
        )
        val kusamaRegistry = TypeRegistry(kusamaParsed.typePreset, DynamicTypeResolver.defaultCompoundResolver())

        print(kusamaParsed.unknownTypes)

        val keysKusama = kusamaRegistry["Keys"]
        assertEquals("Keys", keysKusama?.name) // changed from SessionKeysSubstrate

        val assignments = kusamaRegistry["CompactAssignments"]
        assertEquals("CompactAssignmentsTo257", assignments?.name) // changed only at 2023

        val addressKusama = kusamaRegistry["Address"]
        assertEquals("GenericAccountId", addressKusama?.name) // Address changed to MultiAddress only in 2028

        val weight = kusamaRegistry["Weight"]
        assertEquals(u64, weight) // changed multiple times, latest at 1057

        val refCount = kusamaRegistry["RefCount"]
        assertEquals(u8, refCount)
    }

    private fun parseFromJson(typePreset: TypePreset, json: String): TypeRegistry {
        val tree = gson.fromJson(json, TypeDefinitionsTree::class.java)

        return TypeRegistry(
            TypeDefinitionParser.parseBaseDefinitions(tree, typePreset).typePreset,
            dynamicTypeResolver = DynamicTypeResolver.defaultCompoundResolver()
        )
    }

    private fun definitions(builder: () -> String): String {
        return """
            { 
            "types": {
                    ${builder.invoke()}
                }
             }
        """.trimIndent()
    }
}