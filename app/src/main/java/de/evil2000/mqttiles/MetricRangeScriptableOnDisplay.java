package de.evil2000.mqttiles;

import android.view.View;

import java.util.Locale;

/** Script-side display context for a {@link MetricRange} tile (progress bar/arc). */
public class MetricRangeScriptableOnDisplay extends MetricBasicMqttScriptableOnDisplay {

    protected float  mProgress;     // 0..100
    protected String mProgressColor; // "#RRGGBB"
    protected String mText;

    public MetricRangeScriptableOnDisplay(MetricsListActivity activity, MetricRange metric, String text, View tile) {
        super(activity, metric, tile);
        this.mText          = text;
        this.mProgress      = metric.getProgress();
        this.mProgressColor = String.format(Locale.ROOT, "#%06X", metric.progressColor & 0x00FFFFFF);
    }

    public float  getProgress()      { return this.mProgress; }
    public String getProgressColor() { return this.mProgressColor; }
    public String getText()          { return this.mText; }

    public void setProgress(float f)      { this.mProgress = f; }
    public void setProgressColor(String s){ this.mProgressColor = s; }
    public void setText(String s)         { this.mText = s; }
}
