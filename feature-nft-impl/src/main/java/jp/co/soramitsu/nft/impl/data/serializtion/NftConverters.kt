package jp.co.soramitsu.nft.impl.data.serializtion

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftCollection
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftContractInfo
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftId
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftMediaInfo
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftMetadata
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftOpenseaInfo
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftSpamInfo
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftTokenMetadata

class AlchemyNftContractInfoDeserializer : JsonDeserializer<AlchemyNftContractInfo> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): AlchemyNftContractInfo {
        val address = json?.asJsonObject?.get("address")?.asString
        return AlchemyNftContractInfo(address)
    }
}

class AlchemyNftMetadataDeserializer : JsonDeserializer<AlchemyNftMetadata> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): AlchemyNftMetadata? {
        return json?.asJsonObject?.let {
            AlchemyNftMetadata(
                name = it.get("name")?.asString,
                description = it.get("description")?.asString,
                backgroundColor = it.get("backgroundColor")?.asString,
                poster = it.get("poster")?.asString,
                image = it.get("image")?.asString,
                externalUrl = it.get("externalUrl")?.asString,
            )
        }
    }
}

class AlchemyNftMediaInfoDeserializer : JsonDeserializer<AlchemyNftMediaInfo> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): AlchemyNftMediaInfo? {
        return json?.asJsonObject?.let {
            AlchemyNftMediaInfo(
                gateway = it.get("gateway")?.asString,
                thumbnail = it.get("thumbnail")?.asString,
                raw = it.get("raw")?.asString,
                format = it.get("format")?.asString,
                bytes = it.get("bytes")?.asBigInteger,
            )
        }
    }
}
class AlchemyNftCollectionDeserializer : JsonDeserializer<AlchemyNftCollection> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): AlchemyNftCollection? {
        return json?.asJsonObject?.let {
            val tt = object : TypeToken<List<AlchemyNftMediaInfo>>() {}.type
            AlchemyNftCollection(
                address = it.get("address")?.asString,
                totalBalance = it.get("totalBalance")?.asInt,
                numDistinctTokensOwned = it.get("numDistinctTokensOwned")?.asInt,
                isSpam = it.get("isSpam")?.asString,
                tokenId = it.get("tokenId")?.asString,
                name = it.get("name")?.asString,
                title = it.get("title")?.asString,
                symbol = it.get("symbol")?.asString,
                totalSupply = it.get("totalSupply")?.asString,
                tokenType = it.get("tokenType")?.asString,
                contractDeployer = it.get("contractDeployer")?.asString,
                deployedBlockNumber = it.get("deployedBlockNumber")?.asBigInteger,
                openSea = context?.deserialize<AlchemyNftOpenseaInfo?>(it.get("openSea"), AlchemyNftOpenseaInfo::class.java),
                media = context?.deserialize<List<AlchemyNftMediaInfo>?>(it.get("media"), tt)
            )
        }
    }
}

class OpenSeaDeserializer: JsonDeserializer<AlchemyNftOpenseaInfo> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): AlchemyNftOpenseaInfo? {
        return json?.asJsonObject?.let{
            AlchemyNftOpenseaInfo(
                floorPrice = it.get("floorPrice")?.asFloat,
                collectionName = it.get("collectionName")?.asString,
                collectionSlug = it.get("collectionSlug")?.asString,
                safelistRequestStatus = it.get("safelistRequestStatus")?.asString,
                imageUrl = it.get("imageUrl")?.asString,
                description = it.get("description")?.asString,
                externalUrl = it.get("externalUrl")?.asString,
                twitterUsername = it.get("twitterUsername")?.asString,
                bannerImageUrl = it.get("bannerImageUrl")?.asString,
                lastIngestedAt = it.get("lastIngestedAt")?.asString,
            )
        }
    }
}

class AlchemyNftIdDeserializer: JsonDeserializer<AlchemyNftId> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): AlchemyNftId? {
        return json?.asJsonObject?.let{
            AlchemyNftId(
                tokenId = it.get("tokenId")?.asString,
                tokenMetadata = context?.deserialize<AlchemyNftTokenMetadata>(it.get("tokenMetadata"), AlchemyNftTokenMetadata::class.java),
            )
        }
    }
}

class AlchemyNftTokenMetadataDeserializer: JsonDeserializer<AlchemyNftTokenMetadata> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): AlchemyNftTokenMetadata? {
        return json?.asJsonObject?.let{
            AlchemyNftTokenMetadata(
                tokenType = it.get("tokenType")?.asString,
            )
        }
    }
}

class AlchemyNftSpamInfoDeserializer: JsonDeserializer<AlchemyNftSpamInfo> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): AlchemyNftSpamInfo? {
        return json?.asJsonObject?.let{ jObject ->
            AlchemyNftSpamInfo(
                isSpam = jObject.get("isSpam")?.asBoolean,
                classifications = jObject.get("classifications")?.asJsonArray?.map { it.asString },
            )
        }
    }
}