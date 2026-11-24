# Solana send on Android (4.2.0)

Solana ships as an app-owned mainnet with send enabled, matching the browser extension and
iOS. This page records how Android meets the shared Solana contract and names the tests that
cover each point. No automated test sends on mainnet or a public testnet.

## Network

`UniversalWalletRegistry.solanaMainnet` defines chain id `solana:mainnet` (catalog id
`solana-mainnet`), genesis hash `5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d`, SOL with 9
decimals and the default path `m/44'/501'/0'/0'`. `nativeWalletChains()` merges it into the
downloaded registry, keeping recognized historical ids.

Tests: `NativeWalletChainsTest`, `SolanaKeyDerivationTest`, `UniversalWalletV2VectorsTest`,
`UniversalWalletRegistryContractTest`.

## Endpoints and failover

Balances, rent, fees, blockhashes, simulation, broadcast and signature status use JSON-RPC
only; nothing on mainnet depends on the Solana indexer. `NativeSendRpcEndpoints.resolve`
(built on `SolanaRpcEndpoints.ordered`) asks, in order:

1. the build's provider, if `FL_ANDROID_SOLANA_RPC_URL` is set (mainnet only; see below);
2. the node selected for the network;
3. `https://api.mainnet-beta.solana.com`, `https://solana-rpc.publicnode.com` and
   `https://public.rpc.solanavibestation.com`;
4. any other configured node, up to eight endpoints.

Devnet and private clusters never fall back to public mainnet nodes. Every request is bound to
the cluster genesis hash, rechecked on the endpoint before the call.

`FL_ANDROID_SOLANA_RPC_URL` is optional and read from `local.properties` or the environment;
the release workflow passes the repository secret of the same name. Empty means no provider.
Otherwise it must be an `https` URL with a host and no user info or fragment, with the provider
key in the URL path or query (the app sends no auth header). One pair of surrounding quotes is
dropped; any other malformed value, `http` included, fails the build without printing it.

A read moves to the next endpoint on transport errors, HTTP 408/429/5xx, any other HTTP 4xx
(an endpoint refusing the client; publicnode answers 403 to `getTokenAccountsByOwner`),
node-unavailable JSON-RPC codes (-32004, -32005, -32010, -32014, -32016, -32601), other
JSON-RPC errors and unreadable envelopes. A well-formed answer with an invalid value fails
closed.

`public.rpc.solanavibestation.com` needs no key and serves token-account reads for both token
programs, so SPL balances load even when mainnet-beta is busy and publicnode refuses them. It
answers HTTP 429 to requests less than about 600 ms apart, so every request to it, genesis
checks included, starts at least 700 ms after the previous one (`SolanaRequestPacer`, shared by
the whole app); a balance read it serves takes a few seconds. It never becomes the endpoint
that later calls try first. Both rules follow its host, so a configured copy of its URL with a
trailing slash, a query, different letter case or a trailing dot after the host shares the same
pace. Like every node in the list, it receives the wallet's address with
each balance read and the signed transaction if a send reaches it.

Tests: `SolanaRpcEndpointsTest`, `SolanaRpcEndpointRefusalTest`, `SolanaRateLimitedEndpointTest`,
`SolanaRpcClusterBindingTest`, `SolanaRpcClientTest`, `NativeSendRoutingTest`.

## Balances

`SolanaRpcBalanceSource` reads SOL first, then the SPL Token and Token-2022 inventories. If a
token read fails or is inconsistent on every endpoint, SOL is still updated and no token row
is touched, so last-known token amounts are kept and never zeroed; the network is marked
outdated with limited coverage until a complete read succeeds. Only a complete inventory can
zero a token it no longer lists.

Tests: `SolanaRpcBalanceSourceTest`, `SolanaBalanceLoaderTest`
(`refused token reads update SOL and keep the last token amount ...`).

## Recipients

The send screen checks a Solana recipient as a 32-byte base58 key
(`Chain.isValidRecipientAddress`); the generic Substrate format check does not apply to
app-owned networks. A payment to the wallet's own address asks for confirmation; Bitcoin
addresses are compared in their canonical lowercase form, Solana keys exactly. When the payment is quoted, `SolanaAccountPreflight` refuses programs,
token accounts and other program-owned accounts. A SOL recipient may be a signing key or an
existing plain System account at any address (for example a multisig vault); an off-curve
address with no System account is refused. Token sends require a signing owner key.

Tests: `RecipientAddressExtTest`, `NativeSendValidationTest`, `SolanaAccountPreflightTest`,
`SolanaPaymentPlannerTest`.

## Fees, rent and Max

`SolanaPaymentPlanner` builds the exact message that is signed, with ComputeBudget unit limit
and price, and quotes `getFeeForMessage` for it. Rent rules are checked before signing: a new
recipient must reach the rent-exempt minimum and the sender must keep either nothing or at
least that minimum. A token transfer to a holder without an associated token account creates
it idempotently; that rent is shown separately. Max leaves exactly zero SOL (or sends every
token unit).

Tests: `SolanaPaymentPlannerTest`, `SolanaPaymentMessageBuilderTest`,
`SolanaAccountPreflightTest`, and the private-chain test below.

## Token-2022

Token-2022 tokens are held, shown and received. Sending one is refused when the quote reads
the mint, before anything is built, with "This token can't be sent yet ... It stays in your
wallet, and you can still receive it" (as on iOS).

Tests: `SolanaPaymentPlannerTest` (`a Token-2022 mint is refused ...`),
`NativeSendPresentationTest`.

## Broadcast outcomes and the pending payment

Every transfer is simulated with signature verification. The signed transaction is retained
in `WalletSendJournal` before it leaves the device; if that write fails, nothing is sent. One
payment per wallet and network is in flight at a time, and a retry re-sends identical bytes.
Nothing is broadcast once the blockhash's last valid height has passed.

`RetrofitSolanaRpcClient.sendRawTransaction` offers the bytes to each endpoint once and
classifies a failure (`SolanaBroadcastException`):

- not sent: every endpoint refused the request (HTTP 4xx other than 408/429), no connection
  was made (unknown host, refused connection, no route, failed TLS handshake), or the
  endpoint's cluster could not be verified;
- refused: a node judged the transaction before relaying it: preflight failure (-32002),
  signature failure (-32003), invalid request or parameters (-32600, -32602) or undecodable
  bytes (-32700). No other endpoint is asked;
- handed over: a node that cannot take the transaction at all (-32601 method not offered,
  -32004, -32005, -32014, -32016 unhealthy or behind) passes it to the next endpoint;
- ambiguous: any other JSON-RPC error, such as a gateway's quota, auth or internal error
  (-32603, -32000, -32001, -32007, -32403, -32429), a timeout, a dropped connection, throttling,
  a server error or an unreadable reply. The bytes may have been relayed, so later endpoints
  are asked only to relay them (preflight skipped); if none accepts them the payment stays
  pending.

A first submission that is not sent or refused is released at once and the user is told
nothing left the wallet. Anything ambiguous stays pending. A payment settles only on a
confirmed or finalized status; a rooted status reported without `confirmationStatus`
(`confirmations` null) also counts, as long as the `confirmations` and `err` keys are present.

`RetrofitSolanaRpcClient.signatureOutcome` asks every endpoint at once, gives each 15 seconds,
and judges each answer on its own, because a lagging node or a load-balanced URL can find no
trace of a transaction that has landed. An endpoint proves expiry only when its finalized
`getEpochInfo` (slot and block height from one answer) is past the blockhash's last valid
height and its `getSignatureStatuses` lookup, asked afterwards with
`searchTransactionHistory`, answers from a slot at least that recent and finds nothing. A
confirmed status from any endpoint settles the payment at once; any other trace, from any
endpoint, keeps it pending. Without a trace, expiry stands only when every endpoint that
answered proves it, with one exception: when at least three endpoints answer, an endpoint whose
finalized slot is more than 1,000 slots behind the lower median of all answers abstains, so a
stalled node cannot keep a payment pending forever. The lower median keeps endpoints reporting
an absurdly high slot from silencing the others. An endpoint that fails or does not answer in
time does not count; with no answer at all the check fails and the payment stays pending. An
expiry result says how many distinct hosts proved it: hosts compare in lowercase without a
trailing dot, so two spellings of one node (for example a user-added copy of publicnode with a
trailing slash) count as one proof. Different host names run by one operator cannot be told
apart this way.

A payment that expired unseen is released, and the call that finds this stops there: the user
sees the message and confirms again before any new payment is signed or sent. Its history row
is closed before the record is removed, and the record stays if the row cannot be closed, so an
interrupted release is finished on the next check. Only an expiry that at least two distinct
hosts proved on their own, seen within six hours of signing, closes the row as failed and says
nothing left the wallet. One node's empty lookup can come from a backend that lacks that slot's
history, and nodes keep only part of the ledger, so an expiry proved by a single host (for
example when the others fail or time out) or seen later still releases the payment, but closes the row
as Unknown and asks the user to check the balance and the explorer before sending again. The
same rules apply in the Recent payment dialog, which keeps its explorer button after such a
release.

Tests: `SolanaBroadcastOutcomeTest`, `SolanaRpcPreflightTest`, `WalletSendJournalTest`,
`NativeSendExecutorTest`, `NativeSendRecoveryTest`, `NativeSendPresentationTest`,
`BitcoinTransferServiceProviderTest` (the send service's verdict mapping, including one run
through the production JSON-RPC client) and `NativeSendUiInstrumentedTest`.

## Remote veto

`solana_send_enabled` is veto-only and sticky (`docs/remote-feature-switches.md`): an absent
key keeps the shipped default (enabled), an explicit `false` is remembered, and a failed fetch
never re-enables.

Tests: `ProductFeatureToggleStoreTest`, `FeatureContinuityTest`, `SendPauseTransferGuardTest`,
`SendSetupPausedSendTest`, `BalanceDetailPausedActionsTest`.

## History

The Solana history service is optional. When it answers 404, serves another cluster, is down
or returns an unreadable page, the list keeps cached operations and says "Transaction history
for this network is not supported yet" under the "View history in explorer" link, which opens
`https://solscan.io/account/<address>` (as the browser extension does). Transaction links open
`https://solscan.io/tx/<signature>`.

Tests: `SolanaHistorySourceTest`, `TransactionHistoryProviderExplorerTest`,
`NativeSendRoutingTest`.

## Private-chain test

`NativePrivateChainSendInstrumentedTest` runs the production services against a disposable
local `solana-test-validator` on loopback (`scripts/validation/test-native-private-chain-sends.mjs`):
native and SPL sends with new and existing associated token accounts, exact-byte retry after a
lost reply, a broadcast that no node received (released and sent again with the same bytes),
a lost request that blocks other payments until the network proves its expiry (the call that
finds it stops, and confirming the next payment again sends it), and native Max.

Not covered: Solana staking, an NFT gallery and Solana Pay are deferred. A small funded mainnet
canary is a release-team task.
