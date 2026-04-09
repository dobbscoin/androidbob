package finance.subgenius.dobbscoin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private static final int REQUEST_UNLOCK = 902;

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

        TextView description = bodyText("Security controls and wallet session actions.");
        card.addView(description, matchWrapParams());

        Button lockNowButton = createActionButton("LOCK NOW");
        lockNowButton.setOnClickListener(v -> {
            SecurityStore.lockNow();
            maybeRequireUnlock();
        });
        LinearLayout.LayoutParams buttonParams = matchWrapParams();
        buttonParams.topMargin = dp(20);
        card.addView(lockNowButton, buttonParams);

        return scrollView;
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
