package jp.co.soramitsu.wallet.impl.domain.qr

import jp.co.soramitsu.wallet.impl.domain.model.QrContentCBDC
import java.math.BigDecimal
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object CbdcQrParser {
    private const val QUERY_PARAMETER = "qr"
    private const val MAX_QR_LENGTH = 4096
    private const val MAX_TLV_NODES = 128
    private const val ACCOUNT_ID_MIN_TAG = 26
    private const val ACCOUNT_ID_MAX_TAG = 51

    private const val TAG_TRANSACTION_AMOUNT = "54"
    private const val TAG_TRANSACTION_CURRENCY = "53"
    private const val TAG_MERCHANT_NAME = "59"
    private const val TAG_ADDITIONAL_DATA = "62"
    private const val TAG_ADDITIONAL_BILL_NUMBER = "01"
    private const val TAG_ADDITIONAL_PURPOSE = "08"
    private const val TAG_MERCHANT_ACCOUNT_AID = "00"

    fun parse(content: String): QrContentCBDC? = runCatching {
        val payload = extractQueryParameter(content, QUERY_PARAMETER) ?: return null
        if (payload.isBlank() || payload.length > MAX_QR_LENGTH) return null

        val root = parseTlv(payload)
        val transactionAmount = root[TAG_TRANSACTION_AMOUNT]
            ?.takeIf { it.length <= 32 }
            ?.let(::BigDecimal)
            ?.takeIf { it > BigDecimal.ZERO }
            ?: BigDecimal.ZERO

        val currency = root[TAG_TRANSACTION_CURRENCY]
            ?.takeIf { it.isNotBlank() && it.length <= 8 }
            ?: return null

        val merchantName = root[TAG_MERCHANT_NAME]
            ?.takeIf { it.isNotBlank() && it.length <= 128 }
            ?: return null

        val additionalData = root[TAG_ADDITIONAL_DATA]?.let(::parseTlv).orEmpty()

        QrContentCBDC(
            transactionAmount = transactionAmount,
            transactionCurrencyCode = currency,
            description = additionalData[TAG_ADDITIONAL_PURPOSE]?.takeIf { it.length <= 128 },
            name = merchantName,
            billNumber = additionalData[TAG_ADDITIONAL_BILL_NUMBER]?.takeIf { it.length <= 128 },
            recipientId = requireMerchantAccountId(root)
        )
    }.getOrNull()

    private fun requireMerchantAccountId(root: Map<String, String>): String {
        for (tag in ACCOUNT_ID_MIN_TAG..ACCOUNT_ID_MAX_TAG) {
            val accountInfo = root[tag.toString()] ?: continue
            val aid = parseTlv(accountInfo)[TAG_MERCHANT_ACCOUNT_AID]
            if (!aid.isNullOrBlank() && aid.length <= 256) {
                return aid
            }
        }

        error("CBDC recipient account id not found")
    }

    private fun parseTlv(payload: String): Map<String, String> {
        var offset = 0
        var nodes = 0
        val result = linkedMapOf<String, String>()

        while (offset < payload.length) {
            if (nodes++ >= MAX_TLV_NODES) error("Too many TLV nodes")
            if (offset + 4 > payload.length) error("Incomplete TLV header")

            val tag = payload.substring(offset, offset + 2)
            val lengthText = payload.substring(offset + 2, offset + 4)
            if (!tag.all(Char::isDigit) || !lengthText.all(Char::isDigit)) {
                error("Invalid TLV header")
            }

            val length = lengthText.toInt()
            val valueStart = offset + 4
            val valueEnd = valueStart + length
            if (valueEnd > payload.length) error("TLV value exceeds payload")

            result[tag] = payload.substring(valueStart, valueEnd)
            offset = valueEnd
        }

        return result
    }

    private fun extractQueryParameter(content: String, name: String): String? {
        val queryStart = content.indexOf('?').takeIf { it >= 0 }?.plus(1) ?: return null
        val fragmentStart = content.indexOf('#', startIndex = queryStart).takeIf { it >= 0 } ?: content.length
        val query = content.substring(queryStart, fragmentStart)

        return query.split('&')
            .asSequence()
            .mapNotNull { parameter ->
                val separator = parameter.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    val key = urlDecode(parameter.substring(0, separator))
                    val value = parameter.substring(separator + 1)
                    key to urlDecode(value)
                }
            }
            .firstOrNull { (key, _) -> key == name }
            ?.second
    }

    private fun urlDecode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}
