package jp.co.soramitsu.account.impl.data.repository

import java.security.MessageDigest
import java.util.Collections

/**
 * Receiving identity inventory frozen from the bundled chain registry, including disabled
 * historical Substrate entries so their public identities remain representable. This grants
 * no network access or signing permission. Policy changes require a new reviewed digest;
 * staged cohorts bind this digest and cannot silently adopt a different chain interpretation.
 */
internal object PortableWalletReceivingChainPolicy {
    const val SOURCE_SHA256 = "aee46cd72ca4355f4dccfcc1385a92167a1a9b6e1ddc4b4960ef4a14a8186977"
    const val SHA256 = "25216fc234b88a9e3daa73e45b98cec21c607d785ec4d8403a371e9a1d1b4520"
    private const val DOMAIN = "FPWCHN01\n"

    private val rawGenesisIds = listOf(
        "00dcb981df86429de8bbacf9803401f09485366c44efbf53af9ecfab03adc7e5",
        "0614f7b74a2e47f7c8d8e2a5335be84bdde9402a43f5decdec03200a87c8b943",
        "0e06260459b4f9034aba0a75108c08ed73ea51d2763562749b1d3600986c4ea5",
        "0f62b701fb12d02237a33b84818c11f621653d2b1614c777973babf4652b535d",
        "19a3733beb9cb8a970a308d835599e9005e02dc007a35440e461a451466776f8",
        "1bb969d85965e4bb5a651abbedf21a54b6b31a21f66b5401cc3f1e286268d736",
        "1bf2a2ecb4a868de66ea8610f2ce7c8c43706561b6476031315f6640fe38e060",
        "262e1b2ad728475fd6fe88e62d34c200abe6fd693931ddad144059b1eb884e5b",
        "2991d4125ac465d64c4c0b915fedd7168b5961b7676480636e3747f1ad64cfb9",
        "2fc8bb6ed7c0051bdcf4866c322ed32b6276572713607e3297ccf411b8f14aa9",
        "3266816be9fa51b32cfea58d3e33ca77246bc9618595a4300e44c8856a8d8a17",
        "35a06bfec2edf0ff4be89a6428ccd9ff5bd0167d618c5a0d4341f9600a458d14",
        "3920bcb4960a1eef5580cd5367ff3f430eef052774f78468852f7b9cb39f8a3c",
        "3af4ff48ec76d2efc8476730f423ac07e25ad48f5f4c9dc39c778b164d808615",
        "411f057b9107718c9624d6aa4a3f23c1653898297f3d4d529d9bb6511a39dd21",
        "4319cc49ee79495b57a1fec4d2bd43f59052dcc690276de566c2691d6df4f7b8",
        "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a",
        "4a12be580bb959937a1c7a61d5cf24428ed67fa571974b4007645d1886e7c89f",
        "4a587bf17a404e3572747add7aab7bbe56e805a5479c6c436f07f36fcc8d3ae1",
        "4ac80c99289841dd946ef92765bf659a307d39189b3ce374a92b5f0415ee17a1",
        "50dd5d206917bf10502c68fb4d18a59fc8aa31586f4e8856b493e43544aa82aa",
        "52149c30c1eb11460dce6c08b73df8d53bb93b4a15d0a2e7fd5dafe86a73c0da",
        "577d331ca43646f547cdaa07ad0aa387a383a93416764480665103081f3eaf14",
        "5c7bd13edf349b33eb175ffae85210299e324d852916336027391536e686f267",
        "5d3c298622d5634ed019bf61ea4b71655030015bde9beb0d6a24743714462c86",
        "631ccc82a078481584041656af292834e1ae6daab61d2875b4dd0c14bb9b17bc",
        "6408de7737c59c238890533af25896a2c20608d8b380bb01029acb392781063e",
        "64a1c658a48b2e70a7fb1ad4c39eea35022568c20fc44a6e2e3d0a57aee6053b",
        "6811a339673c9daa897944dcdac99c6e2939cc88245ed21951a0a3c9a2be75bc",
        "6859c81ca95ef624c9dfe4dc6e3381c33e5d6509e35e147092bfbc780f777c4e",
        "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f",
        "6bd89e052d67a45bb60a9a23e8581053d5e0d619f15cb9865946937e690c42d6",
        "6d8d9f145c2177fa83512492cdd80a71e29f22473f4a8943a6292149ac319fb9",
        "6e938c4a786f8df6f38d0c06f00a8573f1f7aabeebf48aee5157a93cc5fe3271",
        "6f09966420b2608d1947ccfb0f2a362450d1fc7fd902c29b67c906eaa965a7ae",
        "6f0f071506de39058fe9a95bbca983ac0e9c5da3443909574e95d52eb078d348",
        "70255b4d28de0fc4e1a193d7e175ad1ccef431598211c55538f1018651a0344e",
        "724c168d8e86b78b831c641e2cc822b8d1bf99fa0b4b28fe59985cd6fd580215",
        "7834781d38e4798d548e34ec947d19deea29df148a7bf32484b7b24dacf8d4b7",
        "7838c3c774e887c0a53bcba9e64f702361a1a852d5550b86b58cd73827fa1e1e",
        "7dd99936c1e9e6d1ce7d90eb6f33bea8393b4bf87677d675aa63c9cb3e8c5b5b",
        "7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5",
        "84322d9cddbf35088f1e54e9a85c967a41a56a4f43445768125e61af166c7d31",
        "8685a8d3e57fa8024b91b8ead6cc97acf953889c6fb0a355602826a1e2db198f",
        "89d3ec46d2fb43ef5a9713833373d5ea666b092fa8fd68fbc34596036571b907",
        "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
        "97da7ede98d7bad4e36b4d734b6055425a3be036da2a332ea5a7037656427a21",
        "9af9a64e6e4da8e3073901c3ff0cc4c3aad9563786d89daf6ad820b6e14a0b8b",
        "9de765698374eb576968c8a764168893fb277e65ad3ddafcfe2c49593fc6d663",
        "9eb76c5184c4ab8679d2d5d819fdf90b9c001403e9e17da2e14b6d8aec4029c6",
        "9f28c6a68e0fc9646eff64935684f6eeeece527e37bbe1f213d22caa1d9d6bed",
        "a37725fd8943d2a524cb7ecc65da438f9fa644db78ba24dcd0003e2f95645e8f",
        "a85cfb9b9fd4d622a5b28289a02347af987d8f73fa3108450e2b4a11c1ce5755",
        "aa3876c1dc8a1afcc2e9a685a49ff7704cfd36ad8c90bf2702b9d1b00cc40011",
        "afdc188f45c71dacbaa0b62e16a91f726c7b8699a9748cdf715459de6b7f366d",
        "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe",
        "b34f6cd03a41f0fab38ba9fd5b11cce5f303633c46f39f0c6fdc7c3c602bafa9",
        "b3db41421702df9a7fcac62b53ffeac85f7853cc4e689e0b93aeb3db18c09d82",
        "baf5aabe40646d11f0ee8abbdc64f4a4b7674925cba08e4a05ff9ebed6e2126b",
        "bf88efe70e9e0e916416e8bed61f2b45717f517d7f3523e33c7b001e5ffcbc72",
        "c14597baeccb232d662770d2d50ae832ca8c3192693d2b0814e6433f2888ddd6",
        "c1af4cb4eb3918e5db15086c0cc5ec17fb334f728b7c65dd44bfe1e174ff8b3f",
        "ca93a37c913a25fa8fdb33c7f738afc39379cb71d37874a16d4c091a5aef9f89",
        "cceae7f3b9947cdb67369c026ef78efa5f34a08fe5808d373c04421ecf4f1aaf",
        "cd4d732201ebe5d6b014edda071c4203e16867305332301dc8d092044b28e554",
        "cdedc8eadbfa209d3f207bba541e57c3c58a667b05a2e1d1e86353c9000758da",
        "d42e9606a995dfe433dc7955dc2a70f495f350f373daa200098ae84437816ad2",
        "d43540ba6d3eb4897c28a77d48cb5b729fea37603cbbfc7a86a73b72adb3be8d",
        "d4c0c08ca49dc7c680c3dac71a7c0703e5b222f4b6c03fe4c5219bb8f22c18dc",
        "d611f22d291c5b7b69f1e105cca03352984c344c4421977efaa4cbdd1834e2aa",
        "d8761d3c88f26dc12875c00d3165f7d67243d56fc85b4cf19937601a7916e5a9",
        "da5831fbc8570e3c6336d0d72b8c08f8738beefec812df21ef2afc2982ede09c",
        "daab8df776eb52ec604a5df5d388bb62a050a0aaec4556a64265b9d42755552d",
        "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e",
        "e358eb1d11b31255a286c12e44fe6780b7edb171d657905a97e39f71d9c6c3ee",
        "e61a41c53f5dcd0beb09df93b34402aada44cb05117b71059cce40a2723a4e97",
        "e7e0962324a3b86c83404dbea483f25fb5dab4c224791c81b756cfc948006174",
        "e92d165ad41e41e215d09713788173aecfdbe34d3bed29409d33a2ef03980738",
        "eacdd2d5b42de9769ccbb6e8d9013ab0d90ab105bf601d4aac53e874c145ec21",
        "f0b8924b12e8108550d28870bc03f7b45a947e1b2b9abf81bfb0b89ecb60570e",
        "f1cf9022c7ebb34b162d5b5e34e705a5a740b2d0ecc1009fb89023e62a488108",
        "f22b7850cdd5a7657bbfd90ac86441275bbc57ace3d2698a740c7b0ec4de5ec3",
        "f2584690455deda322214e97edfffaf4c1233b6e4625e39478496b3e2f5a44c5",
        "f3c7ad88f6a80f366c4be216691411ef0622e8b809b1046ea297ef106058d4eb",
        "fc41b9bd8ef8fe53d58c7ea67c794c7ec9a73daf05e6d54b14ff6342c99ba64c",
        "fd4d46e9a51e16babf791b94d6dbf771ed1d7de8a11b310aa98c847890fa9ff3",
        "feb426ca713f0f46c96465b8f039890370cf6bfd687c9076ea2843f58a6ae8a7",
    )

    val approvedGenesis: List<PortableWalletChainSigningProof.ApprovedGenesis> = run {
        check(rawGenesisIds == rawGenesisIds.distinct().sorted()) { "Receiving chain inventory is not canonical" }
        val bytes = (DOMAIN + rawGenesisIds.joinToString("\n", postfix = "\n")).toByteArray(Charsets.US_ASCII)
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        check(actual == SHA256) { "Receiving chain inventory commitment changed" }
        Collections.unmodifiableList(
            rawGenesisIds.map {
            PortableWalletChainSigningProof.ApprovedGenesis(
                "0x$it", PortableWalletChainSigningProof.IdentityKind.SUBSTRATE,
            )
        }
        )
    }
}
