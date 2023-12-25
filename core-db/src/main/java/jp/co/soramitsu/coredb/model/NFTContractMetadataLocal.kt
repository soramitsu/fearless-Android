package jp.co.soramitsu.coredb.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import java.math.BigInteger

@Entity(
    tableName = "nft_contract_metadata_response",
    primaryKeys = ["chainId", "address"],
    indices = [Index(value = ["chainId", "address"], unique = true)]
)
class NFTContractMetadataResponseLocal(
    val chainId: String,
    val address: String,
    @Embedded
    val contractMetadata: NFTContractMetadataLocal?
)

class NFTContractMetadataLocal(
    val name: String?,
    val symbol: String?,
    val totalSupply: String?,
    val tokenType: String?,
    val contractDeployer: String?,
    val deployedBlockNumber: BigInteger?,
    @Embedded
    val openSea: NFTOpenSeaLocal?
)

class NFTOpenSeaLocal(
    val floorPrice: Float?,
    val collectionName: String?,
    val safelistRequestStatus: String?,
    val imageUrl: String?,
    val description: String?,
    val externalUrl: String?,
    val twitterUsername: String?,
    val lastIngestedAt: String?
)

