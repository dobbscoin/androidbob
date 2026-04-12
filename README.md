# Dobbscoin Android Wallet

Android wallet client for the Dobbscoin (BOB) network, with a small companion API service that mediates blockchain access.

This document summarizes the current implementation for developers and operators. It describes the code as it exists now, including deterministic wallet support, device-local seed storage, a persistent `wallet_id`, and Google Drive backup of encrypted wallet metadata.

## 1. What This Project Is

This project is an Android application that provides a mobile wallet interface for Dobbscoin users.

The current implementation is a hybrid design:

- The Android app now creates and stores a deterministic BIP39 seed locally.
- Receive addresses are derived locally on the device.
- A per-installation `wallet_id` is generated locally and sent to the API server on every request.
- The mobile app still relies on an external wallet API service for balance queries, history queries, network status, fee estimation, and transaction broadcast/submission.

This is not yet a fully independent SPV or full-node wallet. It is a mobile client with local identity and partial local wallet capabilities, backed by a server-side API and an RPC-connected Dobbscoin node.

## 2. System Architecture

Current high-level architecture:

```text
Android App
  ├─ Local secure storage
  │   ├─ EncryptedSharedPreferences
  │   ├─ Android Keystore
  │   ├─ PIN / biometric session controls
  │   └─ Deterministic wallet metadata
  │
  ├─ Google Sign-In / Google Drive AppData backup
  │
  └─ HTTPS calls with X-Wallet-ID header
          ↓
Wallet API Service
  ├─ Validates X-Wallet-ID
  ├─ Exposes REST endpoints
  └─ Calls Dobbscoin RPC
          ↓
Dobbscoin Full Node / Wallet RPC
          ↓
Dobbscoin Blockchain Network
```

Main components:

- Android UI and wallet logic: `app/src/main/java/finance/subgenius/dobbscoin/`
- Android secure storage: `SecurityStore`, `SeedCipher`, `WalletIdentityStore`
- Deterministic wallet functions: `WalletManager`
- Local transaction-building primitives: `TransactionSigner`
- Encrypted Drive backup: `DriveBackupManager`
- API server: separate Node/Express service in `/home/btcbob/wallet-api/server.js`

## 3. How The Android Wallet Works

The wallet is a native Android app built in Java with programmatic UI construction.

At runtime it does the following:

- Shows a splash screen and launches the security flow if the app is locked.
- Creates or loads a persistent `wallet_id` on startup.
- Creates or loads a deterministic wallet seed.
- Derives a receive address locally from the HD wallet path `m/44'/0'/0'/0/index`.
- Loads network status, balance, transaction history, and fee estimates from the API server.
- Sends transactions through the server-side API after local user authorization.
- Provides settings actions for seed export, Google Drive backup, and Google Drive restore.

The app currently uses:

- `SplashScreenActivity` for startup
- `SecurityActivity` for PIN and biometric authentication
- `MainActivity` for main wallet functionality
- `SettingsActivity` for security and backup actions
- `TransactionDetailsActivity` for transaction drill-down

## 4. How The Wallet Communicates With The Server

The Android app communicates with the API over HTTP(S) JSON endpoints.

Current endpoint set:

- `GET /status`
- `GET /address`
- `GET /balance`
- `GET /history`
- `GET /estimatesmartfee`
- `POST /send`

The app sends a required request header on every API call:

```text
X-Wallet-ID: <uuid>
```

Current request flow:

1. App loads or creates the local `wallet_id`
2. App sends the header on each GET/POST request
3. API validates the header before route logic
4. API calls Dobbscoin RPC methods on the node
5. API returns JSON to the app

The server is now expected to reject missing or malformed wallet IDs with:

```json
{
  "success": false,
  "error": "Invalid wallet ID"
}
```

## 5. How Wallet Identity Works (`wallet_id`)

`wallet_id` is a persistent, per-installation UUID used as an identity label for server-side account isolation.

Properties:

- Generated using `UUID.randomUUID()`
- Stored in `EncryptedSharedPreferences`
- Persisted across app restarts
- Deleted automatically when the app is uninstalled, because app storage is removed
- Restored if the app restores from the encrypted Google Drive backup payload

Important constraints:

- `wallet_id` is not derived from the seed
- `wallet_id` is not derived from IMEI, Android ID, MAC address, email, or any hardware identifier
- `wallet_id` is not a private key
- `wallet_id` is not used for cryptographic signing

Current code roles:

- `WalletIdentityStore` creates, reads, and restores the UUID
- `SplashScreenActivity` and `MainActivity` log it at startup
- `MainActivity` attaches it to every API request
- API middleware validates it globally

## 6. How Balances And Transactions Are Handled

### Balances

Balance data currently comes from the API server, which in turn queries the Dobbscoin node.

Display behavior:

- Balances are formatted with `BigDecimal`
- All visible BOB amounts are displayed with exactly 8 decimal places
- Values smaller than `0.00000001` display as `0.00000000`

Examples:

- `1.00000000`
- `0.00988429`
- `0.00000001`
- `0.00000000`

### Transactions

Transaction history currently comes from the API server.

The app:

- Requests transaction history from `/history`
- Sorts transactions by time descending
- Displays txid, date, amount, confirmations, and address
- Opens a detail screen for a single transaction

### Send Flow

Current send behavior is still API-assisted:

1. User enters destination address and amount
2. App estimates fee
3. App shows a confirmation dialog
4. User authorizes with PIN or biometrics
5. App submits the send request to the API server

The code now includes local transaction-building and signing primitives, but the server API remains the authoritative send path because the current API does not yet expose the full UTXO and raw-transaction broadcast interfaces needed for fully independent end-to-end local signing.

## 7. How Google Drive Backup And Restore Works

The app supports encrypted backup to the user’s Google Drive AppData folder.

Backup file:

- `dobbscoin_wallet_backup.dat`

Google components:

- Google Sign-In
- Google Drive API
- Drive AppData scope

What gets backed up:

- Encrypted seed ciphertext
- Encryption IV for the stored seed payload
- Current receive address index
- Wallet schema version
- Persistent `wallet_id`

What does not get backed up directly:

- Plaintext seed phrase
- PIN
- Biometric templates
- Full transaction history cache

Backup flow:

1. User taps `Backup to Google Drive`
2. App requires unlock
3. App requires Google authentication
4. App serializes encrypted wallet metadata
5. App writes the file into Drive AppData

Restore flow:

1. User taps `Restore from Backup`
2. App requires unlock
3. App requires Google authentication
4. App loads the AppData file
5. App restores encrypted wallet metadata and `wallet_id`

Important note:

The backup stores the encrypted seed blob, not the plaintext mnemonic. Practical restore behavior therefore depends on Android Keystore compatibility and the expected recovery model of the device environment.

## 8. What Security Mechanisms Are Currently Implemented

Current security controls include:

- PIN-based app access
- Optional biometric unlock
- Session lock after background timeout
- Manual lock action in Settings
- EncryptedSharedPreferences for sensitive local state
- Android Keystore-backed seed encryption
- Required `X-Wallet-ID` header validation on the API server
- Encrypted Google Drive backup content

Security-relevant classes:

- `SecurityStore`
- `SecurityActivity`
- `SeedCipher`
- `WalletManager`
- `WalletIdentityStore`
- `DriveBackupManager`

What is intentionally not stored in plaintext:

- Seed phrase at rest
- PIN hash input
- Wallet ID storage payload

## 9. What Data Is Stored Locally On The Device

Current local data includes:

- Encrypted deterministic wallet seed
- Seed encryption IV
- Current receive derivation index
- Wallet schema version
- Encrypted PIN hash
- Session lock state in memory
- Persistent `wallet_id`
- Google account session state managed by Google Play Services
- Normal Android app preferences and UI state

The app does not currently embed server secrets or RPC credentials.

## 10. What Happens On Uninstall / Reinstall

### Uninstall

When the app is uninstalled:

- Android app data is removed
- Local encrypted preferences are deleted
- The stored seed blob is deleted
- The local PIN configuration is deleted
- The local `wallet_id` is deleted

### Reinstall without restore

When reinstalled without restoring backup:

- A new `wallet_id` is generated
- A new local deterministic wallet is created if needed
- Previous local identity and local wallet metadata are not automatically recovered

### Reinstall with restore

If the user restores from the Drive backup:

- The encrypted wallet metadata is restored
- The prior `wallet_id` is restored
- The receive derivation index is restored

Operationally, this means reinstall behavior depends on the presence of a valid Drive backup and on the compatibility of the encrypted wallet restore model.

## 11. What External Services Are Required

### Required for normal wallet operation

- Dobbscoin full node with wallet/RPC enabled
- Wallet API server reachable by the Android app
- Network connectivity between app, API server, and node

### Required for backup features

- Google Play Services on the Android device
- Google Sign-In support
- Google Drive API access for the authenticated user

### Development requirements

- Java 17
- Android SDK / Gradle toolchain
- Android device or emulator for testing

## 12. Current Limitations Or Risks

Current limitations and risks include:

- The app is not yet a fully self-contained non-custodial wallet.
- Balance and transaction history still depend on the server API.
- The server-side API still drives the final send/broadcast path.
- The current API surface does not expose a complete UTXO set or raw transaction broadcast flow for full local-signing independence.
- Google Drive backup currently stores encrypted wallet metadata, so restore semantics are tied to the encryption/storage design and must be validated carefully across real device restore scenarios.
- The API server currently appears to use a single backend wallet context unless server-side account isolation is expanded beyond header validation.
- `wallet_id` is an identity label, not an authorization primitive; it should not be treated as a secret.
- API transport and server deployment security remain operational responsibilities outside the app repo.
- Logging should be reviewed in production environments to avoid overexposing operational metadata.

## 13. Next Logical Development Milestones

Recommended next milestones:

1. Server-side wallet isolation keyed by `wallet_id`
2. UTXO query endpoint(s) for client-side transaction construction
3. Raw transaction broadcast endpoint so the server only broadcasts signed hex
4. Full end-to-end local signing path with no server-side signing assumptions
5. Seed export UX hardening, including better operator/user warnings
6. Restore-path validation across uninstall/reinstall and real-device migration scenarios
7. Backup versioning and migration support for future wallet schema changes
8. Better observability and audit logging on the API service
9. Stronger automated tests for wallet identity, secure storage, and backup/restore
10. Optional address gap management and deterministic account discovery logic

## Build

Build the Android app:

```bash
./gradlew assembleDebug
```

Expected debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Operator Notes

- Do not place RPC credentials in the Android app.
- Terminate TLS in front of the API service.
- Treat the API and RPC node as production infrastructure.
- Validate backup/restore behavior on target Android versions before relying on it operationally.
- If `wallet_id` is used for account partitioning on the server, define and document the exact mapping policy there.
