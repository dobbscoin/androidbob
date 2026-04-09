package finance.subgenius.dobbscoin;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.TextViewCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String ADDRESS_URL = "https://wallet.subgenius.finance/api/address";
    private static final String BALANCE_URL = "https://wallet.subgenius.finance/api/balance";
    private static final String HISTORY_URL = "https://wallet.subgenius.finance/api/history";
    private static final String STATUS_URL = "https://wallet.subgenius.finance/api/status";
    private static final String SEND_URL = "https://wallet.subgenius.finance/api/send";
    private static final String ESTIMATE_SMART_FEE_URL = "https://wallet.subgenius.finance/api/estimatesmartfee";
    private static final int TAB_MAIN = 0;
    private static final int TAB_RECEIVE = 1;
    private static final int TAB_TRANSACTIONS = 2;
    private static final int TAB_SEND = 3;
    private static final int REQUEST_CAMERA_PERMISSION = 701;
    private static final int REQUEST_APP_UNLOCK = 801;
    private static final int REQUEST_SEND_AUTH = 802;
    private static final long AUTO_REFRESH_INTERVAL_MS = 5000L;
    private static final int CARD_PADDING_DP = 16;
    private static final int SECTION_SPACING_DP = 16;
    private static final int QR_SIZE_DP = 280;
    private static final BigDecimal DEFAULT_FEE = new BigDecimal("0.00100000");
    private static final BigDecimal FALLBACK_FEE_RATE = new BigDecimal("0.00400000");
    private static final BigDecimal ESTIMATED_TX_SIZE_KB = new BigDecimal("0.25");
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.US);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefreshRunnable = this::runAutoRefresh;
    private final Runnable hideSendSuccessRunnable = this::hideSendSuccessMessage;

    private TextView balanceView;
    private TextView mainAddressView;
    private TextView addressView;
    private TextView connectionStatusView;
    private TextView transactionsTitleView;
    private TextView feeView;
    private TextView totalView;
    private TextView sendStatusView;
    private TextView sendErrorView;
    private TextView sendSuccessTitleView;
    private TextView sendSuccessTxidView;
    private Button sendButton;
    private TextView mainTabLink;
    private TextView receiveTabLink;
    private TextView transactionsTabLink;
    private TextView sendTabLink;
    private ImageView qrCodeView;
    private ImageButton scanQrButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View mainTabContent;
    private View receiveTabContent;
    private View transactionsTabContent;
    private View sendTabContent;
    private LinearLayout historyContainer;
    private LinearLayout sendProgressContainer;
    private LinearLayout sendSuccessContainer;
    private EditText sendAddressInput;
    private EditText sendAmountInput;
    private volatile boolean loading;
    private volatile boolean addressRefreshLoading;
    private volatile boolean sendLoading;
    private volatile boolean feeEstimateLoading;
    private boolean autoRefreshEnabled;
    private boolean securityPromptActive;
    private boolean initialWalletLoadCompleted;
    private String currentAddress = "";
    private BigDecimal currentFee = DEFAULT_FEE;
    private String pendingSendAddress;
    private BigDecimal pendingSendAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(buildContentView());
        renderConnectionState(ConnectionState.SYNCING);
        showTab(TAB_MAIN);
        if (!maybeRequireSecurity(false)) {
            loadWalletData(true);
            initialWalletLoadCompleted = true;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (maybeRequireSecurity(false)) {
            return;
        }
        autoRefreshEnabled = true;
        scheduleNextAutoRefresh(AUTO_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onStop() {
        autoRefreshEnabled = false;
        refreshHandler.removeCallbacks(autoRefreshRunnable);
        if (!securityPromptActive) {
            SecurityStore.noteBackgrounded(this);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacks(autoRefreshRunnable);
        refreshHandler.removeCallbacks(hideSendSuccessRunnable);
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildContentView() {
        swipeRefreshLayout = null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F3EFE7"));
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(headerRow, matchWrapParams());

        TextView titleView = new TextView(this);
        titleView.setText("Dobbscoin Wallet");
        titleView.setTextColor(Color.parseColor("#1F2933"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        titleView.setSingleLine(true);
        headerRow.addView(titleView, weightedMatchParams(0));

        connectionStatusView = new TextView(this);
        connectionStatusView.setBackgroundResource(R.drawable.wallet_status_badge);
        connectionStatusView.setPadding(dp(12), dp(6), dp(12), dp(6));
        connectionStatusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        connectionStatusView.setTypeface(connectionStatusView.getTypeface(), android.graphics.Typeface.BOLD);
        headerRow.addView(connectionStatusView, wrapParams());

        LinearLayout.LayoutParams tabBarParams = matchWrapParams();
        tabBarParams.topMargin = dp(16);
        root.addView(buildTabBar(), tabBarParams);

        FrameLayout contentContainer = new FrameLayout(this);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        );
        contentParams.topMargin = dp(16);
        root.addView(contentContainer, contentParams);

        mainTabContent = buildMainScreen();
        receiveTabContent = buildReceiveScreen();
        transactionsTabContent = buildTransactionsScreen();
        sendTabContent = buildSendScreen();

        contentContainer.addView(mainTabContent, frameMatchParams());
        contentContainer.addView(sendTabContent, frameMatchParams());
        contentContainer.addView(receiveTabContent, frameMatchParams());
        contentContainer.addView(transactionsTabContent, frameMatchParams());

        return root;
    }

    private View buildTabBar() {
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundResource(R.drawable.wallet_card_surface);
        int paddingHorizontal = dp(6);
        int paddingVertical = dp(2);
        tabBar.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);

        mainTabLink = createNavLink("MAIN", true);
        mainTabLink.setOnClickListener(v -> showTab(TAB_MAIN));
        tabBar.addView(mainTabLink, equalWeightParams(0));

        sendTabLink = createNavLink("SEND", false);
        sendTabLink.setOnClickListener(v -> showTab(TAB_SEND));
        tabBar.addView(sendTabLink, equalWeightParams(0));

        receiveTabLink = createNavLink("RECEIVE", false);
        receiveTabLink.setOnClickListener(v -> showTab(TAB_RECEIVE));
        tabBar.addView(receiveTabLink, equalWeightParams(0));

        transactionsTabLink = createNavLink("TX's", false);
        transactionsTabLink.setOnClickListener(v -> showTab(TAB_TRANSACTIONS));
        tabBar.addView(transactionsTabLink, equalWeightParams(0));

        return tabBar;
    }

    private View buildMainScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        ScrollView scrollView = buildScreenScroll();
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        );
        root.addView(scrollView, scrollParams);

        LinearLayout content = buildScreenContent(scrollView);

        content.addView(buildScreenSectionTitle("Balance"), matchWrapParams());

        balanceView = bodyText("Loading balance...");
        balanceView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        balanceView.setTextColor(Color.parseColor("#102A43"));
        LinearLayout.LayoutParams balanceParams = matchWrapParams();
        balanceParams.topMargin = dp(8);
        content.addView(balanceView, balanceParams);

        TextView addressLabel = buildScreenSectionTitle("Receive Address");
        LinearLayout.LayoutParams addressLabelParams = matchWrapParams();
        addressLabelParams.topMargin = dp(24);
        content.addView(addressLabel, addressLabelParams);

        mainAddressView = bodyText("Loading receive address...");
        LinearLayout.LayoutParams mainAddressParams = matchWrapParams();
        mainAddressParams.topMargin = dp(8);
        content.addView(mainAddressView, mainAddressParams);

        LinearLayout settingsContainer = new LinearLayout(this);
        settingsContainer.setOrientation(LinearLayout.HORIZONTAL);
        settingsContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        settingsContainer.setBackgroundResource(R.drawable.wallet_card_surface);
        LinearLayout.LayoutParams settingsContainerParams = matchWrapParams();
        settingsContainerParams.topMargin = dp(16);
        root.addView(settingsContainer, settingsContainerParams);

        TextView settingsTab = createNavLink("SETTINGS", false);
        settingsTab.setMinHeight(dp(56));
        settingsTab.setMinimumHeight(dp(56));
        settingsTab.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        settingsContainer.addView(settingsTab, matchWrapParams());

        return root;
    }

    private View buildReceiveScreen() {
        ScrollView scrollView = buildScreenScroll();
        LinearLayout content = buildScreenContent(scrollView);

        content.addView(buildScreenSectionTitle("Receive Address"), matchWrapParams());

        addressView = bodyText("Loading receive address...");
        LinearLayout.LayoutParams addressParams = matchWrapParams();
        addressParams.topMargin = dp(8);
        content.addView(addressView, addressParams);

        TextView qrLabel = buildScreenSectionTitle("QR Code");
        LinearLayout.LayoutParams qrLabelParams = matchWrapParams();
        qrLabelParams.topMargin = dp(24);
        content.addView(qrLabel, qrLabelParams);

        qrCodeView = new ImageView(this);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(QR_SIZE_DP), dp(QR_SIZE_DP));
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.topMargin = dp(12);
        qrCodeView.setBackgroundColor(Color.WHITE);
        qrCodeView.setPadding(dp(10), dp(10), dp(10), dp(10));
        content.addView(qrCodeView, qrParams);

        return scrollView;
    }

    private View buildTransactionsScreen() {
        ScrollView scrollView = buildScreenScroll();
        LinearLayout content = buildScreenContent(scrollView);

        transactionsTitleView = buildScreenSectionTitle("Transactions");
        content.addView(transactionsTitleView, matchWrapParams());

        historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams historyParams = matchWrapParams();
        historyParams.topMargin = dp(8);
        content.addView(historyContainer, historyParams);
        historyContainer.addView(bodyText("Loading transaction history..."), matchWrapParams());

        return scrollView;
    }

    private View buildSendScreen() {
        ScrollView scrollView = buildScreenScroll();
        LinearLayout card = buildScreenContent(scrollView);

        card.addView(buildScreenSectionTitle("Recipient Address"), matchWrapParams());

        TextView addressLabel = bodyText("Address");
        addressLabel.setTextColor(Color.parseColor("#1F2933"));
        LinearLayout.LayoutParams addressLabelParams = matchWrapParams();
        addressLabelParams.topMargin = dp(12);
        card.addView(addressLabel, addressLabelParams);

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams addressRowParams = matchWrapParams();
        addressRowParams.topMargin = dp(8);
        card.addView(addressRow, addressRowParams);

        sendAddressInput = new EditText(this);
        sendAddressInput.setHint("Enter destination address");
        sendAddressInput.setSingleLine(true);
        sendAddressInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        sendAddressInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        sendAddressInput.setBackgroundResource(R.drawable.wallet_input_surface);
        addressRow.addView(sendAddressInput, weightedMatchParams(0));

        scanQrButton = new ImageButton(this);
        scanQrButton.setImageResource(android.R.drawable.ic_menu_camera);
        scanQrButton.setBackgroundResource(R.drawable.wallet_button_secondary);
        scanQrButton.setOnClickListener(v -> launchQrScanner());
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        scanParams.leftMargin = dp(8);
        addressRow.addView(scanQrButton, scanParams);

        TextView amountSectionLabel = buildScreenSectionTitle("Amount");
        LinearLayout.LayoutParams amountSectionParams = matchWrapParams();
        amountSectionParams.topMargin = dp(24);
        card.addView(amountSectionLabel, amountSectionParams);

        TextView amountLabel = bodyText("Amount");
        amountLabel.setTextColor(Color.parseColor("#1F2933"));
        LinearLayout.LayoutParams amountLabelParams = matchWrapParams();
        amountLabelParams.topMargin = dp(12);
        card.addView(amountLabel, amountLabelParams);

        sendAmountInput = new EditText(this);
        sendAmountInput.setHint("0.00");
        sendAmountInput.setSingleLine(true);
        sendAmountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sendAmountInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        sendAmountInput.setBackgroundResource(R.drawable.wallet_input_surface);
        LinearLayout.LayoutParams amountInputParams = matchWrapParams();
        amountInputParams.topMargin = dp(8);
        card.addView(sendAmountInput, amountInputParams);
        sendAmountInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                updateFeeAndTotalViews();
            }
        });

        sendButton = createActionButton("Send", R.style.WalletButtonPrimary);
        sendButton.setOnClickListener(v -> showSendConfirmationDialog());
        LinearLayout.LayoutParams sendButtonParams = matchWrapParams();
        sendButtonParams.topMargin = dp(20);
        card.addView(sendButton, sendButtonParams);

        sendProgressContainer = new LinearLayout(this);
        sendProgressContainer.setOrientation(LinearLayout.HORIZONTAL);
        sendProgressContainer.setGravity(Gravity.CENTER_VERTICAL);
        sendProgressContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = matchWrapParams();
        progressParams.topMargin = dp(16);
        card.addView(sendProgressContainer, progressParams);

        ProgressBar sendProgressBar = new ProgressBar(this);
        sendProgressContainer.addView(sendProgressBar, wrapParams());

        sendStatusView = bodyText("Sending transaction...");
        LinearLayout.LayoutParams statusParams = wrapParams();
        statusParams.leftMargin = dp(12);
        sendProgressContainer.addView(sendStatusView, statusParams);

        sendSuccessContainer = new LinearLayout(this);
        sendSuccessContainer.setOrientation(LinearLayout.VERTICAL);
        sendSuccessContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams successParams = matchWrapParams();
        successParams.topMargin = dp(16);
        card.addView(sendSuccessContainer, successParams);

        sendSuccessTitleView = bodyText("✔ Transaction Sent");
        sendSuccessTitleView.setTextColor(Color.parseColor("#15803D"));
        sendSuccessTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        sendSuccessTitleView.setTypeface(sendSuccessTitleView.getTypeface(), android.graphics.Typeface.BOLD);
        sendSuccessContainer.addView(sendSuccessTitleView, matchWrapParams());

        sendSuccessTxidView = bodyText("");
        LinearLayout.LayoutParams successTxidParams = matchWrapParams();
        successTxidParams.topMargin = dp(8);
        sendSuccessContainer.addView(sendSuccessTxidView, successTxidParams);

        sendErrorView = bodyText("");
        sendErrorView.setTextColor(Color.parseColor("#B91C1C"));
        sendErrorView.setVisibility(View.GONE);
        LinearLayout.LayoutParams errorParams = matchWrapParams();
        errorParams.topMargin = dp(16);
        card.addView(sendErrorView, errorParams);

        maybeRefreshFeeEstimate();
        updateFeeAndTotalViews();
        return scrollView;
    }

    private TextView createNavLink(String label, boolean selected) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(4), dp(14), dp(4), dp(12));
        view.setMinHeight(dp(44));
        view.setClickable(true);
        view.setFocusable(true);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(view, 9, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        styleNavLink(view, selected);
        return view;
    }

    private void styleNavLink(TextView view, boolean selected) {
        view.setBackgroundResource(selected ? R.drawable.wallet_nav_active : R.drawable.wallet_nav_inactive);
        view.setTextColor(selected ? Color.parseColor("#9A3412") : Color.parseColor("#475569"));
        view.setTypeface(view.getTypeface(), selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        view.setAlpha(selected ? 1f : 0.82f);
    }

    private void showTab(int tab) {
        setTabVisibility(mainTabContent, tab == TAB_MAIN);
        setTabVisibility(receiveTabContent, tab == TAB_RECEIVE);
        setTabVisibility(transactionsTabContent, tab == TAB_TRANSACTIONS);
        setTabVisibility(sendTabContent, tab == TAB_SEND);
        styleNavLink(mainTabLink, tab == TAB_MAIN);
        styleNavLink(sendTabLink, tab == TAB_SEND);
        styleNavLink(receiveTabLink, tab == TAB_RECEIVE);
        styleNavLink(transactionsTabLink, tab == TAB_TRANSACTIONS);
    }

    private void setTabVisibility(View view, boolean visible) {
        if (visible) {
            if (view.getVisibility() != View.VISIBLE) {
                view.setAlpha(0f);
                view.setVisibility(View.VISIBLE);
                view.animate().alpha(1f).setDuration(180).start();
            }
        } else {
            view.animate().cancel();
            view.setVisibility(View.GONE);
        }
    }

    private boolean maybeRequireSecurity(boolean forSend) {
        if (securityPromptActive) {
            return true;
        }

        if (!SecurityStore.isPinConfigured(this)) {
            Intent intent = new Intent(this, SecurityActivity.class);
            intent.putExtra(SecurityActivity.EXTRA_MODE, SecurityActivity.MODE_SETUP);
            securityPromptActive = true;
            startActivityForResult(intent, REQUEST_APP_UNLOCK);
            return true;
        }

        if (forSend || SecurityStore.shouldRequireUnlock(this)) {
            Intent intent = new Intent(this, SecurityActivity.class);
            intent.putExtra(
                SecurityActivity.EXTRA_MODE,
                forSend ? SecurityActivity.MODE_SEND_AUTH : SecurityActivity.MODE_UNLOCK
            );
            securityPromptActive = true;
            startActivityForResult(intent, forSend ? REQUEST_SEND_AUTH : REQUEST_APP_UNLOCK);
            return true;
        }

        return false;
    }

    private void loadWalletData(boolean includeAddress) {
        if (loading) {
            return;
        }
        loading = true;

        executor.execute(() -> {
            try {
                WalletData walletData = fetchWalletData(includeAddress);
                runOnUiThread(() -> renderWalletData(walletData));
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.isEmpty()) {
                    message = e.getClass().getSimpleName();
                }
                String errorMessage = "Unable to load wallet data. " + message;
                runOnUiThread(() -> renderError(errorMessage, includeAddress));
            } finally {
                runOnUiThread(() -> {
                    loading = false;
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
        });
    }

    private void runAutoRefresh() {
        if (!autoRefreshEnabled) {
            return;
        }
        if (!loading && !addressRefreshLoading) {
            loadWalletData(false);
        }
        scheduleNextAutoRefresh(AUTO_REFRESH_INTERVAL_MS);
    }

    private void scheduleNextAutoRefresh(long delayMs) {
        refreshHandler.removeCallbacks(autoRefreshRunnable);
        if (!autoRefreshEnabled) {
            return;
        }
        refreshHandler.postDelayed(autoRefreshRunnable, delayMs);
    }

    private void refreshAddress() {
        if (addressRefreshLoading || loading) {
            return;
        }
        addressRefreshLoading = true;

        executor.execute(() -> {
            try {
                JSONObject addressPayload = fetchJson(ADDRESS_URL);
                if (!addressPayload.has("address")) {
                    throw new IllegalStateException("Missing address field");
                }
                String address = addressPayload.getString("address");
                runOnUiThread(() -> renderAddress(address));
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.isEmpty()) {
                    message = e.getClass().getSimpleName();
                }
                final String toastMessage = "Unable to refresh address. " + message;
                runOnUiThread(() -> Toast.makeText(
                    MainActivity.this,
                    toastMessage,
                    Toast.LENGTH_SHORT
                ).show());
            } finally {
                runOnUiThread(() -> {
                    addressRefreshLoading = false;
                });
            }
        });
    }

    private void maybeRefreshFeeEstimate() {
        if (feeEstimateLoading || sendLoading) {
            return;
        }
        feeEstimateLoading = true;
        executor.execute(() -> {
            BigDecimal estimatedFee = DEFAULT_FEE;
            try {
                JSONObject payload = fetchJson(ESTIMATE_SMART_FEE_URL);
                estimatedFee = parseEstimatedFee(payload);
            } catch (Exception ignored) {
                estimatedFee = DEFAULT_FEE;
            }
            final BigDecimal result = estimatedFee;
            runOnUiThread(() -> {
                feeEstimateLoading = false;
                currentFee = result;
                updateFeeAndTotalViews();
            });
        });
    }

    private WalletData fetchWalletData(boolean includeAddress) throws Exception {
        JSONObject statusPayload = fetchJson(STATUS_URL);
        JSONObject balancePayload = fetchJson(BALANCE_URL);
        JSONObject historyPayload = fetchJson(HISTORY_URL);
        JSONObject addressPayload = includeAddress ? fetchJson(ADDRESS_URL) : null;
        return parseWalletData(statusPayload, addressPayload, balancePayload, historyPayload, includeAddress);
    }

    private WalletData parseWalletData(
        JSONObject statusPayload,
        JSONObject addressPayload,
        JSONObject balancePayload,
        JSONObject historyPayload,
        boolean includeAddress
    ) throws JSONException {
        if (!balancePayload.has("balance")) {
            throw new IllegalStateException("Missing balance field");
        }
        if (!statusPayload.has("blocks")) {
            throw new IllegalStateException("Missing blocks field");
        }
        if (includeAddress) {
            if (addressPayload == null || !addressPayload.has("address")) {
                throw new IllegalStateException("Missing address field");
            }
        }

        JSONArray transactions = historyPayload.optJSONArray("transactions");
        if (transactions == null) {
            throw new IllegalStateException("Missing transactions field");
        }

        long blockHeight = statusPayload.getLong("blocks");
        String balance = balancePayload.get("balance").toString();
        String address = includeAddress ? addressPayload.getString("address") : null;

        ConnectionState connectionState = parseConnectionState(statusPayload);

        return new WalletData(blockHeight, balance, address, transactions, connectionState);
    }

    private void renderWalletData(WalletData walletData) {
        balanceView.setText("Balance: " + walletData.balance + " BOB");
        renderConnectionState(walletData.connectionState);
        if (walletData.address != null) {
            renderAddress(walletData.address);
        }
        renderTransactions(walletData.transactions);
    }

    private void renderAddress(String address) {
        currentAddress = address;
        if (addressView != null) {
            addressView.setText(address);
        }
        if (mainAddressView != null) {
            mainAddressView.setText(address);
        }
        renderQrCode(address);
    }

    private void lockWalletNow() {
        SecurityStore.lockNow();
        maybeRequireSecurity(false);
    }

    private void showSendConfirmationDialog() {
        if (sendLoading) {
            return;
        }
        String address = sendAddressInput.getText().toString().trim();
        String amountText = sendAmountInput.getText().toString().trim();
        if (address.isEmpty()) {
            sendAddressInput.setError("Address is required");
            return;
        }
        BigDecimal amount = parseAmount(amountText);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            sendAmountInput.setError("Enter a valid amount");
            return;
        }
        BigDecimal total = amount.add(currentFee);
        String message = "Send amount: " + formatBob(amount)
            + "\nDestination address: " + address
            + "\nFee: " + formatBob(currentFee)
            + "\nTotal: " + formatBob(total);
        new AlertDialog.Builder(this)
            .setTitle("Confirm Send")
            .setMessage(message)
            .setPositiveButton("CONFIRM", (dialog, which) -> authenticateAndSend(address, amount))
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void authenticateAndSend(String address, BigDecimal amount) {
        pendingSendAddress = address;
        pendingSendAmount = amount;
        if (!maybeRequireSecurity(true)) {
            performSend(address, amount);
        }
    }

    private void performSend(String address, BigDecimal amount) {
        if (sendLoading) {
            return;
        }
        sendLoading = true;
        sendButton.setEnabled(false);
        sendButton.setText("Sending...");
        sendProgressContainer.setVisibility(View.VISIBLE);
        sendStatusView.setText("Sending transaction...");
        sendErrorView.setVisibility(View.GONE);
        hideSendSuccessRunnable.run();
        refreshHandler.removeCallbacks(hideSendSuccessRunnable);

        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("address", address);
                payload.put("amount", amount.stripTrailingZeros().toPlainString());
                JSONObject response = postJson(SEND_URL, payload);
                String txid = extractTxid(response);
                runOnUiThread(() -> renderSendSuccess(txid));
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.isEmpty()) {
                    message = e.getClass().getSimpleName();
                }
                final String errorMessage = message;
                runOnUiThread(() -> renderSendError(errorMessage));
            } finally {
                runOnUiThread(() -> {
                    sendLoading = false;
                    sendButton.setEnabled(true);
                    sendButton.setText("Send");
                    sendProgressContainer.setVisibility(View.GONE);
                });
            }
        });
    }

    private void renderSendSuccess(String txid) {
        sendSuccessTxidView.setText("TXID: " + txid);
        sendSuccessContainer.setAlpha(0f);
        sendSuccessContainer.setVisibility(View.VISIBLE);
        sendSuccessContainer.animate().alpha(1f).setDuration(250).start();
        sendAmountInput.setText("");
        sendErrorView.setVisibility(View.GONE);
        clearPendingSend();
        refreshHandler.removeCallbacks(hideSendSuccessRunnable);
        refreshHandler.postDelayed(hideSendSuccessRunnable, 3000L);
        loadWalletData(false);
    }

    private void renderSendError(String message) {
        sendSuccessContainer.setVisibility(View.GONE);
        sendErrorView.setText("Send failed\n" + message);
        sendErrorView.setVisibility(View.VISIBLE);
        clearPendingSend();
    }

    private void hideSendSuccessMessage() {
        if (sendSuccessContainer != null) {
            sendSuccessContainer.animate().cancel();
            sendSuccessContainer.setVisibility(View.GONE);
        }
    }

    private void clearPendingSend() {
        pendingSendAddress = null;
        pendingSendAmount = null;
    }

    private void updateFeeAndTotalViews() {
        BigDecimal amount = parseAmount(sendAmountInput == null ? "" : sendAmountInput.getText().toString().trim());
        BigDecimal total = (amount == null ? BigDecimal.ZERO : amount).add(currentFee);
        if (feeView != null) {
            feeView.setText("Fee: " + formatBob(currentFee));
        }
        if (totalView != null) {
            totalView.setText("Total: " + formatBob(total));
        }
    }

    private void launchQrScanner() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        try {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            integrator.setPrompt("Scan Dobbscoin address");
            integrator.setBeepEnabled(false);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "QR scanner is unavailable on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                String scannedAddress = extractAddressFromQr(result.getContents());
                sendAddressInput.setText(scannedAddress);
                showTab(TAB_SEND);
            }
            return;
        }
        if (requestCode == REQUEST_APP_UNLOCK) {
            securityPromptActive = false;
            if (resultCode == RESULT_OK) {
                SecurityStore.markUnlocked();
                if (!initialWalletLoadCompleted) {
                    loadWalletData(true);
                    initialWalletLoadCompleted = true;
                }
                autoRefreshEnabled = true;
                scheduleNextAutoRefresh(AUTO_REFRESH_INTERVAL_MS);
            } else {
                finish();
            }
            return;
        }
        if (requestCode == REQUEST_SEND_AUTH) {
            securityPromptActive = false;
            if (resultCode == RESULT_OK && pendingSendAddress != null && pendingSendAmount != null) {
                SecurityStore.markUnlocked();
                performSend(pendingSendAddress, pendingSendAmount);
            } else {
                clearPendingSend();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchQrScanner();
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR codes.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void renderTransactions(JSONArray transactions) {
        List<TransactionItem> items = buildSortedTransactions(transactions);
        historyContainer.removeAllViews();
        transactionsTitleView.setText("Transactions (" + items.size() + ")");

        if (items.isEmpty()) {
            TextView emptyView = bodyText("No transactions yet.");
            historyContainer.addView(emptyView, matchWrapParams());
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            historyContainer.addView(buildTransactionCard(items.get(i), i), transactionCardParams(i));
        }
    }

    private List<TransactionItem> buildSortedTransactions(JSONArray transactions) {
        List<TransactionItem> items = new ArrayList<>();
        for (int i = 0; i < transactions.length(); i++) {
            items.add(TransactionItem.from(transactions.opt(i), i + 1));
        }
        Collections.sort(items, Comparator.comparingLong(TransactionItem::sortTime).reversed());
        return items;
    }

    private View buildTransactionCard(TransactionItem item, int index) {
        LinearLayout card = buildNestedCard();
        card.setBackgroundResource(R.drawable.wallet_transaction_row);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openTransactionDetails(item));

        TextView title = new TextView(this);
        title.setText(item.displayTxid());
        title.setTextColor(Color.parseColor("#102A43"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        card.addView(title, matchWrapParams());

        addTransactionDetail(card, "Date", item.date);
        addTransactionDetail(card, "Amount", item.amount);
        addTransactionDetail(card, "Confirmations", item.confirmations);
        addTransactionDetail(card, "Address", item.address);

        return card;
    }

    private void openTransactionDetails(TransactionItem item) {
        Intent intent = new Intent(this, TransactionDetailsActivity.class);
        intent.putExtra(TransactionDetailsActivity.EXTRA_TXID, item.fullTxid());
        intent.putExtra(TransactionDetailsActivity.EXTRA_DATE, item.date);
        intent.putExtra(TransactionDetailsActivity.EXTRA_AMOUNT, item.amount);
        intent.putExtra(TransactionDetailsActivity.EXTRA_CONFIRMATIONS, item.confirmations);
        intent.putExtra(TransactionDetailsActivity.EXTRA_ADDRESS, item.address);
        startActivity(intent);
    }

    private void addTransactionDetail(LinearLayout parent, String label, String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return;
        }
        parent.addView(detailLine(label, value), detailParams());
    }

    private TextView detailLine(String label, String value) {
        TextView view = new TextView(this);
        view.setText(label + ": " + value);
        view.setTextColor(Color.parseColor("#334E68"));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        return view;
    }

    private void copyAddressToClipboard() {
        if (currentAddress == null || currentAddress.isEmpty()) {
            Toast.makeText(this, "No address available to copy.", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "Clipboard unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Dobbscoin address", currentAddress));
        Toast.makeText(this, "Address copied.", Toast.LENGTH_SHORT).show();
    }

    private void renderQrCode(String address) {
        try {
            int size = dp(QR_SIZE_DP) - dp(20);
            BitMatrix matrix = new QRCodeWriter().encode(address, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            qrCodeView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            qrCodeView.setImageBitmap(null);
            addressView.setText(address + "\n\nQR code unavailable.");
        }
    }

    private void renderError(String errorMessage, boolean clearAddress) {
        if (balanceView != null) {
            balanceView.setText(errorMessage);
        }
        if (clearAddress) {
            currentAddress = "";
            if (addressView != null) {
                addressView.setText("Unable to load receive address.");
            }
            if (mainAddressView != null) {
                mainAddressView.setText("Unable to load receive address.");
            }
            if (qrCodeView != null) {
                qrCodeView.setImageBitmap(null);
            }
        }
        if (transactionsTitleView != null) {
            transactionsTitleView.setText("Transactions");
        }
        if (historyContainer != null) {
            historyContainer.removeAllViews();
            historyContainer.addView(bodyText("Check connectivity and wait for auto-refresh to try again."), matchWrapParams());
        }
        renderConnectionState(ConnectionState.OFFLINE);
    }

    private JSONObject fetchJson(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");

        try {
            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

            String body = readFully(stream);
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("HTTP " + responseCode + ": " + body);
            }
            return new JSONObject(body);
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject postJson(String url, JSONObject payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);

        try {
            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(bodyBytes);
            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
            String body = readFully(stream);
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("HTTP " + responseCode + ": " + body);
            }
            return body.isEmpty() ? new JSONObject() : new JSONObject(body);
        } finally {
            connection.disconnect();
        }
    }

    private String readFully(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private LinearLayout buildCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(CARD_PADDING_DP);
        card.setPadding(padding, padding, padding, padding);
        card.setBackgroundResource(R.drawable.wallet_card_surface);
        return card;
    }

    private ScrollView buildScreenScroll() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        return scrollView;
    }

    private LinearLayout buildScreenContent(ScrollView parent) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.wallet_card_surface);
        int padding = dp(CARD_PADDING_DP);
        content.setPadding(padding, padding, padding, padding);
        parent.addView(content, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT
        ));
        return content;
    }

    private LinearLayout buildNestedCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        card.setPadding(padding, padding, padding, padding);
        card.setBackgroundResource(R.drawable.wallet_card_nested);
        return card;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.parseColor("#7C2D12"));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        return view;
    }

    private TextView buildScreenSectionTitle(String text) {
        TextView view = sectionTitle(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        return view;
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.parseColor("#334E68"));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return view;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.topMargin = dp(SECTION_SPACING_DP);
        return params;
    }

    private LinearLayout.LayoutParams transactionCardParams(int index) {
        LinearLayout.LayoutParams params = matchWrapParams();
        if (index > 0) {
            params.topMargin = dp(12);
        }
        return params;
    }

    private LinearLayout.LayoutParams detailParams() {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.topMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams equalWeightParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = leftMargin;
        params.gravity = Gravity.CENTER_VERTICAL;
        return params;
    }

    private LinearLayout.LayoutParams weightedMatchParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = leftMargin;
        return params;
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private FrameLayout.LayoutParams frameMatchParams() {
        return new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private Button createActionButton(String label, int styleRes) {
        Button button = new Button(new ContextThemeWrapper(this, styleRes), null, 0);
        button.setText(label);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        return button;
    }

    private ConnectionState parseConnectionState(JSONObject statusPayload) {
        long blocks = statusPayload.optLong("blocks", -1L);
        long headers = statusPayload.optLong("headers", blocks);
        int connections = statusPayload.optInt("connections", 1);
        if (connections <= 0) {
            return ConnectionState.OFFLINE;
        }
        if (headers > blocks) {
            return ConnectionState.SYNCING;
        }
        return ConnectionState.CONNECTED;
    }

    private void renderConnectionState(ConnectionState state) {
        if (connectionStatusView == null) {
            return;
        }
        connectionStatusView.setText(state.label);
        connectionStatusView.setTextColor(Color.parseColor(state.textColor));
        connectionStatusView.setCompoundDrawablePadding(dp(6));
        connectionStatusView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        connectionStatusView.setBackgroundResource(R.drawable.wallet_status_badge);
        connectionStatusView.setAlpha(state == ConnectionState.OFFLINE ? 0.94f : 1f);
        connectionStatusView.setText("\u25CF " + state.label);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics()
        ));
    }

    private String appendBob(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value + " BOB";
    }

    private BigDecimal parseAmount(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatBob(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " BOB";
    }

    private BigDecimal parseEstimatedFee(JSONObject payload) {
        BigDecimal directFee = parseBigDecimal(optValue(payload, "fee"));
        if (directFee != null && directFee.compareTo(BigDecimal.ZERO) > 0) {
            return directFee;
        }
        BigDecimal feeRate = parseBigDecimal(optValue(payload, "feerate"));
        if (feeRate != null && feeRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal estimated = feeRate.multiply(ESTIMATED_TX_SIZE_KB);
            if (estimated.compareTo(BigDecimal.ZERO) > 0) {
                return estimated;
            }
        }
        BigDecimal result = parseBigDecimal(optValue(payload, "result"));
        if (result != null && result.compareTo(BigDecimal.ZERO) > 0) {
            return result;
        }
        return DEFAULT_FEE.min(FALLBACK_FEE_RATE).max(DEFAULT_FEE);
    }

    private BigDecimal parseBigDecimal(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(rawValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractTxid(JSONObject response) {
        String[] keys = {"txid", "result", "transactionId", "transaction_id", "id"};
        for (String key : keys) {
            String value = optValue(response, key);
            if (value != null && !value.isEmpty() && !"null".equals(value)) {
                return value;
            }
        }
        return "Unavailable";
    }

    private String extractAddressFromQr(String contents) {
        if (contents == null) {
            return "";
        }
        String value = contents.trim();
        if (value.startsWith("dobbscoin:")) {
            String withoutScheme = value.substring("dobbscoin:".length());
            int queryIndex = withoutScheme.indexOf('?');
            if (queryIndex >= 0) {
                withoutScheme = withoutScheme.substring(0, queryIndex);
            }
            return withoutScheme;
        }
        return value;
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    private String formatUnixTime(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(rawValue);
            return DATE_FORMATTER.format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()));
        } catch (Exception e) {
            return rawValue;
        }
    }

    private String formatNow() {
        return DATE_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault()));
    }

    private String optValue(JSONObject item, String key) {
        try {
            if (!item.has(key) || item.isNull(key)) {
                return null;
            }
            return item.get(key).toString();
        } catch (JSONException e) {
            return null;
        }
    }

    private enum ConnectionState {
        CONNECTED("Connected", "#15803D"),
        SYNCING("Syncing", "#C2410C"),
        OFFLINE("Offline", "#B91C1C");

        private final String label;
        private final String textColor;

        ConnectionState(String label, String textColor) {
            this.label = label;
            this.textColor = textColor;
        }
    }

    private static final class WalletData {
        private final long blockHeight;
        private final String balance;
        private final String address;
        private final JSONArray transactions;
        private final ConnectionState connectionState;

        private WalletData(long blockHeight, String balance, String address, JSONArray transactions, ConnectionState connectionState) {
            this.blockHeight = blockHeight;
            this.balance = balance;
            this.address = address;
            this.transactions = transactions;
            this.connectionState = connectionState;
        }
    }

    private static final class TransactionItem {
        private final String txid;
        private final String date;
        private final String amount;
        private final String confirmations;
        private final String address;
        private final long time;
        private final int fallbackIndex;

        private TransactionItem(String txid, String date, String amount, String confirmations, String address, long time, int fallbackIndex) {
            this.txid = txid;
            this.date = date;
            this.amount = amount;
            this.confirmations = confirmations;
            this.address = address;
            this.time = time;
            this.fallbackIndex = fallbackIndex;
        }

        private static TransactionItem from(Object transaction, int fallbackIndex) {
            if (!(transaction instanceof JSONObject)) {
                String raw = String.valueOf(transaction);
                return new TransactionItem(raw, "Unavailable", "Unavailable", "Unavailable", "Unavailable", Long.MIN_VALUE + fallbackIndex, fallbackIndex);
            }

            JSONObject item = (JSONObject) transaction;
            String txid = opt(item, "txid");
            long time = parseLong(opt(item, "time"), Long.MIN_VALUE + fallbackIndex);
            String date = formatTimeValue(opt(item, "time"));
            String amountValue = opt(item, "amount");
            String amount = amountValue == null ? "Unavailable" : amountValue + " BOB";
            String confirmations = fallback(opt(item, "confirmations"));
            String address = fallback(item.optString("address", null));

            return new TransactionItem(
                fallback(txid),
                fallback(date),
                amount,
                confirmations,
                address,
                time,
                fallbackIndex
            );
        }

        private long sortTime() {
            return time;
        }

        private String displayTxid() {
            if (txid.length() <= 12) {
                return txid;
            }
            return txid.substring(0, 8) + "..." + txid.substring(txid.length() - 4);
        }

        private String fullTxid() {
            return txid;
        }

        private static String opt(JSONObject item, String key) {
            try {
                if (!item.has(key) || item.isNull(key)) {
                    return null;
                }
                return item.get(key).toString();
            } catch (JSONException e) {
                return null;
            }
        }

        private static String fallback(String value) {
            return value == null || value.isEmpty() || "null".equals(value) ? "Unavailable" : value;
        }

        private static long parseLong(String value, long fallbackValue) {
            if (value == null || value.isEmpty()) {
                return fallbackValue;
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return fallbackValue;
            }
        }

        private static String formatTimeValue(String rawValue) {
            if (rawValue == null || rawValue.isEmpty()) {
                return "Unavailable";
            }
            try {
                long seconds = Long.parseLong(rawValue);
                return DATE_FORMATTER.format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()));
            } catch (Exception e) {
                return rawValue;
            }
        }
    }
}
