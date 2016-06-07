package de.schildbach.wallet.ui;

/**
 * Created by Physicists on 01-06-2016.
 */
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.media.MediaPlayer;
import subgeneius.dobbs.wallet.R;

public class SplashScreen extends Activity {
    MediaPlayer splash_sound;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        splash_sound = MediaPlayer.create(SplashScreen.this, R.raw.snd);
        splash_sound.start();
        Thread timer = new Thread() {
            public void run() {
                try {
                    sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    Intent openStartingPoint = new Intent(SplashScreen.this, WalletActivity.class);
                    startActivity(openStartingPoint);
                }
            }
        };
        timer.start();
    }

    @Override
    protected  void onPause(){super.onPause();splash_sound.release();
    finish();
    }

}

