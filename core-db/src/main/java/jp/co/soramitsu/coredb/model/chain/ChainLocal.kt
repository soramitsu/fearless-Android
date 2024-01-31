package jp.co.soramitsu.coredb.model.chain

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chains")
class ChainLocal(
    @PrimaryKey val id: String,
    val paraId: String?,
    val parentId: String?,
    val rank: Int?,
    val name: String,
    val minSupportedVersion: String?,
    val icon: String,
    @Embedded
    val externalApi: ExternalApi?,
    val prefix: Int,
    val isEthereumBased: Boolean,
    val isTestNet: Boolean,
    val hasCrowdloans: Boolean,
    val supportStakingPool: Boolean,
    val isEthereumChain: Boolean
) {

    class ExternalApi(
        @Embedded(prefix = "staking_")
        val staking: Section?,

        @Embedded(prefix = "history_")
        val history: Section?,

        @Embedded(prefix = "crowdloans_")
        val crowdloans: Section?
    ) {

        class Section(val url: String, val type: String)
    }
}
