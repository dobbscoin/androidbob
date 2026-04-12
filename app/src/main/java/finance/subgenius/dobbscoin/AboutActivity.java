package finance.subgenius.dobbscoin;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class AboutActivity extends Activity {
    private static final String API_HOST = "https://wallet.subgenius.finance/api";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
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
        title.setText("Dobbscoin Wallet");
        title.setTextColor(0xFF1F2933);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrapParams());

        LinearLayout card = buildCard();
        LinearLayout.LayoutParams cardParams = matchWrapParams();
        cardParams.topMargin = dp(16);
        root.addView(card, cardParams);

        card.addView(detailLine("Version", BuildConfig.VERSION_NAME), matchWrapParams());

        LinearLayout.LayoutParams buildParams = matchWrapParams();
        buildParams.topMargin = dp(12);
        card.addView(detailLine("Build", String.valueOf(BuildConfig.VERSION_CODE)), buildParams);

        LinearLayout.LayoutParams hostParams = matchWrapParams();
        hostParams.topMargin = dp(12);
        card.addView(detailLine("API Host", API_HOST), hostParams);

        LinearLayout.LayoutParams networkParams = matchWrapParams();
        networkParams.topMargin = dp(12);
        card.addView(detailLine("Network", "Mainnet"), networkParams);

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

    private TextView detailLine(String label, String value) {
        TextView view = new TextView(this);
        view.setText(label + ":\n" + value);
        view.setTextColor(0xFF334E68);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return view;
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
