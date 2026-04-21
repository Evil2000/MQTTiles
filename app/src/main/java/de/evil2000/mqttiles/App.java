package de.evil2000.mqttiles;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.HashMap;
import java.util.Map;

/**
 * Process-wide state container.
 *
 * Purpose: keep a per-metric scratchpad accessible to JavaScript handlers
 * ({@link AppScriptable#publish}, Rhino scope via {@link MetricsListActivity#getJsScope})
 * and a one-shot flag that distinguishes a user-initiated broker disconnect from
 * a cold start (needed so {@link BrokersListActivity#onStart} only auto-connects once).
 */
public class App extends Application {

    /** Per-metric JavaScript scratch values keyed by {@link MetricBasic#id}. */
    public final Map<String, Object> javaScriptMap = new HashMap<>();

    /**
     * true = on next BrokersList onStart the default/auto-connect broker is opened;
     * set to false when the user backs out of a dashboard to suppress auto-reconnect.
     */
    public boolean connectToDefaultConnection = true;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new EdgeToEdgeInsetsCallbacks());
    }

    /**
     * Android 15+ (targetSdk ≥ 35) forces edge-to-edge rendering: activity content draws
     * under the status bar, ActionBar, navigation bar, and on-screen keyboard. AppCompat's
     * ActionBar handles its own inset, but our own layouts (activity_brokers_list, the
     * metric settings screens, etc.) still need the remaining system-bar + IME inset applied
     * as padding so content is not clipped. Apply it once globally on android.R.id.content.
     */
    private static final class EdgeToEdgeInsetsCallbacks implements ActivityLifecycleCallbacks {
        @Override public void onActivityCreated(Activity a, Bundle s) {
            View content = a.findViewById(android.R.id.content);
            if (content == null) return;
            ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                Insets i = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }
        @Override public void onActivityStarted(Activity a) { }
        @Override public void onActivityResumed(Activity a) { }
        @Override public void onActivityPaused(Activity a) { }
        @Override public void onActivityStopped(Activity a) { }
        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
        @Override public void onActivityDestroyed(Activity a) { }
    }
}
