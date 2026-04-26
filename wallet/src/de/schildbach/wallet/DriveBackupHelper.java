package de.schildbach.wallet;

import android.content.Context;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.util.Collections;

/**
 * Uploads an encrypted wallet backup file to the user's Google Drive appDataFolder.
 * The appDataFolder is private to this app — it is not visible to other apps or the user
 * in the Drive UI.  No google-services.json is required; OAuth consent is handled by
 * Android's built-in Google account system via play-services-auth.
 */
public final class DriveBackupHelper {

    public static final String BACKUP_FILE_NAME = "dobbscoin-wallet-backup";

    private DriveBackupHelper() {}

    /**
     * Upload backupFile to appDataFolder, replacing any existing file with the same name.
     * Must be called from a background thread.
     */
    public static void upload(final Context context, final GoogleSignInAccount account,
            final java.io.File backupFile) throws IOException {
        final Drive drive = buildDrive(context, account);

        final FileList existing = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='" + BACKUP_FILE_NAME + "' and trashed=false")
                .setFields("files(id)")
                .execute();

        final FileContent content = new FileContent("application/octet-stream", backupFile);

        if (existing.getFiles() != null && !existing.getFiles().isEmpty()) {
            drive.files()
                    .update(existing.getFiles().get(0).getId(), null, content)
                    .execute();
        } else {
            final File metadata = new File();
            metadata.setName(BACKUP_FILE_NAME);
            metadata.setParents(Collections.singletonList("appDataFolder"));
            drive.files().create(metadata, content).setFields("id").execute();
        }
    }

    private static Drive buildDrive(final Context context, final GoogleSignInAccount account) {
        final GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context.getApplicationContext(),
                Collections.singleton(DriveScopes.DRIVE_APPDATA));
        credential.setSelectedAccount(account.getAccount());
        return new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("Dobbscoin Wallet")
                .build();
    }
}
