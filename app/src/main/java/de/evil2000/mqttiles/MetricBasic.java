package de.evil2000.mqttiles;

import android.text.format.DateUtils;

import org.mozilla.javascript.Script;

import java.util.Date;
import java.util.UUID;

/**
 * Common fields shared by every dashboard tile (MQTT or not).
 *
 * Concrete subclasses (see {@link #type} constants) add domain-specific fields.
 * Rhino-compiled handler references ({@code jsOn*Compiled}) are transient so they
 * are re-compiled on demand after a Gson reload.
 */
public class MetricBasic {

    // --- Type discriminator (kept in JSON as "type") ---
    public static final transient int METRIC_TYPE_UNKNOWN      = 0;
    public static final transient int METRIC_TYPE_TEXT         = 1;
    public static final transient int METRIC_TYPE_SWITCH       = 2;
    public static final transient int METRIC_TYPE_RANGE        = 3;
    public static final transient int METRIC_TYPE_MULTI_SWITCH = 4;
    public static final transient int METRIC_TYPE_IMAGE        = 5;
    public static final transient int METRIC_TYPE_COLOR        = 6;

    public String name;
    /** Stable RecyclerView id (stableIds=true). */
    public long   longId = 0;
    public int    type   = METRIC_TYPE_UNKNOWN;
    public String id     = UUID.randomUUID().toString();

    // --- JS hooks: source text is persisted, compiled {@link Script} is lazy + transient. ---
    public String jsOnDisplay = "";
    public transient Script jsOnDisplayCompiled = null;
    public transient String lastJsOnDisplayExceptionMessage = "";

    public String jsOnTap = "";
    public transient Script jsOnTapCompiled = null;
    public transient String lastJsOnTapExceptionMessage = "";

    /** Blink state machine toggled by {@link #timer()}. */
    public transient boolean blink = false;
    private  transient long  blinkLastTime = 0;
    public   transient int   blinkState = 0;

    public String jsBlinkExpression = "";
    public transient Script jsBlinkExpressionCompiled = null;
    public transient String lastJsBlinkExpressionExceptionMessage = "";

    /** Epoch seconds of last MQTT / image activity; 0 = never. */
    public long lastActivity = 0;

    MetricBasic() { }

    /**
     * @return last-activity {@link Date} or {@code null} if nothing arrived yet.
     */
    Date getLastActivityDateTime() {
        if (this.lastActivity > 0) {
            return new Date(this.lastActivity * 1000);
        }
        return null;
    }

    /**
     * Human-readable relative time ("5 min ago"). Empty string if no activity yet.
     */
    public String getLastActivityDateTimeString() {
        if (this.lastActivity == 0) return "";
        return DateUtils.getRelativeTimeSpanString(
                this.lastActivity * 1000,
                new Date().getTime(),
                DateUtils.SECOND_IN_MILLIS).toString();
    }

    /** Seconds elapsed since last activity (may be negative if clock moved). */
    public long getSecondsSinceLastActivity() {
        return (new Date().getTime() / 1000) - this.lastActivity;
    }

    /**
     * Persist a given activity timestamp.  No-op when {@code date == null}.
     * Side effect: updates {@link #lastActivity}.
     */
    void setLastActivityDateTime(Date date) {
        if (date == null) return;
        this.lastActivity = date.getTime() / 1000;
    }

    /**
     * Called ~10 Hz from the adapter timer. Toggles {@link #blinkState} every 500 ms
     * when {@link #blink} is on; forces state=0 while blink is off.
     */
    public void timer() {
        long now = System.currentTimeMillis();
        if (!this.blink) {
            this.blinkState = 0;
        } else if (now - this.blinkLastTime >= 500) {
            this.blinkLastTime = now;
            this.blinkState = (this.blinkState != 1) ? 1 : 0;
        }
    }
}
