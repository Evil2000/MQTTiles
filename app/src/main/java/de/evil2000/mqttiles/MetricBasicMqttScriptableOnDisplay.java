package de.evil2000.mqttiles;

import android.view.View;

/** Adds MQTT-payload read access to {@link MetricBasicScriptableOnDisplay}. */
public class MetricBasicMqttScriptableOnDisplay extends MetricBasicScriptableOnDisplay {

    public MetricBasicMqttScriptableOnDisplay(MetricsListActivity activity, MetricBasic metric, View tile) {
        super(activity, metric, tile);
    }

    /** @return the most recent raw MQTT payload string for this metric. */
    public String getLastPayload() {
        return ((MetricBasicMqtt) this.mMetric).lastPayload;
    }
}
