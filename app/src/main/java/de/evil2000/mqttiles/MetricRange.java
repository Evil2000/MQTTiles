package de.evil2000.mqttiles;

import android.util.Log;

import java.util.Locale;

/**
 * Analog-range tile (progress arc / slider). Parses the payload as a float,
 * clamps it to [{@link #minValue}, {@link #maxValue}], and exposes a 0-100 progress.
 */
public class MetricRange extends MetricBasicMqtt {

    public transient Exception valueError = null;

    public float  minValue = 0.0f;
    public float  maxValue = 100.0f;
    public long   decimalPrecision = 0;
    /** true = show raw value, false = show % of range. */
    public boolean displayPayloadValue = true;
    public String prefix  = "";
    public String postfix = "";
    public int    progressColor = -1;

    MetricRange() {
        this.type = METRIC_TYPE_RANGE;
    }

    /** @return value mapped to [0,100]. Returns 0 on parse error (see {@link #valueError}). */
    public float getProgress() {
        float v = getValue();
        if (v < this.minValue) return 0.0f;
        if (v > this.maxValue) return 100.0f;
        return (v - this.minValue) / ((this.maxValue - this.minValue) / 100.0f);
    }

    /** Formatted display string: "{prefix}{value or progress}{postfix}". */
    public String getStringValue() {
        String fmt = "%s%." + this.decimalPrecision + "f%s";
        return String.format(Locale.ROOT, fmt,
                this.prefix,
                this.displayPayloadValue ? getValue() : getProgress(),
                this.postfix);
    }

    /**
     * Parse the current payload (or JSON-path value) as float.
     * Side effect: sets {@link #valueError} on failure and returns 0.
     */
    public float getValue() {
        String raw = (this.jsonPath == null || this.jsonPath.length() == 0)
                ? this.lastPayload
                : this.lastJsonPathValue;
        this.valueError = null;
        if (raw == null || raw.length() == 0) return 0.0f;
        try {
            return Float.parseFloat(raw);
        } catch (Exception e) {
            this.valueError = e;
            Log.e("MetricRange", e.getMessage() == null ? "parse error" : e.getMessage());
            return 0.0f;
        }
    }
}
