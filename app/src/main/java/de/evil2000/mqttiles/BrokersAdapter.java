package de.evil2000.mqttiles;

import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder;

import java.util.ArrayList;

/**
 * RecyclerView adapter for the brokers list. One view type (broker row). Supports
 * drag-to-reorder via the advanced-recyclerview library; reorders are persisted to
 * SharedPreferences immediately.
 */
public class BrokersAdapter extends RecyclerView.Adapter<BrokersAdapter.ViewHolder>
        implements DraggableItemAdapter<BrokersAdapter.ViewHolder> {

    private static final int BROKER_VIEW_TYPE = 1;

    private final BrokersListActivity mBrokersListActivity;
    private final ArrayList<Broker>   mBrokers;

    public BrokersAdapter(BrokersListActivity host, ArrayList<Broker> brokers) {
        this.mBrokersListActivity = host;
        this.mBrokers = brokers;
        setHasStableIds(true);
    }

    @Override public int  getItemCount()              { return mBrokers.size(); }
    @Override public long getItemId(int position)     { return mBrokers.get(position).longId; }
    @Override public int  getItemViewType(int pos)    { return BROKER_VIEW_TYPE; }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.broker_layout, parent, false);
        final BrokersListActivity host = this.mBrokersListActivity;
        return new ViewHolder(v, clickedView ->
                host.BrokerClickHandler((Broker) clickedView.getTag(R.string.TAG_BROKER), clickedView));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        TextView name = holder.itemView.findViewById(R.id.nameTextView);
        Broker broker = mBrokers.get(position);
        holder.itemView.setTag(R.string.TAG_BROKER, broker);
        mBrokersListActivity.registerForContextMenu(holder.itemView);

        if (broker.autoConnect) {
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            name.setTextColor(ContextCompat.getColor(mBrokersListActivity, R.color.colorAccent));
            name.setText(String.format("%s (default)", broker.name));
        } else {
            name.setTypeface(null, android.graphics.Typeface.NORMAL);
            name.setTextColor(ContextCompat.getColor(mBrokersListActivity, R.color.light_gray_color));
            name.setText(broker.name);
        }
    }

    // ---------- draggable callbacks ----------

    @Override public boolean onCheckCanDrop(int from, int to)                        { return true; }
    @Override public boolean onCheckCanStartDrag(ViewHolder vh, int pos, int x, int y){ return true; }
    @Override public ItemDraggableRange onGetItemDraggableRange(ViewHolder vh, int p) { return null; }
    @Override public void onItemDragStarted(int position)                            { }
    @Override public void onItemDragFinished(int fromPosition, int toPosition, boolean result) { }

    /**
     * Commit a drag-reorder to {@link #mBrokers} and persist the whole list to SharedPreferences.
     * Only calls {@link #notifyItemMoved(int, int)} once the commit succeeds — on commit failure
     * the in-memory list is left reordered but the view is not refreshed.
     */
    @Override
    public void onMoveItem(int fromPosition, int toPosition) {
        Log.d("drag", "onMoveItem(fromPosition = " + fromPosition + ", toPosition = " + toPosition + ")");
        if (fromPosition == toPosition) return;

        Broker moved = mBrokers.get(fromPosition);
        mBrokers.remove(fromPosition);
        mBrokers.add(toPosition, moved);

        SharedPreferences.Editor editor = mBrokersListActivity
                .getSharedPreferences(BrokersListActivity.BROKERS_PREFS, 0)
                .edit();
        editor.remove(BrokersListActivity.BROKERS_DATA);
        editor.putString(BrokersListActivity.BROKERS_DATA, new Gson().toJson(mBrokers));
        if (editor.commit()) notifyItemMoved(fromPosition, toPosition);
    }

    // ---------- view holder ----------

    public static class ViewHolder extends AbstractDraggableItemViewHolder implements View.OnClickListener {
        public interface IViewHolderEventHandler { void onClick(View itemView); }

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
