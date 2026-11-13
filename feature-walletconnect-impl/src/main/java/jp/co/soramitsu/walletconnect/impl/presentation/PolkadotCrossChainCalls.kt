package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.web3j.crypto.Keys
import java.math.BigInteger

/** A call that moves funds or sends XCM to another network, as read from the decoded call. */
internal data class CrossChainCall(
    /** The pallet and function, such as `XcmPallet.limited_teleport_assets`. */
    val name: String,
    /** Where it sends to; null when the call names no destination (`execute` decides it in XCM) or names none readable. */
    val destination: CrossChainDestination?,
    /** Who receives the funds there; null when XCM instructions decide it or it cannot be read. */
    val beneficiary: String?,
    val amounts: List<CrossChainAmount>,
    /** `execute`, `send` and `transfer_assets_using_type_and_then` run XCM instructions the dApp chose. */
    val isHighRisk: Boolean,
    /** Why this transfer can no longer arrive; the wallet then refuses to sign. */
    val retirement: CrossChainRetirement? = null
)

/** Why a cross-chain transfer would lose the funds; [reason] is what the dApp is told. */
internal sealed class CrossChainRetirement(val reason: String) {
    /** A parachain or SORA bridge network, named by [destination], that no longer delivers transfers. */
    class Destination(val destination: String) : CrossChainRetirement("$destination no longer accepts these transfers")

    /** A relay chain's reserve transfer of its own token, whose reserve moved to Asset Hub. */
    data object RelayReserve : CrossChainRetirement("the relay chain is no longer the reserve of its token since the Asset Hub migration")
}

/** Where a cross-chain call sends to, as far as the wallet can place it. */
internal data class CrossChainDestination(
    /** The catalog network, when the wallet knows it. */
    val chain: Chain? = null,
    /** The parachain id, when the catalog does not name the parachain. */
    val paraId: BigInteger? = null,
    /** How the call names the destination otherwise, such as a bridge network or a raw location. */
    val text: String? = null
)

/** An amount of one asset; [asset] is null when the wallet cannot tell which asset it is. */
internal data class CrossChainAmount(val planks: BigInteger?, val asset: Asset?, val description: String)

/**
 * Finds the cross-chain calls a Substrate call dispatches, including calls nested in `utility.batch*`, `proxy.proxy*`,
 * `multisig.as_multi*` and any other call that wraps calls, and refuses destinations known to no longer deliver:
 *
 * - SORA `bridgeProxy.burn` toward Polkadot, Kusama, Rococo, PolkadotAcala and PolkadotAstar (the SORA parachains
 *   stopped producing blocks) and toward Liberland (SORA to Liberland transfers stopped being delivered);
 * - reserve transfers of a relay chain's own token from the relay chain, whose reserve moved to Asset Hub;
 * - XCM to parachains that no longer accept transfers: Moonbeam 2004, Parallel 2012 and SORA 2025 on Polkadot,
 *   Moonriver 2023 and SORA 2011 on Kusama.
 *
 * [chains] is the catalog, used only to name destinations and assets; refusals depend on chain ids alone.
 */
internal class PolkadotCrossChainCalls(
    private val origin: Chain,
    private val chains: List<Chain> = emptyList()
) {
    private val GenericCall.Instance.name: String
        get() = "${module.name}.${function.name}"

    private val DictEnum.Entry<*>.isAccount: Boolean
        get() = name == ACCOUNT_ID_32 || name == ACCOUNT_KEY_20

    /** Refuses with [PolkadotTransactionRefusedException] a call nested deeper than the wallet checks. */
    fun read(call: GenericCall.Instance): List<CrossChainCall> =
        dispatched(call, depth = 0).mapNotNull(::crossChainCall)

    private fun dispatched(call: GenericCall.Instance, depth: Int): List<GenericCall.Instance> {
        requireTransaction(depth <= MAX_NESTING, PolkadotRefusal.UndecodableCall) { "calls nested too deeply" }
        return listOf(call) + call.arguments.values.flatMap { nestedCalls(it) }.flatMap { dispatched(it, depth + 1) }
    }

    private fun nestedCalls(value: Any?): List<GenericCall.Instance> = when (value) {
        is GenericCall.Instance -> listOf(value)
        is List<*> -> value.flatMap(::nestedCalls)
        is Struct.Instance -> value.mapping.values.flatMap(::nestedCalls)
        is DictEnum.Entry<*> -> nestedCalls(value.value)
        else -> emptyList()
    }

    private fun crossChainCall(call: GenericCall.Instance): CrossChainCall? {
        val pallet = call.module.name.normalized()
        val function = call.function.name.normalized()
        return when {
            pallet in XCM_PALLETS && function in XCM_TRANSFERS -> xcmTransfer(call, pallet, function)
            pallet in XCM_PALLETS && function in XCM_PROGRAMS -> xcmProgram(call, pallet, function)
            pallet == X_TOKENS && function.startsWith(TRANSFER) -> xTokensTransfer(call)
            pallet == BRIDGE_PROXY && function == BURN -> soraBridgeBurn(call)
            pallet == ETH_BRIDGE && function == TRANSFER_TO_SIDECHAIN -> ethBridgeTransfer(call)
            pallet == SORA_BRIDGE_APP && function == BURN -> liberlandBridgeBurn(call)
            else -> null
        }
    }

    // teleport_assets, reserve_transfer_assets, transfer_assets and their limited forms.
    private fun xcmTransfer(
        call: GenericCall.Instance,
        pallet: String,
        function: String
    ): CrossChainCall {
        val placed = location(call.argument(DEST))?.let { placed(it, pallet) }
        val assets = assets(call.argument(ASSETS), pallet)
        val relayReserve = isRelay(pallet) && assets.any { it.isLocalNative } &&
            (function in RESERVE_TRANSFERS || function == TRANSFER_ASSETS && placed?.isNonSystemParachain == true)
        return CrossChainCall(
            name = call.name,
            destination = placed?.destination,
            beneficiary = location(call.argument(BENEFICIARY))?.let(::account),
            amounts = assets.map { it.amount },
            isHighRisk = false,
            retirement = placed?.retirement ?: CrossChainRetirement.RelayReserve.takeIf { relayReserve }
        )
    }

    // send, execute and transfer_assets_using_type_and_then run instructions the dApp wrote.
    private fun xcmProgram(
        call: GenericCall.Instance,
        pallet: String,
        function: String
    ): CrossChainCall {
        val program = instructions(call.argument(MESSAGE) ?: call.argument(CUSTOM_XCM_ON_DEST))
        // An executed program's own instructions name destinations relative to this chain; a sent one runs elsewhere.
        val programDestinations = if (function == EXECUTE) programDestinations(program) else emptyList()
        val destinations = (listOfNotNull(location(call.argument(DEST))) + programDestinations).map { placed(it, pallet) }
        val withdrawn = program.filter { it.name == WITHDRAW_ASSET }.flatMap { assets(it.value, pallet) }
        val assets = assets(call.argument(ASSETS), pallet) + withdrawn
        val localReserve = (call.argument(ASSETS_TRANSFER_TYPE) as? DictEnum.Entry<*>)?.name == LOCAL_RESERVE
        val relayReserve = isRelay(pallet) && localReserve && assets.any { it.isLocalNative }
        val retired = destinations.firstNotNullOfOrNull { it.retirement }
        return CrossChainCall(
            name = call.name,
            destination = destinations.firstOrNull()?.destination,
            beneficiary = beneficiaryOf(program),
            amounts = assets.map { it.amount },
            isHighRisk = true,
            retirement = retired ?: CrossChainRetirement.RelayReserve.takeIf { relayReserve }
        )
    }

    // ORML xTokens: the destination location ends with the beneficiary's account.
    private fun xTokensTransfer(call: GenericCall.Instance): CrossChainCall {
        val dest = location(call.argument(DEST))
        val placed = dest?.let {
            placed(XcmLocation(it.parents, it.junctions.filter { junction -> !junction.isAccount }), X_TOKENS)
        }
        val currency = call.argument(CURRENCY_ID)
        val amounts = when {
            currency != null -> listOf(CrossChainAmount(call.argument(AMOUNT) as? BigInteger, null, render(currency)))
            call.argument(CURRENCIES) != null -> (call.argument(CURRENCIES) as? List<*>).orEmpty().map { pair ->
                val (id, amount) = (pair as? List<*>).orEmpty() + listOf(null, null)
                CrossChainAmount(amount as? BigInteger, null, render(id))
            }
            else -> assets(call.argument(ASSET) ?: call.argument(ASSETS), X_TOKENS).map { it.amount }
        }
        return CrossChainCall(
            name = call.name,
            destination = placed?.destination,
            beneficiary = dest?.let(::account),
            amounts = amounts,
            isHighRisk = false,
            retirement = placed?.retirement
        )
    }

    // SORA bridgeProxy.burn(network_id, asset_id, recipient, amount).
    private fun soraBridgeBurn(call: GenericCall.Instance): CrossChainCall {
        val network = call.argument(NETWORK_ID) as? DictEnum.Entry<*>
        val subNetwork = (network?.value as? DictEnum.Entry<*>)?.name?.takeIf { network.name == SUB_NETWORK }
        return CrossChainCall(
            name = call.name,
            destination = CrossChainDestination(text = network?.let(::networkName) ?: render(call.argument(NETWORK_ID))),
            beneficiary = bridgeAccount(call.argument(RECIPIENT)),
            amounts = listOf(soraAmount(call.argument(ASSET_ID), call.argument(AMOUNT))),
            isHighRisk = false,
            retirement = subNetwork?.takeIf { it in RETIRED_SORA_NETWORKS }?.let { CrossChainRetirement.Destination(it) }
        )
    }

    // SORA ethBridge.transfer_to_sidechain(asset_id, to, amount, network_id): the legacy Ethereum bridge.
    private fun ethBridgeTransfer(call: GenericCall.Instance): CrossChainCall = CrossChainCall(
        name = call.name,
        destination = CrossChainDestination(text = "$ETHEREUM_BRIDGE ${render(call.argument(NETWORK_ID))}"),
        beneficiary = bytesOf(call.argument(TO))?.let(::accountText),
        amounts = listOf(soraAmount(call.argument(ASSET_ID), call.argument(AMOUNT))),
        isHighRisk = false
    )

    // Liberland soraBridgeApp.burn(network_id, asset_id, recipient, amount): Liberland to SORA.
    private fun liberlandBridgeBurn(call: GenericCall.Instance): CrossChainCall {
        val network = call.argument(NETWORK_ID)
        val toSora = network is DictEnum.Entry<*> && network.name == SORA_MAINNET
        return CrossChainCall(
            name = call.name,
            destination = CrossChainDestination(text = if (toSora) SORA else render(network)),
            beneficiary = bridgeAccount(call.argument(RECIPIENT)),
            amounts = listOf(CrossChainAmount(call.argument(AMOUNT) as? BigInteger, null, render(call.argument(ASSET_ID)))),
            isHighRisk = false
        )
    }

    private class Placed(val destination: CrossChainDestination, val retirement: CrossChainRetirement?, val isNonSystemParachain: Boolean)

    // Places a location relative to this chain in its relay chain's consensus, when it has one.
    private fun placed(location: XcmLocation, pallet: String): Placed {
        val relayChainId = if (isRelay(pallet)) origin.id else origin.parentId
        val paraId = location.junctions.firstOrNull { it.name == PARACHAIN }?.value as? BigInteger
        // From the relay chain its parachains are children; from a parachain they are siblings under the relay.
        val relayParents = if (relayChainId == origin.id) 0 else 1
        val viaOtherConsensus = location.junctions.any { it.name == GLOBAL_CONSENSUS }
        val sameConsensus = relayChainId != null && location.parents == relayParents && !viaOtherConsensus
        if (!sameConsensus) return Placed(CrossChainDestination(text = location.text), null, false)
        val chain = chains.firstOrNull { candidate ->
            if (paraId == null) candidate.id == relayChainId else candidate.parentId == relayChainId && candidate.paraId == paraId.toString()
        }
        val retired = paraId?.let { RETIRED_PARACHAINS[relayChainId]?.get(it.toInt()) }
        val destination = when {
            chain != null -> CrossChainDestination(chain = chain)
            paraId != null -> CrossChainDestination(paraId = paraId)
            else -> CrossChainDestination(text = location.text)
        }
        return Placed(
            destination = destination,
            retirement = retired?.let { CrossChainRetirement.Destination(it) },
            isNonSystemParachain = paraId != null && paraId >= FIRST_NON_SYSTEM_PARACHAIN
        )
    }

    private fun isRelay(pallet: String) = pallet == XCM_PALLET && origin.parentId == null

    private class Located(val amount: CrossChainAmount, val isLocalNative: Boolean)

    private fun assets(value: Any?, pallet: String): List<Located> {
        val list = when (val unwrapped = unwrapVersion(value)) {
            is List<*> -> unwrapped
            is Struct.Instance -> listOf(unwrapped)
            else -> return emptyList()
        }
        return list.mapNotNull { item ->
            val asset = item as? Struct.Instance ?: return@mapNotNull null
            val id = asset.mapping[ID].let { if (it is DictEnum.Entry<*> && it.name == CONCRETE) it.value else it }
            val location = location(id)
            val planks = (asset.mapping[FUN] as? DictEnum.Entry<*>)?.takeIf { it.name == FUNGIBLE }?.value as? BigInteger
            val known = location?.let { knownAsset(it, pallet) }
            Located(
                amount = CrossChainAmount(planks, known, location?.text ?: render(id)),
                isLocalNative = location != null && location.parents == 0 && location.junctions.isEmpty()
            )
        }
    }

    // The origin's own token, or its relay chain's token from a parachain.
    private fun knownAsset(location: XcmLocation, pallet: String): Asset? = when {
        location.junctions.isNotEmpty() -> null
        location.parents == 0 -> origin.utilityAsset
        location.parents == 1 && !isRelay(pallet) -> chains.firstOrNull { it.id == origin.parentId }?.utilityAsset
        else -> null
    }

    private fun soraAmount(assetId: Any?, amount: Any?): CrossChainAmount {
        val id = bytesOf(assetId ?: return CrossChainAmount(amount as? BigInteger, null, render(null)))
            ?: bytesOf((assetId as? Struct.Instance)?.mapping?.get(CODE))
        val hex = id?.toHexString(withPrefix = true)
        val asset = origin.assets.firstOrNull { hex != null && it.currencyId.equals(hex, ignoreCase = true) }
        return CrossChainAmount(amount as? BigInteger, asset, hex ?: render(assetId))
    }

    // The first DepositAsset beneficiary, including in the instructions a program sends on to another chain.
    private fun beneficiaryOf(program: List<DictEnum.Entry<*>>): String? = program.firstNotNullOfOrNull { instruction ->
        val fields = (instruction.value as? Struct.Instance)?.mapping.orEmpty()
        val nested = fields[XCM]?.let { beneficiaryOf(instructions(it)) }
        if (instruction.name == DEPOSIT_ASSET) fields[BENEFICIARY]?.let(::location)?.let(::account) else nested
    }

    private fun programDestinations(program: List<DictEnum.Entry<*>>): List<XcmLocation> {
        val fields = program.map { (it.value as? Struct.Instance)?.mapping.orEmpty() }
        val named = fields.flatMap { instruction -> listOf(DEST, DESTINATION, RESERVE).mapNotNull(instruction::get) }
        return named.mapNotNull(::location)
    }

    private fun instructions(value: Any?): List<DictEnum.Entry<*>> =
        (unwrapVersion(value) as? List<*>).orEmpty().filterIsInstance<DictEnum.Entry<*>>()

    private fun account(location: XcmLocation): String? {
        val fields = (location.junctions.lastOrNull { it.isAccount }?.value as? Struct.Instance)?.mapping.orEmpty()
        return bytesOf(fields[ID] ?: fields[KEY])?.let(::accountText)
    }

    // GenericAccount: Sora, Liberland and EVM accounts, or a Parachain location.
    private fun bridgeAccount(value: Any?): String? {
        val entry = value as? DictEnum.Entry<*> ?: return null
        return location(entry.value)?.let(::account) ?: bytesOf(entry.value)?.let(::accountText) ?: render(entry)
    }

    // A 32-byte account in the origin's address format, a 20-byte one as an EIP-55 address.
    private fun accountText(bytes: ByteArray): String = when (bytes.size) {
        ACCOUNT_ID_SIZE -> runCatching { SS58Encoder.encode(bytes, origin.addressPrefix.toShort()) }
            .getOrElse { bytes.toHexString(withPrefix = true) }
        ETHEREUM_ADDRESS_SIZE -> Keys.toChecksumAddress(bytes.toHexString(withPrefix = true))
        else -> bytes.toHexString(withPrefix = true)
    }

    private fun networkName(network: DictEnum.Entry<*>): String = when (val inner = network.value) {
        is DictEnum.Entry<*> -> inner.name
        null -> network.name
        else -> "${network.name} ${render(inner)}"
    }

    private fun GenericCall.Instance.argument(name: String): Any? =
        arguments.entries.firstOrNull { it.key.normalized() == name }?.value

    private companion object {
        const val MAX_NESTING = 16
        const val ACCOUNT_ID_SIZE = 32
        const val ETHEREUM_ADDRESS_SIZE = 20
        val FIRST_NON_SYSTEM_PARACHAIN: BigInteger = BigInteger.valueOf(2000)

        // Pallet and function names lower-cased without underscores, so polkadot.js and metadata spellings match.
        const val XCM_PALLET = "xcmpallet"
        val XCM_PALLETS = setOf(XCM_PALLET, "polkadotxcm")
        val RESERVE_TRANSFERS = setOf("reservetransferassets", "limitedreservetransferassets")
        const val TRANSFER_ASSETS = "transferassets"
        val XCM_TRANSFERS = RESERVE_TRANSFERS + setOf("teleportassets", "limitedteleportassets", TRANSFER_ASSETS)
        const val EXECUTE = "execute"
        val XCM_PROGRAMS = setOf("send", EXECUTE, "transferassetsusingtypeandthen")
        const val X_TOKENS = "xtokens"
        const val TRANSFER = "transfer"
        const val BRIDGE_PROXY = "bridgeproxy"
        const val ETH_BRIDGE = "ethbridge"
        const val SORA_BRIDGE_APP = "sorabridgeapp"
        const val BURN = "burn"
        const val TRANSFER_TO_SIDECHAIN = "transfertosidechain"

        // Argument names, normalized the same way.
        const val DEST = "dest"
        const val BENEFICIARY = "beneficiary"
        const val ASSETS = "assets"
        const val ASSET = "asset"
        const val MESSAGE = "message"
        const val CUSTOM_XCM_ON_DEST = "customxcmondest"
        const val ASSETS_TRANSFER_TYPE = "assetstransfertype"
        const val CURRENCY_ID = "currencyid"
        const val CURRENCIES = "currencies"
        const val AMOUNT = "amount"
        const val NETWORK_ID = "networkid"
        const val ASSET_ID = "assetid"
        const val RECIPIENT = "recipient"
        const val TO = "to"

        // Decoded value names, as the runtime metadata spells them.
        const val ID = "id"
        const val KEY = "key"
        const val FUN = "fun"
        const val CODE = "code"
        const val DESTINATION = "destination"
        const val RESERVE = "reserve"
        const val CONCRETE = "Concrete"
        const val FUNGIBLE = "Fungible"
        const val PARACHAIN = "Parachain"
        const val GLOBAL_CONSENSUS = "GlobalConsensus"
        const val ACCOUNT_ID_32 = "AccountId32"
        const val ACCOUNT_KEY_20 = "AccountKey20"
        const val WITHDRAW_ASSET = "WithdrawAsset"
        const val DEPOSIT_ASSET = "DepositAsset"
        const val XCM = "xcm"
        const val LOCAL_RESERVE = "LocalReserve"
        const val SUB_NETWORK = "Sub"
        const val SORA_MAINNET = "Mainnet"
        const val SORA = "SORA"
        const val ETHEREUM_BRIDGE = "Ethereum bridge network"

        const val POLKADOT = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val KUSAMA = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"

        // Parachains that stopped accepting transfers, by relay chain.
        val RETIRED_PARACHAINS = mapOf(
            POLKADOT to mapOf(2004 to "Moonbeam", 2012 to "Parallel", 2025 to "SORA"),
            KUSAMA to mapOf(2023 to "Moonriver", 2011 to "SORA")
        )

        // SORA bridge networks whose transfers are no longer delivered.
        val RETIRED_SORA_NETWORKS = setOf("Polkadot", "Kusama", "Rococo", "PolkadotAcala", "PolkadotAstar", "Liberland")

        fun String.normalized(): String = lowercase().replace("_", "")
    }
}

/** An XCM location in any version's shape: V3 `X1(junction)` and V4/V5 `X1([junction])` alike. */
internal class XcmLocation(val parents: Int, val junctions: List<DictEnum.Entry<*>>) {
    val text: String
        get() = "{parents: $parents, interior: ${if (junctions.isEmpty()) "Here" else junctions.joinToString(prefix = "[", postfix = "]", transform = ::render)}}"
}

private fun unwrapVersion(value: Any?): Any? =
    if (value is DictEnum.Entry<*> && VERSION.matches(value.name)) value.value else value

private val VERSION = Regex("V\\d+")

// Shorter lists of small numbers are shown as numbers: only hashes and accounts read better as hex.
private const val MIN_BYTES_AS_HEX = 16

/** A location from a versioned or plain location value; null when the value is not one. */
internal fun location(value: Any?): XcmLocation? {
    val mapping = (unwrapVersion(value) as? Struct.Instance)?.mapping
    val parents = (mapping?.get("parents") as? BigInteger)?.toInt()
    val junctions = when (val inner = (mapping?.get("interior") as? DictEnum.Entry<*>)?.value) {
        null -> emptyList()
        is DictEnum.Entry<*> -> listOf(inner)
        is List<*> -> inner.filterIsInstance<DictEnum.Entry<*>>().takeIf { it.size == inner.size }
        else -> null
    }
    val isLocation = mapping?.get("interior") is DictEnum.Entry<*>
    return if (parents != null && junctions != null && isLocation) XcmLocation(parents, junctions) else null
}

/** Raw bytes of a byte array value: metadata `[u8; N]` decodes either as bytes or as a list of numbers. */
internal fun bytesOf(value: Any?): ByteArray? = when (value) {
    is ByteArray -> value
    is List<*> -> value.takeIf { it.isNotEmpty() && it.all(::isByte) }?.let { bytes ->
        ByteArray(bytes.size) { (bytes[it] as BigInteger).toByte() }
    }
    else -> null
}

private fun isByte(value: Any?): Boolean =
    value is BigInteger && value.signum() >= 0 && value.bitLength() <= Byte.SIZE_BITS

/** A decoded value as compact text, for values the wallet shows but does not interpret. */
internal fun render(value: Any?): String = when (value) {
    null -> "None"
    is ByteArray -> value.toHexString(withPrefix = true)
    is DictEnum.Entry<*> -> if (value.value == null) value.name else "${value.name}(${render(value.value)})"
    is Struct.Instance -> value.mapping.entries.joinToString(prefix = "{", postfix = "}") {
        "${it.key}: ${render(it.value)}"
    }
    is List<*> -> bytesOf(value)?.takeIf { it.size >= MIN_BYTES_AS_HEX }?.toHexString(withPrefix = true)
        ?: value.joinToString(prefix = "[", postfix = "]", transform = ::render)
    else -> value.toString()
}
