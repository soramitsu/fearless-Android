//! Pure sr25519 operations behind the JNI exports in `lib.rs`.
//!
//! Every function keeps the byte layouts and the behaviour of the library that
//! Fearless Wallet shipped from 2020 to 4.3.1 (schnorrkel 0.9.1):
//!
//! - keypair: 96 bytes = secret scalar (32) | nonce seed (32) | public key (32)
//! - secret: 64 bytes = secret scalar (32) | nonce seed (32)
//! - mini secret ("seed"): 32 bytes, expanded with `ExpansionMode::Ed25519`
//! - chain code: 32 bytes
//! - signatures use the Substrate signing context `b"substrate"`
//!
//! Secret material produced here is returned in `Zeroizing` buffers, so it is
//! wiped when the caller drops it.

use schnorrkel::derive::{CHAIN_CODE_LENGTH, ChainCode, Derivation};
use schnorrkel::{
    ExpansionMode, KEYPAIR_LENGTH, Keypair, MiniSecretKey, PUBLIC_KEY_LENGTH, PublicKey,
    SECRET_KEY_LENGTH, SIGNATURE_LENGTH, SecretKey, Signature,
};
use zeroize::Zeroizing;

/// Signing context that Substrate uses for sr25519 signatures.
pub const SIGNING_CONTEXT: &[u8] = b"substrate";

/// A 96-byte keypair that is wiped on drop.
pub type KeypairBytes = Zeroizing<[u8; KEYPAIR_LENGTH]>;

/// A 64-byte secret key that is wiped on drop.
pub type SecretBytes = Zeroizing<[u8; SECRET_KEY_LENGTH]>;

/// An input the library refuses. Each variant maps to the exception message of
/// the legacy library; a refused keypair no longer echoes the keypair bytes.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Rejection {
    /// The mini secret is not 32 bytes.
    Seed,
    /// The keypair is not 96 bytes, or its secret or public part is invalid.
    Keypair,
    /// The public key is not a valid compressed Ristretto point.
    PublicKey,
    /// The secret key is not 64 bytes or its scalar is not canonical.
    SecretKey,
    /// The chain code is not 32 bytes. The legacy library aborted the process here.
    ChainCode,
}

impl Rejection {
    /// The message of the `java.lang.Exception` thrown for this rejection.
    pub fn message(self) -> &'static str {
        match self {
            Rejection::Seed => "Provided seed is invalid",
            Rejection::Keypair => "Provided pair is invalid",
            Rejection::PublicKey => "Provided public key is invalid.",
            Rejection::SecretKey => "Provided private key is invalid.",
            Rejection::ChainCode => "Provided chain code is invalid.",
        }
    }
}

fn chain_code(bytes: &[u8]) -> Result<ChainCode, Rejection> {
    let code: [u8; CHAIN_CODE_LENGTH] = bytes.try_into().map_err(|_| Rejection::ChainCode)?;
    Ok(ChainCode(code))
}

fn keypair_bytes(keypair: &Keypair) -> KeypairBytes {
    let mut bytes = Zeroizing::new([0u8; KEYPAIR_LENGTH]);
    bytes[..SECRET_KEY_LENGTH].copy_from_slice(Zeroizing::new(keypair.secret.to_bytes()).as_ref());
    bytes[SECRET_KEY_LENGTH..].copy_from_slice(&keypair.public.to_bytes());
    bytes
}

/// Expands a 32-byte mini secret into a keypair (`ExpansionMode::Ed25519`).
pub fn keypair_from_seed(seed: &[u8]) -> Result<KeypairBytes, Rejection> {
    let mini = MiniSecretKey::from_bytes(seed).map_err(|_| Rejection::Seed)?;
    Ok(keypair_bytes(
        &mini.expand_to_keypair(ExpansionMode::Ed25519),
    ))
}

/// Signs `message` with the 64-byte `secret` under the Substrate context.
pub fn sign(
    public_key: &[u8],
    secret: &[u8],
    message: &[u8],
) -> Result<[u8; SIGNATURE_LENGTH], Rejection> {
    let secret = SecretKey::from_bytes(secret).map_err(|_| Rejection::SecretKey)?;
    let public = PublicKey::from_bytes(public_key).map_err(|_| Rejection::PublicKey)?;
    Ok(secret
        .sign_simple(SIGNING_CONTEXT, message, &public)
        .to_bytes())
}

/// Verifies `signature` over `message`. A malformed signature is `Ok(false)`,
/// as before; a malformed public key is refused.
pub fn verify(signature: &[u8], message: &[u8], public_key: &[u8]) -> Result<bool, Rejection> {
    let Ok(signature) = Signature::from_bytes(signature) else {
        return Ok(false);
    };
    let public = PublicKey::from_bytes(public_key).map_err(|_| Rejection::PublicKey)?;
    Ok(public
        .verify_simple(SIGNING_CONTEXT, message, &signature)
        .is_ok())
}

/// Hard (`//`) derivation: a new mini secret from the secret scalar and the
/// chain code, expanded with `ExpansionMode::Ed25519`.
pub fn derive_keypair_hard(keypair: &[u8], code: &[u8]) -> Result<KeypairBytes, Rejection> {
    let keypair = Keypair::from_bytes(keypair).map_err(|_| Rejection::Keypair)?;
    let code = chain_code(code)?;
    let (mini, _) = keypair.secret.hard_derive_mini_secret_key(Some(code), b"");
    Ok(keypair_bytes(
        &mini.expand_to_keypair(ExpansionMode::Ed25519),
    ))
}

/// Soft (`/`) derivation of a keypair. schnorrkel draws a fresh random nonce
/// seed for the child, so only its scalar and public key are deterministic.
pub fn derive_keypair_soft(keypair: &[u8], code: &[u8]) -> Result<KeypairBytes, Rejection> {
    let keypair = Keypair::from_bytes(keypair).map_err(|_| Rejection::Keypair)?;
    let code = chain_code(code)?;
    let (child, _) = keypair.derived_key_simple(code, b"");
    Ok(keypair_bytes(&child))
}

/// Soft (`/`) derivation of a public key.
pub fn derive_public_soft(
    public_key: &[u8],
    code: &[u8],
) -> Result<[u8; PUBLIC_KEY_LENGTH], Rejection> {
    let public = PublicKey::from_bytes(public_key).map_err(|_| Rejection::PublicKey)?;
    let code = chain_code(code)?;
    let (child, _) = public.derived_key_simple(code, b"");
    Ok(child.to_bytes())
}

/// Converts a 64-byte secret into the Ed25519-style expanded form (scalar
/// multiplied by the cofactor) used by polkadot-js JSON exports.
pub fn to_ed25519_bytes(secret: &[u8]) -> Result<SecretBytes, Rejection> {
    let secret = SecretKey::from_bytes(secret).map_err(|_| Rejection::SecretKey)?;
    Ok(Zeroizing::new(secret.to_ed25519_bytes()))
}

/// Converts an Ed25519-style expanded secret back into the 64-byte secret.
/// `None` when the input is not 64 bytes or its scalar is not canonical.
pub fn from_ed25519_bytes(expanded: &[u8]) -> Option<SecretBytes> {
    let secret = SecretKey::from_ed25519_bytes(expanded).ok()?;
    Some(Zeroizing::new(secret.to_bytes()))
}

/// The diagnostic greeting returned by the legacy `Sr25519.test` export.
pub fn greeting(name: &str) -> String {
    format!("Hello, {name}!")
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Mini secret of the Substrate development phrase
    /// "bottom drive obey lake curtain smoke basket hold race lonely fit walk".
    const DEV_SEED: &str = "fac7959dbfe72f052e5a0c3c8d6530f202b02fd8f9f5ca3580ec8deb7797479e";
    const DEV_PUBLIC: &str = "46ebddef8cd9bb167dc30878d7113b7e168e6f0646beffd77d69d39bad76b47a";
    const ALICE_PUBLIC: &str = "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d";
    const FOO_SOFT_PUBLIC: &str =
        "40b9675df90efa6069ff623b0fdfcf706cd47ca7452a5056c7ad58194d23440a";

    fn hex(text: &str) -> Vec<u8> {
        (0..text.len())
            .step_by(2)
            .map(|i| u8::from_str_radix(&text[i..i + 2], 16).unwrap())
            .collect()
    }

    /// SCALE-encoded junction name padded to a 32-byte chain code.
    fn junction(name: &str) -> [u8; 32] {
        let mut code = [0u8; 32];
        code[0] = (name.len() as u8) << 2;
        code[1..=name.len()].copy_from_slice(name.as_bytes());
        code
    }

    fn dev_keypair() -> KeypairBytes {
        keypair_from_seed(&hex(DEV_SEED)).unwrap()
    }

    #[test]
    fn keypair_from_seed_matches_the_substrate_dev_vector() {
        assert_eq!(dev_keypair()[64..], hex(DEV_PUBLIC)[..]);
    }

    #[test]
    fn keypair_from_seed_is_deterministic() {
        assert_eq!(dev_keypair()[..], dev_keypair()[..]);
    }

    #[test]
    fn keypair_from_seed_rejects_wrong_lengths() {
        assert_eq!(keypair_from_seed(&[0u8; 31]).unwrap_err(), Rejection::Seed);
        assert_eq!(keypair_from_seed(&[0u8; 33]).unwrap_err(), Rejection::Seed);
    }

    #[test]
    fn hard_derivation_gives_alice() {
        let alice = derive_keypair_hard(&dev_keypair()[..], &junction("Alice")).unwrap();
        assert_eq!(alice[64..], hex(ALICE_PUBLIC)[..]);
        let again = derive_keypair_hard(&dev_keypair()[..], &junction("Alice")).unwrap();
        assert_eq!(
            alice[..],
            again[..],
            "hard derivation is fully deterministic"
        );
    }

    #[test]
    fn soft_derivation_matches_public_derivation_and_vector() {
        let child = derive_keypair_soft(&dev_keypair()[..], &junction("foo")).unwrap();
        let public = derive_public_soft(&hex(DEV_PUBLIC), &junction("foo")).unwrap();
        assert_eq!(child[64..], public[..]);
        assert_eq!(public[..], hex(FOO_SOFT_PUBLIC)[..]);
    }

    #[test]
    fn soft_derivation_keeps_scalar_and_draws_a_fresh_nonce() {
        let first = derive_keypair_soft(&dev_keypair()[..], &junction("foo")).unwrap();
        let second = derive_keypair_soft(&dev_keypair()[..], &junction("foo")).unwrap();
        assert_eq!(first[..32], second[..32]);
        assert_eq!(first[64..], second[64..]);
        assert_ne!(first[32..64], second[32..64]);
    }

    #[test]
    fn derivation_rejects_bad_inputs() {
        let keypair = dev_keypair();
        assert_eq!(
            derive_keypair_hard(&keypair[..95], &[0u8; 32]).unwrap_err(),
            Rejection::Keypair
        );
        assert_eq!(
            derive_keypair_soft(&keypair[..95], &[0u8; 32]).unwrap_err(),
            Rejection::Keypair
        );
        assert_eq!(
            derive_keypair_hard(&keypair[..], &[0u8; 31]).unwrap_err(),
            Rejection::ChainCode
        );
        assert_eq!(
            derive_keypair_soft(&keypair[..], &[0u8; 33]).unwrap_err(),
            Rejection::ChainCode
        );
        assert_eq!(
            derive_public_soft(&[0u8; 31], &[0u8; 32]).unwrap_err(),
            Rejection::PublicKey
        );
        assert_eq!(
            derive_public_soft(&keypair[64..], &[]).unwrap_err(),
            Rejection::ChainCode
        );
        let mut bad_point = [0u8; 32];
        bad_point[0] = 1;
        assert_eq!(
            derive_public_soft(&bad_point, &[0u8; 32]).unwrap_err(),
            Rejection::PublicKey
        );
        let mut bad_pair = *keypair;
        bad_pair[31] |= 0x80;
        assert_eq!(
            derive_keypair_hard(&bad_pair, &[0u8; 32]).unwrap_err(),
            Rejection::Keypair
        );
    }

    #[test]
    fn sign_and_verify_round_trip() {
        let keypair = dev_keypair();
        let message = b"fearless sr25519 test message";
        let signature = sign(&keypair[64..], &keypair[..64], message).unwrap();
        assert_eq!(signature[63] & 0x80, 0x80, "schnorrkel marker bit");
        assert!(verify(&signature, message, &keypair[64..]).unwrap());
        assert!(!verify(&signature, b"another message", &keypair[64..]).unwrap());
        let other = derive_keypair_hard(&keypair[..], &junction("Alice")).unwrap();
        assert!(!verify(&signature, message, &other[64..]).unwrap());
    }

    #[test]
    fn signatures_are_randomized() {
        let keypair = dev_keypair();
        let first = sign(&keypair[64..], &keypair[..64], b"m").unwrap();
        let second = sign(&keypair[64..], &keypair[..64], b"m").unwrap();
        assert_ne!(first, second);
    }

    #[test]
    fn verify_treats_malformed_signatures_as_invalid() {
        let keypair = dev_keypair();
        let mut unmarked = sign(&keypair[64..], &keypair[..64], b"m").unwrap();
        unmarked[63] &= 0x7f;
        assert!(!verify(&unmarked, b"m", &keypair[64..]).unwrap());
        assert!(!verify(&[0u8; 63], b"m", &keypair[64..]).unwrap());
        assert!(
            !verify(&[0u8; 63], b"m", &[0u8; 3]).unwrap(),
            "signature is checked first"
        );
    }

    #[test]
    fn verify_and_sign_reject_bad_keys() {
        let keypair = dev_keypair();
        let signature = sign(&keypair[64..], &keypair[..64], b"m").unwrap();
        assert_eq!(
            verify(&signature, b"m", &[0u8; 31]).unwrap_err(),
            Rejection::PublicKey
        );
        assert_eq!(
            sign(&keypair[64..], &keypair[..63], b"m").unwrap_err(),
            Rejection::SecretKey
        );
        assert_eq!(
            sign(&[0u8; 31], &keypair[..64], b"m").unwrap_err(),
            Rejection::PublicKey
        );
        let mut high_bit = [0u8; 64];
        high_bit[..64].copy_from_slice(&keypair[..64]);
        high_bit[31] |= 0x80;
        assert_eq!(
            sign(&keypair[64..], &high_bit, b"m").unwrap_err(),
            Rejection::SecretKey
        );
    }

    #[test]
    fn ed25519_bytes_round_trip() {
        let keypair = dev_keypair();
        let expanded = to_ed25519_bytes(&keypair[..64]).unwrap();
        assert_eq!(expanded[32..], keypair[32..64], "nonce is kept");
        let restored = from_ed25519_bytes(&expanded[..]).unwrap();
        assert_eq!(restored[..], keypair[..64]);
    }

    #[test]
    fn ed25519_conversion_rejects_bad_inputs() {
        assert_eq!(
            to_ed25519_bytes(&[0u8; 63]).unwrap_err(),
            Rejection::SecretKey
        );
        assert!(from_ed25519_bytes(&[0u8; 63]).is_none());
        // 8 * l and above divides to a scalar >= l, which is not canonical.
        assert!(from_ed25519_bytes(&[0xffu8; 64]).is_none());
    }

    #[test]
    fn rejection_messages_match_the_legacy_library() {
        assert_eq!(Rejection::Seed.message(), "Provided seed is invalid");
        assert_eq!(Rejection::Keypair.message(), "Provided pair is invalid");
        assert_eq!(
            Rejection::PublicKey.message(),
            "Provided public key is invalid."
        );
        assert_eq!(
            Rejection::SecretKey.message(),
            "Provided private key is invalid."
        );
        assert_eq!(
            Rejection::ChainCode.message(),
            "Provided chain code is invalid."
        );
    }

    #[test]
    fn greeting_matches_the_legacy_test_export() {
        assert_eq!(greeting("sr25519"), "Hello, sr25519!");
    }
}
