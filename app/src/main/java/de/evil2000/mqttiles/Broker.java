package de.evil2000.mqttiles;

import java.util.UUID;

/**
 * Persisted configuration for one MQTT broker (dashboard).
 *
 * Stored as a JSON array under SharedPreferences key
 * {@link BrokersListActivity#BROKERS_DATA} (file {@link BrokersListActivity#BROKERS_PREFS}).
 * Each broker's metrics are stored under its own {@link #id} key in the same prefs file.
 */
public class Broker {

    /** Tile size presets — used by {@link #tileWidth}. */
    public static final transient int TILE_SMALL  = -1;
    public static final transient int TILE_MEDIUM =  0;
    public static final transient int TILE_LARGE  =  1;

    /** Stable RecyclerView id (stableIds=true); monotonically assigned at insert time. */
    public long longId = 0;

    /** MQTT client-id suffix: short random tail prevents broker collisions when reusing a device. */
    public String clientId = "mqttiles-" + UUID.randomUUID().toString().split("-")[0];

    /** Persistent identity used as SharedPreferences key for this broker's metrics. */
    public String id = UUID.randomUUID().toString();

    public String name     = "";
    public String address  = "";
    public String port     = "1883";
    public String user     = "";
    public String password = "";

    public boolean ssl              = false;
    public boolean trust            = false;  // true = accept self-signed certs (trust-all)
    public boolean autoConnect      = false;  // pick as default on launcher start
    public boolean keepScreenOn     = true;
    public boolean metricsEditable  = true;
    public boolean neverHideBrokerName = false;
    /** Show a verbose AlertDialog on connection failure and block reconnects until dismissed. */
    public boolean verboseConnectionErrors = false;

    /** Explicit column counts; 0 = auto-fit based on tile width and screen. */
    public long colsCount = 0;
    public long colsCountHorizontal = 0;

    /** One of {@link #TILE_SMALL}, {@link #TILE_MEDIUM}, {@link #TILE_LARGE}. */
    public int tileWidth = 0;

    Broker() { }
}
