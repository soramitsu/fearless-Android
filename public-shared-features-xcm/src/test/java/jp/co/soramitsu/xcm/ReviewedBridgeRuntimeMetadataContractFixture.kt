package jp.co.soramitsu.xcm

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.FixedArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Option
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u128
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction

/**
 * Audited metadata-native contract for the five executable call signatures in the reviewed
 * bridge catalog. These types intentionally retain the portable-metadata newtype and tuple
 * wrappers that the SCALE encoder sees; flattening either would make this fixture pass values
 * that production encoding rejects.
 */
internal object ReviewedBridgeRuntimeMetadataContractFixture {

    fun functionFor(call: XcmExtrinsicCall): MetadataFunction {
        val arguments = when (call.moduleName to call.callName) {
            "BridgeProxy" to "burn" -> listOf(
                "network_id" to soraBridgeNetworkIdType(),
                "asset_id" to soraAssetIdType(),
                "recipient" to soraBridgeRecipientType(),
                "amount" to u128
            )

            "SoraBridgeApp" to "burn" -> listOf(
                "network_id" to liberlandNetworkIdType(),
                "asset_id" to liberlandAssetIdType(),
                "recipient" to liberlandBridgeRecipientType(),
                "amount" to u128
            )

            "XcmPallet" to "reserveTransferAssets",
            "PolkadotXcm" to "reserveTransferAssets" -> listOf(
                "dest" to versionedLocationType(),
                "beneficiary" to versionedLocationType(),
                "assets" to versionedAssetsType(),
                "fee_asset_item" to u32
            )

            "XTokens" to "transfer" -> listOf(
                "currency_id" to acalaCurrencyIdType(),
                "amount" to u128,
                "dest" to versionedLocationType(),
                "dest_weight_limit" to weightLimitType()
            )

            else -> error("Unreviewed bridge runtime call ${call.moduleName}.${call.callName}")
        }

        return MetadataFunction(
            name = call.callName,
            arguments = arguments.map { (name, type) -> FunctionArgument(name, type) },
            documentation = emptyList(),
            index = 1 to 1
        )
    }

    fun versionedLocationType(): DictEnum {
        val accountId32 = Struct(
            "AccountId32",
            linkedMapOf(
                "network" to TypeReference(Option("Option<NetworkId>", TypeReference(Null))),
                "id" to TypeReference(FixedByteArray("AccountId", 32))
            )
        )
        val junction = DictEnum(
            "Junction",
            listOf(
                DictEnum.Entry("Parachain", TypeReference(u32)),
                DictEnum.Entry("AccountId32", TypeReference(accountId32))
            )
        )
        val interior = DictEnum(
            "Junctions",
            listOf(
                DictEnum.Entry("Here", TypeReference(Null)),
                DictEnum.Entry(
                    "X1",
                    TypeReference(FixedArray("JunctionsX1", 1, TypeReference(junction)))
                ),
                DictEnum.Entry(
                    "X2",
                    TypeReference(FixedArray("JunctionsX2", 2, TypeReference(junction)))
                )
            )
        )
        val location = Struct(
            "MultiLocation",
            linkedMapOf(
                "parents" to TypeReference(u8),
                "interior" to TypeReference(interior)
            )
        )
        return DictEnum(
            "VersionedMultiLocation",
            listOf(DictEnum.Entry("V3", TypeReference(location)))
        )
    }

    private fun soraBridgeNetworkIdType(): DictEnum {
        val substrateNetwork = DictEnum(
            "SubNetworkId",
            listOf(
                DictEnum.Entry("Polkadot", TypeReference(Null)),
                DictEnum.Entry("Kusama", TypeReference(Null)),
                DictEnum.Entry("Liberland", TypeReference(Null))
            )
        )
        return DictEnum(
            "GenericNetworkId",
            listOf(DictEnum.Entry("Sub", TypeReference(substrateNetwork)))
        )
    }

    private fun soraAssetIdType() = Struct(
        "AssetId32",
        linkedMapOf(
            "code" to TypeReference(FixedArray("AssetId32Bytes", 32, TypeReference(u8)))
        )
    )

    private fun soraBridgeRecipientType() = DictEnum(
        "BridgeRecipient",
        listOf(
            DictEnum.Entry("Parachain", TypeReference(versionedLocationType())),
            DictEnum.Entry(
                "Liberland",
                TypeReference(FixedArray("LiberlandAccountId", 32, TypeReference(u8)))
            )
        )
    )

    private fun liberlandNetworkIdType() = DictEnum(
        "SoraNetworkId",
        listOf(DictEnum.Entry("Mainnet", TypeReference(Null)))
    )

    private fun liberlandAssetIdType() = DictEnum(
        "LiberlandAssetId",
        listOf(
            DictEnum.Entry("LLD", TypeReference(Null)),
            DictEnum.Entry("Asset", TypeReference(u32))
        )
    )

    private fun liberlandBridgeRecipientType() = DictEnum(
        "LiberlandBridgeRecipient",
        listOf(
            DictEnum.Entry(
                "Sora",
                TypeReference(FixedArray("SoraAccountId", 32, TypeReference(u8)))
            )
        )
    )

    private fun versionedAssetsType(): DictEnum {
        val assetId = DictEnum(
            "AssetId",
            listOf(DictEnum.Entry("Concrete", TypeReference(locationType())))
        )
        val fungibility = DictEnum(
            "Fungibility",
            listOf(DictEnum.Entry("Fungible", TypeReference(u128)))
        )
        val multiAsset = Struct(
            "MultiAsset",
            linkedMapOf(
                "id" to TypeReference(assetId),
                "fun" to TypeReference(fungibility)
            )
        )
        val assets = Tuple(
            "Assets",
            listOf(TypeReference(Vec("Vec<MultiAsset>", TypeReference(multiAsset))))
        )
        return DictEnum(
            "VersionedMultiAssets",
            listOf(DictEnum.Entry("V3", TypeReference(assets)))
        )
    }

    private fun locationType(): Struct {
        val interior = DictEnum(
            "Junctions",
            listOf(DictEnum.Entry("Here", TypeReference(Null)))
        )
        return Struct(
            "MultiLocation",
            linkedMapOf(
                "parents" to TypeReference(u8),
                "interior" to TypeReference(interior)
            )
        )
    }

    private fun acalaCurrencyIdType(): DictEnum {
        val tokenSymbol = DictEnum(
            "TokenSymbol",
            listOf(DictEnum.Entry("ACA", TypeReference(Null)))
        )
        return DictEnum(
            "CurrencyId",
            listOf(DictEnum.Entry("Token", TypeReference(tokenSymbol)))
        )
    }

    private fun weightLimitType() = DictEnum(
        "WeightLimit",
        listOf(DictEnum.Entry("Unlimited", TypeReference(Null)))
    )
}
