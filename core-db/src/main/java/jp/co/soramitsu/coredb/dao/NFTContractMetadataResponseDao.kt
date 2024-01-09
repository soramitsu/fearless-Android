package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import jp.co.soramitsu.coredb.model.NFTContractMetadataResponseLocal
import kotlin.math.abs

const val DEFAULT_CACHE_LIMIT = 250

@Dao
abstract class NFTContractMetadataResponseDao {

    @Query("SELECT * FROM nft_contract_metadata_response WHERE chainId = :chainId AND address IN(:contractAddresses)")
    abstract fun responses(chainId: String, contractAddresses: Set<String>): List<NFTContractMetadataResponseLocal>

    @Transaction
    open fun insert(responses: List<NFTContractMetadataResponseLocal>) {
        val overflow = DEFAULT_CACHE_LIMIT - rowsCount() - responses.size

        /* We want to store no more than DEFAULT_CACHE_LIMIT rows for caching purposes */
        if (overflow < 0) {
            removeOverflowedRows(abs(overflow))
        }

        val insertionRange = 0..responses.size.minus(1).coerceAtMost(DEFAULT_CACHE_LIMIT)

        for (response in responses.slice(insertionRange)) {
            insertResponse(response)
        }
    }

    @Query("SELECT COUNT(*) FROM nft_contract_metadata_response")
    protected abstract fun rowsCount(): Int

    @Query(
        """
            DELETE FROM nft_contract_metadata_response 
            WHERE address IN (
                SELECT address FROM nft_contract_metadata_response LIMIT :count
            )
        """
    )
    protected abstract fun removeOverflowedRows(count: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertResponse(response: NFTContractMetadataResponseLocal)

}