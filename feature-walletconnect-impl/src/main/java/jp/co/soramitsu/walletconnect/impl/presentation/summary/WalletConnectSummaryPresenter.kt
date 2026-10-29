package jp.co.soramitsu.walletconnect.impl.presentation.summary

import co.jp.soramitsu.feature_walletconnect_impl.R
import jp.co.soramitsu.common.compose.component.SigningDetailViewState
import jp.co.soramitsu.common.compose.component.SigningSummarySection
import jp.co.soramitsu.common.compose.component.SigningSummaryViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatExactTokenAmount
import jp.co.soramitsu.common.utils.formatSigningTime
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.walletconnect.impl.presentation.CrossChainAmount
import jp.co.soramitsu.walletconnect.impl.presentation.CrossChainCall
import jp.co.soramitsu.walletconnect.impl.presentation.CrossChainDestination
import jp.co.soramitsu.walletconnect.impl.presentation.CrossChainRetirement
import jp.co.soramitsu.walletconnect.impl.presentation.PolkadotRefusal
import java.math.BigInteger

/** Turns a decoded WalletConnect request into what the approval screens show. */
internal class WalletConnectSummaryPresenter(
    private val resourceManager: ResourceManager,
    private val formatTime: (BigInteger) -> String = ::formatSigningTime
) {

    /** [paused]: a remote kill switch paused signing for the request's family, which is then said first. */
    fun present(
        chain: Chain,
        summary: WalletConnectRequestSummary,
        paused: Boolean = false
    ): SigningSummaryViewState {
        val builder = SummaryBuilder(resourceManager, chain)
        if (paused) builder.refuse(resourceManager.getString(R.string.signing_refused_paused))
        when (summary) {
            is WalletConnectRequestSummary.Message -> builder.message(summary)
            is WalletConnectRequestSummary.EvmTransaction -> builder.evmTransaction(summary)
            is WalletConnectRequestSummary.TypedData -> builder.typedData(summary.typedData)
            is WalletConnectRequestSummary.PolkadotTransaction -> builder.polkadotTransaction(summary)
            WalletConnectRequestSummary.Undecoded -> builder.headline = resourceManager.getString(R.string.common_unknown)
        }
        return builder.build(summary.isComplete)
    }

    private fun SummaryBuilder.message(summary: WalletConnectRequestSummary.Message) {
        headline = summary.text
        row(R.string.common_message, summary.text)
    }

    private fun SummaryBuilder.evmTransaction(summary: WalletConnectRequestSummary.EvmTransaction) {
        when (val call = summary.call) {
            null -> plainEvmTransaction(summary)
            is EvmTokenCall.Transfer -> {
                action(R.string.signing_action_token_transfer, tokenAmount(summary.to, call.amount))
                row(R.string.signing_field_token, token(summary.to))
                row(R.string.signing_field_recipient, call.recipient)
                row(R.string.common_amount, tokenAmount(summary.to, call.amount))
            }
            is EvmTokenCall.TransferFrom -> {
                action(R.string.signing_action_token_transfer, tokenAmount(summary.to, call.amount))
                row(R.string.signing_field_token, token(summary.to))
                row(R.string.signing_field_owner, call.owner)
                row(R.string.signing_field_recipient, call.recipient)
                row(R.string.common_amount, tokenAmount(summary.to, call.amount))
            }
            is EvmTokenCall.Approve -> evmApproval(summary.to, call)
            is EvmTokenCall.ApprovalForAll -> {
                action(R.string.signing_action_nft_approval)
                row(R.string.signing_field_contract, summary.to)
                row(R.string.signing_field_operator, call.operator)
                row(R.string.signing_field_approved, string(if (call.approved) R.string.common_yes else R.string.common_no), call.approved)
                if (call.approved) warning(R.string.signing_warning_nft_approval)
            }
        }
        evmFeeDetails(summary)
    }

    private fun SummaryBuilder.evmApproval(tokenAddress: String, call: EvmTokenCall.Approve) {
        val allowance = allowance(tokenAddress, call.amount)
        val amount = if (call.isIncrease) "+$allowance" else allowance
        action(R.string.signing_action_token_approval, amount)
        row(R.string.signing_field_token, token(tokenAddress))
        row(R.string.signing_field_spender, call.spender)
        row(R.string.common_amount, amount, isWarning = call.amount.isUnlimitedAllowance())
        if (call.amount.isUnlimitedAllowance()) warning(R.string.signing_warning_unlimited_approval)
    }

    private fun SummaryBuilder.evmFeeDetails(summary: WalletConnectRequestSummary.EvmTransaction) {
        summary.gasPrice?.let { row(R.string.signing_field_gas_price, nativeAmount(it)) }
        summary.gasLimit?.let { row(R.string.signing_field_gas_limit, it.toString()) }
        if (summary.gasPrice != null && summary.gasLimit != null) {
            row(R.string.signing_field_max_fee, nativeAmount(summary.gasPrice * summary.gasLimit))
        }
        if ((summary.call != null || summary.selector != null) && summary.value.signum() > 0) {
            row(R.string.signing_field_value, nativeAmount(summary.value))
        }
    }

    private fun SummaryBuilder.plainEvmTransaction(summary: WalletConnectRequestSummary.EvmTransaction) {
        if (summary.selector == null) {
            action(R.string.signing_action_transfer, nativeAmount(summary.value))
            row(R.string.signing_field_recipient, summary.to)
            row(R.string.common_amount, nativeAmount(summary.value))
        } else {
            action(R.string.signing_action_contract_call)
            row(R.string.signing_field_contract, summary.to)
            row(R.string.signing_field_function, summary.selector)
        }
    }

    private fun SummaryBuilder.typedData(typedData: TypedDataSummary) {
        when (val permit = typedData.permit) {
            is TypedDataPermit.TokenAllowance -> {
                val amount = allowance(permit.token, permit.amount)
                action(R.string.signing_action_token_permit, amount)
                row(R.string.signing_field_token, token(permit.token))
                row(R.string.signing_field_spender, permit.spender)
                row(R.string.common_amount, amount, isWarning = permit.amount.isUnlimitedAllowance())
                row(R.string.signing_field_expires, time(permit.deadline))
                permitWarnings(permit.amount)
            }
            is TypedDataPermit.Permit2Allowance -> {
                action(
                    R.string.signing_action_permit2_allowance,
                    permit.allowances.singleOrNull()?.let {
                    allowance(it.token, it.amount)
                }
                )
                row(R.string.signing_field_spender, permit.spender)
                row(R.string.signing_field_signature_expires, time(permit.signatureDeadline))
                permit.allowances.forEach { allowance ->
                    section(
                        title = tokenSymbol(allowance.token),
                        SigningDetailViewState(string(R.string.signing_field_token), token(allowance.token)),
                        SigningDetailViewState(
                            string(R.string.common_amount),
                            allowance(allowance.token, allowance.amount),
                            allowance.amount.isUnlimitedAllowance()
                        ),
                        SigningDetailViewState(string(R.string.signing_field_expires), time(allowance.expiration))
                    )
                    permitWarnings(allowance.amount)
                }
            }
            is TypedDataPermit.Permit2Transfer -> {
                action(
                    R.string.signing_action_permit2_transfer,
                    permit.transfers.singleOrNull()?.let {
                    tokenAmount(it.token, it.amount)
                }
                )
                row(R.string.signing_field_spender, permit.spender)
                row(R.string.signing_field_signature_expires, time(permit.deadline))
                permit.transfers.forEach { transfer ->
                    section(
                        title = tokenSymbol(transfer.token),
                        SigningDetailViewState(string(R.string.signing_field_token), token(transfer.token)),
                        SigningDetailViewState(string(R.string.common_amount), tokenAmount(transfer.token, transfer.amount))
                    )
                    permitWarnings(transfer.amount)
                }
            }
            null -> {
                action(R.string.signing_action_typed_data, typedData.primaryType)
                row(R.string.signing_field_data_type, typedData.primaryType)
            }
        }
        typedData.domain?.let { domain ->
            listOfNotNull(domain.name, domain.version?.let { "v$it" }).takeIf { it.isNotEmpty() }?.let {
                row(R.string.signing_field_domain, it.joinToString(" "))
            }
            domain.verifyingContract?.let { row(R.string.signing_field_verifying_contract, it) }
            domain.chainId?.let { row(R.string.signing_field_chain_id, it.toString()) }
        }
    }

    private fun SummaryBuilder.permitWarnings(amount: BigInteger) {
        warning(R.string.signing_warning_permit)
        if (amount.isUnlimitedAllowance()) warning(R.string.signing_warning_unlimited_approval)
    }

    private fun SummaryBuilder.polkadotTransaction(summary: WalletConnectRequestSummary.PolkadotTransaction) {
        val call = summary.call
        when {
            call == null -> {
                headline = string(R.string.common_unknown)
                row(R.string.signing_field_call, string(R.string.common_unknown))
            }
            call.module == BALANCES && call.function in BALANCES_TRANSFERS && call.arguments.size == 2 &&
                call.arguments[0].value is PolkadotValue.Account && call.arguments[1].value is PolkadotValue.NativeAmount -> {
                val amount = polkadotValue(call.arguments[1].value)
                action(R.string.signing_action_transfer, amount)
                row(R.string.signing_field_call, call.name)
                row(R.string.signing_field_recipient, polkadotValue(call.arguments[0].value))
                row(R.string.common_amount, amount)
            }
            else -> {
                headline = call.name
                row(R.string.signing_field_call, call.name)
                call.arguments.forEach { argument -> rawRow(argument.name, polkadotValue(argument.value)) }
                nestedCalls(call, prefix = "")
            }
        }
        if (summary.tip.signum() > 0) row(R.string.signing_field_tip, nativeAmount(summary.tip))
        // A transfer to another network is what the request does, wherever in a batch or proxy it sits.
        summary.crossChainCalls.firstOrNull()?.let { first ->
            headline = listOf(string(R.string.signing_section_cross_chain), destinationText(first.destination)).joinToString(" · ")
        }
        summary.crossChainCalls.forEach { crossChain(it) }
        summary.refusal?.let { refusal -> refuse(refusalText(refusal, summary.crossChainCalls)) }
    }

    // Where the funds go, to whom and how much, in a section of its own for every cross-chain call.
    private fun SummaryBuilder.crossChain(call: CrossChainCall) {
        val destination = destinationText(call.destination)
        val setByXcm = string(R.string.signing_value_set_by_xcm)
        sectionRows(
            title = string(R.string.signing_section_cross_chain),
            rows = listOf(
                SigningDetailViewState(string(R.string.signing_field_call), call.name, call.isHighRisk),
                SigningDetailViewState(string(R.string.signing_field_destination), destination, call.retirement != null),
                SigningDetailViewState(string(R.string.signing_field_beneficiary), call.beneficiary ?: setByXcm)
            ) + call.amounts.map { SigningDetailViewState(string(R.string.common_amount), amountText(it)) }
        )
        warning(R.string.signing_warning_cross_chain)
        if (call.isHighRisk) warning(R.string.signing_warning_xcm_program)
    }

    private fun SummaryBuilder.destinationText(destination: CrossChainDestination?): String = when {
        destination == null -> string(R.string.signing_value_set_by_xcm)
        destination.chain != null -> destination.chain.name
        destination.paraId != null -> resourceManager.getString(R.string.signing_value_parachain, destination.paraId.toString())
        else -> destination.text ?: string(R.string.common_unknown)
    }

    private fun SummaryBuilder.amountText(amount: CrossChainAmount): String = when {
        amount.planks == null -> amount.description
        amount.asset != null -> amount.planks.formatExactTokenAmount(amount.asset.precision, amount.asset.symbol)
        else -> resourceManager.getString(R.string.signing_value_raw_units, amount.planks.toString()) + " · " + amount.description
    }

    private fun SummaryBuilder.refusalText(refusal: PolkadotRefusal, crossChainCalls: List<CrossChainCall>): String =
        when (refusal) {
        PolkadotRefusal.OtherNetwork -> string(R.string.signing_refused_other_network)
        PolkadotRefusal.NeverExpires -> string(R.string.signing_refused_never_expires)
        PolkadotRefusal.OtherRuntime -> string(R.string.signing_refused_other_runtime)
        PolkadotRefusal.UnsupportedFormat -> string(R.string.signing_refused_format)
        PolkadotRefusal.UndecodableCall -> string(R.string.signing_refused_undecoded_call)
        PolkadotRefusal.RetiredDestination -> {
            val retirement = crossChainCalls.firstNotNullOfOrNull { it.retirement }
            if (retirement is CrossChainRetirement.Destination) {
                resourceManager.getString(R.string.signing_refused_destination, retirement.destination)
            } else {
                string(R.string.signing_refused_relay_reserve)
            }
        }
    }

    // The calls a batch or proxy dispatches, each in its own numbered section.
    private fun SummaryBuilder.nestedCalls(call: PolkadotCallSummary, prefix: String) {
        val nested = call.arguments.flatMap { (it.value as? PolkadotValue.Calls)?.calls.orEmpty() }
        nested.forEachIndexed { index, inner ->
            val number = "$prefix${index + 1}"
            sectionRows(
                title = resourceManager.getString(R.string.signing_section_call, number),
                rows = listOf(SigningDetailViewState(string(R.string.signing_field_call), inner.name)) +
                    inner.arguments.map { SigningDetailViewState(it.name, polkadotValue(it.value)) }
            )
            nestedCalls(inner, "$number.")
        }
    }

    private fun SummaryBuilder.polkadotValue(value: PolkadotValue): String = when (value) {
        is PolkadotValue.Account -> value.address
        is PolkadotValue.NativeAmount -> nativeAmount(value.planks)
        is PolkadotValue.Text -> value.text
        is PolkadotValue.Calls -> value.calls.joinToString("\n") { it.name }
    }

    private fun SummaryBuilder.time(seconds: BigInteger?): String =
        seconds?.takeIf { it < TypedDataSummaries.NEVER_EXPIRES }?.let(formatTime) ?: string(R.string.signing_value_never)

    /** Collects rows, sections and warnings for one summary. */
    private class SummaryBuilder(private val resourceManager: ResourceManager, private val chain: Chain) {
        var headline: String = ""
        private val rows = mutableListOf<SigningDetailViewState>()
        private val sections = mutableListOf<SigningSummarySection>()
        private val warnings = linkedSetOf<String>()
        private val refusals = linkedSetOf<String>()

        fun string(id: Int): String = resourceManager.getString(id)

        /** The action row, and the headline: the action and, when there is one, its main amount. */
        fun action(id: Int, detail: String? = null) {
            headline = listOfNotNull(string(id), detail).joinToString(" · ")
            row(R.string.signing_field_action, string(id))
        }

        fun row(
            titleId: Int,
            value: String,
            isWarning: Boolean = false
        ) {
            rows += SigningDetailViewState(string(titleId), value, isWarning)
        }

        fun rawRow(title: String, value: String) {
            rows += SigningDetailViewState(title, value)
        }

        fun section(title: String?, vararg rows: SigningDetailViewState) {
            sectionRows(title, rows.toList())
        }

        fun sectionRows(title: String?, rows: List<SigningDetailViewState>) {
            sections += SigningSummarySection(title, rows)
        }

        fun warning(id: Int) {
            warnings += string(id)
        }

        /** The wallet will not sign the request: shown first, and as the headline of the request screen. */
        fun refuse(text: String) {
            refusals += text
        }

        fun nativeAmount(amount: BigInteger): String =
            chain.utilityAsset?.let { amount.formatExactTokenAmount(it.precision, it.symbol) } ?: rawUnits(amount)

        /** A token by its contract: its symbol when the network lists it, and always its address. */
        fun token(contract: String?): String = listOfNotNull(tokenSymbol(contract), contract).joinToString("\n")

        fun tokenSymbol(contract: String?): String =
            tokenAt(contract)?.symbol?.uppercase() ?: string(R.string.signing_value_unknown_token)

        fun tokenAmount(contract: String?, amount: BigInteger): String =
            tokenAt(contract)?.let { amount.formatExactTokenAmount(it.precision, it.symbol) } ?: rawUnits(amount)

        fun allowance(contract: String?, amount: BigInteger): String = if (amount.isUnlimitedAllowance()) {
            string(R.string.signing_value_unlimited) + " · " + tokenAmount(contract, amount)
        } else {
            tokenAmount(contract, amount)
        }

        private fun rawUnits(amount: BigInteger): String =
            resourceManager.getString(R.string.signing_value_raw_units, amount.toString())

        private fun tokenAt(contract: String?) = contract?.let {
            chain.assets.firstOrNull { asset -> !asset.isUtility && asset.id.equals(contract, ignoreCase = true) }
        }

        fun build(isComplete: Boolean): SigningSummaryViewState {
            if (!isComplete) warnings += string(R.string.signing_warning_undecoded)
            val main = SigningSummarySection(title = null, rows = rows.toList()).takeIf { rows.isNotEmpty() }
            return SigningSummaryViewState(
                // Whatever the request does, why it is refused comes first.
                headline = refusals.firstOrNull() ?: headline,
                sections = listOfNotNull(main) + sections,
                warnings = refusals.toList() + warnings.toList(),
                showsRawData = !isComplete,
                blocksSigning = refusals.isNotEmpty()
            )
        }
    }

    private companion object {
        const val BALANCES = "Balances"
        val BALANCES_TRANSFERS = setOf("transfer", "transfer_keep_alive", "transfer_allow_death")
    }
}
