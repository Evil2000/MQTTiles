package de.evil2000.mqttiles;

/**
 * Color-picker tile. Payload is parsed as either a "#RRGGBB" hex string
 * or an integer (see {@link #format}).
 */
public class MetricColor extends MetricBasicMqtt {

    public static final transient int COLOR_FORMAT_HEX = 0;
    public static final transient int COLOR_FORMAT_INT = 1;

    public String icon   = "ic_radio_button_checked";
    public int    format = COLOR_FORMAT_HEX;

    MetricColor() {
        this.type = METRIC_TYPE_COLOR;
    }
}
