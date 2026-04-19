package de.schildbach.wallet.ui;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.media.MediaPlayer;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ProgressBar;
import subgeneius.dobbs.wallet.R;

public class SplashScreen extends Activity {

    private MediaPlayer splash_sound;
    private final Handler handler = new Handler();
    private boolean intentionalFinish = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Play splash sound; release via completion listener so it always plays fully
        splash_sound = MediaPlayer.create(SplashScreen.this, R.raw.snd);
        if (splash_sound != null) {
            splash_sound.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                }
            });
            splash_sound.start();
        }

        // Animate progress bar 0 → 100 over exactly 5 seconds (synced with auto-advance)
        final ProgressBar progressBar = findViewById(R.id.splash_progress);
        final ObjectAnimator progressAnim = ObjectAnimator.ofInt(progressBar, "progress", 0, 100);
        progressAnim.setDuration(5000);
        progressAnim.setInterpolator(new LinearInterpolator());
        progressAnim.start();

        // Ordained Minister button
        Button btnOrdained = findViewById(R.id.btn_ordained);
        btnOrdained.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browser = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.subgenius.com/scatalog/membership.htm"));
                startActivity(browser);
            }
        });

        // Auto-advance to wallet after 5 seconds
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    intentionalFinish = true;
                    startActivity(new Intent(SplashScreen.this, WalletActivity.class));
                    finish();
                }
            }
        }, 5000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
        if (!intentionalFinish) {
            // Interrupted (e.g. back button) — release sound immediately
            if (splash_sound != null) {
                splash_sound.release();
                splash_sound = null;
            }
            finish();
        }
        // When intentionalFinish=true the sound keeps playing via the completion
        // listener; no release here.
    }
}
