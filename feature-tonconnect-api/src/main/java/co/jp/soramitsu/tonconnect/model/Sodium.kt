package co.jp.soramitsu.tonconnect.model

object Sodium {

    init {
        System.loadLibrary("libsodium")
        init()
    }

    external fun init(): Int

    external fun cryptoBoxNonceBytes(): Int

    external fun cryptoBoxMacBytes(): Int

    external fun cryptoBoxOpenEasy(
        plain: ByteArray,
        cipher: ByteArray,
        cipherSize: Int,
        nonce: ByteArray,
        remotePublicKey: ByteArray,
        localPrivateKey: ByteArray
    ): Int


}