#!/usr/bin/env python3
"""Turn AL-4 production-parity vectors into the RC's golden-vector test resources.

The AL-4 oracle (root docs/android-production-parity-oracle.md) runs the
production 4.2.0 crypto library, shared-features 1.1.1.44-FLW at
shared-features-android@7508bc83, next to the RC and writes synthetic vectors
in the `fearless-android-parity/v1` schema. That oracle compiles private
sources and stays out of this repository; only its synthetic vectors are
committed, in three files:

  common/src/test/resources/crypto-golden/<library>.json
      BIP39, seeds, junctions, ed25519/ecdsa/Ethereum keypairs, signatures,
      JSON keyring, TON derivation, plus independent SS58/EIP-55 anchors
      (HistoricalCryptoGoldenVectorTest, HistoricalTonPhraseAcceptanceTest).
  common/src/test/resources/crypto-golden/secret-encodings-<library>.json
      SCALE payloads exactly as 4.2.0 encoded and stored them
      (HistoricalSecretEncodingAcceptanceTest, HistoricalTonPhraseAcceptanceTest).
  core-db/src/androidTest/assets/crypto-golden/<library>-sr25519.json
      The sr25519 rows, which need the shipped native library (core-db packages it
      for its androidTest; HistoricalSr25519GoldenVectorTest).

Further library versions (fearless-utils 1.0.96-1.0.109, older
shared-features) come from the TM-A2 binary oracle in the same layout.

Usage:
  convert_parity_vectors.py --al4 <dir with 01-...12-*.json> --anchors <ss58 json> [--check]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
LIBRARY = "shared-features-1.1.1.44-FLW"
SCHEMA = "fearless-upgrade-matrix/crypto-golden/v1"
JVM_FILE = REPO_ROOT / f"common/src/test/resources/crypto-golden/{LIBRARY}.json"
ENCODINGS_FILE = REPO_ROOT / f"common/src/test/resources/crypto-golden/secret-encodings-{LIBRARY}.json"
SR25519_FILE = REPO_ROOT / f"core-db/src/androidTest/assets/crypto-golden/{LIBRARY}-sr25519.json"

FAMILIES = {
    "01": "01-scale-canonical-hex.json",
    "02": "02-bip39.json",
    "03": "03-seeds.json",
    "04": "04-junctions.json",
    "05": "05-keypairs.json",
    "06": "06-signer.json",
    "07": "07-json-keyring.json",
    "08": "08-ton.json",
    "12": "12-validator-acceptance.json",
}

# Larger SCALE vectors only repeat the two-byte compact length mode already
# covered below this size; every bound case (8,193-byte fields) is kept.
MAX_KEPT_HEX_CHARS = 1_200


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load_family(directory: Path, family: str) -> tuple[dict, str]:
    path = directory / FAMILIES[family]
    raw = path.read_bytes()
    document = json.loads(raw)
    if document.get("schema") != "fearless-android-parity/v1":
        raise SystemExit(f"{path} is not an AL-4 parity vector file")
    return document, sha256_bytes(raw)


def provenance(documents: dict[str, dict], hashes: dict[str, str]) -> dict:
    generators = {json.dumps(doc["generator"], sort_keys=True) for doc in documents.values()}
    if len(generators) != 1:
        raise SystemExit("AL-4 families come from different oracle runs")
    generator = documents["02"]["generator"]
    inputs = generator["inputs"]
    for side in ("rcUtils", "rcAndroid"):
        if inputs[side]["dirty"]:
            raise SystemExit(f"AL-4 run compared a dirty {side} tree")
    return {
        "library": "shared-features 1.1.1.44-FLW",
        "library_source": f"shared-features-android@{inputs['prodSharedFeatures']}",
        "app_glue_source": f"fearless-Android@{inputs['prodApp420']}",
        "releases": ["4.1.1+218", "4.2.0+229"],
        "oracle": "AL-4 production-parity oracle, root docs/android-production-parity-oracle.md",
        "prng_seed": generator["prngSeed"],
        "rc_compared_by_oracle": {
            "fearless-utils-Android": inputs["rcUtils"]["head"],
            "fearless-Android": inputs["rcAndroid"]["head"],
        },
        "converted_by": "tools/upgrade-fixtures/android/convert_parity_vectors.py",
        "input_sha256": {FAMILIES[family]: hashes[family] for family in sorted(hashes)},
    }


def is_sr25519(vector: dict) -> bool:
    crypto = vector.get("cryptoType") or (vector.get("expected") or {}).get("encryption")
    return crypto == "SR25519"


def scale_vectors(document: dict) -> list[dict]:
    kept = []
    for vector in document["vectors"]:
        if not vector["rcEqual"]:
            raise SystemExit(f"AL-4 SCALE vector {vector['case']} differs between 4.2.0 and the RC")
        bound = vector["case"].startswith("bound")
        if bound or len(vector["hex"]) <= MAX_KEPT_HEX_CHARS:
            kept.append({
                "schema": vector["schema"],
                "case": vector["case"],
                "hex": vector["hex"],
                "rc_preflight": "accept" if vector["rcPreflight"] == "accept" else "reject",
            })
    return kept


def stored_secret(vector: dict) -> dict:
    out = {key: vector[key] for key in (
        "store", "source", "case", "cryptoType", "derivationPath", "lastJunctionSoft",
        "typedVariant", "storedText", "stored", "publicKey", "accountId", "address",
    ) if key in vector}
    rc = vector["rc"]
    out["rc_verdict_2026_10_10"] = rc["verdict"]
    if rc["verdict"] == "reject":
        out["rc_error_2026_10_10"] = rc["error"]
    else:
        out["rc_sanitizer_changed_fields_2026_10_10"] = rc.get("sanitizerChangedFields") or []
    return out


def build(directory: Path, anchors_path: Path) -> dict[Path, str]:
    documents: dict[str, dict] = {}
    hashes: dict[str, str] = {}
    for family in FAMILIES:
        documents[family], hashes[family] = load_family(directory, family)
    anchors_raw = anchors_path.read_bytes()
    anchors = json.loads(anchors_raw)
    common = provenance(documents, hashes)

    keypairs = documents["05"]["vectors"]
    json_keyring = documents["07"]["vectors"]
    stored = [stored_secret(vector) for vector in documents["12"]["vectors"]]
    notes = {family: documents[family]["notes"] for family in ("02", "03", "05", "06", "07", "08", "12")}

    jvm = {
        "schema": SCHEMA,
        "provenance": common,
        "synthetic_only": True,
        "notes": notes,
        "sr25519_rows": str(SR25519_FILE.relative_to(REPO_ROOT)),
        "bip39": documents["02"]["vectors"],
        "seeds": documents["03"]["vectors"],
        "junctions": documents["04"]["vectors"],
        "keypairs": [vector for vector in keypairs if not is_sr25519(vector)],
        "signatures": documents["06"]["vectors"],
        "json_keyring": [vector for vector in json_keyring if not is_sr25519(vector)],
        "ton": documents["08"]["vectors"],
        "independent_anchors": {
            "library": anchors["library"],
            "generated_with": "encodeAddress/decodeAddress and ethereumEncode over family 05 public keys",
            "input_sha256": sha256_bytes(anchors_raw),
            "ss58": anchors["ss58"],
            "ethereum_eip55": anchors["ethereum"],
        },
    }
    encodings = {
        "schema": SCHEMA,
        "provenance": common,
        "synthetic_only": True,
        "notes": {
            "01": documents["01"]["notes"],
            "12": documents["12"]["notes"],
            "selection": (
                f"Family 01 keeps every vector of at most {MAX_KEPT_HEX_CHARS} hex characters and every "
                "bound case."
            ),
        },
        "scale_canonical": scale_vectors(documents["01"]),
        "stored_secrets": stored,
    }
    sr25519 = {
        "schema": SCHEMA,
        "provenance": common,
        "synthetic_only": True,
        "notes": {"05": documents["05"]["notes"], "07": documents["07"]["notes"], "12": documents["12"]["notes"]},
        "keypairs": [vector for vector in keypairs if is_sr25519(vector)],
        "json_keyring": [vector for vector in json_keyring if is_sr25519(vector)],
        "stored_secrets": [vector for vector in stored if vector.get("cryptoType") == "SR25519"],
    }
    return {
        JVM_FILE: json.dumps(jvm, indent=1, ensure_ascii=False) + "\n",
        ENCODINGS_FILE: json.dumps(encodings, indent=1, ensure_ascii=False) + "\n",
        SR25519_FILE: json.dumps(sr25519, indent=1, ensure_ascii=False) + "\n",
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--al4", required=True, help="directory holding the AL-4 family files")
    parser.add_argument("--anchors", required=True, help="independent SS58/EIP-55 vectors (polkadot-js)")
    parser.add_argument("--check", action="store_true", help="fail instead of writing when outputs differ")
    args = parser.parse_args(argv)
    outputs = build(Path(args.al4), Path(args.anchors))
    stale = []
    for path, text in outputs.items():
        current = path.read_text(encoding="utf-8") if path.is_file() else None
        if current == text:
            continue
        if args.check:
            stale.append(path)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            print(f"[write] {path.relative_to(REPO_ROOT)} ({len(text.encode('utf-8'))} bytes)")
    for path in stale:
        print(f"[check] stale: {path.relative_to(REPO_ROOT)}", file=sys.stderr)
    return 1 if stale else 0


if __name__ == "__main__":
    sys.exit(main())
