package finance.subgenius.dobbscoin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import androidx.biometric.BiometricManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SecurityStore {
    private static final String TAG = "SecurityStore";
    public static final long BACKGROUND_LOCK_TIMEOUT_MS = 30_000L;

    private static final String PREFS_NAME = "wallet_security";
    private static final String FALLBACK_PREFS_NAME = "wallet_security_fallback";
    private static final String KEY_PIN_HASH = "pin_hash";

    private static boolean unlockedThisSession;
    private static long backgroundedAtMs = -1L;

    private SecurityStore() {
    }

    public static boolean isPinConfigured(Context context) {
        return !prefs(context).getString(KEY_PIN_HASH, "").isEmpty();
    }

    public static void savePin(Context context, String pin) {
        prefs(context).edit().putString(KEY_PIN_HASH, hashPin(pin)).apply();
    }

    public static boolean verifyPin(Context context, String pin) {
        String storedHash = prefs(context).getString(KEY_PIN_HASH, "");
        return !storedHash.isEmpty() && storedHash.equals(hashPin(pin));
    }

    public static void markUnlocked() {
        unlockedThisSession = true;
        backgroundedAtMs = -1L;
    }

    public static void lockNow() {
        unlockedThisSession = false;
        backgroundedAtMs = -1L;
    }

    public static void noteBackgrounded(Context context) {
        if (isPinConfigured(context)) {
            backgroundedAtMs = SystemClock.elapsedRealtime();
        }
    }

    public static boolean shouldRequireUnlock(Context context) {
        if (!isPinConfigured(context)) {
            return false;
        }
        if (!unlockedThisSession) {
            return true;
        }
        if (backgroundedAtMs <= 0L) {
            return false;
        }

        long elapsed = SystemClock.elapsedRealtime() - backgroundedAtMs;
        backgroundedAtMs = -1L;
        if (elapsed >= BACKGROUND_LOCK_TIMEOUT_MS) {
            unlockedThisSession = false;
            return true;
        }
        return false;
    }

    public static boolean shouldShowSecurityOnStartup(Context context) {
        return !isPinConfigured(context) || !unlockedThisSession;
    }

    public static boolean isBiometricAvailable(Context context) {
        try {
            int result = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
            return result == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) {
            Log.e(TAG, "Biometric availability check failed", e);
            return false;
        }
    }

    private static SharedPreferences prefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Unable to initialize encrypted security storage, using fallback prefs", e);
            return context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    private static String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pin.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash PIN", e);
        }
    }
}
