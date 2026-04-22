package de.evil2000.mqttiles;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Predicate;

import org.mozilla.javascript.Script;

import java.util.Date;
import java.util.List;

/**
 * Adds MQTT semantics to {@link MetricBasic}: topic subscription, publish rules,
 * "intermediate state" (spinner shown while waiting for echoed payload after a pub),
 * and optional JSON-path extraction from arriving payloads.
 */
public class MetricBasicMqtt extends MetricBasic {

    /** True on the very first post-arrival render to allow UI to react once. */
    public transient boolean lastPayloadChanged = true;

    /** Topic to subscribe (and, if {@link #topicPub} empty, to publish to). */
    public String  topic                  = "";
    /** Optional separate publish topic (common in bi-directional setups). */
    public String  topicPub               = "";
    public boolean enablePub              = true;
    /** If true, a successful publish updates {@link #lastPayload} immediately (no intermediate state). */
    public boolean updateLastPayloadOnPub = true;
    public boolean enableIntermediateState = true;
    /** Seconds the intermediate state may last; 0 = until echo arrives. */
    public int     intermediateStateTimeout = 0;
    /** Epoch seconds the intermediate state was entered; 0 = not in intermediate state. */
    public int     enteredIntermediateStateAt = 0;

    public byte    qos = 0;
    public boolean retained = false;

    /** Most recent raw payload; null until first message. */
    public String lastPayload = null;

    /** Optional JSONPath; empty = use raw payload. */
    public String jsonPath = "";
    public String lastJsonPathValue = null;

    // --- JS on-receive hook ---
    public String jsOnReceive = "";
    public transient Script jsOnReceiveCompiled = null;
    public transient String lastJsOnReceiveExceptionMessage = "";
    public transient String lastJsOnReceiveExceptionDetail = "";
    public transient String lastJSONExceptionMessage = "";

    MetricBasicMqtt() { }

    /**
     * Enter (or leave) the intermediate "waiting-for-echo" state.
     * Side effect: sets {@link #enteredIntermediateStateAt}.
     */
    public void enterIntermediateState(boolean enter) {
        this.enteredIntermediateStateAt = enter ? (int) (new Date().getTime() / 1000) : 0;
    }

    /**
     * @return true iff we are still waiting for the broker to echo our last publish.
     * Only meaningful when pub is enabled, update-on-pub is off, and intermediate state is enabled.
     */
    public boolean isInIntermediateState() {
        if (this.enablePub && !this.updateLastPayloadOnPub && this.enableIntermediateState) {
            boolean infinite = (this.intermediateStateTimeout == 0 && this.enteredIntermediateStateAt > 0);
            int ageSec = ((int) (new Date().getTime() / 1000)) - this.enteredIntermediateStateAt;
            return infinite || ageSec <= this.intermediateStateTimeout;
        }
        return false;
    }

    /**
     * Consume an arriving MQTT payload. Updates {@link #lastPayload}, clears intermediate
     * state, evaluates optional {@link #jsonPath}, and stamps {@link #lastActivity}.
     *
     * @param payload raw MQTT text payload.
     */
    public void messageReceived(String payload) {
        enterIntermediateState(false);
        this.lastPayloadChanged = !payload.equals(this.lastPayload);
        this.lastActivity = (int) (new Date().getTime() / 1000);

        if (!this.lastPayloadChanged) return;
        this.lastPayload = payload;

        if (this.jsonPath == null || this.jsonPath.length() == 0) return;
        try {
            Object extracted = JsonPath.read(payload, this.jsonPath, new Predicate[0]);
            if (extracted == null) {
                this.lastJsonPathValue = "(null)";
            } else if (extracted instanceof String) {
                this.lastJsonPathValue = (String) extracted;
            } else if (extracted instanceof List) {
                this.lastJsonPathValue = extracted.toString();
            } else {
                this.lastJsonPathValue = extracted.toString();
            }
        } catch (Error | Exception ex) {
            // JsonPath throws both — treat uniformly.
            this.lastJsonPathValue = "";
            this.lastJSONExceptionMessage = "JSON path error";
            ex.printStackTrace();
        }
    }
}
