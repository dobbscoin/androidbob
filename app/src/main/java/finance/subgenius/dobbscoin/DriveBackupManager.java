package finance.subgenius.dobbscoin;

import android.content.Context;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public final class DriveBackupManager {
    public static final String BACKUP_FILE_NAME = "dobbscoin_wallet_backup.dat";

    private final Context appContext;
    private final WalletManager walletManager;

    public DriveBackupManager(Context context, WalletManager walletManager) {
        this.appContext = context.getApplicationContext();
        this.walletManager = walletManager;
    }

    public void backupWallet(GoogleSignInAccount account) {
        try {
            WalletManager.EncryptedBackup backup = walletManager.exportEncryptedBackup();
            String walletId = WalletIdentityStore.getOrCreateWalletId(appContext);
            JSONObject payload = new JSONObject();
            payload.put("version", 1);
            payload.put("wallet_id", walletId);
            payload.put("seed_iv", backup.seedIvBase64);
            payload.put("seed_ciphertext", backup.seedCiphertextBase64);
            payload.put("receive_index", backup.receiveIndex);
            payload.put("schema_version", backup.schemaVersion);
            payload.put("updated_at", System.currentTimeMillis());

            Drive drive = buildDrive(account);
            FileList existing = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='" + BACKUP_FILE_NAME + "' and trashed=false")
                .setFields("files(id,name)")
                .execute();

            ByteArrayContent content = new ByteArrayContent(
                "application/octet-stream",
                payload.toString().getBytes(StandardCharsets.UTF_8)
            );

            if (existing.getFiles() != null && !existing.getFiles().isEmpty()) {
                drive.files().update(existing.getFiles().get(0).getId(), null, content).execute();
                return;
            }

            File metadata = new File();
            metadata.setName(BACKUP_FILE_NAME);
            metadata.setParents(Collections.singletonList("appDataFolder"));
            drive.files().create(metadata, content).setFields("id").execute();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to back up wallet to Google Drive", e);
        }
    }

    public void restoreWallet(GoogleSignInAccount account) {
        try {
            Drive drive = buildDrive(account);
            FileList existing = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='" + BACKUP_FILE_NAME + "' and trashed=false")
                .setFields("files(id,name)")
                .execute();

            if (existing.getFiles() == null || existing.getFiles().isEmpty()) {
                throw new IllegalStateException("No wallet backup found in Google Drive");
            }

            String fileId = existing.getFiles().get(0).getId();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream);
            JSONObject payload = new JSONObject(outputStream.toString(StandardCharsets.UTF_8.name()));
            WalletIdentityStore.restoreWalletId(appContext, payload.optString("wallet_id", ""));

            walletManager.restoreFromBackup(new WalletManager.EncryptedBackup(
                payload.getString("seed_iv"),
                payload.getString("seed_ciphertext"),
                payload.optInt("receive_index", 0),
                payload.optInt("schema_version", 1)
            ));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to restore wallet backup from Google Drive", e);
        }
    }

    private Drive buildDrive(GoogleSignInAccount account) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
            appContext,
            Collections.singleton(DriveScopes.DRIVE_APPDATA)
        );
        credential.setSelectedAccount(account.getAccount());
        return new Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Dobbscoin Wallet")
            .build();
    }
}
