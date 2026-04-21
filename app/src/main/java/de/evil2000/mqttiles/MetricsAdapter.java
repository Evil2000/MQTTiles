package de.evil2000.mqttiles;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemConstants;
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder;
import com.triggertrap.seekarc.SeekArc;

import org.mozilla.javascript.Scriptable;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * RecyclerView adapter for the dashboard tile grid. Manages:
 *   - layout inflation per metric type (small/normal/large per {@link Broker#tileWidth})
 *   - a ~10 Hz timer driving blink + relative time updates, and a 10 s persistence tick
 *   - drag-and-drop reorder (via h6ah4i advrecyclerview) with Gson-persisted list
 *   - kicking off {@link BasicImageDownloader} reloads for {@link MetricImage} tiles
 */
public class MetricsAdapter extends RecyclerView.Adapter<MetricsAdapter.ViewHolder>
        implements DraggableItemAdapter<MetricsAdapter.ViewHolder> {

    private static final long TIMER_INTERVAL         = 100;
    private static final long UPDATE_TIMES_INTERVAL  = 1000;
    private static final long METRICS_SAVE_INTERVAL  = 10000;

    public ArrayList<MetricBasic> metrics;
    private final Broker mBroker;
    private final MetricsListActivity mMetricsListActivity;
    private Timer mTimer;
    private long  mLastSavedTime;
    private long  mTimesLastUpdated;

    private interface Draggable extends DraggableItemConstants { }

    public MetricsAdapter(MetricsListActivity activity, ArrayList<MetricBasic> metrics, Broker broker) {
        this.mMetricsListActivity = activity;
        this.metrics = metrics;
        this.mBroker = broker;

        if (metrics != null) {
            for (MetricBasic m : metrics) {
                if (m instanceof MetricImage) {
                    ((MetricImage) m).loadImageFromFile(activity.getCacheDir());
                }
            }
        }

        setHasStableIds(true);

        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override public void run() {
                mMetricsListActivity.runOnUiThread(MetricsAdapter.this::timerHandler);
            }
        }, 0L, TIMER_INTERVAL);
    }

    /** @return true iff {@code s} parses as a finite {@link Double}. */
    public static boolean isNumeric(String s) {
        try { Double.parseDouble(s); return true; }
        catch (NumberFormatException e) { return false; }
    }

    /**
     * Tint {@code drawable} with {@code color} using {@link PorterDuff.Mode#SRC_ATOP}.
     * No-op when {@code drawable == null}.
     */
    public void applyColorToDrawable(Drawable drawable, int color) {
        if (drawable != null) {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
        }
    }

    /**
     * Fire a background image reload if the metric is URL-based and either the cached
     * bitmap is missing or the configured {@link MetricImage#reloadInterval} has elapsed.
     */
    private void reloadImageForMetric(final MetricImage m) {
        if ((m.kind != MetricImage.IMAGE_URL_IN_PAYLOAD && m.kind != MetricImage.IMAGE_URL)
                || m.isLoading || m.imageUrl == null || m.imageUrl.trim().length() == 0) {
            return;
        }
        boolean shouldReload = m.lastPayloadChanged
                || m.getBitmap() == null
                || (m.imageUrl != null && m.imageUrl.length() > 0 && m.isTimeToReloadImage());
        if (!shouldReload) return;

        m.isLoading = true;
        new BasicImageDownloader(new BasicImageDownloader.OnImageLoaderListener() {
            @Override public void onComplete(Bitmap bitmap) {
                m.lastImageDownloadError = "";
                m.setLastActivityDateTime(new Date());
                m.setBitmap(bitmap);
                m.saveBitmapToFile(mMetricsListActivity.getCacheDir());
                m.isLoading = false;
            }
            @Override public void onError(BasicImageDownloader.ImageError err) {
                Log.e("image_loader", "onError: " + err.toString());
                m.lastImageDownloadError = mMetricsListActivity.getString(R.string.cant_get_image_from_url);
                m.isLoading = false;
            }
            @Override public void onProgressChange(int percent) { /* unused */ }
        }).download(m.imageUrl, false);
    }

    /** 10 Hz tick: refresh blink state, occasionally refresh relative-time labels, periodically persist. */
    private void timerHandler() {
        long now = System.currentTimeMillis();
        if (metrics != null) {
            boolean refreshTimes = now - mTimesLastUpdated >= UPDATE_TIMES_INTERVAL;
            if (refreshTimes) mTimesLastUpdated = now;
            for (int i = 0; i < metrics.size(); i++) {
                MetricBasic m = metrics.get(i);
                int prev = m.blinkState;
                m.timer();
                if (refreshTimes || prev != m.blinkState) notifyItemChanged(i);
            }
        }
        if (metrics != null && now - mLastSavedTime > METRICS_SAVE_INTERVAL) {
            mMetricsListActivity.saveMetrics(metrics);
            mLastSavedTime = now;
        }
    }

    // --- RecyclerView plumbing --------------------------------------------------------------

    @Override public int  getItemCount()       { return metrics == null ? 0 : metrics.size(); }
    @Override public long getItemId(int pos)   { return metrics.get(pos).longId; }
    @Override public int  getItemViewType(int pos) { return metrics.get(pos).type; }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final MetricsListActivity activity = mMetricsListActivity;

        // Touch-tint the tile background while the user is pressing (skipped when pub is disabled).
        View.OnTouchListener touchTint = (v, e) -> {
            MetricBasic tagged = (MetricBasic) v.getTag(R.string.TAG_METRIC);
            if (tagged == null) return false;
            if (tagged instanceof MetricBasicMqtt && !((MetricBasicMqtt) tagged).enablePub) return false;
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setBackgroundColor(Color.GRAY);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setBackgroundColor(ContextCompat.getColor(activity, R.color.colorTile));
                    break;
                default: break;
            }
            return false;
        };

        int layoutRes;
        switch (viewType) {
            case MetricBasic.METRIC_TYPE_TEXT:
                layoutRes = pickLayout(R.layout.text_metric_layout_small,
                                       R.layout.text_metric_layout,
                                       R.layout.text_metric_layout_large);
                break;
            case MetricBasic.METRIC_TYPE_SWITCH:
                layoutRes = pickLayout(R.layout.switch_metric_layout_small,
                                       R.layout.switch_metric_layout,
                                       R.layout.switch_metric_layout_large);
                break;
            case MetricBasic.METRIC_TYPE_RANGE:
                layoutRes = pickLayout(R.layout.range_metric_layout_small,
                                       R.layout.range_metric_layout,
                                       R.layout.range_metric_layout_large);
                break;
            case MetricBasic.METRIC_TYPE_MULTI_SWITCH:
                layoutRes = pickLayout(R.layout.multi_switch_metric_layout_small,
                                       R.layout.multi_switch_metric_layout,
                                       R.layout.multi_switch_metric_layout_large);
                break;
            case MetricBasic.METRIC_TYPE_IMAGE:
                layoutRes = pickLayout(R.layout.image_metric_layout_small,
                                       R.layout.image_metric_layout,
                                       R.layout.image_metric_layout_large);
                break;
            case MetricBasic.METRIC_TYPE_COLOR:
                layoutRes = pickLayout(R.layout.color_metric_layout_small,
                                       R.layout.color_metric_layout,
                                       R.layout.color_metric_layout_large);
                break;
            default:
                return null;
        }

        View tile = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        tile.setOnTouchListener(touchTint);
        tile.findViewById(R.id.intermediateProgressBar).setVisibility(View.GONE);

        activity.registerForContextMenu(tile);
        tile.setOnLongClickListener(v -> { v.showContextMenu(); return true; });

        return new ViewHolder(tile, v -> {
            MetricBasic tagged = (MetricBasic) v.getTag(R.string.TAG_METRIC);
            if (tagged == null) return;
            // Publishing disabled → read-only tile; short-tap does nothing. Images are exempt:
            // they never publish, but their click handler opens a URL or the enlarged popup.
            if (tagged instanceof MetricBasicMqtt
                    && !(tagged instanceof MetricImage)
                    && !((MetricBasicMqtt) tagged).enablePub) return;
            if (tagged instanceof MetricImage) {
                AudioManager am = (AudioManager) activity.getSystemService(android.content.Context.AUDIO_SERVICE);
                if (am != null) am.playSoundEffect(AudioManager.FX_KEY_CLICK);
            }
            dispatchClick(activity, tagged, v);
        });
    }

    /** Pick {@code small/normal/large} layout per {@link Broker#tileWidth}. */
    private int pickLayout(int small, int normal, int large) {
        switch (mBroker.tileWidth) {
            case Broker.TILE_SMALL: return small;
            case Broker.TILE_LARGE: return large;
            case Broker.TILE_MEDIUM:
            default: return normal;
        }
    }

    /** Dispatch a click to {@link MetricsListActivity#MetricClickHandler} with the right subtype. */
    private static void dispatchClick(MetricsListActivity activity, MetricBasic m, View tile) {
        if (m instanceof MetricText)        activity.MetricClickHandler((MetricText) m, tile);
        else if (m instanceof MetricSwitch) activity.MetricClickHandler((MetricSwitch) m, tile);
        else if (m instanceof MetricRange)  activity.MetricClickHandler((MetricRange) m, tile);
        else if (m instanceof MetricMultiSwitch) activity.MetricClickHandler((MetricMultiSwitch) m, tile);
        else if (m instanceof MetricImage)  activity.MetricClickHandler((MetricImage) m, tile);
        else if (m instanceof MetricColor)  activity.MetricClickHandler((MetricColor) m, tile);
    }

    /**
     * Run a metric's {@code jsOnDisplay} hook (if any), exposing {@code event} and {@code app}
     * in scope, so the script can mutate display properties on the scriptable before we read
     * them back in {@code bind*}. Exceptions are swallowed into
     * {@link MetricBasic#lastJsOnDisplayExceptionMessage}.
     */
    private void runOnDisplayHook(MetricBasic m, Object scriptableEvent) {
        if (m.jsOnDisplay == null || m.jsOnDisplay.trim().length() == 0) return;
        try {
            AppScriptable app = new AppScriptable(mMetricsListActivity, mMetricsListActivity);
            Scriptable scope = mMetricsListActivity.getJsScope();
            mMetricsListActivity.addConstToJsScope(scope, scriptableEvent, "event");
            mMetricsListActivity.addConstToJsScope(scope, app, "app");
            mMetricsListActivity.jsEval(m.jsOnDisplay, scope, "<OnDisplay>");
            m.lastJsOnDisplayExceptionMessage = "";
        } catch (Exception e) {
            m.lastJsOnDisplayExceptionMessage = e.toString();
        }
    }

    /**
     * Apply {@code jsOnDisplay}-mutated {@code name}/{@code blink} back onto the tile, overriding
     * the values written earlier in {@link #onBindViewHolder}. Tile blink is expressed as alpha.
     */
    private void applyNameAndBlinkOverrides(View tile, MetricBasicScriptableOnDisplay ev) {
        TextView nameTv = tile.findViewById(R.id.nameTextView);
        if (nameTv != null) nameTv.setText(ev.getName() == null ? "" : ev.getName());
        int blinkState = ev.getMetric().blinkState;
        tile.setAlpha((ev.getBlink() && blinkState == 1) ? 0.5f : 1.0f);
    }

    /**
     * Parse a "#RRGGBB" / "#AARRGGBB" string; fall back to {@code fallback} on any failure.
     * Forces full alpha when the input has no alpha channel.
     */
    private static int parseColorOrDefault(String hex, int fallback) {
        if (hex == null || hex.length() == 0) return fallback;
        try {
            int c = Color.parseColor(hex.trim());
            if (hex.trim().length() == 7) c |= 0xFF000000;  // no alpha in "#RRGGBB"
            return c;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Bind one tile: name, bottom text, intermediate spinner, blink, value — runs the
     * metric's {@code jsOnDisplay} hook before committing so scripts can mutate colors,
     * text, etc. via the scriptable event.
     */
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        MetricBasic m = metrics.get(position);
        View tile = holder.itemView;
        tile.setTag(R.string.TAG_METRIC, m);

        TextView nameTv   = tile.findViewById(R.id.nameTextView);
        TextView bottomTv = tile.findViewById(R.id.bottomTextView);
        ProgressBar spinner = tile.findViewById(R.id.intermediateProgressBar);

        if (nameTv   != null) nameTv.setText(m.name == null ? "" : m.name);
        if (bottomTv != null) bottomTv.setText(bottomLineFor(m));

        if (spinner != null) {
            boolean waiting = (m instanceof MetricBasicMqtt) && ((MetricBasicMqtt) m).isInIntermediateState();
            spinner.setVisibility(waiting ? View.VISIBLE : View.GONE);
        }

        // Blink: alternate alpha on the tile.
        tile.setAlpha((m.blink && m.blinkState == 1) ? 0.5f : 1.0f);

        // Background reset in case a previous touch left it tinted and the view was recycled.
        tile.setBackgroundColor(ContextCompat.getColor(mMetricsListActivity, R.color.colorTile));

        switch (m.type) {
            case MetricBasic.METRIC_TYPE_TEXT:         bindText(tile, (MetricText) m); break;
            case MetricBasic.METRIC_TYPE_SWITCH:       bindSwitch(tile, (MetricSwitch) m); break;
            case MetricBasic.METRIC_TYPE_RANGE:        bindRange(tile, (MetricRange) m); break;
            case MetricBasic.METRIC_TYPE_MULTI_SWITCH: bindMultiSwitch(tile, (MetricMultiSwitch) m); break;
            case MetricBasic.METRIC_TYPE_IMAGE:        bindImage(tile, (MetricImage) m); break;
            case MetricBasic.METRIC_TYPE_COLOR:        bindColor(tile, (MetricColor) m); break;
            default: break;
        }
    }

    /** Bottom-line text: either the last-activity relative time, or an error / error tag. */
    private static String bottomLineFor(MetricBasic m) {
        if (m instanceof MetricImage) {
            MetricImage im = (MetricImage) m;
            if (im.lastImageDownloadError != null && im.lastImageDownloadError.length() > 0) {
                return im.lastImageDownloadError;
            }
        }
        return m.getLastActivityDateTimeString();
    }

    private void bindText(View tile, MetricText m) {
        TextView valueTv = tile.findViewById(R.id.textView);
        if (valueTv == null) return;
        String raw = (m.jsonPath != null && m.jsonPath.length() > 0) ? m.lastJsonPathValue : m.lastPayload;
        String shown = (raw == null) ? mMetricsListActivity.getString(R.string.dash)
                                     : (m.prefix + raw + m.postfix);

        MetricTextScriptableOnDisplay ev =
                new MetricTextScriptableOnDisplay(mMetricsListActivity, m, shown, tile);
        runOnDisplayHook(m, ev);

        valueTv.setText(ev.getText());
        valueTv.setTextColor(parseColorOrDefault(ev.getTextColor(), m.textColor));
        valueTv.setTextSize(textSizeSpFor(m.mainTextSize));
        applyNameAndBlinkOverrides(tile, ev);
    }

    private static float textSizeSpFor(MetricText.TextSize size) {
        switch (size) {
            case SMALL:  return 18f;
            case MEDIUM: return 26f;
            case LARGE:
            default:     return 35f;
        }
    }

    private static float textSizeSpFor(MetricMultiSwitch.TextSize size) {
        switch (size) {
            case SMALL:  return 18f;
            case MEDIUM: return 26f;
            case LARGE:
            default:     return 35f;
        }
    }

    private void bindSwitch(View tile, MetricSwitch m) {
        ImageView iv = tile.findViewById(R.id.iconImageView);
        if (iv == null) return;
        String payloadCompare = (m.jsonPath != null && m.jsonPath.length() > 0)
                ? m.lastJsonPathValue : m.lastPayload;
        boolean isOn = payloadCompare != null && payloadCompare.equals(m.payloadOn);

        MetricSwitchScriptableOnDisplay ev =
                new MetricSwitchScriptableOnDisplay(mMetricsListActivity, m, isOn, tile);
        runOnDisplayHook(m, ev);

        boolean on = ev.getOn();
        String iconName = on ? m.iconOn : m.iconOff;
        int color = parseColorOrDefault(ev.getIconColor(), on ? m.onColor : m.offColor);
        int resId = mMetricsListActivity.getResources().getIdentifier(
                iconName, "drawable", mMetricsListActivity.getPackageName());
        if (resId != 0) {
            Drawable d = AppCompatResources.getDrawable(mMetricsListActivity, resId);
            applyColorToDrawable(d, color);
            iv.setImageDrawable(d);
        }
        applyNameAndBlinkOverrides(tile, ev);
    }

    private void bindRange(View tile, MetricRange m) {
        TextView valueTv = tile.findViewById(R.id.valueTextView);
        SeekArc arc = tile.findViewById(R.id.seekArc);

        MetricRangeScriptableOnDisplay ev =
                new MetricRangeScriptableOnDisplay(mMetricsListActivity, m, m.getStringValue(), tile);
        runOnDisplayHook(m, ev);

        if (valueTv != null) valueTv.setText(ev.getText());
        if (arc != null) {
            arc.setProgress((int) ev.getProgress());
            int color = parseColorOrDefault(ev.getProgressColor(), m.progressColor);
            if (color != -1) arc.setProgressColor(color);
        }
        applyNameAndBlinkOverrides(tile, ev);
    }

    private void bindMultiSwitch(View tile, MetricMultiSwitch m) {
        TextView textTv = tile.findViewById(R.id.textView);
        if (textTv == null) return;
        String payloadCompare = (m.jsonPath != null && m.jsonPath.length() > 0)
                ? m.lastJsonPathValue : m.lastPayload;
        String label = mMetricsListActivity.getString(R.string.dash);
        if (payloadCompare != null) {
            for (MetricMultiSwitchItem it : m.items) {
                if (payloadCompare.equals(it.payload)) { label = it.label; break; }
            }
        }

        MetricMultiSwitchScriptableOnDisplay ev =
                new MetricMultiSwitchScriptableOnDisplay(mMetricsListActivity, m, label, tile);
        runOnDisplayHook(m, ev);

        textTv.setText(ev.getText());
        textTv.setTextColor(parseColorOrDefault(ev.getTextColor(), m.textColor));
        textTv.setTextSize(textSizeSpFor(m.mainTextSize));
        applyNameAndBlinkOverrides(tile, ev);
    }

    private void bindImage(View tile, MetricImage m) {
        ImageView iv = tile.findViewById(R.id.imageView);
        if (iv == null) return;
        TextView  logTv = tile.findViewById(R.id.logTextView);

        MetricImageScriptableOnDisplay ev =
                new MetricImageScriptableOnDisplay(mMetricsListActivity, m, tile);
        runOnDisplayHook(m, ev);
        // Allow scripts to swap the image URL before the downloader kicks in.
        if (ev.getUrl() != null && !ev.getUrl().equals(m.imageUrl)) m.imageUrl = ev.getUrl();

        reloadImageForMetric(m);
        Bitmap b = m.getBitmap();
        boolean hasError = m.lastImageDownloadError != null && m.lastImageDownloadError.length() > 0;

        // ViewHolders are recycled: always reassign the bitmap (null clears a stale image from
        // a different tile) and toggle the error overlay so a failed download doesn't show the
        // previous tile's image.
        if (hasError) {
            iv.setImageBitmap(null);
            iv.setVisibility(View.GONE);
            if (logTv != null) {
                String url = m.imageUrl != null ? m.imageUrl : "";
                logTv.setText(m.lastImageDownloadError + (url.isEmpty() ? "" : "\n" + url));
                logTv.setVisibility(View.VISIBLE);
            }
        } else {
            iv.setImageBitmap(b);
            iv.setVisibility(View.VISIBLE);
            if (logTv != null) logTv.setVisibility(View.GONE);
        }
        applyNameAndBlinkOverrides(tile, ev);
    }

    private void bindColor(View tile, MetricColor m) {
        ImageView iv = tile.findViewById(R.id.iconImageView);
        if (iv == null) return;
        int resId = mMetricsListActivity.getResources().getIdentifier(
                m.icon, "drawable", mMetricsListActivity.getPackageName());
        if (resId == 0) return;

        int tint = parseColorFromPayload(m);
        String initialHex = String.format(Locale.ROOT, "#%06X", tint & 0x00FFFFFF);
        MetricColorScriptableOnDisplay ev =
                new MetricColorScriptableOnDisplay(mMetricsListActivity, m, initialHex, tile);
        runOnDisplayHook(m, ev);

        Drawable d = AppCompatResources.getDrawable(mMetricsListActivity, resId);
        applyColorToDrawable(d, parseColorOrDefault(ev.getColor(), tint));
        iv.setImageDrawable(d);
        applyNameAndBlinkOverrides(tile, ev);
    }

    /** Parse the last payload into a color according to {@link MetricColor#format}; fallback black. */
    private static int parseColorFromPayload(MetricColor m) {
        String raw = (m.jsonPath != null && m.jsonPath.length() > 0) ? m.lastJsonPathValue : m.lastPayload;
        if (raw == null || raw.length() == 0) return Color.BLACK;
        try {
            if (m.format == MetricColor.COLOR_FORMAT_INT) {
                long v = Long.parseLong(raw.trim());
                return (int) v | 0xFF000000;  // force full alpha
            }
            String s = raw.trim();
            if (!s.startsWith("#")) s = "#" + s;
            return Color.parseColor(s);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }

    // --- DraggableItemAdapter ---------------------------------------------------------------

    @Override public boolean onCheckCanDrop(int draggingPos, int dropPos) { return true; }

    @Override public boolean onCheckCanStartDrag(ViewHolder vh, int pos, int x, int y) { return true; }

    @Override public ItemDraggableRange onGetItemDraggableRange(ViewHolder vh, int pos) { return null; }

    @Override public void onItemDragStarted(int position) { }

    @Override public void onItemDragFinished(int fromPosition, int toPosition, boolean result) { }

    @Override
    public void onMoveItem(int from, int to) {
        Log.d("drag", String.format(Locale.ROOT, "onMoveItem(fromPosition = %d, toPosition = %d)", from, to));
        if (from == to) return;
        MetricBasic moved = metrics.remove(from);
        metrics.add(to, moved);

        SharedPreferences prefs = mMetricsListActivity.getSharedPreferences(
                BrokersListActivity.BROKERS_PREFS, 0);
        String json = new Gson().toJson(metrics);
        SharedPreferences.Editor ed = prefs.edit();
        ed.remove(mBroker.id);
        ed.putString(mBroker.id, json);
        if (ed.commit()) notifyItemMoved(from, to);
    }

    /** Stop the background timer. Safe to call multiple times. */
    public void stop() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
            mTimer = null;
        }
    }

    // --- ViewHolder -------------------------------------------------------------------------

    /** Single view holder for every tile type; click forwarded to an injectable handler. */
    public static class ViewHolder extends AbstractDraggableItemViewHolder
            implements View.OnClickListener {

        public interface IViewHolderEventHandler {
            void onClick(View tile);
        }

        private final IViewHolderEventHandler mEventsHandler;

        public ViewHolder(View itemView, IViewHolderEventHandler handler) {
            super(itemView);
            this.mEventsHandler = handler;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (mEventsHandler != null) mEventsHandler.onClick(itemView);
        }
    }
}
