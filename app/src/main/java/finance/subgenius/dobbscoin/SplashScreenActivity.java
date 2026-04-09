package finance.subgenius.dobbscoin;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

public class SplashScreenActivity extends Activity {
    private static MediaPlayer splashPlayer;
    private static final long LOADING_ANIMATION_INTERVAL_MS = 500L;
    private static final String[] LOADING_STATES = {"Loading.", "Loading..", "Loading...", "Loading"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable launchWalletRunnable = this::launchWallet;
    private final Runnable loadingAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            if (loadingTextView == null) {
                return;
            }
            loadingTextView.setText(LOADING_STATES[loadingStateIndex]);
            loadingStateIndex = (loadingStateIndex + 1) % LOADING_STATES.length;
            handler.postDelayed(this, LOADING_ANIMATION_INTERVAL_MS);
        }
    };

    private boolean launched;
    private TextView loadingTextView;
    private int loadingStateIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
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
        long transitionDelay = startThemeMusicAndGetDelay();
        handler.postDelayed(launchWalletRunnable, transitionDelay);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(loadingAnimationRunnable);
        handler.removeCallbacks(launchWalletRunnable);
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

    private void launchWallet() {
        if (launched || isFinishing()) {
            return;
        }
        launched = true;
        Intent intent;
        if (SecurityStore.shouldShowSecurityOnStartup(this)) {
            intent = new Intent(this, SecurityActivity.class);
            intent.putExtra(SecurityActivity.EXTRA_MODE,
                SecurityStore.isPinConfigured(this) ? SecurityActivity.MODE_UNLOCK : SecurityActivity.MODE_SETUP);
            intent.putExtra(SecurityActivity.EXTRA_LAUNCH_MAIN, true);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
