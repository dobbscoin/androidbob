package finance.subgenius.dobbscoin;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SeedCipher {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "dobbscoin_wallet_seed_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private SeedCipher() {
    }

    static EncryptedPayload encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedPayload(
                Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP),
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt wallet seed", e);
        }
    }

    static String decrypt(EncryptedPayload payload) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(128, Base64.decode(payload.ivBase64, Base64.NO_WRAP));
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec);
            byte[] decrypted = cipher.doFinal(Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt wallet seed", e);
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        KeyStore.Entry existingEntry = keyStore.getEntry(KEY_ALIAS, null);
        if (existingEntry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) existingEntry).getSecretKey();
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build();
        keyGenerator.init(spec);
        return keyGenerator.generateKey();
    }

    static final class EncryptedPayload {
        final String ivBase64;
        final String ciphertextBase64;

        EncryptedPayload(String ivBase64, String ciphertextBase64) {
            this.ivBase64 = ivBase64;
            this.ciphertextBase64 = ciphertextBase64;
        }
    }
}
