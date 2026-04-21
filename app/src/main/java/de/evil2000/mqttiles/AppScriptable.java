package de.evil2000.mqttiles;

import android.content.Intent;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Global script-side API exposed to user JavaScript hooks as {@code app}.
 * Lets scripts open external URIs and publish MQTT messages via the host activity.
 */
public class AppScriptable {

    private final AppCompatActivity mContext;           // parent activity (unused outside future hooks)
    private final MetricsListActivity mMetricsListActivity;

    AppScriptable(AppCompatActivity context, MetricsListActivity metricsListActivity) {
        this.mContext = context;
        this.mMetricsListActivity = metricsListActivity;
    }

    /** Open {@code uri} with the default {@code ACTION_VIEW} intent. */
    public void openUri(String uri) {
        openUri(Intent.ACTION_VIEW, uri);
    }

    /** Open {@code uri} with the given intent action. No-op if host activity is gone. */
    public void openUri(String action, String uri) {
        if (this.mMetricsListActivity != null) {
            this.mMetricsListActivity.startActivity(new Intent(action, Uri.parse(uri)));
        }
    }

    /**
     * Publish {@code payload} to {@code topic} via the host activity's broker.
     * @param retained MQTT retained flag
     * @param qos      MQTT QoS (0/1/2)
     */
    public void publish(String topic, String payload, boolean retained, int qos) {
        if (this.mMetricsListActivity != null) {
            this.mMetricsListActivity.publish(topic, payload, retained, qos, null);
        }
    }
}
