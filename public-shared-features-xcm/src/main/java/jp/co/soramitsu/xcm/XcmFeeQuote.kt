package jp.co.soramitsu.xcm

import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.SignedExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.new
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.instances.AddressInstanceConstructor
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.instances.SignatureInstanceConstructor
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import java.math.BigInteger

private const val ED_SR_SIGNATURE_BYTES = 64
private const val ECDSA_SCALAR_BYTES = 32

/**
 * Encodes a fee-query-only extrinsic with an invalid, correctly sized placeholder signature.
 * Keeps the account, signature variant, nonce, era, zero tip and call encoding used for estimates,
 * without requesting a private key or invoking a signer. These bytes must only reach payment RPC.
 */
internal fun buildXcmFeeQuote(
    runtime: RuntimeSnapshot,
    accountId: ByteArray,
    cryptoType: CryptoType,
    nonce: BigInteger,
    era: Era,
    call: XcmExtrinsicCall
): String {
    require(nonce.signum() >= 0) { "XCM fee nonce must not be negative" }
    val module = runtime.metadata.module(call.moduleName)
    val function = module.call(call.callName)
    val placeholder = when (cryptoType) {
        CryptoType.ED25519 -> SignatureWrapper.Ed25519(ByteArray(ED_SR_SIGNATURE_BYTES))
        CryptoType.SR25519 -> SignatureWrapper.Sr25519(ByteArray(ED_SR_SIGNATURE_BYTES))
        CryptoType.ECDSA -> SignatureWrapper.Ecdsa(ByteArray(1), ByteArray(ECDSA_SCALAR_BYTES), ByteArray(ECDSA_SCALAR_BYTES))
    }
    val extrinsic = Extrinsic.Instance(
        signature = Extrinsic.Signature.new(
            accountIdentifier = AddressInstanceConstructor.constructInstance(runtime.typeRegistry, accountId),
            signature = SignatureInstanceConstructor.constructInstance(runtime.typeRegistry, placeholder),
            signedExtras = mapOf(
                SignedExtras.ERA to era,
                SignedExtras.NONCE to nonce,
                SignedExtras.TIP to BigInteger.ZERO,
                SignedExtras.ASSET_TX_PAYMENT to listOf(BigInteger.ZERO, null)
            )
        ),
        call = GenericCall.Instance(module, function, call.toRuntimeArguments(function))
    )
    return Extrinsic.toHex(runtime, extrinsic)
}
