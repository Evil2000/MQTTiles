package de.evil2000.mqttiles;

import java.util.ArrayList;

/**
 * Multi-choice tile (picker). Holds {@link MetricMultiSwitchItem}s; tapping opens
 * a dialog that publishes the chosen item's payload.
 */
public class MetricMultiSwitch extends MetricBasicMqtt {

    public TextSize mainTextSize = TextSize.LARGE;
    public int      textColor    = -1;
    public ArrayList<MetricMultiSwitchItem> items = new ArrayList<>();

    public enum TextSize {
        SMALL, MEDIUM, LARGE;

        public static TextSize[] valuesCustom() { return values(); }
    }

    MetricMultiSwitch() {
        this.type = METRIC_TYPE_MULTI_SWITCH;
    }
}
