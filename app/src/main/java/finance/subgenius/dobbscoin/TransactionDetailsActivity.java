package finance.subgenius.dobbscoin;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class TransactionDetailsActivity extends Activity {
    private static final int REQUEST_UNLOCK = 901;

    public static final String EXTRA_TXID = "txid";
    public static final String EXTRA_DATE = "date";
    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_CONFIRMATIONS = "confirmations";
    public static final String EXTRA_ADDRESS = "address";

    private boolean securityPromptActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
    }

    @Override
    protected void onStart() {
        super.onStart();
        maybeRequireUnlock();
    }

    @Override
    protected void onStop() {
        if (!securityPromptActive) {
            SecurityStore.noteBackgrounded(this);
        }
        super.onStop();
    }

    private ScrollView buildContentView() {
        Intent intent = getIntent();
        String txid = valueOrFallback(intent.getStringExtra(EXTRA_TXID));
        String date = valueOrFallback(intent.getStringExtra(EXTRA_DATE));
        String amount = valueOrFallback(intent.getStringExtra(EXTRA_AMOUNT));
        String confirmations = valueOrFallback(intent.getStringExtra(EXTRA_CONFIRMATIONS));
        String address = valueOrFallback(intent.getStringExtra(EXTRA_ADDRESS));

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
        title.setText("Transaction Details");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTextColor(0xFF1F2933);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrapParams());

        LinearLayout txidHeaderRow = new LinearLayout(this);
        txidHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams txidHeaderParams = matchWrapParams();
        txidHeaderParams.topMargin = dp(20);
        root.addView(txidHeaderRow, txidHeaderParams);

        TextView txidLabel = new TextView(this);
        txidLabel.setText("Full TXID");
        txidLabel.setTextColor(0xFF1F2933);
        txidLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        txidLabel.setTypeface(txidLabel.getTypeface(), android.graphics.Typeface.BOLD);
        txidHeaderRow.addView(txidLabel, weightedParams(0));

        Button headerCopyButton = new Button(new android.view.ContextThemeWrapper(this, R.style.WalletButtonSecondary), null, 0);
        headerCopyButton.setText("COPY TXID");
        headerCopyButton.setAllCaps(false);
        headerCopyButton.setOnClickListener(v -> copyTxid(txid));
        txidHeaderRow.addView(headerCopyButton, wrapParams(dp(8)));

        root.addView(detailValue(txid), detailParams(8));
        root.addView(detailLine("Amount", amount), detailParams(12));
        root.addView(detailLine("Confirmations", confirmations), detailParams(12));
        root.addView(detailLine("Date", date), detailParams(12));
        root.addView(detailLine("Address", address), detailParams(12));

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = matchWrapParams();
        rowParams.topMargin = dp(24);
        root.addView(buttonRow, rowParams);

        Button explorerButton = new Button(new android.view.ContextThemeWrapper(this, R.style.WalletButtonPrimary), null, 0);
        explorerButton.setText("View on Explorer");
        explorerButton.setAllCaps(false);
        explorerButton.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://explorer.dobbscoin.info/tx/" + txid));
            startActivity(browserIntent);
        });
        buttonRow.addView(explorerButton, weightedParams(0));

        return scrollView;
    }

    private TextView detailLine(String label, String value) {
        TextView view = new TextView(this);
        view.setText(label + ":\n" + value);
        view.setTextColor(0xFF334E68);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return view;
    }

    private TextView detailValue(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(0xFF334E68);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return view;
    }

    private LinearLayout.LayoutParams detailParams(int topMarginDp) {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightedParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = leftMargin;
        return params;
    }

    private LinearLayout.LayoutParams wrapParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = leftMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics()
        ));
    }

    private String valueOrFallback(String value) {
        return value == null || value.isEmpty() ? "Unavailable" : value;
    }

    private void copyTxid(String txid) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "Clipboard unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Transaction TXID", txid));
        Toast.makeText(this, "TXID copied.", Toast.LENGTH_SHORT).show();
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
        if (SecurityStore.shouldRequireUnlock(this)) {
            Intent intent = new Intent(this, SecurityActivity.class);
            intent.putExtra(SecurityActivity.EXTRA_MODE, SecurityActivity.MODE_UNLOCK);
            securityPromptActive = true;
            startActivityForResult(intent, REQUEST_UNLOCK);
        }
    }
}
