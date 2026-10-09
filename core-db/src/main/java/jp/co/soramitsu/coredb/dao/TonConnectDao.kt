package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.coredb.model.TonConnectionReadProjection
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TonConnectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTonConnection(connection: TonConnectionLocal)

    @Query(
        "SELECT " + BOUNDED_TON_CONNECTION_PROJECTION +
            " FROM ton_connection " +
            "WHERE metaId = :metaId AND source = :source " +
            "LIMIT 257"
    )
    abstract fun observeTonConnections(
        metaId: Long,
        source: ConnectionSource
    ): Flow<List<TonConnectionReadProjection>>

    @Query(
        "SELECT " + BOUNDED_TON_CONNECTION_PROJECTION +
            " FROM ton_connection " +
            "WHERE metaId = :metaId AND source = :source " +
            "LIMIT 257"
    )
    abstract suspend fun getTonConnections(
        metaId: Long,
        source: ConnectionSource
    ): List<TonConnectionReadProjection>

    @Query(
        "SELECT " + BOUNDED_TON_CONNECTION_PROJECTION +
            " FROM ton_connection " +
            "WHERE metaId = :metaId AND url = :url AND source = :source"
    )
    abstract suspend fun getTonConnection(
        metaId: Long,
        url: String,
        source: ConnectionSource
    ): TonConnectionReadProjection?

    @Query(
        "SELECT " + BOUNDED_TON_CONNECTION_PROJECTION +
            " FROM ton_connection WHERE clientId = :clientId " +
            "LIMIT 257"
    )
    abstract suspend fun getTonConnectionsByClientId(
        clientId: String
    ): List<TonConnectionReadProjection>

    @Query(
        "SELECT EXISTS(" +
            "SELECT 1 FROM ton_connection WHERE clientId = :clientId LIMIT 1" +
            ")"
    )
    abstract suspend fun hasTonConnectionsByClientId(
        clientId: String
    ): Boolean

    @Query(
        "DELETE FROM ton_connection " +
            "WHERE metaId = :metaId AND url = :url AND source = :source"
    )
    abstract suspend fun deleteTonConnection(
        metaId: Long,
        url: String,
        source: ConnectionSource
    )
}

/**
 * Every text expression exposed to CursorWindow is guarded by both its SQLite
 * storage class and its maximum UTF-8 representation. The repeated predicate
 * is intentional: [TonConnectionReadProjection.rowWithinBounds] tells Kotlin
 * whether any returned sentinel represents corrupt storage.
 */
private const val BOUNDED_TON_CONNECTION_PROJECTION =
    """
    CASE
        WHEN typeof(metaId) = 'integer' AND metaId > 0
        THEN metaId ELSE 0
    END AS metaId,
    CASE
        WHEN typeof(clientId) = 'text'
            AND length(clientId) <= 64
            AND length(CAST(clientId AS BLOB)) <= 64
        THEN clientId ELSE ''
    END AS clientId,
    CASE
        WHEN typeof(name) = 'text'
            AND length(name) <= 512
            AND length(CAST(name AS BLOB)) <= 2048
        THEN name ELSE ''
    END AS name,
    CASE
        WHEN typeof(icon) = 'text'
            AND length(icon) <= 4096
            AND length(CAST(icon AS BLOB)) <= 16384
        THEN icon ELSE ''
    END AS icon,
    CASE
        WHEN typeof(url) = 'text'
            AND length(url) <= 4096
            AND length(CAST(url AS BLOB)) <= 16384
        THEN url ELSE ''
    END AS url,
    CASE
        WHEN typeof(source) = 'text'
            AND source IN ('QR', 'WEB')
        THEN source ELSE ''
    END AS source,
    CASE WHEN typeof(clientId) = 'text'
        THEN length(CAST(clientId AS BLOB)) ELSE -1
    END AS clientIdUtf8Bytes,
    CASE WHEN typeof(name) = 'text'
        THEN length(CAST(name AS BLOB)) ELSE -1
    END AS nameUtf8Bytes,
    CASE WHEN typeof(icon) = 'text'
        THEN length(CAST(icon AS BLOB)) ELSE -1
    END AS iconUtf8Bytes,
    CASE WHEN typeof(url) = 'text'
        THEN length(CAST(url AS BLOB)) ELSE -1
    END AS urlUtf8Bytes,
    CASE WHEN typeof(source) = 'text'
        THEN length(CAST(source AS BLOB)) ELSE -1
    END AS sourceUtf8Bytes,
    CASE
        WHEN typeof(metaId) = 'integer'
            AND metaId > 0
            AND typeof(clientId) = 'text'
            AND length(clientId) <= 64
            AND length(CAST(clientId AS BLOB)) <= 64
            AND typeof(name) = 'text'
            AND length(name) <= 512
            AND length(CAST(name AS BLOB)) <= 2048
            AND typeof(icon) = 'text'
            AND length(icon) <= 4096
            AND length(CAST(icon AS BLOB)) <= 16384
            AND typeof(url) = 'text'
            AND length(url) <= 4096
            AND length(CAST(url AS BLOB)) <= 16384
            AND typeof(source) = 'text'
            AND source IN ('QR', 'WEB')
        THEN 1 ELSE 0
    END AS rowWithinBounds
    """
//https://ton-explorer.dev.sora2.tachi.soramitsu.co.jp/
//https://ton-connect.example.fearless.soramitsu.co.jp/
//https://ton-connect.github.io/demo-dapp/
