package jp.co.soramitsu.onboarding.impl.data

import com.google.gson.JsonDeserializer
import jp.co.soramitsu.onboarding.api.data.OnboardingConfig

val OnboardingConfig.Companion.deserializer: JsonDeserializer<OnboardingConfig>
    get() = JsonDeserializer { json, typeOfT, context ->
        val jsonObj = json.asJsonObject

        return@JsonDeserializer OnboardingConfig(
            en_EN = context.deserialize<OnboardingConfig.Variants>(
                jsonObj.get("en-EN"), OnboardingConfig.Variants::class.java
            )
        )
    }

val OnboardingConfig.Variants.Companion.deserializer: JsonDeserializer<OnboardingConfig.Variants>
    get() = JsonDeserializer { json, typeOfT, context ->
        val jsonObj = json.asJsonObject

        return@JsonDeserializer OnboardingConfig.Variants(
            new = jsonObj.get("new")?.asJsonArray?.mapNotNull { jsonElem ->
                context?.deserialize<OnboardingConfig.Variants.ScreenInfo>(
                    jsonElem, OnboardingConfig.Variants.ScreenInfo::class.java
                )
            } ?: emptyList(),
            regular = jsonObj.get("regular")?.asJsonArray?.mapNotNull { jsonElem ->
                context?.deserialize<OnboardingConfig.Variants.ScreenInfo>(
                    jsonElem, OnboardingConfig.Variants.ScreenInfo::class.java
                )
            } ?: emptyList()
        )
    }

val OnboardingConfig.Variants.ScreenInfo.Companion.deserializer: JsonDeserializer<OnboardingConfig.Variants.ScreenInfo>
    get() = JsonDeserializer { json, typeOfT, context ->
        val jsonObj = json.asJsonObject

        return@JsonDeserializer OnboardingConfig.Variants.ScreenInfo(
            title = jsonObj.get("title").asString,
            description = jsonObj.get("description").asString,
            image = jsonObj.get("image").asString
        )
    }