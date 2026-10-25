package jp.co.soramitsu.common.data.network.solana

import java.math.BigInteger

/** Solana PDA curve membership, separate from Ed25519 signature/public-key validation. */
object SolanaPdaCurve {
    /**
     * Matches curve25519-dalek's CompressedEdwardsY.decompress().is_some(): test whether
     * x² = (y² - 1) / (d*y² + 1) has a root. The sign bit selects a root and cannot change
     * membership. Dalek reduces the 255-bit field encoding; small-order points also count.
     * Inputs are public address hashes, never private signing material.
     *
     * See anza-xyz/solana-sdk address::bytes_are_curve_point and dalek edwards::decompress.
     */
    fun isDecompressible(encoded: ByteArray): Boolean {
        if (encoded.size != 32) return false
        val y = BigInteger(1, encoded.reversedArray()).and(MASK).mod(P)
        val squared = y.multiply(y).mod(P)
        val numerator = squared.subtract(BigInteger.ONE).mod(P)
        val denominator = D.multiply(squared).add(BigInteger.ONE).mod(P)
        if (denominator == BigInteger.ZERO) return numerator == BigInteger.ZERO
        val xSquared = numerator.multiply(denominator.modInverse(P)).mod(P)
        return xSquared == BigInteger.ZERO || xSquared.modPow(LEGENDRE_EXPONENT, P) == BigInteger.ONE
    }

    private val P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val MASK = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.ONE)
    private val D = BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P)
    private val LEGENDRE_EXPONENT = P.subtract(BigInteger.ONE).shiftRight(1)
}
