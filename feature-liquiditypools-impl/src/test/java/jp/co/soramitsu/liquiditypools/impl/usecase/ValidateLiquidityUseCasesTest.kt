package jp.co.soramitsu.liquiditypools.impl.usecase

import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.pswap
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.walletAsset
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ValidateLiquidityUseCasesTest {

    private val add = ValidateAddLiquidityUseCase()
    private val remove = ValidateRemoveLiquidityUseCase()

    @Test
    fun `adding counts the fee against the utility side of the pair`() {
        val base = walletAsset(xor, free = BigDecimal("11"))
        val target = walletAsset(valToken, free = BigDecimal("11"))

        assertEquals(TransferValidationResult.Valid, add(base, target, "xor", BigDecimal.ZERO, "9.9".bd, "10".bd, "1".bd).getOrThrow())
        // 10 XOR plus a 1 XOR fee leaves nothing of the 11 XOR available.
        assertEquals(
            TransferValidationResult.InsufficientBalance,
            add(base, target, "xor", BigDecimal.ZERO, "10".bd, "10".bd, "1".bd).getOrThrow()
        )
        assertEquals(
            TransferValidationResult.InsufficientBalance,
            add(target, base, "xor", BigDecimal.ZERO, "10".bd, "10".bd, "1".bd).getOrThrow()
        )
    }

    @Test
    fun `adding a pair without XOR needs XOR for the fee`() {
        val base = walletAsset(valToken, free = BigDecimal("100"))
        val target = walletAsset(pswap, free = BigDecimal("100"))

        assertEquals(
            TransferValidationResult.InsufficientUtilityAssetBalance,
            add(base, target, "xor", "0.5".bd, "10".bd, "10".bd, "1".bd).getOrThrow()
        )
        assertEquals(TransferValidationResult.Valid, add(base, target, "xor", "2".bd, "10".bd, "10".bd, "1".bd).getOrThrow())
        assertEquals(
            TransferValidationResult.InsufficientBalance,
            add(base, target, "xor", "0.5".bd, "100".bd, "10".bd, "1".bd).getOrThrow()
        )
    }

    @Test
    fun `removing is limited by the pooled amounts and the XOR fee`() {
        assertEquals(TransferValidationResult.Valid, remove("2".bd, "10".bd, "20".bd, "10".bd, "20".bd, "1".bd).getOrThrow())
        assertEquals(TransferValidationResult.InsufficientBalance, remove("2".bd, "10".bd, "20".bd, "11".bd, "1".bd, "1".bd).getOrThrow())
        assertEquals(TransferValidationResult.InsufficientBalance, remove("2".bd, "10".bd, "20".bd, "1".bd, "21".bd, "1".bd).getOrThrow())
        assertEquals(
            TransferValidationResult.InsufficientUtilityAssetBalance,
            remove("1".bd, "10".bd, "20".bd, "1".bd, "1".bd, "1".bd).getOrThrow()
        )
    }

    private val String.bd get() = BigDecimal(this)
}
