package de.evil2000.mqttiles;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Script-side context for the user's {@code onClick} JS hook.
 * If {@link #setPreventDefault(boolean)} is set, the host activity skips its default
 * click handling (publish / dialog open / etc.).
 */
public class MetricScriptableOnClick {

    private final MetricsListActivity mMetricsListActivity;
    private final View       mTile;
    private final MetricBasic mMetric;
    private boolean          mPreventDefault = false;

    public MetricScriptableOnClick(MetricsListActivity activity, MetricBasic metric, View tile) {
        this.mMetricsListActivity = activity;
        this.mTile   = tile;
        this.mMetric = metric;
    }

    public AppCompatActivity getActivity()  { return this.mMetricsListActivity; }
    public MetricBasic       getMetric()    { return this.mMetric; }
    public View              getTile()      { return this.mTile; }
    public boolean           getPreventDefault() { return this.mPreventDefault; }
    public void              setPreventDefault(boolean b) { this.mPreventDefault = b; }

    public Object getData() {
        return this.mMetricsListActivity.getOrCreateJsData(this.mMetric);
    }
    public void setData(Object data) {
        ((App) this.mMetricsListActivity.getApplication()).javaScriptMap.put(this.mMetric.id, data);
    }
}
