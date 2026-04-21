package de.evil2000.mqttiles;

/**
 * Text tile — displays the raw (or JSON-path-extracted) MQTT payload as text
 * with optional prefix/postfix and a user-selected color.
 */
public class MetricText extends MetricBasicMqtt {

    public TextSize mainTextSize = TextSize.LARGE;
    public String   prefix       = "";
    public String   postfix      = "";
    public int      textColor    = -1;  // 0xFFFFFFFF = white (default)

    public enum TextSize {
        SMALL, MEDIUM, LARGE;

        public static TextSize[] valuesCustom() { return values(); }
    }

    MetricText() {
        this.type = METRIC_TYPE_TEXT;
    }
}
