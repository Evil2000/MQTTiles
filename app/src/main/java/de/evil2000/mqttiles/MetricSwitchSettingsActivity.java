package de.evil2000.mqttiles;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.thebluealliance.spectrum.SpectrumDialog;

/** Settings editor for a {@link MetricSwitch} tile (on/off icons + colors + payloads). */
public class MetricSwitchSettingsActivity extends AppCompatActivity {

    public static final String METRIC_TYPE_KEY = "METRIC_TYPE_KEY";

    private static final int SELECT_OFF_ICON_REQUEST = 0;
    private static final int SELECT_ON_ICON_REQUEST  = 1;
    private static final int REQ_JS_ON_RECEIVE = 112;
    private static final int REQ_JS_ON_DISPLAY = 113;
    private static final int REQ_JS_ON_TAP     = 114;

    private MetricSwitch metric;
    private boolean isNew;

    private EditText   mNameEditText, mTopicEditText, mTopicPubEditText;
    private EditText   mOnEditText, mOffEditText, mJsonPathEditText;
    private EditText   mIntermediateStateTimeoutEditText, mJsBlinkExpressionEditText;
    private ImageView  mOnImageView, mOffImageView;
    private SurfaceView mOnColorSurfaceView, mOffColorSurfaceView;
    private RadioButton mQos0RadioButton, mQos1RadioButton, mQos2RadioButton;
    private CheckBox   mRetainedCheckBox, mEnablePubCheckBox, mUpdateOnPubCheckBox;
    private CheckBox   mEnableIntermediateStateCheckBox;
    private TextView   mTopicPubTextView, mIntermediateStateTimeoutTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metric_switch_settings);

        String json = getIntent().getStringExtra(MetricsListActivity.METRIC_DATA);
        if (json == null) { isNew = false; metric = new MetricSwitch(); }
        else               { isNew = true;  metric = new Gson().fromJson(json, MetricSwitch.class); }

        mNameEditText       = findViewById(R.id.nameEditText);
        mTopicEditText      = findViewById(R.id.topicEditText);
        mTopicPubEditText   = findViewById(R.id.topicPubEditText);
        mOnEditText         = findViewById(R.id.onEditText);
        mOffEditText        = findViewById(R.id.offEditText);
        mOnImageView        = findViewById(R.id.onImageView);
        mOffImageView       = findViewById(R.id.offImageView);
        mQos0RadioButton    = findViewById(R.id.qos0RadioButton);
        mQos1RadioButton    = findViewById(R.id.qos1RadioButton);
        mQos2RadioButton    = findViewById(R.id.qos2RadioButton);
        mRetainedCheckBox   = findViewById(R.id.retainedCheckBox);
        mJsonPathEditText   = findViewById(R.id.jsonPathEditText);
        mEnableIntermediateStateCheckBox  = findViewById(R.id.enableIntermediateStateCheckBox);
        mIntermediateStateTimeoutTextView = findViewById(R.id.intermediateStateTimeoutTextView);
        mIntermediateStateTimeoutEditText = findViewById(R.id.intermediateStateTimeoutEditText);
        mTopicPubTextView   = findViewById(R.id.topicPubTextView);
        mEnablePubCheckBox  = findViewById(R.id.enablePubCheckBox);
        mUpdateOnPubCheckBox = findViewById(R.id.updateOnPubCheckBox);
        mOnColorSurfaceView  = findViewById(R.id.onColorSurfaceView);
        mOffColorSurfaceView = findViewById(R.id.offColorSurfaceView);
        mJsBlinkExpressionEditText = findViewById(R.id.blinkEditText);

        mEnableIntermediateStateCheckBox.setOnCheckedChangeListener((b, checked) -> {
            int vis = checked ? View.VISIBLE : View.GONE;
            mIntermediateStateTimeoutTextView.setVisibility(vis);
            mIntermediateStateTimeoutEditText.setVisibility(vis);
        });
        mEnableIntermediateStateCheckBox.setChecked(metric.enableIntermediateState);
        mIntermediateStateTimeoutEditText.setText(String.valueOf(metric.intermediateStateTimeout));

        mOnColorSurfaceView.setBackgroundColor(metric.onColor);
        mOffColorSurfaceView.setBackgroundColor(metric.offColor);
        mOnColorSurfaceView.setOnClickListener(v ->
                openColor(metric.onColor, chosen -> {
                    metric.onColor = chosen;
                    mOnColorSurfaceView.setBackgroundColor(chosen);
                    applyColorToDrawable(mOnImageView.getDrawable(), chosen);
                }, "onColor"));
        mOffColorSurfaceView.setOnClickListener(v ->
                openColor(metric.offColor, chosen -> {
                    metric.offColor = chosen;
                    mOffColorSurfaceView.setBackgroundColor(chosen);
                    applyColorToDrawable(mOffImageView.getDrawable(), chosen);
                }, "offColor"));

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

        mNameEditText    .setText(metric.name);
        mTopicEditText   .setText(metric.topic);
        mTopicPubEditText.setText(metric.topicPub);
        mOnEditText      .setText(metric.payloadOn);
        mOffEditText     .setText(metric.payloadOff);
        mJsonPathEditText.setText(metric.jsonPath);
        mOnImageView .setImageResource(getResources().getIdentifier(metric.iconOn,  "drawable", getPackageName()));
        mOffImageView.setImageResource(getResources().getIdentifier(metric.iconOff, "drawable", getPackageName()));
        applyColorToDrawable(mOnImageView .getDrawable(), metric.onColor);
        applyColorToDrawable(mOffImageView.getDrawable(), metric.offColor);

        switch (metric.qos) {
            case 1: mQos1RadioButton.setChecked(true); break;
            case 2: mQos2RadioButton.setChecked(true); break;
            default: mQos0RadioButton.setChecked(true); break;
        }
        mRetainedCheckBox.setChecked(metric.retained);

        mOnImageView.setOnClickListener(v -> {
            Intent i = new Intent(this, IconSelectorActivity.class);
            i.putExtra(METRIC_TYPE_KEY, metric.type);
            startActivityForResult(i, SELECT_ON_ICON_REQUEST);
        });
        mOffImageView.setOnClickListener(v -> {
            Intent i = new Intent(this, IconSelectorActivity.class);
            i.putExtra(METRIC_TYPE_KEY, 2);
            startActivityForResult(i, SELECT_OFF_ICON_REQUEST);
        });

        Button onRecv = findViewById(R.id.jsOnReceiveButton);
        onRecv.setOnClickListener(v -> openJsEditor(metric.jsOnReceive, REQ_JS_ON_RECEIVE));
        Button onDisp = findViewById(R.id.jsOnDisplayButton);
        onDisp.setOnClickListener(v -> openJsEditor(metric.jsOnDisplay, REQ_JS_ON_DISPLAY));
        Button onTap  = findViewById(R.id.jsOnTapButton);
        onTap .setOnClickListener(v -> openJsEditor(metric.jsOnTap,     REQ_JS_ON_TAP));

        mJsBlinkExpressionEditText.setText(metric.jsBlinkExpression);
    }

    private void openJsEditor(String script, int reqCode) {
        Intent i = new Intent(this, JsEditorActivity.class);
        i.putExtra(JsEditorActivity.SCRIPT_DATA, script);
        i.putExtra(JsEditorActivity.METRIC_TYPE, metric.type);
        startActivityForResult(i, reqCode);
    }

    /** Open a Spectrum color dialog seeded with {@code initial}; on pick, invoke {@code cb}. */
    private void openColor(int initial, ColorCb cb, String tag) {
        new SpectrumDialog.Builder(this)
                .setColors(R.array.metric_colors)
                .setSelectedColor(initial)
                .setDismissOnColorSelected(false)
                .setOutlineWidth(2)
                .setOnColorSelectedListener((positive, color) -> { if (positive) cb.accept(color); })
                .build()
                .show(getSupportFragmentManager(), tag);
    }
    private interface ColorCb { void accept(int color); }

    /** Tint {@code d} with {@code color} via SRC_ATOP. */
    public static void applyColorToDrawable(Drawable d, int color) {
        if (d != null) d.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
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

        if (requestCode == SELECT_ON_ICON_REQUEST || requestCode == SELECT_OFF_ICON_REQUEST) {
            int resId = data.getIntExtra(IconSelectorActivity.ICON_RES_ID, -1);
            String entry = getResources().getResourceEntryName(resId);
            if (requestCode == SELECT_ON_ICON_REQUEST) {
                metric.iconOn = entry;
                mOnImageView.setImageResource(resId);
                applyColorToDrawable(mOnImageView.getDrawable(), metric.onColor);
            } else {
                metric.iconOff = entry;
                mOffImageView.setImageResource(resId);
                applyColorToDrawable(mOffImageView.getDrawable(), metric.offColor);
            }
            return;
        }
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

    private void saveInputToMetric() {
        metric.name       = mNameEditText.getText().toString();
        metric.topic      = mTopicEditText.getText().toString();
        metric.topicPub   = mTopicPubEditText.getText().toString();
        metric.payloadOn  = mOnEditText.getText().toString();
        metric.payloadOff = mOffEditText.getText().toString();
        metric.enablePub  = mEnablePubCheckBox.isChecked();
        metric.updateLastPayloadOnPub = mUpdateOnPubCheckBox.isChecked();

        if      (mQos0RadioButton.isChecked()) metric.qos = 0;
        else if (mQos1RadioButton.isChecked()) metric.qos = 1;
        else if (mQos2RadioButton.isChecked()) metric.qos = 2;
        else                                   metric.qos = 0;

        metric.retained = mRetainedCheckBox.isChecked();
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
