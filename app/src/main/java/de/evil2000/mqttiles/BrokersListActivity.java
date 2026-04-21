package de.evil2000.mqttiles;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;

import java.util.ArrayList;

/**
 * Top-level broker picker. Lists stored {@link Broker}s from SharedPreferences, supports
 * add/edit/delete, drag-to-reorder, and (on first launch) auto-connecting to the default.
 *
 * Context menu items (item-id → action): 0 edit, 1 delete, 2 create home-screen shortcut.
 */
public class BrokersListActivity extends AppCompatActivity {

    // ---------- intent / prefs keys ----------
    public static final String BROKERS_PREFS     = "BROKERS_PREFS";
    public static final String BROKERS_DATA      = "BROKERS_DATA";
    public static final String BROKER_DATA       = "BROKER_DATA";
    public static final String FROM_BROKERS_LIST = "FROM_BROKER_LIST";

    // ---------- startActivityForResult codes ----------
    private static final int ADD_BROKER_REQUEST  = 1;
    private static final int EDIT_BROKER_REQUEST = 2;
    private static final int RECONNECT_REQUEST   = 5;  // TODO: used by incoming reconnect flow; preserved for compatibility

    private RecyclerView              mBrokersRecyclerView;
    private RecyclerView.Adapter<?>   mAdapter;
    private RecyclerViewDragDropManager dragMgr;
    private ArrayList<Broker>         mBrokersList;

    /** User flipped to "rearrange mode" via the menu. While on, drag reorders are live. */
    private boolean rearranging = false;
    /** On first onStart, auto-connect to the default broker. Cleared on rotation. */
    private boolean autoconnect = true;

    // ----------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_brokers_list);

        mBrokersRecyclerView = findViewById(R.id.brokers_recycler_view);
        if (mBrokersRecyclerView == null) throw new AssertionError("recycler view missing");
        mBrokersRecyclerView.setHasFixedSize(true);
        mBrokersRecyclerView.setLayoutManager(
                new GridLayoutManager(this, 1, GridLayoutManager.VERTICAL, false));

        loadData();

        dragMgr  = new RecyclerViewDragDropManager();
        mAdapter = dragMgr.createWrappedAdapter(new BrokersAdapter(this, mBrokersList));
        mBrokersRecyclerView.setAdapter(mAdapter);

        dragMgr.setInitiateOnMove(false);
        dragMgr.setInitiateOnLongPress(false);
        dragMgr.attachRecyclerView(mBrokersRecyclerView);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // suppress auto-connect if we're just rotating
        this.autoconnect = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (tryConnectFromShortcut(getIntent())) return;

        App app = (App) getApplication();
        if (!this.autoconnect || !app.connectToDefaultConnection) return;
        this.autoconnect = false;

        // auto-connect to whichever broker is marked default
        for (Broker b : mBrokersList) {
            if (b.autoConnect) { connectTo(b); return; }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // When launched via pinned home-screen shortcut while the activity is already
        // in a back-stack, the intent arrives here rather than re-running onCreate.
        // Persist it so getIntent() reflects the latest launch, and honour it.
        setIntent(intent);
        tryConnectFromShortcut(intent);
    }

    /**
     * If {@code intent} carries a {@code broker_id} extra from a pinned shortcut,
     * connect to that broker and return {@code true}. Consumes the extra so a
     * subsequent rotation / onStart cycle doesn't reconnect.
     */
    private boolean tryConnectFromShortcut(Intent intent) {
        if (intent == null) return false;
        String brokerIdExtra = intent.getStringExtra("broker_id");
        if (brokerIdExtra == null || brokerIdExtra.trim().isEmpty()) return false;

        // consume — avoid a reconnect if the activity is simply recreated later
        intent.removeExtra("broker_id");
        this.autoconnect = false;

        for (Broker b : mBrokersList) {
            if (b.id.equals(brokerIdExtra)) { connectTo(b); return true; }
        }
        return true; // extra was present even if the broker is gone — suppress default auto-connect
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        System.exit(0);
    }

    // ----------------------------------------------------- data

    /** Load the persisted broker list from SharedPreferences (returns [] on miss). */
    public void loadData() {
        String json = getSharedPreferences(BROKERS_PREFS, 0).getString(BROKERS_DATA, "[]");
        mBrokersList = new Gson().fromJson(json, new TypeToken<ArrayList<Broker>>() {}.getType());
        if (mBrokersList == null) mBrokersList = new ArrayList<>();
    }

    /** Persist {@link #mBrokersList} to SharedPreferences. */
    private boolean saveData() {
        SharedPreferences.Editor e = getSharedPreferences(BROKERS_PREFS, 0).edit();
        e.putString(BROKERS_DATA, new Gson().toJson(mBrokersList));
        return e.commit();
    }

    // ----------------------------------------------------- clicks / connect

    /** Row-tap handler invoked by {@link BrokersAdapter}. */
    public void BrokerClickHandler(Broker broker, View view) {
        Log.d("click", broker.name);
        connectTo(broker);
    }

    /** Start {@link MetricsListActivity} pointed at {@code broker}. */
    public void connectTo(Broker broker) {
        Intent i = new Intent(this, MetricsListActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra(BROKER_DATA, new Gson().toJson(broker));
        i.putExtra(FROM_BROKERS_LIST, true);
        startActivity(i);
    }

    // ----------------------------------------------------- activity result
    //
    // The settings activity returns a Broker (as Gson JSON) in {@link #BROKER_DATA}; for
    // ADD we append, for EDIT we find-and-replace by id (falling back to append).

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        String json = data.getStringExtra(BROKER_DATA);
        if (json == null) return;
        Broker broker = new Gson().fromJson(json, Broker.class);
        if (broker == null) return;

        switch (requestCode) {
            case ADD_BROKER_REQUEST: {
                mBrokersList.add(broker);
                if (saveData()) mAdapter.notifyItemInserted(mBrokersList.size() - 1);
                break;
            }
            case EDIT_BROKER_REQUEST: {
                int idx = -1;
                for (int i = 0; i < mBrokersList.size(); i++) {
                    if (mBrokersList.get(i).id.equals(broker.id)) { idx = i; break; }
                }
                if (idx >= 0) {
                    mBrokersList.set(idx, broker);
                    if (saveData()) mAdapter.notifyItemChanged(idx);
                } else {
                    mBrokersList.add(broker);
                    if (saveData()) mAdapter.notifyItemInserted(mBrokersList.size() - 1);
                }
                break;
            }
            case RECONNECT_REQUEST:
                break;
            default: break;
        }
    }

    // ----------------------------------------------------- context menu (row long-press)

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, v, info);
        int idx = mBrokersList.indexOf((Broker) v.getTag(R.string.TAG_BROKER));
        menu.add(idx, 0, 0, R.string.edit);
        menu.add(idx, 1, 0, R.string.delete_dotted);
        menu.add(idx, 2, 0, R.string.create_desktop_shortcut);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        try {
            final int pos = item.getGroupId();
            switch (item.getItemId()) {
                case 0: {                                           // edit
                    Broker b = mBrokersList.get(pos);
                    Intent i = new Intent(this, BrokerSettingsActivity.class);
                    i.putExtra(BROKER_DATA, new Gson().toJson(b));
                    startActivityForResult(i, EDIT_BROKER_REQUEST);
                    return true;
                }
                case 1: {                                           // delete
                    DialogInterface.OnClickListener l = (dialog, which) -> {
                        if (which != DialogInterface.BUTTON_POSITIVE) return;
                        Broker b = mBrokersList.get(pos);
                        mBrokersList.remove(pos);
                        SharedPreferences.Editor e = getSharedPreferences(BROKERS_PREFS, 0).edit();
                        e.remove(BROKERS_DATA);
                        e.remove(b.id);          // broker's own per-id prefs slot
                        e.putString(BROKERS_DATA, new Gson().toJson(mBrokersList));
                        if (e.commit()) mAdapter.notifyItemRemoved(pos);
                    };
                    new AlertDialog.Builder(this)
                            .setMessage(R.string.delete_broker_ask)
                            .setPositiveButton(android.R.string.yes, l)
                            .setNegativeButton(android.R.string.no, l)
                            .show();
                    return true;
                }
                case 2: {                                           // home-screen shortcut
                    Broker b = mBrokersList.get(pos);
                    Intent launch = new Intent(getApplicationContext(), BrokersListActivity.class);
                    launch.setAction(Intent.ACTION_MAIN);
                    launch.putExtra("broker_id", b.id);
                    launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    // Legacy INSTALL_SHORTCUT broadcast is a no-op since Android 8 (API 26).
                    // Use ShortcutManagerCompat.requestPinShortcut — it prompts the user's
                    // launcher to pin, and falls back to the broadcast on API < 26.
                    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
                        Toast.makeText(this,
                                "Launcher does not support pinning shortcuts",
                                Toast.LENGTH_LONG).show();
                        return true;
                    }
                    String label = (b.name != null && !b.name.trim().isEmpty()) ? b.name : "MQTTiles";
                    ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(this, "broker-" + b.id)
                            .setShortLabel(label)
                            .setLongLabel(label)
                            .setIcon(IconCompat.createWithResource(this, R.mipmap._ic_shortcut))
                            .setIntent(launch)
                            .build();
                    ShortcutManagerCompat.requestPinShortcut(this, info, null);
                    return true;
                }
                default:
                    return super.onContextItemSelected(item);
            }
        } catch (Exception e) {
            Toast.makeText(this,
                    getString(R.string.sorry_error_happened) + e.getLocalizedMessage(),
                    Toast.LENGTH_LONG).show();
            return true;
        }
    }

    // ----------------------------------------------------- options menu (toolbar)

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_brokers_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_about) {
            Dialog d = new Dialog(this);
            d.setContentView(R.layout.about_dialog_popup);
            d.setTitle(R.string.about);
            ImageView iv = d.findViewById(R.id.imageView);
            iv.setImageResource(getResources().getIdentifier(
                    "app_icon", "drawable", getPackageName()));
            d.show();
            return true;
        }
        if (id == R.id.action_rearrange) {
            rearranging = !rearranging;
            if (rearranging) {
                item.setIcon(R.drawable.ic_lock_opened_outline);
                Toast.makeText(this, R.string.you_can_now_rearrange_brokers, Toast.LENGTH_SHORT).show();
            } else {
                item.setIcon(R.drawable.ic_lock_closed);
            }
            dragMgr.setInitiateOnMove(rearranging);
            return true;
        }
        if (id == R.id.action_add) {
            startActivityForResult(new Intent(this, BrokerSettingsActivity.class), ADD_BROKER_REQUEST);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
