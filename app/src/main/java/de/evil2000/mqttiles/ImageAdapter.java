package de.evil2000.mqttiles;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import androidx.appcompat.content.res.AppCompatResources;

import java.util.ArrayList;

/**
 * GridView adapter used by {@link IconSelectorActivity}. Each cell is a simple
 * {@link ImageView} showing a drawable resource id; tap forwards to a host-provided listener.
 */
public class ImageAdapter extends BaseAdapter {

    public final ArrayList<Integer> imageIds;          // R.drawable.* ids
    private final View.OnClickListener mActivityClickHandler;
    private final Context mContext;

    public ImageAdapter(Context context, ArrayList<Integer> imageIds,
                        View.OnClickListener clickHandler) {
        this.mContext = context;
        this.imageIds = imageIds;
        this.mActivityClickHandler = clickHandler;
    }

    @Override public int    getCount()              { return imageIds.size(); }
    @Override public Object getItem(int position)   { return null; }
    @Override public long   getItemId(int position) { return 0L; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView iv;
        if (convertView == null) {
            iv = new ImageView(mContext);
            iv.setLayoutParams(new AbsListView.LayoutParams(150, 150));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(10, 10, 10, 10);
            iv.setClickable(true);
        } else {
            iv = (ImageView) convertView;
        }
        int resId = imageIds.get(position);
        iv.setImageDrawable(AppCompatResources.getDrawable(mContext, resId));
        iv.setTag(resId);
        iv.setOnClickListener(mActivityClickHandler);
        return iv;
    }
}
