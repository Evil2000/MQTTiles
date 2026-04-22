package de.evil2000.mqttiles;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.faendir.rhino_android.RhinoAndroidHelper;
import com.google.gson.Gson;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;
import com.thebluealliance.spectrum.SpectrumDialog;
import com.triggertrap.seekarc.SeekArc;

import info.mqtt.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import hotchemi.android.rate.AppRate;
import hotchemi.android.rate.StoreType;

/**
 * Hosts the scrolling grid of metric tiles for a single {@link Broker}. Owns the MQTT
 * connection, the {@link MetricsAdapter}, the Rhino JavaScript context used by on-receive/
 * on-display/on-tap hooks, and all per-tile interaction dialogs.
 *
 * <p>Flow: onCreate builds the Rhino context + recycler + adapter → onResume calls
 * {@link #connect()} → {@link MqttCallback#messageArrived} routes payloads into metrics
 * → user taps a tile → {@link #dispatchTileClick} runs the optional jsOnTap hook and
 * then one of the per-type {@code MetricClickHandler} overloads.
 */
public class MetricsListActivity extends AppCompatActivity implements MqttCallback {

    // Request codes: 0..5 add a new metric of the matching type; 100..105 edit an existing one.
    private static final int ADD_TEXT_METRIC_REQUEST         = 0;
    private static final int ADD_SWITCH_METRIC_REQUEST       = 1;
    private static final int ADD_RANGE_METRIC_REQUEST        = 2;
    private static final int ADD_MULTI_SWITCH_METRIC_REQUEST = 3;
    private static final int ADD_IMAGE_METRIC_REQUEST        = 4;
    private static final int ADD_COLOR_METRIC_REQUEST        = 5;
    private static final int EDIT_TEXT_METRIC_REQUEST         = 100;
    private static final int EDIT_SWITCH_METRIC_REQUEST       = 101;
    private static final int EDIT_RANGE_METRIC_REQUEST        = 102;
    private static final int EDIT_MULTI_SWITCH_METRIC_REQUEST = 103;
    private static final int EDIT_IMAGE_METRIC_REQUEST        = 104;
    private static final int EDIT_COLOR_METRIC_REQUEST        = 105;

    private static final int CTX_EDIT                    = 0;
    private static final int CTX_CLONE                   = 1;
    private static final int CTX_DELETE                  = 2;
    private static final int CTX_DELETE_RETAINED_MESSAGE = 3;

    private static final String LIST_POS = "LIST_POS";
    public  static       String METRIC_DATA = "METRIC_DATA";
    private static final String TAG = "METRICS_LIST";

    private RecyclerViewDragDropManager dragMgr;
    private RecyclerView.Adapter        mAdapter;
    private MetricsAdapter              mMetricsAdapter;
    private MqttClientPersistence       persistence;
    private LinearLayout                mProgress;
    private RecyclerView                mRecyclerView;

    private boolean destroying       = false;
    private Intent  starterIntent    = null;
    private final Handler handler    = new Handler(Looper.getMainLooper());
    private Runnable runnable        = null;

    public Broker             broker  = null;
    public MqttAndroidClient  client  = null;
    public boolean rearranging = false;
    public boolean editing     = false;
    public boolean showingPopup = false;
    public boolean exiting     = false;
    public boolean visible     = false;

    private boolean isConnectionLost   = false;
    /** While true, {@link #reconnect(boolean)} is a no-op (verbose-error dialog is open). */
    private boolean connectionErrorDialogOpen = false;
    private boolean saveMetricsOnStop  = true;
    private String  importMetricsTopic = null;
    private Dialog  importExportDialog = null;
    private AlertDialog setValueAlertDialog = null;

    // Rhino JS context shared by all JS hooks on this screen.
    public RhinoAndroidHelper js        = null;
    public Context            jsContext = null;
    public Scriptable         jsScope   = null;
    /** Per-metric persistent scopes so {@code var}s declared in onReceive are visible in
     *  onDisplay / onTap on the same tile. Keyed by {@link MetricBasic#id}. */
    private final java.util.HashMap<String, Scriptable> jsScopesByMetricId = new java.util.HashMap<>();

    // =========================================================================================
    // Lifecycle
    // =========================================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.starterIntent = getIntent();

        try {
            this.broker = new Gson().fromJson(
                    this.starterIntent.getStringExtra(BrokersListActivity.BROKER_DATA),
                    Broker.class);

            ActionBar bar = getSupportActionBar();
            if (bar != null) {
                bar.setTitle(this.broker.name);
                if (!this.broker.metricsEditable && !this.broker.neverHideBrokerName) bar.hide();
            }
            Window window = getWindow();
            if (window != null) {
                window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                WindowManager.LayoutParams.FLAG_FULLSCREEN);
                if (this.broker.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else                          window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            this.js        = new RhinoAndroidHelper(this);
            this.jsContext = this.js.enterContext();
            this.jsContext.setOptimizationLevel(-1);
            this.jsScope   = new ImporterTopLevel(this.jsContext);

            setContentView(R.layout.activity_metrics_list);
            this.mRecyclerView = findViewById(R.id.metrics_recycler_view);
            this.mProgress     = findViewById(R.id.progressbar_view);

            try {
                ((SimpleItemAnimator) this.mRecyclerView.getItemAnimator())
                        .setSupportsChangeAnimations(false);
                this.mRecyclerView.getItemAnimator().setChangeDuration(0L);
                this.mRecyclerView.setHasFixedSize(true);
            } catch (Exception e) {
                Toast.makeText(this, R.string.cant_configure_metrics_list, Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }

            boolean fromBrokersList = this.starterIntent.getBooleanExtra(
                    BrokersListActivity.FROM_BROKERS_LIST, false);
            this.starterIntent.putExtra(BrokersListActivity.FROM_BROKERS_LIST, false);

            try {
                handleRotation();
                this.mRecyclerView.addItemDecoration(new ItemOffsetDecoration(this, R.dimen.item_offset));

                ArrayList<MetricBasic> loaded;
                try {
                    loaded = loadMetrics();
                } catch (JSONException ex) {
                    ex.printStackTrace();
                    Toast.makeText(this, ex.getMessage() != null ? ex.getMessage()
                            : getString(R.string.cant_load_saved_metrics), Toast.LENGTH_LONG).show();
                    loaded = null;
                }
                if (loaded == null) loaded = new ArrayList<>();

                // After returning from the brokers list, reset intermediate-state timers so
                // stale "waiting" spinners don't carry across sessions.
                if (fromBrokersList) {
                    for (MetricBasic o : loaded) {
                        try {
                            if (o instanceof MetricBasicMqtt) {
                                ((MetricBasicMqtt) o).enterIntermediateState(false);
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, R.string.cant_reset_progress, Toast.LENGTH_LONG).show();
                        }
                    }
                }

                try {
                    this.dragMgr         = new RecyclerViewDragDropManager();
                    this.mMetricsAdapter = new MetricsAdapter(this, loaded, this.broker);
                    this.mAdapter        = this.dragMgr.createWrappedAdapter(this.mMetricsAdapter);
                    this.mRecyclerView.setAdapter(this.mAdapter);
                    this.dragMgr.setInitiateOnMove(false);
                    this.dragMgr.setInitiateOnLongPress(false);
                    this.dragMgr.attachRecyclerView(this.mRecyclerView);

                    try {
                        AppRate.with(this)
                                .setStoreType(StoreType.GOOGLEPLAY)
                                .setInstallDays(10)
                                .setLaunchTimes(8)
                                .setRemindInterval(4)
                                .setShowLaterButton(true)
                                .setCancelable(true)
                                .setTitle(R.string.new_rate_dialog_title)
                                .setMessage(R.string.new_rate_dialog_text)
                                .monitor();
                        AppRate.showRateDialogIfMeetsConditions(this);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, R.string.cant_initialize_metrics_list, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                    finish();
                }
            } catch (Exception e) {
                Toast.makeText(this, R.string.handle_rotation_error, Toast.LENGTH_LONG).show();
                e.printStackTrace();
                finish();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.invalid_broker_data, Toast.LENGTH_LONG).show();
            e.printStackTrace();
            finish();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (!isOnline()) {
            Toast.makeText(this, getString(R.string.no_network_detected), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        this.visible = true;
        if (this.isConnectionLost) reconnect(true);
        else                       connect();
    }

    @Override protected void onPause() {
        super.onPause();
        this.visible = false;
    }

    @Override protected void onStop() {
        super.onStop();
        if (this.saveMetricsOnStop) saveMetrics(this.mMetricsAdapter.metrics);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        this.destroying = true;
        try { disconnect(); } catch (MqttPersistenceException ignored) { }
    }

    @Override public void onBackPressed() {
        super.onBackPressed();
        this.exiting = true;
        ((App) getApplication()).connectToDefaultConnection = false;
        try { disconnect(); } catch (MqttPersistenceException e) { e.printStackTrace(); }
        finish();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (this.destroying || this.exiting) return;
        handleRotation();
    }

    // =========================================================================================
    // Layout / grid sizing
    // =========================================================================================

    /** Pick a GridLayoutManager based on broker col-count overrides, or auto-fit to tile width. */
    public void handleRotation() {
        int rotation = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        boolean portrait = rotation == 0 || rotation == 2;

        if (this.broker.colsCount > 0 && portrait) {
            this.mRecyclerView.setLayoutManager(new GridLayoutManager(this, (int) this.broker.colsCount));
            return;
        }
        if (this.broker.colsCountHorizontal > 0 && !portrait) {
            this.mRecyclerView.setLayoutManager(new GridLayoutManager(this, (int) this.broker.colsCountHorizontal));
            return;
        }
        int offsetDp = ((int) (getResources().getDimension(R.dimen.item_offset)
                / getResources().getDisplayMetrics().density)) + 1;
        int tileWidthRes;
        switch (this.broker.tileWidth) {
            case Broker.TILE_SMALL: tileWidthRes = R.dimen.tile_width_small; break;
            case Broker.TILE_LARGE: tileWidthRes = R.dimen.tile_width_large; break;
            default:                tileWidthRes = R.dimen.tile_width;       break;
        }
        int tileDp = (int) (getResources().getDimension(tileWidthRes)
                / getResources().getDisplayMetrics().density);
        this.mRecyclerView.setLayoutManager(
                new GridAutofitLayoutManager(this, dpToPx(tileDp + offsetDp * 5), rotation));
    }

    public int dpToPx(int dp) {
        return Math.round((getResources().getDisplayMetrics().xdpi / 160.0f) * dp);
    }

    // =========================================================================================
    // Connectivity
    // =========================================================================================

    public boolean isOnline() {
        NetworkInfo ni = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE))
                .getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    /** Build an SSL factory that accepts any certificate. Used when {@code broker.trust} is set. */
    private static SSLSocketFactory createTrustAllSslSocketFactory() throws Exception {
        TrustManager[] trustAll = { new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        } };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx.getSocketFactory();
    }

    /** Open the MQTT connection, show the progress bar, and subscribe on success. */
    public void connect() {
        SocketFactory socketFactory;
        if (this.persistence == null) this.persistence = new MemoryPersistence();
        String url = (this.broker.ssl ? "ssl://" : "tcp://")
                + this.broker.address + ":" + this.broker.port;

        if (this.client == null || !this.client.isConnected()) {
            // hannesa2 fork: persistence is managed internally (defaults to MemoryPersistence,
            // matching what we were passing); 4-arg ctor here takes an Ack enum, not persistence.
            this.client = new MqttAndroidClient(this, url, this.broker.clientId);
        }
        if (this.client.isConnected()) return;

        this.client.setCallback(this);
        this.mProgress.setVisibility(View.VISIBLE);
        this.mRecyclerView.setVisibility(View.GONE);

        try {
            MqttConnectOptions opts = new MqttConnectOptions();
            if (this.broker.ssl) {
                if (this.broker.trust) {
                    try {
                        socketFactory = createTrustAllSslSocketFactory();
                    } catch (Exception e) {
                        Log.e(TAG, getString(R.string.cant_create_ssl_connection));
                        Toast.makeText(this, R.string.cant_create_ssl_connection, Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else {
                    socketFactory = SSLSocketFactory.getDefault();
                }
                opts.setSocketFactory(socketFactory);
            }
            if (this.broker.user != null && this.broker.user.trim().length() > 0)
                opts.setUserName(this.broker.user);
            if (this.broker.password != null && this.broker.password.trim().length() > 0)
                opts.setPassword(this.broker.password.toCharArray());
            opts.setKeepAliveInterval(30);

            this.client.connect(opts).setActionCallback(new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken token) {
                    mProgress.setVisibility(View.GONE);
                    mRecyclerView.setVisibility(View.VISIBLE);
                    setListPos(starterIntent.getIntExtra(LIST_POS, 0));
                    reloadTiles();
                }
                @Override public void onFailure(IMqttToken token, Throwable t) {
                    mProgress.setVisibility(View.GONE);
                    mRecyclerView.setVisibility(View.VISIBLE);
                    if (broker != null && broker.verboseConnectionErrors) {
                        showVerboseConnectionError(t);
                    } else {
                        Toast.makeText(MetricsListActivity.this, R.string.connection_failed, Toast.LENGTH_SHORT).show();
                        reconnect(false);
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, e.getMessage() != null ? e.getMessage()
                    : getString(R.string.connection_error), Toast.LENGTH_SHORT).show();
        }
    }

    public void disconnect() throws MqttPersistenceException {
        if (this.client == null) return;
        try {
            if (this.runnable != null) this.handler.removeCallbacks(this.runnable);
            this.mMetricsAdapter.stop();
            this.client.close();
            this.client.disconnect();
        } catch (Exception ignored) { }
        this.client.unregisterResources();
        this.client = null;
    }

    /** Schedule a reconnect attempt either immediately or after a 5-second back-off. */
    private void reconnect(final boolean now) {
        if (this.destroying || this.exiting) return;
        if (this.connectionErrorDialogOpen) return;
        this.runnable = () -> {
            if (destroying || exiting) return;
            if (!isOnline()) {
                Toast.makeText(MetricsListActivity.this,
                        getString(R.string.no_network_detected), Toast.LENGTH_SHORT).show();
                reconnect(false);
                return;
            }
            try { disconnect(); } catch (MqttPersistenceException e) { e.printStackTrace(); }
            recreate();
        };
        if (now) this.handler.post(this.runnable);
        else     this.handler.postDelayed(this.runnable, 5000L);
    }

    private void setListPos(int pos) {
        this.mRecyclerView.getLayoutManager().scrollToPosition(pos);
    }

    /**
     * Render a modal AlertDialog with a full breakdown of the connection failure and
     * suspend reconnect attempts until the user dismisses it.
     */
    private void showVerboseConnectionError(Throwable t) {
        if (this.destroying || this.exiting) return;
        this.connectionErrorDialogOpen = true;

        StringBuilder sb = new StringBuilder();
        sb.append("Broker: ").append(broker != null ? broker.name : "?").append('\n');
        if (broker != null) {
            sb.append("URI: ")
              .append(broker.ssl ? "ssl://" : "tcp://")
              .append(broker.address).append(':').append(broker.port).append('\n');
            if (broker.user != null && !broker.user.isEmpty())
                sb.append("User: ").append(broker.user).append('\n');
            sb.append("Client-id: ").append(broker.clientId).append('\n');
        }
        sb.append('\n');

        if (t == null) {
            sb.append("(no throwable reported)");
        } else {
            sb.append(t.getClass().getName()).append('\n');
            if (t.getMessage() != null) sb.append(t.getMessage()).append('\n');
            if (t instanceof MqttException) {
                MqttException me = (MqttException) t;
                sb.append("MQTT reason code: ").append(me.getReasonCode()).append('\n');
            }
            Throwable c = t.getCause();
            int depth = 0;
            while (c != null && depth < 5) {
                sb.append("\nCaused by: ").append(c.getClass().getName());
                if (c.getMessage() != null) sb.append(": ").append(c.getMessage());
                c = c.getCause();
                depth++;
            }
            sb.append("\n\nStack:\n").append(Log.getStackTraceString(t));
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.connection_error_title)
                .setMessage(sb.toString())
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    connectionErrorDialogOpen = false;
                    reconnect(false);
                })
                .show();
    }

    // =========================================================================================
    // MQTT callbacks
    // =========================================================================================

    @Override public void connectionLost(Throwable cause) {
        Log.e(TAG, cause != null ? String.valueOf(cause.getMessage()) : "Connection lost");
        if (this.destroying || this.exiting) return;
        this.isConnectionLost = true;
        if (!this.visible || this.editing || this.rearranging || this.showingPopup) return;
        runOnUiThread(() -> reconnect(true));
    }

    @Override public void deliveryComplete(IMqttDeliveryToken token) { }

    /**
     * Dispatch an incoming payload either to the import handler (if {@code topic} matches
     * the import-export subscription) or to every subscribed {@link MetricBasicMqtt} whose
     * topic matches. Also runs the optional jsOnReceive hook.
     */
    @Override public void messageArrived(final String topic, final MqttMessage msg) {
        runOnUiThread(() -> {
            if (importMetricsTopic != null && topic.equals(importMetricsTopic)) {
                saveMetrics(msg.toString());
                saveMetricsOnStop = false;
                try {
                    if (importExportDialog != null) importExportDialog.dismiss();
                } finally {
                    importExportDialog = null;
                    reconnect(true);
                    Toast.makeText(MetricsListActivity.this,
                            getString(R.string.metrics_data_received), Toast.LENGTH_LONG).show();
                }
                return;
            }

            ArrayList<MetricBasic> metrics = mMetricsAdapter.metrics;
            for (int i = 0; i < metrics.size(); i++) {
                MetricBasic m = metrics.get(i);
                if (!(m instanceof MetricBasicMqtt)) continue;
                MetricBasicMqtt mm = (MetricBasicMqtt) m;

                // MetricImage with kind=IMAGE_URL polls independently, never consumes MQTT.
                if (mm instanceof MetricImage && ((MetricImage) mm).kind == MetricImage.IMAGE_URL) continue;
                if (mm.topic == null || mm.topic.compareTo(topic) != 0) continue;

                boolean wasIntermediate = mm.isInIntermediateState();
                String asText = (mm.type != MetricBasic.METRIC_TYPE_IMAGE
                        || ((MetricImage) mm).kind == MetricImage.IMAGE_URL_IN_PAYLOAD)
                        ? msg.toString() : "";
                mm.lastJsOnReceiveExceptionMessage = "";
                mm.lastJsOnReceiveExceptionDetail  = "";
                mm.lastJSONExceptionMessage       = "";

                if (mm.type == MetricBasic.METRIC_TYPE_IMAGE) {
                    ((MetricImage) mm).messageReceived(msg);
                } else {
                    mm.messageReceived(asText);
                }

                if (mm.lastJSONExceptionMessage.length() > 0) {
                    mAdapter.notifyItemChanged(i);
                    continue;
                }

                if (mm.jsOnReceive != null && mm.jsOnReceive.trim().length() > 0) {
                    try {
                        MetricMqttScriptableOnReceive ev =
                                new MetricMqttScriptableOnReceive(MetricsListActivity.this, mm, topic, asText);
                        AppScriptable app = new AppScriptable(MetricsListActivity.this, MetricsListActivity.this);
                        Scriptable scope = getJsScopeFor(mm);
                        addConstToJsScope(scope, ev,  "event");
                        addConstToJsScope(scope, app, "app");
                        jsEval(mm.jsOnReceive, scope, "<OnReceive>");
                        mm.messageReceived(ev.getPayload());
                    } catch (Exception e) {
                        mm.lastJsOnReceiveExceptionMessage = MetricBasic.formatJsErrorShort(e);
                        mm.lastJsOnReceiveExceptionDetail  = MetricBasic.formatJsErrorDetail(e);
                        mAdapter.notifyItemChanged(i);
                    }
                }

                if ((wasIntermediate || mm.lastPayloadChanged) && !rearranging) {
                    mAdapter.notifyItemChanged(i);
                }
            }
        });
    }

    // =========================================================================================
    // Rhino JS helpers
    // =========================================================================================

    public Scriptable getJsScope() {
        Scriptable s = this.jsContext.newObject(this.jsScope);
        s.setPrototype(this.jsScope);
        s.setParentScope(null);
        return s;
    }

    /**
     * Return the per-metric JS scratch object used by {@code event.data}, creating a fresh
     * empty native JS object on first access and caching it in {@link App#javaScriptMap}.
     * This lets hooks use {@code event.data['key'] = value} directly — the returned object
     * is live, so indexed/dot writes persist across all hook invocations on the same tile.
     */
    public Object getOrCreateJsData(MetricBasic m) {
        App app = (App) getApplication();
        Object o = app.javaScriptMap.get(m.id);
        if (o == null) {
            o = this.jsContext.newObject(this.jsScope);
            app.javaScriptMap.put(m.id, o);
        }
        return o;
    }

    /**
     * Return the persistent per-metric scope used by all three JS hooks on that metric,
     * creating it on first use. Chained under {@link #jsScope} so {@code app}/standard
     * bindings are visible; {@code var} declarations by hooks live here and persist
     * across onReceive → onDisplay → onTap calls.
     */
    public Scriptable getJsScopeFor(MetricBasic m) {
        Scriptable s = this.jsScopesByMetricId.get(m.id);
        if (s == null) {
            s = this.jsContext.newObject(this.jsScope);
            s.setPrototype(this.jsScope);
            s.setParentScope(null);
            this.jsScopesByMetricId.put(m.id, s);
        }
        return s;
    }

    public Scriptable addConstToJsScope(Scriptable scope, Object value, String name) {
        // Writable put (not putConstProperty): these bindings are re-set on every hook
        // invocation in the persistent per-metric scope and const would reject rebinding.
        ScriptableObject.putProperty(scope, name, Context.javaToJS(value, scope));
        return scope;
    }

    public Script jsCompile(String src, String name) {
        return this.jsContext.compileString(src, name, 1, null);
    }

    public String jsEval(String src, Scriptable scope, String name) {
        Object o = this.jsContext.evaluateString(scope, src, name, 1, null);
        return o != null ? Context.toString(o) : "";
    }

    public boolean jsEvalBooleanExpression(String src, Scriptable scope, String name) {
        return Context.toBoolean(this.jsContext.evaluateString(scope, src, name, 1, null));
    }

    public String jsExec(Script script, Scriptable scope) {
        Object o = script.exec(Context.getCurrentContext(), scope);
        return o != null ? Context.toString(o) : "";
    }

    public boolean jsExecBooleanExpression(Script script, Scriptable scope) {
        return Context.toBoolean(script.exec(Context.getCurrentContext(), scope));
    }

    /**
     * Evaluate {@link MetricBasic#jsBlinkExpression} against {@code val} (current raw
     * payload, or JSON-path extraction if set) and {@code secs} (seconds since last
     * activity), and write the boolean result into {@link MetricBasic#blink}. Empty
     * expression → blink off. Evaluation errors are surfaced via
     * {@code lastJsBlinkExpressionException*}.
     */
    public void evaluateBlinkExpression(MetricBasic m) {
        if (m.jsBlinkExpression == null || m.jsBlinkExpression.trim().length() == 0) {
            m.blink = false;
            m.lastJsBlinkExpressionExceptionMessage = "";
            m.lastJsBlinkExpressionExceptionDetail  = "";
            return;
        }
        try {
            Scriptable scope = getJsScopeFor(m);
            String val = "";
            if (m instanceof MetricBasicMqtt) {
                MetricBasicMqtt mm = (MetricBasicMqtt) m;
                String raw = (mm.jsonPath == null || mm.jsonPath.length() == 0)
                        ? mm.lastPayload : mm.lastJsonPathValue;
                if (raw != null) val = raw;
            }
            addConstToJsScope(scope, val, "val");
            addConstToJsScope(scope, m.getSecondsSinceLastActivity(), "secs");
            m.blink = jsEvalBooleanExpression(m.jsBlinkExpression, scope, "<BlinkExpression>");
            m.lastJsBlinkExpressionExceptionMessage = "";
            m.lastJsBlinkExpressionExceptionDetail  = "";
        } catch (Exception e) {
            m.blink = false;
            m.lastJsBlinkExpressionExceptionMessage = MetricBasic.formatJsErrorShort(e);
            m.lastJsBlinkExpressionExceptionDetail  = MetricBasic.formatJsErrorDetail(e);
        }
    }

    // =========================================================================================
    // Persistence
    // =========================================================================================

    /** Read the broker's metrics JSON from SharedPreferences and deserialize each into its subtype. */
    public ArrayList<MetricBasic> loadMetrics() throws JSONException {
        JSONArray arr = new JSONArray(loadMetricsString());
        ArrayList<MetricBasic> out = new ArrayList<>();
        Gson gson = new Gson();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            switch (obj.getInt("type")) {
                case MetricBasic.METRIC_TYPE_TEXT:         out.add(gson.fromJson(obj.toString(), MetricText.class));        break;
                case MetricBasic.METRIC_TYPE_SWITCH:       out.add(gson.fromJson(obj.toString(), MetricSwitch.class));      break;
                case MetricBasic.METRIC_TYPE_RANGE:        out.add(gson.fromJson(obj.toString(), MetricRange.class));       break;
                case MetricBasic.METRIC_TYPE_MULTI_SWITCH: out.add(gson.fromJson(obj.toString(), MetricMultiSwitch.class)); break;
                case MetricBasic.METRIC_TYPE_IMAGE:        out.add(gson.fromJson(obj.toString(), MetricImage.class));       break;
                case MetricBasic.METRIC_TYPE_COLOR:        out.add(gson.fromJson(obj.toString(), MetricColor.class));       break;
                default: Log.e(TAG, "Unknown metric type: " + obj); break;
            }
        }
        return out;
    }

    public String loadMetricsString() {
        return getSharedPreferences(BrokersListActivity.BROKERS_PREFS, 0)
                .getString(this.broker.id, "[]");
    }

    @SuppressLint("CommitPrefEdits")
    public void saveMetrics(String raw) {
        try {
            SharedPreferences.Editor ed = getSharedPreferences(BrokersListActivity.BROKERS_PREFS, 0).edit();
            ed.remove(this.broker.id);
            ed.putString(this.broker.id, raw);
            ed.commit();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.cant_save_metrics) + e.getLocalizedMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("CommitPrefEdits")
    public void saveMetrics(ArrayList<MetricBasic> metrics) {
        try {
            saveMetrics(new Gson().toJson(metrics));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.cant_save_metrics) + e.getLocalizedMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================================
    // Subscriptions
    // =========================================================================================

    public void reloadTiles() {
        this.mAdapter.notifyDataSetChanged();
        subscribeToAll();
    }

    /** Subscribe to every MQTT-backed metric's topic with its configured QoS. */
    public void subscribeToAll() {
        if (this.client == null || this.mMetricsAdapter == null || this.mMetricsAdapter.metrics == null) return;
        for (MetricBasic m : this.mMetricsAdapter.metrics) {
            if (!(m instanceof MetricBasicMqtt)) continue;
            final MetricBasicMqtt mm = (MetricBasicMqtt) m;
            if (mm.topic == null || mm.topic.length() == 0) continue;
            try {
                this.client.subscribe(mm.topic, mm.qos).setActionCallback(new IMqttActionListener() {
                    @Override public void onSuccess(IMqttToken t) { }
                    @Override public void onFailure(IMqttToken t, Throwable th) {
                        Log.e(TAG, "Failed to subscribe to: " + mm.topic);
                    }
                });
            } catch (Error | Exception e) {
                e.printStackTrace();
                Toast.makeText(this, R.string.subscription_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Unsubscribe from a topic, but only if no other metric is still using it.
     * (Guard avoids tearing down a shared subscription.)
     */
    public void unsubscribe(final String topic) {
        if (this.client == null || topic == null || topic.length() == 0) return;
        int users = 0;
        for (MetricBasic m : this.mMetricsAdapter.metrics) {
            if (!(m instanceof MetricBasicMqtt)) continue;
            MetricBasicMqtt mm = (MetricBasicMqtt) m;
            if (mm.topic != null && mm.topic.compareTo(topic) == 0) users++;
            if (users > 1) return;
        }
        try {
            this.client.unsubscribe(topic).setActionCallback(new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken t) { }
                @Override public void onFailure(IMqttToken t, Throwable th) {
                    if (th != null) th.printStackTrace();
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void unsubscribeFromAll() {
        for (MetricBasic m : this.mMetricsAdapter.metrics) {
            if (!(m instanceof MetricBasicMqtt)) continue;
            MetricBasicMqtt mm = (MetricBasicMqtt) m;
            if (mm.topic != null && mm.topic.length() != 0) unsubscribe(mm.topic);
        }
    }

    // =========================================================================================
    // Publishing
    // =========================================================================================

    /** Publish a raw topic/payload with explicit retain + QoS, invoking the listener on both outcomes. */
    public void publish(final String topic, final String payload, boolean retained,
                        int qos, final IMqttActionListener listener) {
        if (this.client == null || topic == null || payload == null) {
            if (listener != null) listener.onSuccess(null);
            return;
        }
        try {
            int actualQos = (qos > 1 || qos < 0) ? 0 : qos;
            this.client.publish(topic, payload.getBytes(), actualQos, retained)
                    .setActionCallback(new IMqttActionListener() {
                        @Override public void onSuccess(IMqttToken t) {
                            if (listener != null) listener.onSuccess(t);
                        }
                        @Override public void onFailure(IMqttToken t, Throwable th) {
                            if (listener != null) listener.onFailure(t, th);
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
            if (listener != null) listener.onFailure(null, e);
        }
    }

    /**
     * Publish a payload on a metric's configured topic (prefers {@code topicPub} if set).
     * On success either updates the lastPayload locally (if {@code updateLastPayloadOnPub})
     * or enters the intermediate "waiting for echo" state, then refreshes the tile.
     */
    public void publish(final MetricBasicMqtt metric, final String payload, boolean retained) {
        if (this.client == null || metric == null) return;
        if ((metric.topic == null && metric.topicPub == null) || payload == null) return;

        final String topicToUse = (metric.topicPub != null && metric.topicPub.length() > 0)
                ? metric.topicPub : metric.topic;
        try {
            byte qos = metric.qos <= 1 ? metric.qos : (byte) 1;
            this.client.publish(topicToUse, payload.getBytes(), qos, retained)
                    .setActionCallback(new IMqttActionListener() {
                        @Override public void onSuccess(IMqttToken t) {
                            if (metric.updateLastPayloadOnPub) {
                                metric.lastPayload        = payload;
                                metric.lastPayloadChanged = true;
                                metric.lastActivity       = (int) (new Date().getTime() / 1000);
                                metric.enterIntermediateState(false);
                            } else if (metric.enableIntermediateState) {
                                metric.enterIntermediateState(true);
                            }
                            int idx = mMetricsAdapter.metrics.indexOf(metric);
                            if (idx >= 0) runOnUiThread(() -> mAdapter.notifyItemChanged(idx));
                        }
                        @Override public void onFailure(IMqttToken t, Throwable th) {
                            Log.i(TAG, "Failed to publish to: " + metric.topic
                                    + (th != null ? ", " + th.getMessage() : ""));
                        }
                    });
        } catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================================================
    // Per-tile click handling
    // =========================================================================================

    /**
     * Entry point called by {@link MetricsAdapter}'s row click dispatcher. Runs the jsOnTap
     * hook (if any) and only calls the default handler if the hook did not call
     * {@code event.setPreventDefault(true)}.
     */
    private boolean runOnTapHook(MetricBasic metric, View tile) {
        if (metric.jsOnTap == null || metric.jsOnTap.trim().length() == 0) return false;
        try {
            MetricScriptableOnClick ev = new MetricScriptableOnClick(this, metric, tile);
            AppScriptable app = new AppScriptable(this, this);
            Scriptable scope = getJsScopeFor(metric);
            addConstToJsScope(scope, ev,  "event");
            addConstToJsScope(scope, app, "app");
            jsEval(metric.jsOnTap, scope, "<OnTap>");
            metric.lastJsOnTapExceptionMessage = "";
            metric.lastJsOnTapExceptionDetail  = "";
            return ev.getPreventDefault();
        } catch (Exception e) {
            metric.lastJsOnTapExceptionMessage = MetricBasic.formatJsErrorShort(e);
            metric.lastJsOnTapExceptionDetail  = MetricBasic.formatJsErrorDetail(e);
            int idx = mMetricsAdapter.metrics.indexOf(metric);
            if (idx >= 0) mAdapter.notifyItemChanged(idx);
            showJsErrorDialog(metric);
            return true;
        }
    }

    /** Show the metric's most recent JS error: hook name, message, and source location. */
    public void showJsErrorDialog(MetricBasic metric) {
        String hook;
        String message;
        String location;
        MetricBasicMqtt mm = (metric instanceof MetricBasicMqtt) ? (MetricBasicMqtt) metric : null;
        if (mm != null
                && mm.lastJsOnReceiveExceptionMessage != null
                && mm.lastJsOnReceiveExceptionMessage.length() > 0) {
            hook     = getString(R.string.on_receive);
            message  = mm.lastJsOnReceiveExceptionMessage;
            location = mm.lastJsOnReceiveExceptionDetail;
        } else if (metric.lastJsOnDisplayExceptionMessage != null
                && metric.lastJsOnDisplayExceptionMessage.length() > 0) {
            hook     = getString(R.string.on_display);
            message  = metric.lastJsOnDisplayExceptionMessage;
            location = metric.lastJsOnDisplayExceptionDetail;
        } else if (metric.lastJsOnTapExceptionMessage != null
                && metric.lastJsOnTapExceptionMessage.length() > 0) {
            hook     = getString(R.string.on_tap);
            message  = metric.lastJsOnTapExceptionMessage;
            location = metric.lastJsOnTapExceptionDetail;
        } else {
            return;
        }

        StringBuilder body = new StringBuilder(hook).append('\n').append(message);
        if (location != null && !location.isEmpty()) body.append('\n').append(location);

        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(body.toString());
        tv.setTextIsSelectable(true);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle(R.string.javascript_error)
                .setView(sv)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Text tile: show a small popup with prefix/value/postfix and optional set-button. */
    public void MetricClickHandler(final MetricText metric, View tile) {
        if (runOnTapHook(metric, tile)) return;
        this.showingPopup = true;

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        View content = getLayoutInflater().inflate(R.layout.text_metric_popup, null);
        TextView tvPrefix  = content.findViewById(R.id.textView8);
        TextView tvPostfix = content.findViewById(R.id.textView7);
        final EditText value = content.findViewById(R.id.valueEditText);
        Button setButton = content.findViewById(R.id.setButton);

        tvPrefix.setText(metric.prefix);
        tvPostfix.setText(metric.postfix);
        value.setText(metric.lastPayload == null ? "" : metric.lastPayload);

        b.setView(content);
        b.setTitle(metric.name);
        this.setValueAlertDialog = b.create();

        if (!metric.enablePub) {
            setButton.setVisibility(View.GONE);
            value.setEnabled(false);
            value.setFocusable(false);
        } else {
            setButton.setOnClickListener(v -> {
                publish(metric, value.getText().toString(), metric.retained);
                if (setValueAlertDialog != null) setValueAlertDialog.dismiss();
                setValueAlertDialog = null;
            });
        }
        this.setValueAlertDialog.setOnDismissListener(d -> dismissed());
        this.setValueAlertDialog.show();
    }

    /** Switch tile: publish the opposite of the current state immediately. */
    public void MetricClickHandler(MetricSwitch metric, View tile) {
        if (runOnTapHook(metric, tile)) return;
        if (!metric.enablePub) return;
        String current = (metric.jsonPath == null || metric.jsonPath.length() == 0)
                ? metric.lastPayload : metric.lastJsonPathValue;
        String next = (current != null && current.equals(metric.payloadOn))
                ? metric.payloadOff : metric.payloadOn;
        publish(metric, next, metric.retained);
    }

    /** Range tile: SeekArc popup; set button publishes the numeric value. */
    public void MetricClickHandler(final MetricRange metric, View tile) {
        if (runOnTapHook(metric, tile)) return;
        this.showingPopup = true;

        View content = getLayoutInflater().inflate(R.layout.range_metric_popup, null);
        final EditText value = content.findViewById(R.id.valueEditText);
        final SeekArc  arc   = content.findViewById(R.id.seekArc);
        Button setButton     = content.findViewById(R.id.setButton);

        final String fmt = "%." + metric.decimalPrecision + "f";
        value.setText(String.format(java.util.Locale.ROOT, fmt, metric.getValue()));
        arc.setProgress((int) metric.getProgress());
        arc.setOnSeekArcChangeListener(new SeekArc.OnSeekArcChangeListener() {
            @Override public void onProgressChanged(SeekArc seekArc, int progress, boolean fromUser) {
                if (!fromUser) return;
                float v = metric.minValue + progress * (metric.maxValue - metric.minValue) / 100f;
                value.setText(String.format(java.util.Locale.ROOT, fmt, v));
            }
            @Override public void onStartTrackingTouch(SeekArc seekArc) { }
            @Override public void onStopTrackingTouch(SeekArc seekArc)  { }
        });

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(metric.name)
                .setView(content);
        this.setValueAlertDialog = b.create();

        if (!metric.enablePub) {
            setButton.setVisibility(View.GONE);
            value.setEnabled(false);
            arc.setEnabled(false);
        } else {
            setButton.setOnClickListener(v -> {
                publish(metric, value.getText().toString(), metric.retained);
                if (setValueAlertDialog != null) setValueAlertDialog.dismiss();
                setValueAlertDialog = null;
            });
        }
        this.setValueAlertDialog.setOnDismissListener(d -> dismissed());
        this.setValueAlertDialog.show();
    }

    /** MultiSwitch tile: list-chooser AlertDialog; pick an item → publish its payload. */
    public void MetricClickHandler(final MetricMultiSwitch metric, View tile) {
        if (runOnTapHook(metric, tile)) return;
        if (!metric.enablePub || metric.items == null || metric.items.isEmpty()) return;
        this.showingPopup = true;

        CharSequence[] labels = new CharSequence[metric.items.size()];
        for (int i = 0; i < metric.items.size(); i++) {
            MetricMultiSwitchItem it = metric.items.get(i);
            labels[i] = (it.label == null || it.label.length() == 0) ? it.payload : it.label;
        }
        this.setValueAlertDialog = new AlertDialog.Builder(this)
                .setTitle(metric.name)
                .setItems(labels, (dialog, which) -> {
                    publish(metric, metric.items.get(which).payload, metric.retained);
                    dialog.dismiss();
                    setValueAlertDialog = null;
                })
                .create();
        this.setValueAlertDialog.setOnDismissListener(d -> dismissed());
        this.setValueAlertDialog.show();
    }

    /** Image tile: if openUrl is set, launch a browser; otherwise show the enlarged-image popup. */
    public void MetricClickHandler(final MetricImage metric, View tile) {
        if (runOnTapHook(metric, tile)) return;
        if (metric.openUrl != null && metric.openUrl.length() > 0) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(metric.openUrl)));
            } catch (Exception e) {
                Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
            return;
        }
        this.showingPopup = true;
        final Dialog dialog = new Dialog(this);
        metric.popup = dialog;
        dialog.setContentView(R.layout.image_metric_popup);
        dialog.setTitle(metric.name);

        ImageView iv = dialog.findViewById(R.id.imageView);
        if (metric.getBitmap() != null) iv.setImageBitmap(metric.getBitmap());
        iv.setOnClickListener(v -> {
            dialog.cancel();
            metric.popup = null;
            dismissed();
        });
        dialog.setOnDismissListener(d -> {
            metric.popup = null;
            dismissed();
        });
        dialog.show();
    }

    /** Color tile: SpectrumDialog; publishes the chosen color in the metric's configured format. */
    public void MetricClickHandler(final MetricColor metric, View tile) {
        if (runOnTapHook(metric, tile)) return;
        if (!metric.enablePub) return;
        this.showingPopup = true;

        int selected = parseCurrentColor(metric);
        new SpectrumDialog.Builder(this)
                .setColors(R.array.metric_colors)
                .setSelectedColor(selected)
                .setDismissOnColorSelected(true)
                .setOutlineWidth(2)
                .setOnColorSelectedListener((positive, color) -> {
                    dismissed();
                    if (!positive) return;
                    String payload = (metric.format == MetricColor.COLOR_FORMAT_INT)
                            ? String.valueOf(color & 0x00FFFFFF)
                            : String.format("#%02X%02X%02X",
                                    (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
                    publish(metric, payload, metric.retained);
                })
                .build()
                .show(getSupportFragmentManager(), "color_picker");
    }

    /** Parse the color tile's current payload (hex or int) into an ARGB int; fall back to black. */
    private static int parseCurrentColor(MetricColor m) {
        String raw = (m.jsonPath != null && m.jsonPath.length() > 0) ? m.lastJsonPathValue : m.lastPayload;
        if (raw == null || raw.length() == 0) return android.graphics.Color.BLACK;
        try {
            if (m.format == MetricColor.COLOR_FORMAT_INT) {
                return (int) Long.parseLong(raw.trim()) | 0xFF000000;
            }
            String s = raw.trim();
            if (!s.startsWith("#")) s = "#" + s;
            return android.graphics.Color.parseColor(s);
        } catch (Exception e) {
            return android.graphics.Color.BLACK;
        }
    }

    /** Common cleanup after any popup closes: clear flags and recover from dropped connections. */
    private void dismissed() {
        this.showingPopup = false;
        if (this.isConnectionLost) reconnect(true);
    }

    // =========================================================================================
    // Menu / context menu / activity result
    // =========================================================================================

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        if (!this.broker.metricsEditable) return false;
        getMenuInflater().inflate(R.menu.menu_metrics_list, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (!this.broker.metricsEditable) return super.onOptionsItemSelected(item);
        int id = item.getItemId();
        if (id == R.id.action_rearrange) {
            this.rearranging = !this.rearranging;
            if (this.rearranging) {
                item.setIcon(R.drawable.ic_lock_opened_outline);
                Toast.makeText(this, R.string.you_can_now_rearrange_metrics, Toast.LENGTH_SHORT).show();
            } else {
                item.setIcon(R.drawable.ic_lock_closed);
                this.mAdapter.notifyDataSetChanged();
            }
            this.dragMgr.setInitiateOnMove(this.rearranging);
            return true;
        }
        if (id == R.id.action_add) {
            showAddMetricChooser();
            return true;
        }
        if (id == R.id.action_import_export) {
            showImportExportDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** "Add metric" dialog — one entry per metric type, dispatching to its settings activity. */
    private void showAddMetricChooser() {
        CharSequence[] names = {
                getString(R.string.Text), getString(R.string.switch_button),
                getString(R.string.range_progress), getString(R.string.multi_choice),
                getString(R.string.image), getString(R.string.color) };
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_type)
                .setItems(names, (d, which) -> {
                    Class<?> cls;
                    int req;
                    switch (which) {
                        case 0: cls = MetricTextSettingsActivity.class;        req = ADD_TEXT_METRIC_REQUEST; break;
                        case 1: cls = MetricSwitchSettingsActivity.class;      req = ADD_SWITCH_METRIC_REQUEST; break;
                        case 2: cls = MetricRangeSettingsActivity.class;       req = ADD_RANGE_METRIC_REQUEST; break;
                        case 3: cls = MetricMultiSwitchSettingsActivity.class; req = ADD_MULTI_SWITCH_METRIC_REQUEST; break;
                        case 4: cls = MetricImageSettingsActivity.class;       req = ADD_IMAGE_METRIC_REQUEST; break;
                        case 5: cls = MetricColorSettingsActivity.class;       req = ADD_COLOR_METRIC_REQUEST; break;
                        default: return;
                    }
                    this.editing = true;
                    startActivityForResult(new Intent(this, cls), req);
                })
                .show();
    }

    /**
     * Import/export dialog: publish the broker's metrics JSON to a topic, or subscribe to
     * a topic and overwrite the local metrics from the next message.
     */
    private void showImportExportDialog() {
        this.importMetricsTopic = null;
        final Dialog dialog = new Dialog(this);
        this.importExportDialog = dialog;
        dialog.setContentView(R.layout.import_export_popup);
        dialog.setTitle(R.string.action_import_export);

        final EditText topicEdit = dialog.findViewById(R.id.topicEditText);
        final boolean[] subscribed = { false };
        final Button publishBtn          = dialog.findViewById(R.id.publishButton);
        final Button publishRetainedBtn  = dialog.findViewById(R.id.publishRetainedButton);
        final Button deleteRetainedBtn   = dialog.findViewById(R.id.deleteRetainedButton);
        final Button subscribeBtn        = dialog.findViewById(R.id.subscribeButton);
        final Button closeBtn            = dialog.findViewById(R.id.closeButton);

        View.OnClickListener publishHandler = v -> {
            boolean retained = v == publishRetainedBtn;
            importMetricsTopic = null;
            publish(topicEdit.getText().toString(), loadMetricsString(), retained, 0,
                    new IMqttActionListener() {
                        @Override public void onSuccess(IMqttToken t) {
                            Toast.makeText(MetricsListActivity.this,
                                    getString(R.string.published_successfully), Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onFailure(IMqttToken t, Throwable th) {
                            String m = (th != null && th.getMessage() != null && th.getMessage().length() > 0)
                                    ? th.getMessage() : getString(R.string.connection_error);
                            Toast.makeText(MetricsListActivity.this, m, Toast.LENGTH_SHORT).show();
                        }
                    });
        };
        publishBtn        .setOnClickListener(publishHandler);
        publishRetainedBtn.setOnClickListener(publishHandler);

        // Clearing a retained message is an empty payload published with retain=true.
        deleteRetainedBtn.setOnClickListener(v -> {
            importMetricsTopic = null;
            publish(topicEdit.getText().toString(), "", true, 0,
                    new IMqttActionListener() {
                        @Override public void onSuccess(IMqttToken t) {
                            Toast.makeText(MetricsListActivity.this,
                                    R.string.retained_deleted, Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onFailure(IMqttToken t, Throwable th) {
                            String m = (th != null && th.getMessage() != null && th.getMessage().length() > 0)
                                    ? th.getMessage() : getString(R.string.connection_error);
                            Toast.makeText(MetricsListActivity.this, m, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        subscribeBtn.setOnClickListener(v -> {
            if (subscribed[0]) {
                importMetricsTopic = null;
                unsubscribe(topicEdit.getText().toString());
                subscribed[0] = false;
                subscribeBtn.setText(R.string.subscribe_and_wait_for_metrics);
                topicEdit.setEnabled(true); topicEdit.setFocusable(true);
                publishBtn.setEnabled(true); publishRetainedBtn.setEnabled(true);
                deleteRetainedBtn.setEnabled(true);
                Toast.makeText(MetricsListActivity.this,
                        getString(R.string.unsibscribed_from) + " " + topicEdit.getText(),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                if (client == null) {
                    Toast.makeText(MetricsListActivity.this,
                            R.string.cant_subscribe_connection_issues, Toast.LENGTH_SHORT).show();
                    return;
                }
                importMetricsTopic = topicEdit.getText().toString();
                client.subscribe(importMetricsTopic, 0).setActionCallback(new IMqttActionListener() {
                    @Override public void onSuccess(IMqttToken t) {
                        subscribed[0] = true;
                        subscribeBtn.setText(R.string.unsubscribe);
                        topicEdit.setEnabled(false); topicEdit.setFocusable(false);
                        publishBtn.setEnabled(false); publishRetainedBtn.setEnabled(false);
                        deleteRetainedBtn.setEnabled(false);
                        Toast.makeText(MetricsListActivity.this,
                                R.string.subscribed_waiting_for_incoming_data, Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onFailure(IMqttToken t, Throwable th) {
                        subscribed[0] = false;
                        importMetricsTopic = null;
                        Toast.makeText(MetricsListActivity.this,
                                th != null ? th.getMessage() : getString(R.string.subscription_error),
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        importExportDialog = null;
                    }
                });
            } catch (Exception e) {
                subscribed[0] = false;
                importMetricsTopic = null;
                e.printStackTrace();
                Toast.makeText(MetricsListActivity.this,
                        e.getMessage() != null ? e.getMessage() : getString(R.string.subscription_error),
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                importExportDialog = null;
            }
        });

        closeBtn.setOnClickListener(v -> { dialog.dismiss(); importExportDialog = null; });
        dialog.setOnDismissListener(d -> {
            importMetricsTopic = null;
            if (subscribed[0]) {
                unsubscribe(topicEdit.getText().toString());
                Toast.makeText(MetricsListActivity.this,
                        getString(R.string.unsibscribed_from) + " " + topicEdit.getText(),
                        Toast.LENGTH_SHORT).show();
                importExportDialog = null;
            }
        });
        dialog.show();
    }

    @Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, v, info);
        if (!this.broker.metricsEditable) return;
        int idx = this.mMetricsAdapter.metrics.indexOf((MetricBasic) v.getTag(R.string.TAG_METRIC));
        menu.add(idx, CTX_EDIT,   0, R.string.edit);
        menu.add(idx, CTX_CLONE,  0, R.string.clone);
        menu.add(idx, CTX_DELETE, 0, R.string.delete_dotted);
        if (this.mMetricsAdapter.metrics.get(idx) instanceof MetricBasicMqtt) {
            MetricBasicMqtt mm = (MetricBasicMqtt) this.mMetricsAdapter.metrics.get(idx);
            boolean hasTopic    = mm.topic != null    && mm.topic.trim().length() > 0;
            boolean hasPubTopic = mm.topicPub != null && mm.topicPub.trim().length() > 0;
            if (hasTopic || hasPubTopic) menu.add(idx, CTX_DELETE_RETAINED_MESSAGE, 0,
                    R.string.delete_retained_message);
        }
    }

    @Override public boolean onContextItemSelected(MenuItem item) {
        if (!this.broker.metricsEditable) return false;
        try {
            switch (item.getItemId()) {
                case CTX_EDIT:   return onContextEdit(item);
                case CTX_CLONE:  return onContextClone(item);
                case CTX_DELETE: return onContextDelete(item);
                case CTX_DELETE_RETAINED_MESSAGE: return onContextDeleteRetained(item);
                default: return super.onContextItemSelected(item);
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.sorry_error_happened) + e.getLocalizedMessage(),
                    Toast.LENGTH_LONG).show();
            return true;
        }
    }

    private boolean onContextEdit(MenuItem item) {
        MetricBasic m = this.mMetricsAdapter.metrics.get(item.getGroupId());
        Class<?> cls; int req;
        switch (m.type) {
            case MetricBasic.METRIC_TYPE_TEXT:         cls = MetricTextSettingsActivity.class;        req = EDIT_TEXT_METRIC_REQUEST; break;
            case MetricBasic.METRIC_TYPE_SWITCH:       cls = MetricSwitchSettingsActivity.class;      req = EDIT_SWITCH_METRIC_REQUEST; break;
            case MetricBasic.METRIC_TYPE_RANGE:        cls = MetricRangeSettingsActivity.class;       req = EDIT_RANGE_METRIC_REQUEST; break;
            case MetricBasic.METRIC_TYPE_MULTI_SWITCH: cls = MetricMultiSwitchSettingsActivity.class; req = EDIT_MULTI_SWITCH_METRIC_REQUEST; break;
            case MetricBasic.METRIC_TYPE_IMAGE:        cls = MetricImageSettingsActivity.class;       req = EDIT_IMAGE_METRIC_REQUEST; break;
            case MetricBasic.METRIC_TYPE_COLOR:        cls = MetricColorSettingsActivity.class;       req = EDIT_COLOR_METRIC_REQUEST; break;
            default: return true;
        }
        Intent intent = new Intent(this, cls);
        intent.putExtra(METRIC_DATA, new Gson().toJson(m));
        this.editing = true;
        startActivityForResult(intent, req);
        return true;
    }

    private boolean onContextClone(MenuItem item) {
        this.editing = true;
        int groupId = item.getGroupId();
        MetricBasic original = this.mMetricsAdapter.metrics.get(groupId);
        Gson gson = new Gson();
        MetricBasic copy = (MetricBasic) gson.fromJson(gson.toJson(original), original.getClass());
        copy.id = UUID.randomUUID().toString();
        long maxLongId = 0;
        for (MetricBasic m : this.mMetricsAdapter.metrics)
            if (m.longId > maxLongId) maxLongId = m.longId;
        copy.longId = maxLongId + 1;
        copy.lastActivity = 0L;
        if (copy instanceof MetricBasicMqtt) {
            MetricBasicMqtt mm = (MetricBasicMqtt) copy;
            mm.lastPayloadChanged = false;
            mm.lastPayload        = null;
            mm.lastJsonPathValue  = null;
        }
        int at = groupId + 1;
        this.mMetricsAdapter.metrics.add(at, copy);
        saveMetrics(this.mMetricsAdapter.metrics);
        this.editing = false;
        if (this.isConnectionLost) { reconnect(true); reloadTiles(); }
        else                       { this.mMetricsAdapter.notifyItemInserted(at); }
        return true;
    }

    private boolean onContextDelete(MenuItem item) {
        final int groupId = item.getGroupId();
        DialogInterface.OnClickListener l = (d, which) -> {
            if (which != DialogInterface.BUTTON_POSITIVE) return;
            MetricBasic m = mMetricsAdapter.metrics.get(groupId);
            if (m instanceof MetricBasicMqtt) unsubscribe(((MetricBasicMqtt) m).topic);
            jsScopesByMetricId.remove(m.id);
            ((App) getApplication()).javaScriptMap.remove(m.id);
            mMetricsAdapter.metrics.remove(groupId);
            saveMetrics(mMetricsAdapter.metrics);
            mAdapter.notifyItemRemoved(groupId);
        };
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_metric_ask)
                .setPositiveButton(android.R.string.yes, l)
                .setNegativeButton(android.R.string.no,  l)
                .show();
        return true;
    }

    private boolean onContextDeleteRetained(MenuItem item) {
        final int groupId = item.getGroupId();
        DialogInterface.OnClickListener l = (d, which) -> {
            if (which != DialogInterface.BUTTON_POSITIVE) return;
            MetricBasic m = mMetricsAdapter.metrics.get(groupId);
            if (!(m instanceof MetricBasicMqtt)) return;
            MetricBasicMqtt mm = (MetricBasicMqtt) m;
            // Empty retained payload clears the server-side retention for that topic.
            if (mm.topic    != null && mm.topic.trim().length()    > 0) publish(mm.topic,    "", true, 0, null);
            if (mm.topicPub != null && mm.topicPub.trim().length() > 0) publish(mm.topicPub, "", true, 0, null);
        };
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_retained_message_question)
                .setPositiveButton(android.R.string.yes, l)
                .setNegativeButton(android.R.string.no,  l)
                .show();
        return true;
    }

    /**
     * Merge the saved metric payload from a settings activity back into the list.
     * Add-flow (codes 0-5) appends a new metric with a fresh longId. Edit-flow (100-105)
     * replaces the existing metric by id, preserving non-edited state and clearing stale
     * exception messages when the JS source actually changed.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || !this.broker.metricsEditable || data == null) return;
        String json = data.getStringExtra(METRIC_DATA);
        if (json == null) return;
        Gson gson = new Gson();

        switch (requestCode) {
            case ADD_TEXT_METRIC_REQUEST:         appendNew(gson.fromJson(json, MetricText.class));        break;
            case ADD_SWITCH_METRIC_REQUEST:       appendNew(gson.fromJson(json, MetricSwitch.class));      break;
            case ADD_RANGE_METRIC_REQUEST:        appendNew(gson.fromJson(json, MetricRange.class));       break;
            case ADD_MULTI_SWITCH_METRIC_REQUEST: appendNew(gson.fromJson(json, MetricMultiSwitch.class)); break;
            case ADD_IMAGE_METRIC_REQUEST:        appendNew(gson.fromJson(json, MetricImage.class));       break;
            case ADD_COLOR_METRIC_REQUEST:        appendNew(gson.fromJson(json, MetricColor.class));       break;

            case EDIT_TEXT_METRIC_REQUEST:         replaceExisting(gson.fromJson(json, MetricText.class));        break;
            case EDIT_SWITCH_METRIC_REQUEST:       replaceExisting(gson.fromJson(json, MetricSwitch.class));      break;
            case EDIT_RANGE_METRIC_REQUEST:        replaceExisting(gson.fromJson(json, MetricRange.class));       break;
            case EDIT_MULTI_SWITCH_METRIC_REQUEST: replaceExisting(gson.fromJson(json, MetricMultiSwitch.class)); break;
            case EDIT_IMAGE_METRIC_REQUEST:        replaceExisting(gson.fromJson(json, MetricImage.class));       break;
            case EDIT_COLOR_METRIC_REQUEST:        replaceExisting(gson.fromJson(json, MetricColor.class));       break;
            default: break;
        }
    }

    private void appendNew(MetricBasic metric) {
        long maxLongId = 0;
        for (MetricBasic m : this.mMetricsAdapter.metrics)
            if (m.longId > maxLongId) maxLongId = m.longId;
        metric.longId = maxLongId + 1;
        this.mMetricsAdapter.metrics.add(metric);
        saveMetrics(this.mMetricsAdapter.metrics);
        afterMetricEdit();
    }

    private void replaceExisting(MetricBasic updated) {
        int idx = -1;
        for (int i = 0; i < this.mMetricsAdapter.metrics.size(); i++) {
            if (this.mMetricsAdapter.metrics.get(i).id.compareTo(updated.id) == 0) { idx = i; break; }
        }
        if (idx > -1) {
            MetricBasic prev = this.mMetricsAdapter.metrics.get(idx);
            // Clear stale jsOnReceive exception if the source text changed (or image kind changed).
            if (updated instanceof MetricBasicMqtt && prev instanceof MetricBasicMqtt) {
                MetricBasicMqtt u = (MetricBasicMqtt) updated;
                MetricBasicMqtt p = (MetricBasicMqtt) prev;
                boolean imageKindChanged = (updated instanceof MetricImage && prev instanceof MetricImage)
                        && ((MetricImage) updated).kind != ((MetricImage) prev).kind;
                if (u.lastJsOnReceiveExceptionMessage.length() > 0
                        && (imageKindChanged || u.jsOnReceive.compareTo(p.jsOnReceive) != 0)) {
                    u.lastJsOnReceiveExceptionMessage = "";
                }
            }
            if (updated.lastJsBlinkExpressionExceptionMessage.length() > 0
                    && updated.jsBlinkExpression.compareTo(prev.jsBlinkExpression) != 0) {
                updated.lastJsBlinkExpressionExceptionMessage = "";
            }
            this.mMetricsAdapter.metrics.remove(idx);
            this.mMetricsAdapter.metrics.add(idx, updated);
            // JS source may have changed: drop the persistent scope and data so stale vars don't linger.
            jsScopesByMetricId.remove(updated.id);
            ((App) getApplication()).javaScriptMap.remove(updated.id);
        }
        saveMetrics(this.mMetricsAdapter.metrics);
        afterMetricEdit();
    }

    private void afterMetricEdit() {
        if (this.isConnectionLost) reconnect(true);
        else { this.editing = false; reloadTiles(); }
    }
}
