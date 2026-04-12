package finance.subgenius.dobbscoin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class ExportPrivateKeyActivity extends Activity {
    private static final int REQUEST_UNLOCK = 911;

    private WalletManager walletManager;
    private boolean securityPromptActive;
    private EditText privateKeyView;
    private Button copyButton;
    private String revealedPrivateKey = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        walletManager = new WalletManager(this);
        setContentView(buildContentView());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (SecurityStore.shouldRequireUnlock(this)) {
            maybeRequireUnlock();
        }
    }

    @Override
    protected void onStop() {
        if (!securityPromptActive) {
            SecurityStore.noteBackgrounded(this);
        }
        super.onStop();
    }

    private View buildContentView() {
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
        title.setText("Export Private Key");
        title.setTextColor(0xFF1F2933);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrapParams());

        LinearLayout card = buildCard();
        LinearLayout.LayoutParams cardParams = matchWrapParams();
        cardParams.topMargin = dp(16);
        root.addView(card, cardParams);

        TextView warning = bodyText("Reveal the private key for the current receive address only if you understand the risk.");
        card.addView(warning, matchWrapParams());

        Button revealButton = createActionButton("Reveal Private Key");
        revealButton.setOnClickListener(v -> showRevealWarningDialog());
        LinearLayout.LayoutParams revealParams = matchWrapParams();
        revealParams.topMargin = dp(20);
        card.addView(revealButton, revealParams);

        privateKeyView = new EditText(this);
        privateKeyView.setText("");
        privateKeyView.setKeyListener(null);
        privateKeyView.setFocusable(false);
        privateKeyView.setClickable(false);
        privateKeyView.setLongClickable(false);
        privateKeyView.setTextIsSelectable(true);
        privateKeyView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        privateKeyView.setTypeface(Typeface.MONOSPACE);
        privateKeyView.setMinLines(3);
        privateKeyView.setGravity(Gravity.START);
        privateKeyView.setBackgroundResource(R.drawable.wallet_input_surface);
        privateKeyView.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams privateKeyParams = matchWrapParams();
        privateKeyParams.topMargin = dp(16);
        card.addView(privateKeyView, privateKeyParams);

        copyButton = createActionButton("Copy to Clipboard");
        copyButton.setEnabled(false);
        copyButton.setOnClickListener(v -> copyPrivateKey());
        LinearLayout.LayoutParams copyParams = matchWrapParams();
        copyParams.topMargin = dp(12);
        card.addView(copyButton, copyParams);

        return scrollView;
    }

    private void showRevealWarningDialog() {
        new AlertDialog.Builder(this)
            .setTitle("WARNING")
            .setMessage("Anyone with this private key can spend your funds.\n\nContinue?")
            .setPositiveButton("Continue", (dialog, which) -> revealPrivateKey())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void revealPrivateKey() {
        try {
            String address = walletManager.getReceiveAddress();
            revealedPrivateKey = walletManager.getPrivateKey(address);
            privateKeyView.setText(revealedPrivateKey);
            copyButton.setEnabled(true);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copyPrivateKey() {
        if (revealedPrivateKey == null || revealedPrivateKey.isEmpty()) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "Clipboard unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Dobbscoin private key", revealedPrivateKey));
        Toast.makeText(this, "Private key copied", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_UNLOCK) {
            securityPromptActive = false;
            if (resultCode == RESULT_OK) {
                SecurityStore.markUnlocked();
            } else {
                finish();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void maybeRequireUnlock() {
        if (securityPromptActive) {
            return;
        }
        Intent intent = new Intent(this, SecurityActivity.class);
        intent.putExtra(SecurityActivity.EXTRA_MODE, SecurityActivity.MODE_UNLOCK);
        securityPromptActive = true;
        startActivityForResult(intent, REQUEST_UNLOCK);
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
}
