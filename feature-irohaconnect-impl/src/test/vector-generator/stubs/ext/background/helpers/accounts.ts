// Harness stub: the fake keyring's accounts become Iroha accounts exactly as the extension shows them.
import { encodeIrohaI105Address } from '../../../../src/util/iroha.ts';
export const transformIrohaAccounts = ({ accounts }: { accounts: Record<string, any> }, network: 'taira' | 'nexus') =>
  Object.values(accounts).map((account: any) => ({
    address: encodeIrohaI105Address(account.json.meta.irohaPublicKeyHex, network),
    name: account.json.meta.name,
    publicKeyHex: account.json.meta.irohaPublicKeyHex,
  }));
