package de.evil2000.mqttiles;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Script-side view of a metric being rendered. Passed into the user's
 * {@code onDisplay} JS hook — mutations to its setters feed back into the tile.
 * Base class for all per-metric-type display scriptables.
 */
public class MetricBasicScriptableOnDisplay {

    protected boolean mBlink;
    protected String  mLastActivityString;
    protected String  mName;
    protected MetricBasic mMetric;

    private final MetricsListActivity mMetricsListActivity;
    private final View mTile;

    public MetricBasicScriptableOnDisplay(MetricsListActivity activity, MetricBasic metric, View tile) {
        this.mMetricsListActivity = activity;
        this.mTile    = tile;
        this.mMetric  = metric;
        this.mName    = metric.name;
        this.mBlink   = metric.blink;
        this.mLastActivityString = metric.getLastActivityDateTimeString();
    }

    public AppCompatActivity getActivity() { return this.mMetricsListActivity; }
    public boolean    getBlink()              { return this.mBlink; }
    public String     getLastActivityString() { return this.mLastActivityString; }
    public MetricBasic getMetric()            { return this.mMetric; }
    public String     getName()               { return this.mName; }
    public double     getSecondsSinceLastActivity() { return this.mMetric.getSecondsSinceLastActivity(); }
    public View       getTile()               { return this.mTile; }

    public void setBlink(boolean b)               { this.mBlink = b; }
    public void setLastActivityString(String s)   { this.mLastActivityString = s; }
    public void setName(String s)                 { this.mName = s; }

    /** Read the per-metric scratch slot (auto-created on first access so
     *  {@code event.data['key'] = value} just works). */
    public Object getData() {
        return this.mMetricsListActivity.getOrCreateJsData(this.mMetric);
    }

    /** Write the per-metric scratch slot (survives across hook invocations for this metric). */
    public void setData(Object data) {
        ((App) this.mMetricsListActivity.getApplication()).javaScriptMap.put(this.mMetric.id, data);
    }
}
