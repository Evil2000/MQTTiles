package de.evil2000.mqttiles;

import android.view.View;

import java.util.Locale;

/** Script-side display context for a {@link MetricSwitch} tile (on/off + icon tint). */
public class MetricSwitchScriptableOnDisplay extends MetricBasicMqttScriptableOnDisplay {

    protected boolean mOn;
    protected String  mIconColor;   // "#RRGGBB"

    public MetricSwitchScriptableOnDisplay(MetricsListActivity activity, MetricSwitch metric, boolean on, View tile) {
        super(activity, metric, tile);
        this.mOn        = on;
        int color = on ? metric.onColor : metric.offColor;
        this.mIconColor = String.format(Locale.ROOT, "#%06X", color & 0x00FFFFFF);
    }

    public boolean getOn()        { return this.mOn; }
    public String  getIconColor() { return this.mIconColor; }
    public void    setOn(boolean b)      { this.mOn = b; }
    public void    setIconColor(String s){ this.mIconColor = s; }
}
