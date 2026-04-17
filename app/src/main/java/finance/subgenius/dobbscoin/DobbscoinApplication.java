package finance.subgenius.dobbscoin;

import android.app.Application;
import android.util.Log;

public class DobbscoinApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Thread.setDefaultUncaughtExceptionHandler(
            new CrashHandler(this)
        );

        Log.i("App", "Application started");
    }
}
