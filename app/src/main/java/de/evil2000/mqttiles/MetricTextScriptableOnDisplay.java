package de.evil2000.mqttiles;

import android.view.View;

import java.util.Locale;

/** Script-side display context for a {@link MetricText} tile. */
public class MetricTextScriptableOnDisplay extends MetricBasicMqttScriptableOnDisplay {

    protected String mText;
    protected String mTextColor;   // "#RRGGBB"

    public MetricTextScriptableOnDisplay(MetricsListActivity activity, MetricText metric, String text, View tile) {
        super(activity, metric, tile);
        this.mText      = text;
        this.mTextColor = String.format(Locale.ROOT, "#%06X", metric.textColor & 0x00FFFFFF);
    }

    public String getText()      { return this.mText; }
    public String getTextColor() { return this.mTextColor; }
    public void   setText(String s)      { this.mText = s; }
    public void   setTextColor(String s) { this.mTextColor = s; }
}
