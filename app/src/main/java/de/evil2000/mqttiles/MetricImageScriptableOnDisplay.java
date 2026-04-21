package de.evil2000.mqttiles;

import android.view.View;

/** Script-side display context for a {@link MetricImage} tile. */
public class MetricImageScriptableOnDisplay extends MetricBasicMqttScriptableOnDisplay {

    private String mUrl;

    MetricImageScriptableOnDisplay(MetricsListActivity activity, MetricImage metric, View tile) {
        super(activity, metric, tile);
        this.mUrl = metric.imageUrl == null ? "" : metric.imageUrl;
    }

    public String getUrl()        { return this.mUrl; }
    public void   setUrl(String s){ this.mUrl = s; }
}
