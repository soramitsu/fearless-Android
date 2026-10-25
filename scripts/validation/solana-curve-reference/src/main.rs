// Independent public-address membership oracle used by Solana's Address implementation.
// cargo run --locked --offline --manifest-path scripts/validation/solana-curve-reference/Cargo.toml
use curve25519_dalek::edwards::CompressedEdwardsY;
fn main() {
    let mut vectors = vec![[0u8; 32]];
    for sign in [0u8, 0x80] {
        let mut one = [0; 32]; one[0] = 1; one[31] = sign; vectors.push(one);
        for low in [0xecu8, 0xed, 0xee, 0xff] {
            let mut edge = [0xff; 32]; edge[0] = low; edge[31] = 0x7f | sign; vectors.push(edge);
        }
    }
    let mut state = 0x4f6e6c795075626cu64;
    for _ in 0..512 {
        let mut encoded = [0u8; 32];
        for byte in &mut encoded {
            state ^= state << 13; state ^= state >> 7; state ^= state << 17;
            *byte = state as u8;
        }
        vectors.push(encoded);
    }
    println!("{{\"library\":\"curve25519-dalek 4.1.3\",\"vectors\":[");
    for (index, encoded) in vectors.iter().enumerate() {
        let hex: String = encoded.iter().map(|b| format!("{b:02x}")).collect();
        let member = CompressedEdwardsY(*encoded).decompress().is_some();
        println!("{}{{\"hex\":\"{}\",\"onCurve\":{}}}", if index == 0 {""} else {","}, hex, member);
    }
    println!("]}}");
}
