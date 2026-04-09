package finance.subgenius.dobbscoin;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class SecurityActivity extends FragmentActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_LAUNCH_MAIN = "launch_main";

    public static final String MODE_SETUP = "setup";
    public static final String MODE_UNLOCK = "unlock";
    public static final String MODE_SEND_AUTH = "send_auth";

    private TextView titleView;
    private TextView subtitleView;
    private TextView pinView;
    private Button biometricButton;
    private Button enterButton;

    private String mode;
    private boolean launchMainAfterSuccess;
    private String pendingPin;
    private final StringBuilder currentInput = new StringBuilder();
    private boolean biometricPromptShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        launchMainAfterSuccess = getIntent().getBooleanExtra(EXTRA_LAUNCH_MAIN, false);
        if (mode == null || mode.isEmpty()) {
            mode = MODE_UNLOCK;
        }

        setContentView(buildContentView());
        updateUi();

        if (canUseBiometrics()) {
            pinView.post(this::showBiometricPromptOnce);
        }
    }

    private View buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF3EFE7);
        int padding = dp(20);
        root.setPadding(padding, padding, padding, padding);

        titleView = new TextView(this);
        titleView.setTextColor(0xFF1F2933);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(titleView, matchWrapParams());

        subtitleView = new TextView(this);
        subtitleView.setTextColor(0xFF52606D);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subtitleParams = matchWrapParams();
        subtitleParams.topMargin = dp(8);
        root.addView(subtitleView, subtitleParams);

        pinView = new TextView(this);
        pinView.setTextColor(0xFF102A43);
        pinView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        pinView.setGravity(Gravity.CENTER_HORIZONTAL);
        pinView.setLetterSpacing(0.3f);
        LinearLayout.LayoutParams pinParams = matchWrapParams();
        pinParams.topMargin = dp(24);
        root.addView(pinView, pinParams);

        biometricButton = new Button(new android.view.ContextThemeWrapper(this, R.style.WalletButtonSecondary), null, 0);
        biometricButton.setText("Use Biometric");
        biometricButton.setAllCaps(false);
        biometricButton.setOnClickListener(v -> showBiometricPrompt());
        LinearLayout.LayoutParams biometricParams = matchWrapParams();
        biometricParams.topMargin = dp(20);
        root.addView(biometricButton, biometricParams);

        root.addView(buildKeypad(), keypadParams());

        return root;
    }

    private View buildKeypad() {
        LinearLayout keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);

        keypad.addView(buildKeypadRow("7", "8", "9"), matchWrapParams());
        keypad.addView(buildKeypadRow("4", "5", "6"), rowParams());
        keypad.addView(buildKeypadRow("1", "2", "3"), rowParams());
        keypad.addView(buildKeypadRow("0", "DELETE", "ENTER"), rowParams());

        return keypad;
    }

    private View buildKeypadRow(String left, String middle, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(buildKeypadButton(left), weightedParams(0));
        row.addView(buildKeypadButton(middle), weightedParams(dp(8)));
        row.addView(buildKeypadButton(right), weightedParams(dp(8)));
        return row;
    }

    private View buildKeypadButton(String label) {
        Button button = new Button(new android.view.ContextThemeWrapper(this, R.style.WalletButtonSecondary), null, 0);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> onKeyPressed(label));
        if ("ENTER".equals(label)) {
            enterButton = button;
        }
        return button;
    }

    private void onKeyPressed(String key) {
        if ("DELETE".equals(key)) {
            if (currentInput.length() > 0) {
                currentInput.deleteCharAt(currentInput.length() - 1);
            }
        } else if ("ENTER".equals(key)) {
            handleAction();
            return;
        } else if (currentInput.length() < 6) {
            currentInput.append(key);
        }
        updateUi();
    }

    private void handleAction() {
        String pin = currentInput.toString();
        if (pin.length() < 4 || pin.length() > 6) {
            Toast.makeText(this, "PIN must be 4 to 6 digits.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (MODE_SETUP.equals(mode)) {
            if (pendingPin == null) {
                pendingPin = pin;
                currentInput.setLength(0);
                updateUi();
                return;
            }

            if (!pendingPin.equals(pin)) {
                pendingPin = null;
                currentInput.setLength(0);
                Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show();
                updateUi();
                return;
            }

            SecurityStore.savePin(this, pin);
            completeSuccess();
            return;
        }

        if (SecurityStore.verifyPin(this, pin)) {
            completeSuccess();
        } else {
            currentInput.setLength(0);
            Toast.makeText(this, "Incorrect PIN.", Toast.LENGTH_SHORT).show();
            updateUi();
        }
    }

    private void completeSuccess() {
        SecurityStore.markUnlocked();
        if (launchMainAfterSuccess) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        setResult(RESULT_OK);
        finish();
    }

    private void updateUi() {
        boolean setupMode = MODE_SETUP.equals(mode);
        boolean confirmStep = setupMode && pendingPin != null;

        titleView.setText(setupMode ? "Set PIN" : "Enter PIN");
        if (MODE_SEND_AUTH.equals(mode)) {
            titleView.setText("Authorize Send");
        }

        if (setupMode) {
            titleView.setText(confirmStep ? "Confirm PIN" : "Set PIN");
            subtitleView.setText(confirmStep ? "Re-enter your PIN" : "Create a 4 to 6 digit PIN");
        } else if (MODE_SEND_AUTH.equals(mode)) {
            subtitleView.setText("Use PIN or biometric unlock before sending");
        } else {
            subtitleView.setText("Unlock your wallet");
        }

        pinView.setText(maskedPin(currentInput.length()));
        if (enterButton != null) {
            enterButton.setEnabled(currentInput.length() >= 4 && currentInput.length() <= 6);
            enterButton.setText(setupMode ? (confirmStep ? "ENTER" : "ENTER") : "ENTER");
        }
        biometricButton.setVisibility(canUseBiometrics() ? View.VISIBLE : View.GONE);
    }

    private boolean canUseBiometrics() {
        return !MODE_SETUP.equals(mode) && SecurityStore.isBiometricAvailable(this);
    }

    private void showBiometricPromptOnce() {
        if (biometricPromptShown) {
            return;
        }
        biometricPromptShown = true;
        showBiometricPrompt();
    }

    private void showBiometricPrompt() {
        if (!canUseBiometrics()) {
            return;
        }
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                completeSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                Toast.makeText(SecurityActivity.this, "Biometric unlock failed. Use your PIN.", Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .setTitle(MODE_SEND_AUTH.equals(mode) ? "Authorize Send" : "Unlock Wallet")
            .setSubtitle("Use fingerprint or face unlock")
            .setNegativeButtonText("Use PIN")
            .build();
        prompt.authenticate(promptInfo);
    }

    @Override
    public void onBackPressed() {
        if (launchMainAfterSuccess) {
            finish();
            return;
        }
        super.onBackPressed();
    }

    private String maskedPin(int length) {
        if (length <= 0) {
            return "• • • •";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append('•');
        }
        return builder.toString();
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

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.topMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams keypadParams() {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.topMargin = dp(24);
        return params;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics()
        ));
    }
}
