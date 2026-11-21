package jp.co.soramitsu.walletconnect.impl.presentation.summary

import java.math.BigInteger

/** EIP-712 typed data: the domain it is bound to, its type and, for token permits, what they allow. */
internal data class TypedDataSummary(
    val primaryType: String,
    /** Null when the domain cannot be read exactly as it is hashed. */
    val domain: TypedDataDomain?,
    /** Null for typed data that is not a known token permit. */
    val permit: TypedDataPermit?,
    val hasUndecodedDomainFields: Boolean = false
) {
    val isComplete: Boolean
        get() = domain?.verifyingContract != null && domain.chainId != null && permit != null && !hasUndecodedDomainFields &&
            (permit !is TypedDataPermit.Permit2Transfer || !permit.hasWitness)
}

/** The domain fields the signature commits to; a field the domain type does not declare is null. */
internal data class TypedDataDomain(
    val name: String?,
    val version: String?,
    val chainId: BigInteger?,
    val verifyingContract: String?
)

internal sealed interface TypedDataPermit {
    val spender: String

    /**
     * An EIP-2612 or DAI permit: an allowance for [spender] on the token at [token], the domain's
     * verifying contract. DAI's `allowed` is an unlimited allowance or none.
     */
    data class TokenAllowance(
        val token: String?,
        override val spender: String,
        val amount: BigInteger,
        /** The signature's deadline in unix seconds, or null when it never expires. */
        val deadline: BigInteger?
    ) : TypedDataPermit

    /** A Permit2 `PermitSingle` or `PermitBatch`: allowances Permit2 keeps for [spender]. */
    data class Permit2Allowance(
        override val spender: String,
        val allowances: List<Allowance>,
        val signatureDeadline: BigInteger
    ) : TypedDataPermit {
        data class Allowance(val token: String, val amount: BigInteger, val expiration: BigInteger)
    }

    /** A Permit2 `PermitTransferFrom` (batch, witness) signature: [spender] may take these tokens once. */
    data class Permit2Transfer(
        override val spender: String,
        val transfers: List<Transfer>,
        val deadline: BigInteger,
        /** The signature also commits to dApp order data (a witness) that is not decoded. */
        val hasWitness: Boolean
    ) : TypedDataPermit {
        data class Transfer(val token: String, val amount: BigInteger)
    }
}

internal object TypedDataSummaries {
    private val EIP2612_TYPES = mapOf(
        "owner" to "address",
        "spender" to "address",
        "value" to "uint256",
        "nonce" to "uint256",
        "deadline" to "uint256"
    )
    private val DAI_TYPES = mapOf(
        "holder" to "address",
        "spender" to "address",
        "nonce" to "uint256",
        "expiry" to "uint256",
        "allowed" to "bool"
    )
    private val PERMIT2_ALLOWANCE_TYPES = mapOf("spender" to "address", "sigDeadline" to "uint256")
    private val PERMIT_DETAILS_TYPES = mapOf(
        "token" to "address",
        "amount" to "uint160",
        "expiration" to "uint48",
        "nonce" to "uint48"
    )
    private val PERMIT2_TRANSFER_TYPES = mapOf("spender" to "address", "nonce" to "uint256", "deadline" to "uint256")
    private val TOKEN_PERMISSIONS_TYPES = mapOf("token" to "address", "amount" to "uint256")

    private val MAX_UINT256: BigInteger = BigInteger.ONE.shiftLeft(256) - BigInteger.ONE

    /** Deadlines from this one on are later than any date a phone can show: they never expire. */
    @Suppress("MagicNumber")
    val NEVER_EXPIRES: BigInteger = BigInteger.ONE.shiftLeft(47)

    fun summary(typedData: String): TypedDataSummary? {
        val view = TypedDataView.parse(typedData) ?: return null
        val domainStruct = view.domain
        val domain = domainStruct?.let {
            TypedDataDomain(
                name = it.string("name"),
                version = it.string("version"),
                chainId = it.uint("chainId"),
                verifyingContract = it.address("verifyingContract")
            )
        }
        val permit = view.message?.let { message -> permit(view.primaryType, message, domain?.verifyingContract) }
        val knownDomainFields = mapOf("name" to "string", "version" to "string", "chainId" to "uint256", "verifyingContract" to "address")
        val hasUndecodedDomainFields = domainStruct?.fieldNames?.any { name ->
            knownDomainFields[name]?.let { domainStruct.has(name, it) } != true
        } ?: true
        return TypedDataSummary(view.primaryType, domain, permit, hasUndecodedDomainFields)
    }

    private fun permit(
        primaryType: String,
        message: TypedStruct,
        verifyingContract: String?
    ): TypedDataPermit? = when (primaryType) {
            "Permit" -> when {
                message.hasExactly(EIP2612_TYPES) -> eip2612Allowance(message, verifyingContract)
                message.hasExactly(DAI_TYPES) -> daiAllowance(message, verifyingContract)
                else -> null
            }
            "PermitSingle" -> message.struct("details")?.let { details ->
                permit2Allowance(message, listOf(details), "PermitDetails")
            }
            "PermitBatch" -> message.structs("details")?.let { details ->
                permit2Allowance(message, details, "PermitDetails[]")
            }
            "PermitTransferFrom", "PermitWitnessTransferFrom" -> message.struct("permitted")?.let { permitted ->
                permit2Transfer(message, listOf(permitted), "TokenPermissions", primaryType == "PermitWitnessTransferFrom")
            }
            "PermitBatchTransferFrom", "PermitBatchWitnessTransferFrom" -> message.structs("permitted")?.let { permitted ->
                permit2Transfer(message, permitted, "TokenPermissions[]", primaryType == "PermitBatchWitnessTransferFrom")
            }
            else -> null
        }

    private fun eip2612Allowance(message: TypedStruct, token: String?): TypedDataPermit? {
        val spender = message.address("spender") ?: return null
        val amount = message.uint("value") ?: return null
        val deadline = message.uint("deadline") ?: return null
        return TypedDataPermit.TokenAllowance(token, spender, amount, deadline.takeIf { it < NEVER_EXPIRES })
    }

    // DAI's `allowed` grants an unlimited allowance or none, and an expiry of 0 never expires.
    private fun daiAllowance(message: TypedStruct, token: String?): TypedDataPermit? {
        val spender = message.address("spender") ?: return null
        val allowed = message.bool("allowed") ?: return null
        val expiry = message.uint("expiry") ?: return null
        return TypedDataPermit.TokenAllowance(
            token = token,
            spender = spender,
            amount = if (allowed) MAX_UINT256 else BigInteger.ZERO,
            deadline = expiry.takeIf { it.signum() > 0 && it < NEVER_EXPIRES }
        )
    }

    private fun permit2Allowance(
        message: TypedStruct,
        details: List<TypedStruct>,
        detailsType: String
    ): TypedDataPermit? {
        if (!message.hasExactly(PERMIT2_ALLOWANCE_TYPES + ("details" to detailsType))) return null
        val spender = message.address("spender")
        val deadline = message.uint("sigDeadline")
        val allowances = details.map(::allowanceDetails)
        return if (spender != null && deadline != null && allowances.all { it != null }) {
            TypedDataPermit.Permit2Allowance(spender, allowances.filterNotNull(), deadline)
        } else {
                null
            }
    }

    private fun allowanceDetails(detail: TypedStruct): TypedDataPermit.Permit2Allowance.Allowance? {
        if (!detail.hasExactly(PERMIT_DETAILS_TYPES)) return null
        val token = detail.address("token")
        val amount = detail.uint("amount")
        val expiration = detail.uint("expiration")
        return if (token != null && amount != null && expiration != null) {
            TypedDataPermit.Permit2Allowance.Allowance(token, amount, expiration)
        } else {
                null
            }
    }

    // A witness transfer commits to one more field, the dApp's order data, which is not decoded.
    private fun permit2Transfer(
        message: TypedStruct,
        permitted: List<TypedStruct>,
        permittedType: String,
        hasWitness: Boolean
    ): TypedDataPermit? {
        val types = PERMIT2_TRANSFER_TYPES + ("permitted" to permittedType)
        val witnessFields = message.fieldNames - types.keys
        val isTransfer = types.all { (name, type) -> message.has(name, type) } && witnessFields.size == if (hasWitness) 1 else 0
        if (!isTransfer) return null
        val spender = message.address("spender")
        val deadline = message.uint("deadline")
        val transfers = permitted.map(::transferPermissions)
        return if (spender != null && deadline != null && transfers.all { it != null }) {
            TypedDataPermit.Permit2Transfer(spender, transfers.filterNotNull(), deadline, hasWitness)
        } else {
                null
            }
    }

    private fun transferPermissions(item: TypedStruct): TypedDataPermit.Permit2Transfer.Transfer? {
        if (!item.hasExactly(TOKEN_PERMISSIONS_TYPES)) return null
        val token = item.address("token")
        val amount = item.uint("amount")
        return if (token != null && amount != null) TypedDataPermit.Permit2Transfer.Transfer(token, amount) else null
    }
}
