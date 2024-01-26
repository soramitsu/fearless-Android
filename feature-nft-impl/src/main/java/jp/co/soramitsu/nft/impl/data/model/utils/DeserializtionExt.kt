package jp.co.soramitsu.nft.impl.data.model.utils

import com.google.gson.JsonDeserializer
import jp.co.soramitsu.nft.data.models.Contract
import jp.co.soramitsu.nft.data.models.TokenId
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse

internal val NFTResponse.ContractMetadata.Companion.deserializer: JsonDeserializer<NFTResponse.ContractMetadata>
    get() = JsonDeserializer { json, _, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        return@JsonDeserializer NFTResponse.ContractMetadata(
            address = jsonObj.get("address")?.asString,
            contractMetadata = context?.deserialize<TokenInfo.WithMetadata.ContractMetadata?>(
                jsonObj.get("contractMetadata"), TokenInfo.WithMetadata.ContractMetadata::class.java
            )
        )
    }

internal val NFTResponse.UserOwnedTokens.Companion.deserializer: JsonDeserializer<NFTResponse.UserOwnedTokens>
    get() = JsonDeserializer<NFTResponse.UserOwnedTokens> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        return@JsonDeserializer NFTResponse.UserOwnedTokens(
            tokensInfoList = jsonObj.get("ownedNfts")?.asJsonArray?.mapNotNull { jsonElem ->
                context?.deserialize<TokenInfo.WithoutMetadata>(
                    jsonElem, TokenInfo.WithoutMetadata::class.java
                )
            } ?: emptyList(),
            nextPage = jsonObj.get("pageKey")?.asString,
            totalCount = jsonObj.get("totalCount")?.asInt
        )
    }

internal val NFTResponse.TokensCollection.Companion.deserializer: JsonDeserializer<NFTResponse.TokensCollection>
    get() = JsonDeserializer<NFTResponse.TokensCollection> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        val tokensJsonObj = when {
            jsonObj.has("nfts") ->
                jsonObj.get("nfts")

            jsonObj.has("ownedNfts") ->
                jsonObj.get("ownedNfts")

            else -> null
        }

        return@JsonDeserializer NFTResponse.TokensCollection(
            tokenInfoList = tokensJsonObj?.asJsonArray?.mapNotNull { jsonElem ->
                context?.deserialize<TokenInfo.WithMetadata>(
                    jsonElem, TokenInfo.WithMetadata::class.java
                )
            } ?: emptyList(),
            nextPage = jsonObj.get("nextToken")?.asString
        )
    }

internal val Contract.Companion.deserializer: JsonDeserializer<Contract>
    get() = JsonDeserializer<Contract> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        return@JsonDeserializer Contract(
            address = jsonObj.get("address")?.asString
        )
    }

internal val TokenId.WithoutMetadata.Companion.deserializer: JsonDeserializer<TokenId.WithoutMetadata>
    get() = JsonDeserializer<TokenId.WithoutMetadata> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        return@JsonDeserializer TokenId.WithoutMetadata(
            tokenId = jsonObj.get("tokenId")?.asString
        )
    }

internal val TokenId.WithMetadata.Companion.deserializer: JsonDeserializer<TokenId.WithMetadata>
    get() = JsonDeserializer<TokenId.WithMetadata> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        return@JsonDeserializer TokenId.WithMetadata(
            tokenId = jsonObj.get("tokenId")?.asString,
            tokenMetadata = context?.deserialize<TokenId.WithMetadata.TokenMetadata>(
                jsonObj.get("tokenMetadata"), TokenId.WithMetadata.TokenMetadata::class.java
            )
        )
    }

internal val TokenId.WithMetadata.TokenMetadata.Companion.deserializer: JsonDeserializer<TokenId.WithMetadata.TokenMetadata>
    get() = JsonDeserializer<TokenId.WithMetadata.TokenMetadata> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        return@JsonDeserializer TokenId.WithMetadata.TokenMetadata(
            tokenType = jsonObj.get("tokenType")?.asString,
        )
    }

internal val TokenInfo.WithoutMetadata.Companion.deserializer: JsonDeserializer<TokenInfo.WithoutMetadata>
    get() = JsonDeserializer<TokenInfo.WithoutMetadata> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        return@JsonDeserializer TokenInfo.WithoutMetadata(
            contract = context?.deserialize<Contract?>(
                jsonObj.get("contract"), Contract::class.java
            ),
            id = context?.deserialize<TokenId.WithoutMetadata?>(
                jsonObj.get("id"), TokenId.WithoutMetadata::class.java
            ),
            balance = jsonObj.get("balance")?.asString
        )
    }

internal val TokenInfo.WithMetadata.Companion.deserializer: JsonDeserializer<TokenInfo.WithMetadata>
    get() = JsonDeserializer<TokenInfo.WithMetadata> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        return@JsonDeserializer TokenInfo.WithMetadata(
            contract = context?.deserialize<Contract?>(
                jsonObj.get("contract"), Contract::class.java
            ),
            id = context?.deserialize<TokenId.WithMetadata?>(
                jsonObj.get("id"), TokenId.WithMetadata::class.java
            ),
            title = jsonObj.get("title")?.asString,
            balance = jsonObj.get("balance")?.asString,
            description = jsonObj.get("description")?.asString,
            media = jsonObj.get("media").asJsonArray?.mapNotNull { mediaElement ->
                context?.deserialize<TokenInfo.WithMetadata.Media?>(
                    mediaElement, TokenInfo.WithMetadata.Media::class.java
                )
            },
            metadata = context?.deserialize<TokenInfo.WithMetadata.TokenMetadata?>(
                jsonObj.get("metadata"), TokenInfo.WithMetadata.TokenMetadata::class.java
            ),
            contractMetadata = context?.deserialize<TokenInfo.WithMetadata.ContractMetadata?>(
                jsonObj.get("contractMetadata"), TokenInfo.WithMetadata.ContractMetadata::class.java
            ),
            spamInfo = context?.deserialize<TokenInfo.WithMetadata.SpamInfo?>(
                jsonObj.get("spamInfo"), TokenInfo.WithMetadata.SpamInfo::class.java
            )
        )
    }

internal val TokenInfo.WithMetadata.Media.Companion.deserializer: JsonDeserializer<TokenInfo.WithMetadata.Media>
    get() = JsonDeserializer<TokenInfo.WithMetadata.Media> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        return@JsonDeserializer TokenInfo.WithMetadata.Media(
            gateway = jsonObj.get("gateway")?.asString,
            thumbnail = jsonObj.get("thumbnail")?.asString,
            raw = jsonObj.get("raw")?.asString,
            format = jsonObj.get("format")?.asString,
            bytes = jsonObj.get("bytes")?.asBigInteger,
        )
    }

internal val TokenInfo.WithMetadata.TokenMetadata.Companion.deserializer: JsonDeserializer<TokenInfo.WithMetadata.TokenMetadata>
    get() = JsonDeserializer<TokenInfo.WithMetadata.TokenMetadata> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        return@JsonDeserializer TokenInfo.WithMetadata.TokenMetadata(
            name = jsonObj.get("name")?.asString,
            description = jsonObj.get("description")?.asString,
            backgroundColor = jsonObj.get("backgroundColor")?.asString,
            poster = jsonObj.get("poster")?.asString,
            image = jsonObj.get("image")?.asString,
            externalUrl = jsonObj.get("externalUrl")?.asString,
        )
    }

internal val TokenInfo.WithMetadata.ContractMetadata.Companion.deserializer: JsonDeserializer<TokenInfo.WithMetadata.ContractMetadata>
    get() = JsonDeserializer<TokenInfo.WithMetadata.ContractMetadata> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        return@JsonDeserializer TokenInfo.WithMetadata.ContractMetadata(
            name = jsonObj.get("name")?.asString,
            symbol = jsonObj.get("symbol")?.asString,
            totalSupply = jsonObj.get("totalSupply")?.asString,
            tokenType = jsonObj.get("tokenType")?.asString,
            contractDeployer = jsonObj.get("contractDeployer")?.asString,
            deployedBlockNumber = jsonObj.get("deployedBlockNumber")?.asBigInteger,
            openSea = context?.deserialize<TokenInfo.WithMetadata.ContractMetadata.OpenSea?>(
                jsonObj.get("openSea"), TokenInfo.WithMetadata.ContractMetadata.OpenSea::class.java
            ),
        )
    }

internal val TokenInfo.WithMetadata.ContractMetadata.OpenSea.Companion.deserializer: JsonDeserializer<TokenInfo.WithMetadata.ContractMetadata.OpenSea>
    get() = JsonDeserializer<TokenInfo.WithMetadata.ContractMetadata.OpenSea> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObject ?: return@JsonDeserializer null

        return@JsonDeserializer TokenInfo.WithMetadata.ContractMetadata.OpenSea(
            floorPrice = jsonObj.get("floorPrice")?.asFloat,
            collectionName = jsonObj.get("collectionName")?.asString,
            safelistRequestStatus = jsonObj.get("safelistRequestStatus")?.asString,
            imageUrl = jsonObj.get("imageUrl")?.asString,
            description = jsonObj.get("description")?.asString,
            externalUrl = jsonObj.get("externalUrl")?.asString,
            twitterUsername = jsonObj.get("twitterUsername")?.asString,
            lastIngestedAt = jsonObj.get("lastIngestedAt")?.asString,
        )
    }