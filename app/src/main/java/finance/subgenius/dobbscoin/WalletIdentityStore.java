package finance.subgenius.dobbscoin;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.util.UUID;

public final class WalletIdentityStore {
    private static final String TAG = "WalletIdentityStore";
    private static final String PREFS_NAME = "wallet_identity";
    private static final String FALLBACK_PREFS_NAME = "wallet_identity_fallback";
    private static final String KEY_WALLET_ID = "wallet_id";

    private WalletIdentityStore() {
    }

    public static String getOrCreateWalletId(Context context) {
        try {
            SharedPreferences prefs = prefs(context);
            String walletId = prefs.getString(KEY_WALLET_ID, "");
            if (walletId != null && !walletId.isEmpty()) {
                return walletId;
            }

            walletId = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_WALLET_ID, walletId).apply();
            Log.i(TAG, "Generated new wallet_id=" + walletId);
            return walletId;
        } catch (Exception e) {
            Log.e(TAG, "Secure wallet_id init failed, falling back to standard preferences", e);
            SharedPreferences fallbackPrefs = fallbackPrefs(context);
            String walletId = fallbackPrefs.getString(KEY_WALLET_ID, "");
            if (walletId != null && !walletId.isEmpty()) {
                return walletId;
            }

            walletId = UUID.randomUUID().toString();
            fallbackPrefs.edit().putString(KEY_WALLET_ID, walletId).apply();
            Log.i(TAG, "Generated fallback wallet_id=" + walletId);
            return walletId;
        }
    }

    public static String getWalletId(Context context) {
        try {
            return prefs(context).getString(KEY_WALLET_ID, "");
        } catch (Exception e) {
            Log.e(TAG, "Secure wallet_id read failed, using fallback preferences", e);
            return fallbackPrefs(context).getString(KEY_WALLET_ID, "");
        }
    }

    public static void restoreWalletId(Context context, String walletId) {
        if (walletId == null || walletId.isEmpty()) {
            return;
        }
        try {
            prefs(context).edit().putString(KEY_WALLET_ID, walletId).apply();
            Log.i(TAG, "Restored wallet_id=" + walletId);
        } catch (Exception e) {
            Log.e(TAG, "Secure wallet_id restore failed, using fallback preferences", e);
            fallbackPrefs(context).edit().putString(KEY_WALLET_ID, walletId).apply();
            Log.i(TAG, "Restored fallback wallet_id=" + walletId);
        }
    }

    private static SharedPreferences prefs(Context context) {
        try {
            Log.i(TAG, "Initializing secure wallet identity storage");
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
            throw new IllegalStateException("Unable to initialize wallet identity storage", e);
        }
    }

    private static SharedPreferences fallbackPrefs(Context context) {
        Log.i(TAG, "Initializing fallback wallet identity storage");
        return context.getApplicationContext().getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE);
    }
}
