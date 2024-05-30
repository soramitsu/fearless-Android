package jp.co.soramitsu.onboarding.impl.data

import com.google.gson.JsonDeserializer
import jp.co.soramitsu.onboarding.api.data.OnboardingConfig

val OnboardingConfig.Companion.deserializer: JsonDeserializer<OnboardingConfig>
    get() = JsonDeserializer { json, typeOfT, context ->
        val jsonObj = json.asJsonObject

        return@JsonDeserializer OnboardingConfig(
            configs = jsonObj.get("Android")?.asJsonArray?.mapNotNull { jsonElem ->
                context?.deserialize<OnboardingConfig.OnboardingConfigItem>(
                    jsonElem, OnboardingConfig.OnboardingConfigItem::class.java
                )
            } ?: emptyList(),
        )
    }

val OnboardingConfig.OnboardingConfigItem.Companion.deserializer: JsonDeserializer<OnboardingConfig.OnboardingConfigItem>
    get() = JsonDeserializer { json, typeOfT, context ->
        val jsonObj = json.asJsonObject

        return@JsonDeserializer OnboardingConfig.OnboardingConfigItem(
            minVersion = jsonObj.get("minVersion").asString,
            background = jsonObj.get("background").asString,
            enEn = context.deserialize<OnboardingConfig.OnboardingConfigItem.Variants>(
                jsonObj.get("en-EN"), OnboardingConfig.OnboardingConfigItem.Variants::class.java
            )
        )
    }

val OnboardingConfig.OnboardingConfigItem.Variants.Companion.deserializer: JsonDeserializer<OnboardingConfig.OnboardingConfigItem.Variants>
    get() = JsonDeserializer { json, typeOfT, context ->
        val jsonObj = json.asJsonObject

        return@JsonDeserializer OnboardingConfig.OnboardingConfigItem.Variants(
            new = jsonObj.get("new")?.asJsonArray?.mapNotNull { jsonElem ->
                context?.deserialize<OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo>(
                    jsonElem, OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo::class.java
                )
            } ?: emptyList(),
            regular = jsonObj.get("regular")?.asJsonArray?.mapNotNull { jsonElem ->
                context?.deserialize<OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo>(
                    jsonElem, OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo::class.java
                )
            } ?: emptyList()
        )
    }

val OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo.Companion.deserializer: JsonDeserializer<OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo>
    get() = JsonDeserializer { json, typeOfT, context ->
        val jsonObj = json.asJsonObject

        return@JsonDeserializer OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo(
            title = context.deserialize<OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo.TitleInfo>(
                jsonObj.get("title"), OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo.TitleInfo::class.java
            ),
            description = jsonObj.get("description").asString,
            image = jsonObj.get("image").asString
        )
    }

val OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo.TitleInfo.Companion.deserializer: JsonDeserializer<OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo.TitleInfo>
    get() = JsonDeserializer { json, typeOfT, context ->
        val jsonObj = json.asJsonObject

        return@JsonDeserializer OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo.TitleInfo(
            text = jsonObj.get("text").asString,
            color = jsonObj.get("color").asString
        )
    }