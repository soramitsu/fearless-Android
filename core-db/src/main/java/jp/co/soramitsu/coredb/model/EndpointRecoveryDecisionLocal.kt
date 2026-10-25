package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** No foreign key, projection, activation or authorization. Read through the bounded decision store. */
@Entity(tableName = "endpoint_recovery_decisions", indices = [Index(value = ["sourceKind", "sourceRowId"], unique = true)])
data class EndpointRecoveryDecisionLocal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val revision: Long,
    val sourceKind: String,
    val sourceRowId: Long,
    val sourceRevision: Long?,
    val sourceFingerprint: String,
    val candidateName: String,
    val candidateUrl: String,
    val targetChainId: String,
    val targetIdentityFingerprint: String,
    val targetFamily: String,
    val targetExpectedGenesis: String?,
    val state: String
) {
    override fun toString(): String = "EndpointRecoveryDecisionLocal(id=$id, revision=$revision)"
}
