package jp.co.soramitsu.common.wallet

import com.google.gson.Gson
import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletIndexedAssetBalance
import jp.co.soramitsu.common.model.UniversalWalletIndexedOperationType
import jp.co.soramitsu.common.model.UniversalWalletIndexedTokenMetadata
import jp.co.soramitsu.common.model.UniversalWalletIndexedTransaction
import jp.co.soramitsu.common.model.UniversalWalletIndexedTransactionDirection
import jp.co.soramitsu.common.model.UniversalWalletIndexedTransactionStatus
import jp.co.soramitsu.common.model.UniversalWalletIndexerPageInfo
import jp.co.soramitsu.common.model.UniversalWalletIndexerValidationError
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalWalletIndexerContractTest {

    @Test
    fun `validates and serializes normalized asset balances`() {
        val balance = assetBalance()

        assertTrue(balance.validationErrors().isEmpty())

        val json = Gson().toJson(balance)
        assertTrue(json.contains("\"ecosystem\":\"solana\""))
        assertTrue(json.contains("\"assetId\":\"SOL\""))
        assertTrue(json.contains("\"amount\":\"123456789\""))
        assertTrue(json.contains("\"tokenProgram\":\"spl-token\""))
    }

    @Test
    fun `rejects malformed asset balances`() {
        val balance = assetBalance().copy(
            accountId = "../bad",
            ecosystem = "unknown",
            chainId = "../bad",
            assetId = " SOL ",
            amount = "-1",
            decimals = 256,
            symbol = "bad\u0000symbol",
            name = "bad\u0000name",
        tokenAccountId = " token ",
        contractAddress = " contract ",
        tokenProgram = " token program ",
        syncedAtMillis = 0
    )

        val errors = balance.validationErrors()

        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidAccountId))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidEcosystem))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidChainId))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidAssetId))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidAmount))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidDecimals))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidSymbol))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidName))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidAddress))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidSyncedAt))
    }

    @Test
    fun `validates and serializes normalized transactions`() {
        val transaction = indexedTransaction()

        assertTrue(transaction.validationErrors().isEmpty())

        val json = Gson().toJson(transaction)
        assertTrue(json.contains("\"status\":\"confirmed\""))
        assertTrue(json.contains("\"direction\":\"outgoing\""))
        assertTrue(json.contains("\"operationType\":\"transfer\""))
    }

    @Test
    fun `serializes Nexus operation buckets`() {
        assertTrue(
            Gson()
                .toJson(indexedTransaction().copy(operationType = UniversalWalletIndexedOperationType.OfflineCash))
                .contains("\"operationType\":\"offline-cash\"")
        )
        assertTrue(
            Gson()
                .toJson(indexedTransaction().copy(operationType = UniversalWalletIndexedOperationType.Sccp))
                .contains("\"operationType\":\"sccp\"")
        )
        assertTrue(
            Gson()
                .toJson(indexedTransaction().copy(operationType = UniversalWalletIndexedOperationType.Governance))
                .contains("\"operationType\":\"governance\"")
        )
    }

    @Test
    fun `rejects malformed normalized transactions`() {
        val transaction = indexedTransaction().copy(
            accountId = "bad account",
            ecosystem = "bad",
            chainId = "bad chain",
            transactionId = " tx ",
            timestampMillis = -1,
            amount = "01",
            assetId = " asset ",
            feeAmount = "1.2",
            feeAssetId = " fee ",
            counterpartyAddress = " counterparty ",
            blockNumber = "-2",
            cursor = " cursor ",
            explorerUrl = "http://example.com/tx",
            syncedAtMillis = 0
        )

        val errors = transaction.validationErrors()

        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidAccountId))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidEcosystem))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidChainId))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidTransactionId))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidTimestamp))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidAmount))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidAssetId))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidAddress))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidBlockNumber))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidCursor))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidUrl))
        assertTrue(errors.contains(UniversalWalletIndexerValidationError.InvalidSyncedAt))
    }

    @Test
    fun `validates token metadata and page info contracts`() {
        val metadata = UniversalWalletIndexedTokenMetadata(
            ecosystem = UniversalWalletEcosystem.Ton.id,
            chainId = "ton:mainnet",
            assetId = "EQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM9c",
            decimals = 9,
            symbol = "TON",
            name = "Toncoin",
            iconUrl = "ipfs://bafybeigdyrzt",
            metadataUrl = "https://example.com/ton.json",
            isVerified = true,
            syncedAtMillis = 1_710_000_000_000
        )
        val pageInfo = UniversalWalletIndexerPageInfo(
            nextCursor = "next-cursor",
            limit = 100,
            total = 1,
            syncedAtMillis = 1_710_000_000_000
        )

        assertTrue(metadata.validationErrors().isEmpty())
        assertTrue(pageInfo.validationErrors().isEmpty())
    }

    @Test
    fun `rejects malformed token metadata and page info contracts`() {
        val metadata = UniversalWalletIndexedTokenMetadata(
            ecosystem = "bad",
            chainId = "bad chain",
            assetId = " asset ",
            decimals = -1,
            symbol = "bad\u0000symbol",
            name = "bad\u0000name",
            iconUrl = "ftp://example.com/icon.png",
            metadataUrl = "http://example.com/meta.json",
            syncedAtMillis = 0
        )
        val pageInfo = UniversalWalletIndexerPageInfo(
            nextCursor = " cursor ",
            limit = 251,
            total = -1,
            syncedAtMillis = 0
        )

        val metadataErrors = metadata.validationErrors()
        val pageErrors = pageInfo.validationErrors()

        assertTrue(metadataErrors.contains(UniversalWalletIndexerValidationError.InvalidEcosystem))
        assertTrue(metadataErrors.contains(UniversalWalletIndexerValidationError.InvalidChainId))
        assertTrue(metadataErrors.contains(UniversalWalletIndexerValidationError.InvalidAssetId))
        assertTrue(metadataErrors.contains(UniversalWalletIndexerValidationError.InvalidDecimals))
        assertTrue(metadataErrors.contains(UniversalWalletIndexerValidationError.InvalidSymbol))
        assertTrue(metadataErrors.contains(UniversalWalletIndexerValidationError.InvalidName))
        assertTrue(metadataErrors.contains(UniversalWalletIndexerValidationError.InvalidUrl))
        assertTrue(metadataErrors.contains(UniversalWalletIndexerValidationError.InvalidSyncedAt))
        assertTrue(pageErrors.contains(UniversalWalletIndexerValidationError.InvalidCursor))
        assertTrue(pageErrors.contains(UniversalWalletIndexerValidationError.InvalidLimit))
        assertTrue(pageErrors.contains(UniversalWalletIndexerValidationError.InvalidTotal))
        assertTrue(pageErrors.contains(UniversalWalletIndexerValidationError.InvalidSyncedAt))
    }

    private fun assetBalance() = UniversalWalletIndexedAssetBalance(
        accountId = "solana-mainnet",
        ecosystem = UniversalWalletEcosystem.Solana,
        chainId = "solana:mainnet",
        assetId = "SOL",
        amount = "123456789",
        decimals = 9,
        isNative = true,
        symbol = "SOL",
        name = "Solana",
        uiAmountString = "0.123456789",
        tokenProgram = "spl-token",
        syncedAtMillis = 1_710_000_000_000
    )

    private fun indexedTransaction() = UniversalWalletIndexedTransaction(
        accountId = "bitcoin-mainnet",
        ecosystem = UniversalWalletEcosystem.Bitcoin.id,
        chainId = "bitcoin:mainnet",
        transactionId = "a".repeat(64),
        status = UniversalWalletIndexedTransactionStatus.Confirmed,
        direction = UniversalWalletIndexedTransactionDirection.Outgoing,
        operationType = UniversalWalletIndexedOperationType.Transfer,
        timestampMillis = 1_710_000_000_000,
        amount = "1000",
        assetId = "BTC",
        feeAmount = "100",
        feeAssetId = "BTC",
        counterpartyAddress = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu",
        blockNumber = "800000",
        cursor = "a".repeat(64),
        explorerUrl = "https://mempool.space/tx/${"a".repeat(64)}",
        syncedAtMillis = 1_710_000_000_000
    )
}
