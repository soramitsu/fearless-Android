package jp.co.soramitsu.common.model

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.storage.Preferences

data class AssetMetadataDescriptor(
    val trust: AssetMetadataTrust,
    val source: AssetMetadataSource
)

/** Durable AssetKey-bound trust/provenance. Trust and source are intentionally independent. */
@Singleton
class AssetMetadataDescriptorStore @Inject constructor(
    private val preferences: Preferences
) {
    fun record(identity: CanonicalAssetIdentity, descriptor: AssetMetadataDescriptor) {
        preferences.putString(key(identity, "trust"), descriptor.trust.name)
        preferences.putString(key(identity, "source"), descriptor.source.name)
    }

    fun get(identity: CanonicalAssetIdentity): AssetMetadataDescriptor? {
        val trust = preferences.getString(key(identity, "trust"))
            ?.let { stored -> AssetMetadataTrust.entries.firstOrNull { it.name == stored } }
            ?: return null
        val source = preferences.getString(key(identity, "source"))
            ?.let { stored -> AssetMetadataSource.entries.firstOrNull { it.name == stored } }
            ?: return null
        return AssetMetadataDescriptor(trust, source)
    }

    private fun key(identity: CanonicalAssetIdentity, field: String): String {
        return "asset_metadata.${identity.serialized}.$field"
    }
}
