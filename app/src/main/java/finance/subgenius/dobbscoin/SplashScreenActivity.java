package finance.subgenius.dobbscoin;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

public class SplashScreenActivity extends Activity {
    private static final String TAG = "SplashScreenActivity";
    private static MediaPlayer splashPlayer;
    private static final long LOADING_ANIMATION_INTERVAL_MS = 500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loadingAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            if (loadingTextView == null) {
                return;
            }
            loadingTextView.setText(buildLoadingText(dotCount));
            dotCount++;
            if (dotCount > 10) {
                dotCount = 1;
            }
            handler.postDelayed(this, LOADING_ANIMATION_INTERVAL_MS);
        }
    };

    private boolean launched;
    private TextView loadingTextView;
    private int dotCount = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {

            Log.i("Splash", "Splash starting");

            setContentView(
                R.layout.activity_splash
            );

            loadingTextView = findViewById(R.id.splashLoadingText);
            Button splashMinistryButton = findViewById(R.id.splashMinistryButton);
            splashMinistryButton.setOnClickListener(v -> {
                Intent browserIntent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.subgenius.com/scatalog/membership.htm")
                );
                startActivity(browserIntent);
            });
            handler.post(loadingAnimationRunnable);
            startThemeMusicAndGetDelay();
            initializeApp();

        }
        catch (Exception e) {

            Log.e(
                "Splash",
                "Fatal splash error",
                e
            );

            launchMain();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(loadingAnimationRunnable);
        if (!launched) {
            stopThemeMusic();
        }
        super.onDestroy();
    }

    private long startThemeMusicAndGetDelay() {
        stopThemeMusic();
        splashPlayer = MediaPlayer.create(this, R.raw.bob_loves_you_john_zero);
        if (splashPlayer == null) {
            return 0L;
        }
        splashPlayer.setLooping(false);
        splashPlayer.setOnCompletionListener(player -> stopThemeMusic());
        int duration = splashPlayer.getDuration();
        splashPlayer.start();
        if (duration <= 0) {
            return 0L;
        }
        return duration;
    }

    private void stopThemeMusic() {
        if (splashPlayer == null) {
            return;
        }

        if (splashPlayer.isPlaying()) {
            splashPlayer.stop();
        }
        splashPlayer.release();
        splashPlayer = null;
    }

    private void initializeApp() {
        new Thread(() -> {
            boolean initialized = false;
            try {
                Log.i(
                    "Splash",
                    "Initializing wallet"
                );

                WalletManager manager =
                    new WalletManager(
                        getApplicationContext()
                    );

                manager.loadOrCreateWallet(
                    getApplicationContext()
                );

                Log.i(
                    "Splash",
                    "Wallet ready"
                );
                initialized = true;
            } catch (Exception e) {
                Log.e(
                    "Splash",
                    "Initialization failed",
                    e
                );
            }

            final boolean walletInitialized = initialized;
            runOnUiThread(() -> {
                if (walletInitialized) {
                    launchMain();
                } else if (loadingTextView != null) {
                    loadingTextView.setText("Wallet failed to open. Existing wallet preserved.");
                }
            });
        }).start();
    }

    private void launchMain() {
        if (launched || isFinishing()) {
            return;
        }

        try {
            stopThemeMusic();
            Intent intent =
                new Intent(
                    SplashScreenActivity.this,
                    MainActivity.class
                );
            startActivity(intent);
            launched = true;
            finish();

        }
        catch (Exception e) {
            Log.e(
                "Splash",
                "Failed to start MainActivity",
                e
            );
            if (loadingTextView != null) {
                loadingTextView.setText("Startup failed. Check crash.log.");
            }
        }
    }

    private String buildLoadingText(int dotCount) {
        StringBuilder builder = new StringBuilder("Loading");
        for (int i = 0; i < dotCount; i++) {
            builder.append('.');
        }
        return builder.toString();
    }
}
