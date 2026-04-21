package de.evil2000.mqttiles;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Predicate;

import java.util.Date;
import java.util.List;

/**
 * Two-state switch tile. Compares incoming payloads against {@link #payloadOn}/{@link #payloadOff}
 * and renders the matching icon/color.
 */
public class MetricSwitch extends MetricBasicMqtt {

    public String payloadOn  = "1";
    public String payloadOff = "0";
    public String iconOn     = "ic_check_box_checked";
    public String iconOff    = "ic_check_box_unchecked";
    public int    onColor    = -1;
    public int    offColor   = -1;

    MetricSwitch() {
        this.type = METRIC_TYPE_SWITCH;
    }

    /**
     * Override: only accept payloads matching {@link #payloadOn}/{@link #payloadOff}
     * (after optional JSONPath extraction). Everything else is ignored so the UI
     * doesn't flicker on unrelated broker traffic.
     */
    @Override
    public void messageReceived(String raw) {
        String jsonValue = null;

        if (this.jsonPath != null && this.jsonPath.length() > 0) {
            try {
                Object extracted = JsonPath.read(raw, this.jsonPath, new Predicate[0]);
                if      (extracted == null)               jsonValue = "(null)";
                else if (extracted instanceof String)     jsonValue = (String) extracted;
                else if (extracted instanceof List)       jsonValue = extracted.toString();
                else                                      jsonValue = extracted.toString();
            } catch (Error | Exception ex) {
                this.lastJsonPathValue = "";
                this.lastJSONExceptionMessage = "JSON path error";
                ex.printStackTrace();
            }
        }

        String compare = (jsonValue != null && jsonValue.length() > 0) ? jsonValue : raw;
        boolean matches = compare != null && compare.length() > 0
                && (compare.equals(this.payloadOn) || compare.equals(this.payloadOff));

        if (!matches) return;

        enterIntermediateState(false);
        this.lastActivity = (int) (new Date().getTime() / 1000);
        this.lastPayloadChanged = !raw.equals(this.lastPayload);
        if (this.lastPayloadChanged) {
            this.lastPayload = raw;
            this.lastJsonPathValue = jsonValue;
        }
    }
}
