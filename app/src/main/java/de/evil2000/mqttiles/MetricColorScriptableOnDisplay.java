package de.evil2000.mqttiles;

import android.view.View;

/** Script-side display context for a {@link MetricColor} tile. */
public class MetricColorScriptableOnDisplay extends MetricBasicMqttScriptableOnDisplay {

    private String mColor;   // "#RRGGBB" as formatted by the host

    MetricColorScriptableOnDisplay(MetricsListActivity activity, MetricColor metric, String color, View tile) {
        super(activity, metric, tile);
        this.mColor = color;
    }

    public String getColor()        { return this.mColor; }
    public void   setColor(String s){ this.mColor = s; }
}
