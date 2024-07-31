package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import jp.co.soramitsu.coredb.model.BasicPoolLocal
import jp.co.soramitsu.coredb.model.UserPoolJoinedLocal
import jp.co.soramitsu.coredb.model.UserPoolJoinedLocalNullable
import jp.co.soramitsu.coredb.model.UserPoolLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface PoolDao {

    companion object {
        private const val userPoolJoinBasic = """
            SELECT * FROM userpools left join allpools on userpools.userTokenIdBase=allpools.tokenIdBase and userpools.userTokenIdTarget=allpools.tokenIdTarget
        """
    }

    @Query(
        """
        select * from allpools where tokenIdBase=:base and tokenIdTarget=:target
    """
    )
    suspend fun getBasicPool(base: String, target: String): BasicPoolLocal?

    @Query("select * from allpools")
    suspend fun getBasicPools(): List<BasicPoolLocal>

    @Query("DELETE FROM userpools where accountAddress = :curAccount")
    suspend fun clearTable(curAccount: String)

    @Query("DELETE FROM allpools")
    suspend fun clearBasicTable()

    @Query(
        """
        $userPoolJoinBasic where userpools.accountAddress = :accountAddress
    """
    )
    fun subscribePoolsList(accountAddress: String): Flow<List<UserPoolJoinedLocal>>

    @Query(
        """
        select * from allpools a left join userpools u on 
        a.tokenIdBase = u.userTokenIdBase and a.tokenIdTarget = u.userTokenIdTarget 
        and u.accountAddress is not null 
        and u.accountAddress = :accountAddress
    """
    )
    fun subscribeAllPools(accountAddress: String?): Flow<List<UserPoolJoinedLocalNullable>>

    @Query(
        """
        $userPoolJoinBasic where userpools.accountAddress = :accountAddress 
    """
    )
    suspend fun getPoolsList(accountAddress: String): List<UserPoolJoinedLocal>

    @Query(
        """
        select * from allpools a 
        left join userpools u on a.tokenIdBase = u.userTokenIdBase 
                             and a.tokenIdTarget = u.userTokenIdTarget
                             and u.accountAddress is not null 
                             and u.accountAddress = :accountAddress
        where a.tokenIdBase=:baseTokenId and a.tokenIdTarget=:targetTokenId
    """
    )
    fun subscribePool(accountAddress: String, baseTokenId: String, targetTokenId: String): Flow<UserPoolJoinedLocalNullable>

    @Delete
    suspend fun deleteBasicPools(p: List<BasicPoolLocal>)

    @Upsert
    suspend fun insertBasicPools(pools: List<BasicPoolLocal>)

    @Upsert
    suspend fun insertUserPools(pools: List<UserPoolLocal>)

}
