package jp.co.soramitsu.runtime.multiNetwork.runtime.metadatahash

/**
 * Networks whose runtime lists `CheckMetadataHash` but was built without a metadata hash. Such a runtime answers a
 * transaction signed with Mode::Enabled with `UnknownTransaction::CannotLookup` (frame-metadata-hash-extension 0.17,
 * `implicit`), so it can only be signed with Mode::Disabled. The list is compiled in and reviewed: no node answer and
 * no remote configuration can add a network to it.
 *
 * Evidence for AvN (2026-11-07, `TaggedTransactionQueue_validate_transaction` of a remark signed by an unfunded
 * throwaway key): Mode::Disabled gives `Invalid(Payment)`, Mode::Enabled with the RFC 78 hash gives
 * `Unknown(CannotLookup)`.
 *
 * Pendulum was listed until its runtime was built with a hash. On 2026-11-13 spec pendulum 27 answered a transaction
 * signed with Mode::Enabled and this app's RFC 78 hash with `Invalid(Payment)`, the accepted outcome, where spec 26 gave
 * `Unknown(CannotLookup)` (`LiveMetadataHashValidateTest`). Pendulum now signs with Mode::Enabled like every other
 * network that checks the hash.
 */
class MetadataHashExemptions(private val reasons: Map<String, String>) {

    /** Why [chainId] signs with Mode::Disabled although its runtime lists the extension, or null when it must not. */
    fun reasonFor(chainId: String): String? = reasons[chainId.removePrefix("0x").lowercase()]

    companion object {
        val Default = MetadataHashExemptions(
            mapOf(
                // AvN Polkadot, spec avn-parachain 240.
                "8b5c955b5c8fd7112562327e3859473df4e3dff49bd72a113dbb668d2cfa20d7" to "AvN runtime has no metadata hash"
            )
        )
    }
}
