package jp.co.soramitsu.staking.impl.data.mappers

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import jp.co.soramitsu.common.data.network.subquery.SoraEraInfoValidatorResponse

class SoraEraInfoValidatorResponseDeserializer : JsonDeserializer<SoraEraInfoValidatorResponse> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): SoraEraInfoValidatorResponse? {
        val stakingEraNominators = json?.asJsonObject?.getAsJsonArray("stakingEraNominators")
        val tt = object : TypeToken<List<SoraEraInfoValidatorResponse.Nominator>>() {}.type
        val content = context?.deserialize<List<SoraEraInfoValidatorResponse.Nominator>?>(
            stakingEraNominators,
            tt
        )
        return content?.let { SoraEraInfoValidatorResponse(it) }
    }
}

class SoraEraInfoValidatorResponseNominatorDeserializer :
    JsonDeserializer<SoraEraInfoValidatorResponse.Nominator> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): SoraEraInfoValidatorResponse.Nominator? {
        val nominations = json?.asJsonObject?.getAsJsonArray("nominations")
        val tt =
            object : TypeToken<List<SoraEraInfoValidatorResponse.Nominator.Nomination>>() {}.type
        val content = context?.deserialize<List<SoraEraInfoValidatorResponse.Nominator.Nomination>>(
            nominations,
            tt
        )
        return content?.let { SoraEraInfoValidatorResponse.Nominator(it) }
    }
}

class SoraEraInfoValidatorResponseNominationDeserializer :
    JsonDeserializer<SoraEraInfoValidatorResponse.Nominator.Nomination> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): SoraEraInfoValidatorResponse.Nominator.Nomination? {
        val validator = json?.asJsonObject?.getAsJsonObject("validator")
        val content =
            context?.deserialize<SoraEraInfoValidatorResponse.Nominator.Nomination.Validator>(
                validator,
                SoraEraInfoValidatorResponse.Nominator.Nomination.Validator::class.java
            )
        return content?.let { SoraEraInfoValidatorResponse.Nominator.Nomination(it) }
    }
}

class SoraEraInfoValidatorResponseValidatorDeserializer :
    JsonDeserializer<SoraEraInfoValidatorResponse.Nominator.Nomination.Validator> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): SoraEraInfoValidatorResponse.Nominator.Nomination.Validator? {
        val id = json?.asJsonObject?.getAsJsonObject("validator")?.getAsJsonPrimitive("id")?.asString
        return id?.let { SoraEraInfoValidatorResponse.Nominator.Nomination.Validator(it) }
    }
}
