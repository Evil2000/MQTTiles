package de.evil2000.mqttiles;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Script-side context for the user's {@code onReceive} JS hook. JS can mutate
 * {@code topic}/{@code payload} to rewrite what the app consumes.
 */
public class MetricMqttScriptableOnReceive {

    private final MetricsListActivity mMetricsListActivity;
    private final MetricBasicMqtt mMetric;
    private String mTopic;
    private String mPayload;

    MetricMqttScriptableOnReceive(MetricsListActivity activity, MetricBasicMqtt metric, String topic, String payload) {
        this.mMetricsListActivity = activity;
        this.mMetric  = metric;
        this.mTopic   = topic   == null ? "" : topic;
        this.mPayload = payload == null ? "" : payload;
    }

    public AppCompatActivity getActivity() { return this.mMetricsListActivity; }
    public MetricBasic       getMetric()   { return this.mMetric; }
    public String            getTopic()    { return this.mTopic; }
    public String            getPayload()  { return this.mPayload; }

    public void setTopic(String t)   { this.mTopic   = t; }
    public void setPayload(String p) { this.mPayload = p; }

    public Object getData() {
        return ((App) this.mMetricsListActivity.getApplication()).javaScriptMap.get(this.mMetric.id);
    }
    public void setData(Object data) {
        ((App) this.mMetricsListActivity.getApplication()).javaScriptMap.put(this.mMetric.id, data);
    }
}
