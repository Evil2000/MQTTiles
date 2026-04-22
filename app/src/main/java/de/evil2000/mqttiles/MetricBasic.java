package de.evil2000.mqttiles;

import android.text.format.DateUtils;

import org.mozilla.javascript.RhinoException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.UUID;

/**
 * Common fields shared by every dashboard tile (MQTT or not).
 *
 * Concrete subclasses (see {@link #type} constants) add domain-specific fields.
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

    // --- JS hooks: source text is persisted, last-exception state is transient. ---
    public String jsOnDisplay = "";
    public transient String lastJsOnDisplayExceptionMessage = "";
    public transient String lastJsOnDisplayExceptionDetail = "";

    public String jsOnTap = "";
    public transient String lastJsOnTapExceptionMessage = "";
    public transient String lastJsOnTapExceptionDetail = "";

    /** Blink state machine toggled by {@link #timer()}. */
    public transient boolean blink = false;
    private  transient long  blinkLastTime = 0;
    public   transient int   blinkState = 0;

    public String jsBlinkExpression = "";
    public transient String lastJsBlinkExpressionExceptionMessage = "";
    public transient String lastJsBlinkExpressionExceptionDetail = "";

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

    /** Short, single-line JS error suitable for display on a tile. */
    public static String formatJsErrorShort(Throwable e) {
        if (e == null) return "";
        String m = e.getLocalizedMessage();
        if (m == null || m.isEmpty()) m = e.toString();
        int nl = m.indexOf('\n');
        return nl >= 0 ? m.substring(0, nl) : m;
    }

    /**
     * Location info for an AlertDialog. For Rhino errors, just {@code at <src>:<line>:<col>}
     * plus the offending source line if available; for other throwables, the Java stack trace.
     * Does not repeat the error message (that is shown separately).
     */
    public static String formatJsErrorDetail(Throwable e) {
        if (e == null) return "";
        StringBuilder sb = new StringBuilder();
        if (e instanceof RhinoException) {
            RhinoException re = (RhinoException) e;
            String name = re.sourceName();
            if (name != null && !name.isEmpty()) {
                sb.append("at ").append(name).append(':').append(re.lineNumber());
                if (re.columnNumber() > 0) sb.append(':').append(re.columnNumber());
            }
            String lineSrc = re.lineSource();
            if (lineSrc != null && !lineSrc.isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append("  ").append(lineSrc);
            }
        } else {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }
        return sb.toString();
    }
}
