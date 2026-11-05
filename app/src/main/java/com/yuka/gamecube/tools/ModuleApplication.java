package com.yuka.gamecube.tools;
import android.app.Application;
import com.yuka.gamecube.tools.core.FrameworkConnection;
public final class ModuleApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        com.yuka.gamecube.tools.diagnostics.AppDiagnostics.start(this);
        FrameworkConnection.start();
    }
}
