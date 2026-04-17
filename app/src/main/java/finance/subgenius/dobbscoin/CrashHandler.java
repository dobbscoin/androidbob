package finance.subgenius.dobbscoin;

import android.content.Intent;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler =
            Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {

        try {

            File logFile = new File(
                context.getFilesDir(),
                "crash.log"
            );

            PrintWriter writer =
                new PrintWriter(new FileWriter(logFile, true));

            writer.println("==== CRASH ====");
            writer.println(
                android.os.Build.MANUFACTURER
                    + " "
                    + android.os.Build.MODEL
            );

            throwable.printStackTrace(writer);

            writer.close();

            Log.e(
                "CrashHandler",
                "Crash logged to "
                    + logFile.getAbsolutePath(),
                throwable
            );

        }
        catch (Exception e) {
            Log.e(
                "CrashHandler",
                "Failed to write crash log",
                e
            );
        }

        try {
            Intent intent = new Intent(context, SplashScreenActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                try {
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e("CrashHandler", "Failed to relaunch splash after crash", e);
                }
            });
        } catch (Exception e) {
            Log.e("CrashHandler", "Failed to schedule splash relaunch", e);
        }

        if (thread != null && thread != Looper.getMainLooper().getThread() && defaultHandler != null) {
            defaultHandler.uncaughtException(
                thread,
                throwable
            );
        }
    }
}
