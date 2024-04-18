### Fearless Wallet Android
[![Google Play](https://img.shields.io/badge/Google%20Play-Android-green?logo=google%20play)](https://play.google.com/store/apps/details?id=jp.co.soramitsu.fearless)

![logo](/docs/fearlesswallet_promo.png)

## About
Fearless Wallet is a mobile wallet designed for the decentralized future on the Kusama network, with support on iOS and Android platforms. The best user experience, fast performance, and secure storage for your accounts. Development of Fearless Wallet is supported by Kusama Treasury grant.

[![](https://img.shields.io/twitter/follow/FearlessWallet?label=Follow&style=social)](https://twitter.com/FearlessWallet)

## Roadmap
Fearless Wallet roadmap is available for everyone: [roadmap link](https://soramitsucoltd.aha.io/shared/97bc3006ee3c1baa0598863615cf8d14)

## Dev Status
Track features development: [board link](https://soramitsucoltd.aha.io/shared/343e5db57d53398e3f26d0048158c4a2)

## How to build

To build Fearless Wallet Android project, you need to provide several keys either in environment variables or in `local.properties` file:

### Moonpay properties
``` 
MOONPAY_TEST_SECRET=stub
MOONPAY_PRODUCTION_SECRET=stub
```

Note, that with stub keys buy via moonpay will not work correctly. However, other parts of the application will not be affected.

### Sora CARD SDK

For starting Sora CARD SDK initial data have to be provided via gradle properties due to security purpose.

````
// PayWings repo credentials properties for getting artifacts
PAY_WINGS_REPOSITORY_URL
PAY_WINGS_USERNAME
PAY_WINGS_PASSWORD

// Sora CARD API key
SORA_CARD_API_KEY_TEST
SORA_CARD_API_KEY_PROD
SORA_CARD_DOMAIN_TEST
SORA_CARD_DOMAIN_PROD

// Sora CARD KYC credentials
SORA_CARD_KYC_ENDPOINT_URL_TEST
SORA_CARD_KYC_ENDPOINT_URL_PROD
SORA_CARD_KYC_USERNAME_TEST
SORA_CARD_KYC_USERNAME_PROD
SORA_CARD_KYC_PASSWORD_TEST
SORA_CARD_KYC_PASSWORD_PROD

// Sora CARD backend
SORA_BACKEND_DEBUG
SORA_BACKEND_RELEASE
````

### X1 plugin

X1 is a plugin which is embedded into webView. It requires url and id for launching.

````
X1_ENDPOINT_URL_RELEASE
X1_WIDGET_ID_RELEASE

X1_ENDPOINT_URL_DEBUG
X1_WIDGET_ID_DEBUG
````

### Ethereum properties

Set of params required to deliver Ethereum connection

````
// Ethereum blast api nodes keys
FL_BLAST_API_ETHEREUM_KEY
FL_BLAST_API_BSC_KEY
FL_BLAST_API_SEPOLIA_KEY
FL_BLAST_API_GOERLI_KEY
FL_BLAST_API_POLYGON_KEY

// Ethereum history providers api keys
FL_ANDROID_ETHERSCAN_API_KEY
FL_ANDROID_BSCSCAN_API_KEY
FL_ANDROID_POLYGONSCAN_API_KEY
````

## License
Fearless Wallet Android is available under the Apache 2.0 license. See the LICENSE file for more info.
