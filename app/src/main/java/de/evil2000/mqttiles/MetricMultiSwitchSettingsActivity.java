package de.evil2000.mqttiles;

import android.app.Dialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.thebluealliance.spectrum.SpectrumDialog;

/**
 * Settings editor for a {@link MetricMultiSwitch} tile: name/topic/QoS/retained plus a dynamic
 * table of (payload,label) options, each row is long-pressable for edit/delete via context menu.
 */
public class MetricMultiSwitchSettingsActivity extends AppCompatActivity {

    private static final int REQ_JS_ON_RECEIVE = 112;
    private static final int REQ_JS_ON_DISPLAY = 113;
    private static final int REQ_JS_ON_TAP     = 114;

    private static final int CTX_EDIT   = 0;
    private static final int CTX_DELETE = 1;

    private MetricMultiSwitch metric;
    private boolean isNew;

    private EditText mNameEditText, mTopicEditText, mTopicPubEditText;
    private EditText mJsonPathEditText, mIntermediateStateTimeoutEditText, mJsBlinkExpressionEditText;
    private RadioButton mTextSmallRadioButton, mTextMediumRadioButton, mTextLargeRadioButton;
    private RadioButton mQos0RadioButton, mQos1RadioButton, mQos2RadioButton;
    private CheckBox mRetainedCheckBox, mEnablePubCheckBox, mUpdateOnPubCheckBox;
    private CheckBox mEnableIntermediateStateCheckBox;
    private TextView mTopicPubTextView, mIntermediateStateTimeoutTextView;
    private TableLayout mOptionsTableLayout;
    private SurfaceView mTextColorSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metric_multi_switch_settings);

        String json = getIntent().getStringExtra(MetricsListActivity.METRIC_DATA);
        if (json == null) { isNew = false; metric = new MetricMultiSwitch(); }
        else               { isNew = true;  metric = new Gson().fromJson(json, MetricMultiSwitch.class); }

        mNameEditText          = findViewById(R.id.nameEditText);
        mTopicEditText         = findViewById(R.id.topicEditText);
        mTopicPubEditText      = findViewById(R.id.topicPubEditText);
        mTextSmallRadioButton  = findViewById(R.id.smallRadioButton);
        mTextMediumRadioButton = findViewById(R.id.mediumRadioButton);
        mTextLargeRadioButton  = findViewById(R.id.largeRadioButton);
        mQos0RadioButton       = findViewById(R.id.qos0RadioButton);
        mQos1RadioButton       = findViewById(R.id.qos1RadioButton);
        mQos2RadioButton       = findViewById(R.id.qos2RadioButton);
        mRetainedCheckBox      = findViewById(R.id.retainedCheckBox);
        mOptionsTableLayout    = findViewById(R.id.itemsTableLayout);
        mJsonPathEditText      = findViewById(R.id.jsonPathEditText);
        mEnableIntermediateStateCheckBox  = findViewById(R.id.enableIntermediateStateCheckBox);
        mIntermediateStateTimeoutTextView = findViewById(R.id.intermediateStateTimeoutTextView);
        mIntermediateStateTimeoutEditText = findViewById(R.id.intermediateStateTimeoutEditText);
        mTopicPubTextView      = findViewById(R.id.topicPubTextView);
        mEnablePubCheckBox     = findViewById(R.id.enablePubCheckBox);
        mUpdateOnPubCheckBox   = findViewById(R.id.updateOnPubCheckBox);
        mTextColorSurfaceView  = findViewById(R.id.textColorSurfaceView);
        mJsBlinkExpressionEditText = findViewById(R.id.blinkEditText);

        mNameEditText    .setText(metric.name);
        mTopicEditText   .setText(metric.topic);
        mTopicPubEditText.setText(metric.topicPub);
        mJsonPathEditText.setText(metric.jsonPath);

        mEnableIntermediateStateCheckBox.setOnCheckedChangeListener((b, checked) -> {
            int vis = checked ? View.VISIBLE : View.GONE;
            mIntermediateStateTimeoutTextView.setVisibility(vis);
            mIntermediateStateTimeoutEditText.setVisibility(vis);
        });
        mEnableIntermediateStateCheckBox.setChecked(metric.enableIntermediateState);
        mIntermediateStateTimeoutEditText.setText(String.valueOf(metric.intermediateStateTimeout));

        mTextColorSurfaceView.setBackgroundColor(metric.textColor);
        mTextColorSurfaceView.setOnClickListener(v ->
                new SpectrumDialog.Builder(this)
                        .setColors(R.array.metric_colors)
                        .setSelectedColor(metric.textColor)
                        .setDismissOnColorSelected(false)
                        .setOutlineWidth(2)
                        .setOnColorSelectedListener((positive, color) -> {
                            if (positive) {
                                metric.textColor = color;
                                mTextColorSurfaceView.setBackgroundColor(color);
                            }
                        })
                        .build()
                        .show(getSupportFragmentManager(), "textColor"));

        mUpdateOnPubCheckBox.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                mEnableIntermediateStateCheckBox.setVisibility(View.GONE);
                mIntermediateStateTimeoutTextView.setVisibility(View.GONE);
                mIntermediateStateTimeoutEditText.setVisibility(View.GONE);
            } else {
                mEnableIntermediateStateCheckBox.setVisibility(View.VISIBLE);
                int vis = mEnableIntermediateStateCheckBox.isChecked() ? View.VISIBLE : View.GONE;
                mIntermediateStateTimeoutTextView.setVisibility(vis);
                mIntermediateStateTimeoutEditText.setVisibility(vis);
            }
        });
        mUpdateOnPubCheckBox.setChecked(metric.updateLastPayloadOnPub);

        CompoundButton.OnCheckedChangeListener pubListener = (b, pubEnabled) -> {
            int vis = pubEnabled ? View.VISIBLE : View.GONE;
            mUpdateOnPubCheckBox.setVisibility(vis);
            mTopicPubTextView   .setVisibility(vis);
            mTopicPubEditText   .setVisibility(vis);
            mRetainedCheckBox   .setVisibility(vis);
            boolean upd = mUpdateOnPubCheckBox.isChecked();
            boolean ims = mEnableIntermediateStateCheckBox.isChecked();
            mEnableIntermediateStateCheckBox.setVisibility((pubEnabled && !upd) ? View.VISIBLE : View.GONE);
            boolean showTimeout = pubEnabled && !upd && ims;
            mIntermediateStateTimeoutTextView.setVisibility(showTimeout ? View.VISIBLE : View.GONE);
            mIntermediateStateTimeoutEditText.setVisibility(showTimeout ? View.VISIBLE : View.GONE);
        };
        mEnablePubCheckBox.setOnCheckedChangeListener(pubListener);
        mEnablePubCheckBox.setChecked(metric.enablePub);
        pubListener.onCheckedChanged(mEnablePubCheckBox, mEnablePubCheckBox.isChecked());

        if      (metric.mainTextSize == MetricMultiSwitch.TextSize.SMALL)  mTextSmallRadioButton .setChecked(true);
        else if (metric.mainTextSize == MetricMultiSwitch.TextSize.MEDIUM) mTextMediumRadioButton.setChecked(true);
        else                                                               mTextLargeRadioButton .setChecked(true);

        switch (metric.qos) {
            case 1: mQos1RadioButton.setChecked(true); break;
            case 2: mQos2RadioButton.setChecked(true); break;
            default: mQos0RadioButton.setChecked(true); break;
        }
        mRetainedCheckBox.setChecked(metric.retained);

        for (MetricMultiSwitchItem it : metric.items) addRow(it);

        Button addOption = findViewById(R.id.addOptionButton);
        addOption.setOnClickListener(v -> addRow());

        Button onRecv = findViewById(R.id.jsOnReceiveButton);
        onRecv.setOnClickListener(v -> openJsEditor(metric.jsOnReceive, REQ_JS_ON_RECEIVE));
        Button onDisp = findViewById(R.id.jsOnDisplayButton);
        onDisp.setOnClickListener(v -> openJsEditor(metric.jsOnDisplay, REQ_JS_ON_DISPLAY));
        Button onTap  = findViewById(R.id.jsOnTapButton);
        onTap .setOnClickListener(v -> openJsEditor(metric.jsOnTap,     REQ_JS_ON_TAP));

        mJsBlinkExpressionEditText.setText(metric.jsBlinkExpression);
    }

    /** Open the "add option" popup, append the new item to metric.items and to the table. */
    private void addRow() {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.multi_switch_metric_item_settings_popup);
        final EditText payloadEt = dialog.findViewById(R.id.payloadEditText);
        final EditText labelEt   = dialog.findViewById(R.id.labelEditText);
        final MetricMultiSwitchItem item = new MetricMultiSwitchItem();
        dialog.setTitle(R.string.add_new_option);
        Button done = dialog.findViewById(R.id.buttonDone);
        done.setOnClickListener(v -> {
            item.payload = payloadEt.getText().toString();
            item.label   = labelEt  .getText().toString();
            metric.items.add(item);
            addRow(item);
            dialog.dismiss();
        });
        dialog.show();
    }

    /** Append a {@link TableRow} bound to {@code item} (tagged via {@code R.id.itemTagId}). */
    private void addRow(MetricMultiSwitchItem item) {
        TableRow row = new TableRow(this);
        row.setTag(R.id.itemTagId, item);
        TableRow.LayoutParams rowLp = new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, 0);
        row.setMeasureWithLargestChildEnabled(false);
        row.setLayoutParams(rowLp);

        TextView tv = new TextView(this);
        tv.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_gray_color));
        tv.setId(R.id.itemId);
        TableRow.LayoutParams tvLp = new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);
        tvLp.setMargins(20, 20, 20, 20);
        tv.setPadding(20, 20, 20, 20);
        tv.setLayoutParams(tvLp);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            tv.setTextAppearance(this, android.R.style.TextAppearance_DeviceDefault_Medium);
        } else {
            tv.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
        }
        row.addView(tv);

        mOptionsTableLayout.addView(row, new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
        updateRow(row);
        registerForContextMenu(row);
        findViewById(R.id.optionsInstructionsTextView).setVisibility(View.VISIBLE);
    }

    /** Open the "edit option" popup for the row's item; updates the row's text on done. */
    private void editRow(final TableRow row) {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.multi_switch_metric_item_settings_popup);
        final EditText payloadEt = dialog.findViewById(R.id.payloadEditText);
        final EditText labelEt   = dialog.findViewById(R.id.labelEditText);
        final MetricMultiSwitchItem item = (MetricMultiSwitchItem) row.getTag(R.id.itemTagId);
        dialog.setTitle(R.string.edit_option);
        payloadEt.setText(item.payload);
        labelEt  .setText(item.label);
        Button done = dialog.findViewById(R.id.buttonDone);
        done.setOnClickListener(v -> {
            item.payload = payloadEt.getText().toString();
            item.label   = labelEt  .getText().toString();
            updateRow(row);
            dialog.dismiss();
        });
        dialog.show();
    }

    /** Remove {@code row} from the table and from {@code metric.items} (match by payload+label). */
    private void deleteRow(TableRow row) {
        MetricMultiSwitchItem target = (MetricMultiSwitchItem) row.getTag(R.id.itemTagId);
        mOptionsTableLayout.removeView(row);
        int idx = indexOfItem(target);
        if (idx > -1) metric.items.remove(idx);

        TextView instr = findViewById(R.id.optionsInstructionsTextView);
        instr.setVisibility(metric.items.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }

    /** Refresh the row's label to {@code "(payload) label"}. */
    private void updateRow(TableRow row) {
        MetricMultiSwitchItem item = (MetricMultiSwitchItem) row.getTag(R.id.itemTagId);
        TextView tv = row.findViewById(R.id.itemId);
        tv.setText(String.format("(%s) %s", item.payload, item.label));
    }

    /** Index of {@code item} in {@code metric.items}, compared by payload+label; -1 if absent. */
    private int indexOfItem(MetricMultiSwitchItem item) {
        if (item == null) return -1;
        for (int i = 0; i < metric.items.size(); i++) {
            MetricMultiSwitchItem m = metric.items.get(i);
            if (m.payload.equals(item.payload) && m.label.equals(item.label)) return i;
        }
        return -1;
    }

    /** Locate the table row whose tagged item matches {@code target} by payload+label. */
    private TableRow findRowFor(MetricMultiSwitchItem target) {
        for (int i = 0; i < mOptionsTableLayout.getChildCount(); i++) {
            View child = mOptionsTableLayout.getChildAt(i);
            if (!(child instanceof TableRow)) continue;
            MetricMultiSwitchItem it = (MetricMultiSwitchItem) child.getTag(R.id.itemTagId);
            if (it != null && it.payload.equals(target.payload) && it.label.equals(target.label)) {
                return (TableRow) child;
            }
        }
        return null;
    }

    private void openJsEditor(String script, int reqCode) {
        Intent i = new Intent(this, JsEditorActivity.class);
        i.putExtra(JsEditorActivity.SCRIPT_DATA, script);
        i.putExtra(JsEditorActivity.METRIC_TYPE, metric.type);
        startActivityForResult(i, reqCode);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        intent.putExtra("requestCode", requestCode);
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        String script = data.getStringExtra(JsEditorActivity.SCRIPT_DATA);
        if (script == null) return;
        script = script.trim();
        switch (requestCode) {
            case REQ_JS_ON_RECEIVE: metric.jsOnReceive = script; break;
            case REQ_JS_ON_DISPLAY: metric.jsOnDisplay = script; break;
            case REQ_JS_ON_TAP:     metric.jsOnTap     = script; break;
            default: break;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, v, info);
        MetricMultiSwitchItem item = (MetricMultiSwitchItem) v.getTag(R.id.itemTagId);
        int idx = indexOfItem(item);
        if (idx == -1) return;
        menu.add(idx, CTX_EDIT,   0, R.string.edit);
        menu.add(idx, CTX_DELETE, 0, R.string.delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem) {
        MetricMultiSwitchItem target = metric.items.get(menuItem.getGroupId());
        TableRow row = findRowFor(target);
        if (row == null) return super.onContextItemSelected(menuItem);
        switch (menuItem.getItemId()) {
            case CTX_EDIT:   editRow(row);   break;
            case CTX_DELETE: deleteRow(row); break;
            default: break;
        }
        return super.onContextItemSelected(menuItem);
    }

    private void saveInputToMetric() {
        metric.name     = mNameEditText.getText().toString();
        metric.topic    = mTopicEditText.getText().toString();
        metric.topicPub = mTopicPubEditText.getText().toString();
        metric.enablePub = mEnablePubCheckBox.isChecked();
        metric.updateLastPayloadOnPub = mUpdateOnPubCheckBox.isChecked();

        if      (mTextSmallRadioButton .isChecked()) metric.mainTextSize = MetricMultiSwitch.TextSize.SMALL;
        else if (mTextMediumRadioButton.isChecked()) metric.mainTextSize = MetricMultiSwitch.TextSize.MEDIUM;
        else                                         metric.mainTextSize = MetricMultiSwitch.TextSize.LARGE;

        if      (mQos0RadioButton.isChecked()) metric.qos = 0;
        else if (mQos1RadioButton.isChecked()) metric.qos = 1;
        else if (mQos2RadioButton.isChecked()) metric.qos = 2;
        else                                   metric.qos = 0;

        metric.retained = mRetainedCheckBox.isChecked();

        metric.items.clear();
        for (int i = 0; i < mOptionsTableLayout.getChildCount(); i++) {
            View child = mOptionsTableLayout.getChildAt(i);
            if (!(child instanceof TableRow)) continue;
            MetricMultiSwitchItem it = (MetricMultiSwitchItem) child.getTag(R.id.itemTagId);
            if (it != null) metric.items.add(it);
        }

        metric.enableIntermediateState = mEnableIntermediateStateCheckBox.isChecked();
        String t = mIntermediateStateTimeoutEditText.getText().toString();
        metric.intermediateStateTimeout = t.length() > 0 ? Integer.parseInt(t) : 0;

        metric.jsonPath = mJsonPathEditText.getText().toString().trim();
        if (metric.jsonPath.length() == 0) metric.lastJsonPathValue = null;

        metric.jsBlinkExpression = mJsBlinkExpressionEditText.getText().toString().trim();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_metric_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            saveInputToMetric();
            if (metric.jsonPath != null && metric.jsonPath.length() > 0 && metric.enablePub
                    && (metric.topicPub == null
                        || metric.topicPub.length() == 0
                        || metric.topicPub.equals(metric.topic))) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.you_cant_use_same_topics_with_json_path)
                        .show();
                return false;
            }
            Intent out = new Intent();
            out.putExtra(MetricsListActivity.METRIC_DATA, new Gson().toJson(metric));
            setResult(RESULT_OK, out);
            finish();
            return true;
        }
        setResult(RESULT_CANCELED);
        finish();
        return super.onOptionsItemSelected(item);
    }
}
