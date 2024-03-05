package jp.co.soramitsu.nft.impl.data.model.utils

import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import jp.co.soramitsu.nft.data.models.Contract
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.TokenId
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse

private val JsonElement?.asJsonObjectNullable: JsonObject?
    get() = this as? JsonObject

internal val NFTResponse.UserOwnedContracts.Companion.deserializer: JsonDeserializer<NFTResponse.UserOwnedContracts>
    get() = JsonDeserializer { json, typeOfT, context ->
        val jsonObj = json?.asJsonObjectNullable ?: return@JsonDeserializer null

        return@JsonDeserializer NFTResponse.UserOwnedContracts(
            items = jsonObj.get("contracts")?.asJsonArray?.asSequence()?.mapNotNull { jsonElem ->
                context?.deserialize<ContractInfo>(
                    jsonElem, ContractInfo::class.java
                )
            } ?: emptySequence(),
            nextPage = jsonObj.get("pageKey")?.asString
        )
    }

internal val ContractInfo.Companion.deserializer: JsonDeserializer<ContractInfo>
    get() = JsonDeserializer { json, typeOfT, context ->
        val jsonObj = json?.asJsonObjectNullable ?: return@JsonDeserializer null

        return@JsonDeserializer ContractInfo(
            address = jsonObj.get("address")?.asString,
            totalBalance = jsonObj.get("totalBalance")?.asInt,
            numDistinctTokensOwned = jsonObj.get("numDistinctTokensOwned")?.asInt,
            isSpam = jsonObj.get("isSpam")?.asBoolean,
            tokenId = jsonObj.get("tokenId")?.asString,
            name = jsonObj.get("name")?.asString,
            title = jsonObj.get("title")?.asString,
            symbol = jsonObj.get("symbol")?.asString,
            totalSupply = jsonObj.get("totalSupply")?.asInt,
            tokenType = jsonObj.get("tokenType")?.asString,
            contractDeployer = jsonObj.get("contractDeployer")?.asString,
            deployedBlockNumber = jsonObj.get("deployedBlockNumber")?.asString,
            openSea = context?.deserialize<TokenInfo.ContractMetadata.OpenSea?>(
                jsonObj.get("opensea"), TokenInfo.ContractMetadata.OpenSea::class.java
            ),
            media = jsonObj.get("media")?.asJsonArray?.mapNotNull { mediaElement ->
                context?.deserialize<TokenInfo.Media?>(
                    mediaElement, TokenInfo.Media::class.java
                )
            }
        )
    }

internal val NFTResponse.TokensCollection.Companion.deserializer: JsonDeserializer<NFTResponse.TokensCollection>
    get() = JsonDeserializer<NFTResponse.TokensCollection> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObjectNullable ?: return@JsonDeserializer null

        val tokensJsonObj = when {
            jsonObj.has("nfts") ->
                jsonObj.get("nfts")

            jsonObj.has("ownedNfts") ->
                jsonObj.get("ownedNfts")

            else -> null
        }

        return@JsonDeserializer NFTResponse.TokensCollection(
            items = tokensJsonObj?.asJsonArray?.asSequence()?.mapNotNull { jsonElem ->
                context?.deserialize<TokenInfo>(
                    jsonElem, TokenInfo::class.java
                )
            } ?: emptySequence(),
            nextPage = jsonObj.get("nextToken")?.asString
        )
    }

internal val NFTResponse.TokenOwners.Companion.deserializer: JsonDeserializer<NFTResponse.TokenOwners>
    get() = JsonDeserializer<NFTResponse.TokenOwners> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObjectNullable ?: return@JsonDeserializer null

        return@JsonDeserializer NFTResponse.TokenOwners(
            ownersList = jsonObj.get("owners")?.asJsonArray?.mapNotNull { jsonElem ->
                return@mapNotNull jsonElem?.asString
            }.orEmpty()
        )
    }

internal val Contract.Companion.deserializer: JsonDeserializer<Contract>
    get() = JsonDeserializer<Contract> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObjectNullable ?: return@JsonDeserializer null

        return@JsonDeserializer Contract(
            address = jsonObj.get("address")?.asString
        )
    }

internal val TokenId.Companion.deserializer: JsonDeserializer<TokenId>
    get() = JsonDeserializer<TokenId> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObjectNullable ?: return@JsonDeserializer null

        return@JsonDeserializer TokenId(
            tokenId = jsonObj.get("tokenId")?.asString,
            tokenMetadata = context?.deserialize<TokenId.TokenMetadata>(
                jsonObj.get("tokenMetadata"), TokenId.TokenMetadata::class.java
            )
        )
    }

internal val TokenId.TokenMetadata.Companion.deserializer: JsonDeserializer<TokenId.TokenMetadata>
    get() = JsonDeserializer<TokenId.TokenMetadata> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObjectNullable ?: return@JsonDeserializer null

        return@JsonDeserializer TokenId.TokenMetadata(
            tokenType = jsonObj.get("tokenType")?.asString,
        )
    }

internal val TokenInfo.Companion.deserializer: JsonDeserializer<TokenInfo>
    get() = JsonDeserializer<TokenInfo> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObjectNullable ?: return@JsonDeserializer null

        return@JsonDeserializer TokenInfo(
            contract = context?.deserialize<Contract?>(
                jsonObj.get("contract"), Contract::class.java
            ),
            id = context?.deserialize<TokenId?>(
                jsonObj.get("id"), TokenId::class.java
            ),
            title = jsonObj.get("title")?.asString,
            balance = jsonObj.get("balance")?.asString,
            description = jsonObj.get("description")?.asString,
            media = jsonObj.get("media")?.asJsonArray?.mapNotNull { mediaElement ->
                context?.deserialize<TokenInfo.Media?>(
                    mediaElement, TokenInfo.Media::class.java
                )
            },
            metadata = context?.deserialize<TokenInfo.TokenMetadata?>(
                jsonObj.get("metadata"), TokenInfo.TokenMetadata::class.java
            ),
            contractMetadata = context?.deserialize<TokenInfo.ContractMetadata?>(
                jsonObj.get("contractMetadata"), TokenInfo.ContractMetadata::class.java
            ),
            spamInfo = context?.deserialize<TokenInfo.SpamInfo?>(
                jsonObj.get("spamInfo"), TokenInfo.SpamInfo::class.java
            )
        )
    }

internal val TokenInfo.Media.Companion.deserializer: JsonDeserializer<TokenInfo.Media>
    get() = JsonDeserializer<TokenInfo.Media> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObjectNullable ?: return@JsonDeserializer null

        return@JsonDeserializer TokenInfo.Media(
            gateway = jsonObj.get("gateway")?.asString,
            thumbnail = jsonObj.get("thumbnail")?.asString,
            raw = jsonObj.get("raw")?.asString,
            format = jsonObj.get("format")?.asString,
            bytes = jsonObj.get("bytes")?.asBigInteger,
        )
    }

internal val TokenInfo.TokenMetadata.Companion.deserializer: JsonDeserializer<TokenInfo.TokenMetadata>
    get() = JsonDeserializer<TokenInfo.TokenMetadata> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObjectNullable ?: return@JsonDeserializer null

        return@JsonDeserializer TokenInfo.TokenMetadata(
            name = jsonObj.get("name")?.asString,
            description = jsonObj.get("description")?.asString,
            backgroundColor = jsonObj.get("backgroundColor")?.asString,
            poster = jsonObj.get("poster")?.asString,
            image = jsonObj.get("image")?.asString,
            externalUrl = jsonObj.get("externalUrl")?.asString,
        )
    }

internal val TokenInfo.ContractMetadata.Companion.deserializer: JsonDeserializer<TokenInfo.ContractMetadata>
    get() = JsonDeserializer<TokenInfo.ContractMetadata> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObjectNullable ?: return@JsonDeserializer null

        return@JsonDeserializer TokenInfo.ContractMetadata(
            name = jsonObj.get("name")?.asString,
            symbol = jsonObj.get("symbol")?.asString,
            totalSupply = jsonObj.get("totalSupply")?.asString,
            tokenType = jsonObj.get("tokenType")?.asString,
            contractDeployer = jsonObj.get("contractDeployer")?.asString,
            deployedBlockNumber = jsonObj.get("deployedBlockNumber")?.asBigInteger,
            openSea = context?.deserialize<TokenInfo.ContractMetadata.OpenSea?>(
                jsonObj.get("openSea"), TokenInfo.ContractMetadata.OpenSea::class.java
            ),
        )
    }

internal val TokenInfo.ContractMetadata.OpenSea.Companion.deserializer: JsonDeserializer<TokenInfo.ContractMetadata.OpenSea>
    get() = JsonDeserializer<TokenInfo.ContractMetadata.OpenSea> { json, typeOfT, context ->
        val jsonObj = json?.asJsonObjectNullable ?: return@JsonDeserializer null

        return@JsonDeserializer TokenInfo.ContractMetadata.OpenSea(
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
