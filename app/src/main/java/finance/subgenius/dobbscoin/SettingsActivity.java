package finance.subgenius.dobbscoin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.services.drive.DriveScopes;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {
    private static final String TAG = "SettingsActivity";
    private static final int REQUEST_UNLOCK = 902;
    private static final int REQUEST_PROTECTED_ACTION = 903;
    private static final int REQUEST_DRIVE_SIGN_IN = 904;

    private boolean securityPromptActive;
    private PendingAction pendingAction = PendingAction.NONE;
    private WalletManager walletManager;
    private DriveBackupManager driveBackupManager;
    private ExecutorService executor;
    private Button exportSeedButton;
    private Button exportPrivateKeyButton;
    private Button backupButton;
    private Button restoreButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        walletManager = new WalletManager(this);
        driveBackupManager = new DriveBackupManager(this, walletManager);
        executor = Executors.newSingleThreadExecutor();
        setContentView(buildContentView());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (SecurityStore.shouldRequireUnlock(this)) {
            maybeRequireUnlock(REQUEST_UNLOCK);
        }
    }

    @Override
    protected void onStop() {
        if (!securityPromptActive) {
            SecurityStore.noteBackgrounded(this);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }

    private ViewGroup buildContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFFF3EFE7);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(0xFF1F2933);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrapParams());

        LinearLayout card = buildCard();
        LinearLayout.LayoutParams cardParams = matchWrapParams();
        cardParams.topMargin = dp(16);
        root.addView(card, cardParams);

        TextView description = bodyText("Security controls, seed export, and encrypted backup actions.");
        card.addView(description, matchWrapParams());

        exportSeedButton = createActionButton("Export Seed");
        exportSeedButton.setOnClickListener(v -> requestProtectedAction(PendingAction.EXPORT_SEED));
        LinearLayout.LayoutParams exportParams = matchWrapParams();
        exportParams.topMargin = dp(20);
        card.addView(exportSeedButton, exportParams);

        exportPrivateKeyButton = createActionButton("Export Private Key");
        exportPrivateKeyButton.setOnClickListener(v -> requestProtectedAction(PendingAction.EXPORT_PRIVATE_KEY));
        LinearLayout.LayoutParams exportPrivateKeyParams = matchWrapParams();
        exportPrivateKeyParams.topMargin = dp(12);
        card.addView(exportPrivateKeyButton, exportPrivateKeyParams);

        backupButton = createActionButton("Backup to Google Drive");
        backupButton.setOnClickListener(v -> requestProtectedAction(PendingAction.BACKUP));
        LinearLayout.LayoutParams backupParams = matchWrapParams();
        backupParams.topMargin = dp(12);
        card.addView(backupButton, backupParams);

        restoreButton = createActionButton("Restore from Backup");
        restoreButton.setOnClickListener(v -> requestProtectedAction(PendingAction.RESTORE));
        LinearLayout.LayoutParams restoreParams = matchWrapParams();
        restoreParams.topMargin = dp(12);
        card.addView(restoreButton, restoreParams);

        Button lockNowButton = createActionButton("Lock Now");
        lockNowButton.setOnClickListener(v -> {
            SecurityStore.lockNow();
            maybeRequireUnlock(REQUEST_UNLOCK);
        });
        LinearLayout.LayoutParams lockParams = matchWrapParams();
        lockParams.topMargin = dp(12);
        card.addView(lockNowButton, lockParams);

        Button aboutButton = createActionButton("About / Version");
        aboutButton.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        LinearLayout.LayoutParams aboutParams = matchWrapParams();
        aboutParams.topMargin = dp(12);
        card.addView(aboutButton, aboutParams);

        return scrollView;
    }

    private void requestProtectedAction(PendingAction action) {
        pendingAction = action;
        maybeRequireUnlock(REQUEST_PROTECTED_ACTION);
    }

    private void startProtectedAction() {
        switch (pendingAction) {
            case EXPORT_SEED:
                showSeedDialog();
                pendingAction = PendingAction.NONE;
                return;
            case EXPORT_PRIVATE_KEY:
                startActivity(new Intent(this, ExportPrivateKeyActivity.class));
                pendingAction = PendingAction.NONE;
                return;
            case BACKUP:
            case RESTORE:
                launchDriveSignIn();
                return;
            default:
                return;
        }
    }

    private void showSeedDialog() {
        try {
            String seed = walletManager.exportSeed();
            new AlertDialog.Builder(this)
                .setTitle("Seed Phrase")
                .setMessage(seed)
                .setPositiveButton("Close", null)
                .show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void launchDriveSignIn() {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(new Scope(DriveScopes.DRIVE_APPDATA))
            .build();
        GoogleSignInClient client = GoogleSignIn.getClient(this, options);
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && GoogleSignIn.hasPermissions(account, new Scope(DriveScopes.DRIVE_APPDATA))) {
            runDriveAction(account);
            return;
        }
        startActivityForResult(client.getSignInIntent(), REQUEST_DRIVE_SIGN_IN);
    }

    private void runDriveAction(GoogleSignInAccount account) {
        setActionButtonsEnabled(false);
        executor.execute(() -> {
            try {
                if (pendingAction == PendingAction.BACKUP) {
                    driveBackupManager.backupWallet(account);
                    runOnUiThread(() -> Toast.makeText(
                        SettingsActivity.this,
                        "Encrypted wallet backup saved to Google Drive.",
                        Toast.LENGTH_LONG
                    ).show());
                } else if (pendingAction == PendingAction.RESTORE) {
                    driveBackupManager.restoreWallet(account);
                    runOnUiThread(() -> Toast.makeText(
                        SettingsActivity.this,
                        "Wallet restored from Google Drive backup.",
                        Toast.LENGTH_LONG
                    ).show());
                }
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.isEmpty()) {
                    message = "Backup operation failed.";
                }
                final String errorMessage = message;
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, errorMessage, Toast.LENGTH_LONG).show());
            } finally {
                runOnUiThread(() -> {
                    pendingAction = PendingAction.NONE;
                    setActionButtonsEnabled(true);
                });
            }
        });
    }

    private void setActionButtonsEnabled(boolean enabled) {
        if (exportSeedButton != null) {
            exportSeedButton.setEnabled(enabled);
        }
        if (backupButton != null) {
            backupButton.setEnabled(enabled);
        }
        if (exportPrivateKeyButton != null) {
            exportPrivateKeyButton.setEnabled(enabled);
        }
        if (restoreButton != null) {
            restoreButton.setEnabled(enabled);
        }
    }

    private LinearLayout buildCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        card.setPadding(padding, padding, padding, padding);
        card.setBackgroundResource(R.drawable.wallet_card_surface);
        return card;
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(0xFF334E68);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return view;
    }

    private Button createActionButton(String label) {
        Button button = new Button(new android.view.ContextThemeWrapper(this, R.style.WalletButtonSecondary), null, 0);
        button.setText(label);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        return button;
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics()
        ));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_UNLOCK || requestCode == REQUEST_PROTECTED_ACTION) {
            securityPromptActive = false;
            if (resultCode == RESULT_OK) {
                SecurityStore.markUnlocked();
                if (requestCode == REQUEST_PROTECTED_ACTION) {
                    startProtectedAction();
                }
            } else if (requestCode == REQUEST_UNLOCK) {
                finish();
            } else {
                pendingAction = PendingAction.NONE;
            }
            return;
        }
        if (requestCode == REQUEST_DRIVE_SIGN_IN) {
            Task<GoogleSignInAccount> signInTask = GoogleSignIn.getSignedInAccountFromIntent(data);
            if (resultCode != RESULT_OK) {
                logDriveSignInFailure(signInTask);
                pendingAction = PendingAction.NONE;
                setActionButtonsEnabled(true);
                Toast.makeText(this, "Google Drive authentication was cancelled.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                GoogleSignInAccount account = signInTask.getResult(ApiException.class);
                runDriveAction(account);
            } catch (ApiException e) {
                logDriveSignInFailure(signInTask);
                pendingAction = PendingAction.NONE;
                setActionButtonsEnabled(true);
                Toast.makeText(this, "Google Drive authentication was cancelled.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void logDriveSignInFailure(Task<GoogleSignInAccount> signInTask) {
        Exception exception = signInTask == null ? null : signInTask.getException();
        int statusCode = -1;
        String statusMessage = "unknown";
        if (exception instanceof ApiException) {
            ApiException apiException = (ApiException) exception;
            statusCode = apiException.getStatusCode();
            statusMessage = apiException.getStatusMessage();
        }
        Log.e(
            TAG,
            "Google Drive sign-in failed. statusCode=" + statusCode
                + ", statusMessage=" + statusMessage
                + ", exception=" + exception,
            exception
        );
    }

    private void maybeRequireUnlock(int requestCode) {
        if (securityPromptActive) {
            return;
        }
        Intent intent = new Intent(this, SecurityActivity.class);
        intent.putExtra(SecurityActivity.EXTRA_MODE, SecurityActivity.MODE_UNLOCK);
        securityPromptActive = true;
        startActivityForResult(intent, requestCode);
    }

    private enum PendingAction {
        NONE,
        EXPORT_SEED,
        EXPORT_PRIVATE_KEY,
        BACKUP,
        RESTORE
    }
}
