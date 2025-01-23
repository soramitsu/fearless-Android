package co.jp.soramitsu.tonconnect.model

import android.os.Build
import java.security.SecureRandom

object Security {

    fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        secureRandom().nextBytes(bytes)
        return bytes
    }

    fun secureRandom(): SecureRandom {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SecureRandom.getInstanceStrong()
        } else {
            SecureRandom()
        }
    }
}
