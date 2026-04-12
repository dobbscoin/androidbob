package finance.subgenius.dobbscoin;

import android.content.Context;
import android.content.SharedPreferences;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.params.MainNetParams;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class WalletManager {
    private static final String PREFS_NAME = "wallet_deterministic";
    private static final String KEY_SEED_IV = "seed_iv";
    private static final String KEY_SEED_CIPHERTEXT = "seed_ciphertext";
    private static final String KEY_RECEIVE_INDEX = "receive_index";
    private static final String KEY_SCHEMA_VERSION = "schema_version";
    private static final int CURRENT_SCHEMA_VERSION = 1;

    private static final List<ChildNumber> ACCOUNT_PATH = Arrays.asList(
        new ChildNumber(44, true),
        new ChildNumber(0, true),
        new ChildNumber(0, true),
        ChildNumber.ZERO
    );

    private final Context appContext;
    private final NetworkParameters networkParameters = MainNetParams.get();

    public WalletManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public boolean hasSeed() {
        return !prefs().getString(KEY_SEED_CIPHERTEXT, "").isEmpty();
    }

    public String ensureWallet() {
        if (hasSeed()) {
            return getReceiveAddress();
        }
        generateSeed();
        return getReceiveAddress();
    }

    public String generateSeed() {
        try {
            byte[] entropy = new byte[16];
            new SecureRandom().nextBytes(entropy);
            List<String> words = MnemonicCode.INSTANCE.toMnemonic(entropy);
            String mnemonic = joinWords(words);
            storeSeed(mnemonic);
            prefs().edit()
                .putInt(KEY_RECEIVE_INDEX, 0)
                .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                .apply();
            return mnemonic;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate BIP39 seed", e);
        }
    }

    public LoadedWallet loadWallet() {
        String mnemonic = exportSeed();
        int receiveIndex = prefs().getInt(KEY_RECEIVE_INDEX, 0);
        return new LoadedWallet(mnemonic, receiveIndex, deriveAddress(receiveIndex));
    }

    public String exportSeed() {
        String ciphertext = prefs().getString(KEY_SEED_CIPHERTEXT, "");
        String iv = prefs().getString(KEY_SEED_IV, "");
        if (ciphertext.isEmpty() || iv.isEmpty()) {
            throw new IllegalStateException("Wallet seed has not been created");
        }
        return SeedCipher.decrypt(new SeedCipher.EncryptedPayload(iv, ciphertext));
    }

    public String getReceiveAddress() {
        ensureSeedExists();
        return deriveAddress(prefs().getInt(KEY_RECEIVE_INDEX, 0));
    }

    public String getNextAddress() {
        ensureSeedExists();
        int nextIndex = prefs().getInt(KEY_RECEIVE_INDEX, 0) + 1;
        prefs().edit().putInt(KEY_RECEIVE_INDEX, nextIndex).apply();
        return deriveAddress(nextIndex);
    }

    public int getCurrentReceiveIndex() {
        return prefs().getInt(KEY_RECEIVE_INDEX, 0);
    }

    public String getAddressForIndex(int index) {
        ensureSeedExists();
        return deriveAddress(index);
    }

    public List<String> getTrackedAddresses() {
        ensureSeedExists();
        int currentIndex = Math.max(0, getCurrentReceiveIndex());
        List<String> addresses = new ArrayList<>();
        for (int index = 0; index <= currentIndex; index++) {
            addresses.add(deriveAddress(index));
        }
        return addresses;
    }

    public String getPrivateKey(String address) {
        ensureSeedExists();
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("Address is required");
        }

        int currentIndex = Math.max(0, getCurrentReceiveIndex());
        for (int index = 0; index <= currentIndex; index++) {
            if (address.equals(deriveAddress(index))) {
                DeterministicKey key = getKeyForIndex(index);
                return key.getPrivateKeyAsWiF(networkParameters);
            }
        }

        throw new IllegalArgumentException("Address does not belong to this wallet");
    }

    public DeterministicKey getKeyForIndex(int index) {
        ensureSeedExists();
        List<String> words = splitMnemonic(exportSeed());
        byte[] seedBytes = MnemonicCode.toSeed(words, "");
        DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seedBytes);
        DeterministicKey key = masterKey;
        for (ChildNumber child : ACCOUNT_PATH) {
            key = HDKeyDerivation.deriveChildKey(key, child);
        }
        key = HDKeyDerivation.deriveChildKey(key, new ChildNumber(index, false));
        return key;
    }

    public EncryptedBackup exportEncryptedBackup() {
        ensureSeedExists();
        return new EncryptedBackup(
            prefs().getString(KEY_SEED_IV, ""),
            prefs().getString(KEY_SEED_CIPHERTEXT, ""),
            prefs().getInt(KEY_RECEIVE_INDEX, 0),
            prefs().getInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
        );
    }

    public void restoreFromBackup(EncryptedBackup backup) {
        if (backup.seedIvBase64 == null || backup.seedIvBase64.isEmpty()
            || backup.seedCiphertextBase64 == null || backup.seedCiphertextBase64.isEmpty()) {
            throw new IllegalArgumentException("Backup payload is incomplete");
        }
        prefs().edit()
            .putString(KEY_SEED_IV, backup.seedIvBase64)
            .putString(KEY_SEED_CIPHERTEXT, backup.seedCiphertextBase64)
            .putInt(KEY_RECEIVE_INDEX, Math.max(0, backup.receiveIndex))
            .putInt(KEY_SCHEMA_VERSION, Math.max(1, backup.schemaVersion))
            .apply();
    }

    private void storeSeed(String mnemonic) {
        SeedCipher.EncryptedPayload payload = SeedCipher.encrypt(mnemonic);
        prefs().edit()
            .putString(KEY_SEED_IV, payload.ivBase64)
            .putString(KEY_SEED_CIPHERTEXT, payload.ciphertextBase64)
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .apply();
    }

    private String deriveAddress(int index) {
        DeterministicKey key = getKeyForIndex(index);
        Address address = LegacyAddress.fromKey(networkParameters, key);
        return address.toString();
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void ensureSeedExists() {
        if (!hasSeed()) {
            throw new IllegalStateException("Wallet seed has not been created");
        }
    }

    private String joinWords(List<String> words) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(words.get(i));
        }
        return builder.toString();
    }

    private List<String> splitMnemonic(String mnemonic) {
        String[] parts = mnemonic.trim().split("\\s+");
        return new ArrayList<>(Arrays.asList(parts));
    }

    public static final class LoadedWallet {
        public final String mnemonic;
        public final int receiveIndex;
        public final String receiveAddress;

        LoadedWallet(String mnemonic, int receiveIndex, String receiveAddress) {
            this.mnemonic = mnemonic;
            this.receiveIndex = receiveIndex;
            this.receiveAddress = receiveAddress;
        }
    }

    public static final class EncryptedBackup {
        public final String seedIvBase64;
        public final String seedCiphertextBase64;
        public final int receiveIndex;
        public final int schemaVersion;

        public EncryptedBackup(String seedIvBase64, String seedCiphertextBase64, int receiveIndex, int schemaVersion) {
            this.seedIvBase64 = seedIvBase64;
            this.seedCiphertextBase64 = seedCiphertextBase64;
            this.receiveIndex = receiveIndex;
            this.schemaVersion = schemaVersion;
        }
    }
}
