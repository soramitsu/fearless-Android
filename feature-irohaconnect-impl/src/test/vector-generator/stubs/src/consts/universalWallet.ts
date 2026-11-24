const UNIVERSAL_WALLET_DERIVATION_PATHS = {
  substrateRoot: '',
  evmDefault: "m/44'/60'/0'/0/0",
  bitcoinMainnetAccount: "m/84'/0'/0'",
  bitcoinMainnetFirstReceive: "m/84'/0'/0'/0/0",
  bitcoinTestnetAccount: "m/84'/1'/0'",
  bitcoinTestnetFirstReceive: "m/84'/1'/0'/0/0",
  solanaDefault: "m/44'/501'/0'/0'",
  tonDefault: "m/44'/607'/0'/0'/0'",
  irohaDefault: "m/44'/617'/0'/0'",
} as const;
const UNIVERSAL_WALLET_IROHA_NETWORKS = {
  taira: {
    id: 'taira-testnet',
    chainId: 'fc56984b-2be7-431d-840e-21514d1883f0',
    chainDiscriminant: 369,
    toriiBaseUrl: 'https://taira.sora.org',
    // Genesis hash of the deployed Taira ledger (2026-10-22 reset). Transfers are signed for this
    // exact network and refused when the node reports another genesis; update it after a reset.
    networkId: 'hash:4978D0BD15FC32B834672B27BBD8A03653B1DF060A1EEE7E9374D29AF3886D91#6774',
    enabledByDefault: true,
    nativeAsset: {
      id: '6TEAJqbb8oEPmLncoNiMRbLEK6tw',
      symbol: 'XOR',
      decimals: 9,
      name: 'XOR',
    },
  },
  nexus: {
    id: 'sora-nexus-mainnet',
    chainId: 'sora:nexus:global',
    chainDiscriminant: 753,
    toriiBaseUrl: 'https://minamoto.sora.org',
    enabledByDefault: false,
  },
} as const;
export { UNIVERSAL_WALLET_IROHA_NETWORKS, UNIVERSAL_WALLET_DERIVATION_PATHS };
