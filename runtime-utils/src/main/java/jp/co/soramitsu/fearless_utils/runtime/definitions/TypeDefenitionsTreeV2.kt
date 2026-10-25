package jp.co.soramitsu.fearless_utils.runtime.definitions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class TypeDefinitionsTreeV2(
    @SerialName("runtime_id")
    val runtimeId: Int? = null,
    @SerialName("types")
    val types: JsonObject,
    @SerialName("versioning")
    val versioning: List<Versioning>? = null,
    @SerialName("overrides")
    val overrides: List<OverriddenItem>? = null
) {

    @Serializable
    class OverriddenItem(
        @SerialName("module")
        val module: String,
        @SerialName("constants")
        val constants: List<OverriddenConstant> = emptyList()
    )

    @Serializable
    class OverriddenConstant(
        @SerialName("name")
        val name: String,
        @SerialName("value")
        val value: String
    )

    @Serializable
    class Versioning(
        @SerialName("runtime_range")
        val range: List<Int?>,
        @SerialName("types")
        val types: JsonObject
    ) {
        val from: Int
            get() = range.first()!!
        fun isMatch(v: Int): Boolean = (v >= from && range.size == 2) && ((range[1] == null) || (range[1] != null && range[1]!! >= v))
    }
}
