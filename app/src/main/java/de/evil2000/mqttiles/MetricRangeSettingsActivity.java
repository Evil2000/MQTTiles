package de.evil2000.mqttiles;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.thebluealliance.spectrum.SpectrumDialog;

/** Settings editor for a {@link MetricRange} tile (min/max/precision + progress color). */
public class MetricRangeSettingsActivity extends AppCompatActivity {

    private static final int REQ_JS_ON_RECEIVE = 112;
    private static final int REQ_JS_ON_DISPLAY = 113;
    private static final int REQ_JS_ON_TAP     = 114;

    private MetricRange metric;
    private boolean isNew;

    private EditText mNameEditText, mTopicEditText, mTopicPubEditText;
    private EditText mMinEditText, mMaxEditText, mPrefixEditText, mPostfixEditText;
    private EditText mPrecisionEditText, mJsonPathEditText;
    private EditText mIntermediateStateTimeoutEditText, mJsBlinkExpressionEditText;
    private CheckBox mDisplayPayloadCheckBox, mRetainedCheckBox;
    private CheckBox mEnablePubCheckBox, mUpdateOnPubCheckBox, mEnableIntermediateStateCheckBox;
    private RadioButton mQos0RadioButton, mQos1RadioButton, mQos2RadioButton;
    private TextView mTopicPubTextView, mIntermediateStateTimeoutTextView;
    private SurfaceView mProgressColorSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metric_range_settings);

        String json = getIntent().getStringExtra(MetricsListActivity.METRIC_DATA);
        if (json == null) { isNew = false; metric = new MetricRange(); }
        else               { isNew = true;  metric = new Gson().fromJson(json, MetricRange.class); }

        mNameEditText     = findViewById(R.id.nameEditText);
        mTopicEditText    = findViewById(R.id.topicEditText);
        mTopicPubEditText = findViewById(R.id.topicPubEditText);
        mMaxEditText      = findViewById(R.id.maxEditText);
        mMinEditText      = findViewById(R.id.minEditText);
        mPrefixEditText   = findViewById(R.id.prefixEditText);
        mPostfixEditText  = findViewById(R.id.postfixEditText);
        mPrecisionEditText = findViewById(R.id.precisionEditText);
        mDisplayPayloadCheckBox = findViewById(R.id.displayPayloadCheckBox);
        mQos0RadioButton  = findViewById(R.id.qos0RadioButton);
        mQos1RadioButton  = findViewById(R.id.qos1RadioButton);
        mQos2RadioButton  = findViewById(R.id.qos2RadioButton);
        mRetainedCheckBox = findViewById(R.id.retainedCheckBox);
        mJsonPathEditText = findViewById(R.id.jsonPathEditText);
        mEnableIntermediateStateCheckBox  = findViewById(R.id.enableIntermediateStateCheckBox);
        mIntermediateStateTimeoutTextView = findViewById(R.id.intermediateStateTimeoutTextView);
        mIntermediateStateTimeoutEditText = findViewById(R.id.intermediateStateTimeoutEditText);
        mTopicPubTextView    = findViewById(R.id.topicPubTextView);
        mEnablePubCheckBox   = findViewById(R.id.enablePubCheckBox);
        mUpdateOnPubCheckBox = findViewById(R.id.updateOnPubCheckBox);
        mProgressColorSurfaceView = findViewById(R.id.progressColorSurfaceView);
        mJsBlinkExpressionEditText = findViewById(R.id.blinkEditText);

        mEnableIntermediateStateCheckBox.setOnCheckedChangeListener((b, checked) -> {
            int vis = checked ? View.VISIBLE : View.GONE;
            mIntermediateStateTimeoutTextView.setVisibility(vis);
            mIntermediateStateTimeoutEditText.setVisibility(vis);
        });
        mEnableIntermediateStateCheckBox.setChecked(metric.enableIntermediateState);
        mIntermediateStateTimeoutEditText.setText(String.valueOf(metric.intermediateStateTimeout));

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
        mMaxEditText     .setText(Double.toString(metric.maxValue));
        mMinEditText     .setText(Double.toString(metric.minValue));
        mPrefixEditText  .setText(metric.prefix);
        mPostfixEditText .setText(metric.postfix);
        mPrecisionEditText.setText(Long.toString(metric.decimalPrecision));
        mDisplayPayloadCheckBox.setChecked(metric.displayPayloadValue);
        mJsonPathEditText.setText(metric.jsonPath);

        mProgressColorSurfaceView.setBackgroundColor(metric.progressColor);
        mProgressColorSurfaceView.setOnClickListener(v ->
                new SpectrumDialog.Builder(this)
                        .setColors(R.array.metric_colors)
                        .setSelectedColor(metric.progressColor)
                        .setDismissOnColorSelected(false)
                        .setOutlineWidth(2)
                        .setOnColorSelectedListener((positive, color) -> {
                            if (positive) {
                                metric.progressColor = color;
                                mProgressColorSurfaceView.setBackgroundColor(color);
                            }
                        })
                        .build()
                        .show(getSupportFragmentManager(), "progressColor"));

        switch (metric.qos) {
            case 1: mQos1RadioButton.setChecked(true); break;
            case 2: mQos2RadioButton.setChecked(true); break;
            default: mQos0RadioButton.setChecked(true); break;
        }
        mRetainedCheckBox.setChecked(metric.retained);

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

    private void saveInputToMetric() {
        metric.name     = mNameEditText.getText().toString();
        metric.topic    = mTopicEditText.getText().toString();
        metric.topicPub = mTopicPubEditText.getText().toString();
        metric.maxValue = parseFloatOr(mMaxEditText.getText().toString(), 100f);
        metric.minValue = parseFloatOr(mMinEditText.getText().toString(),   0f);
        metric.prefix   = mPrefixEditText.getText().toString();
        metric.postfix  = mPostfixEditText.getText().toString();
        metric.decimalPrecision = parseLongOr(mPrecisionEditText.getText().toString(), 0L);
        metric.displayPayloadValue = mDisplayPayloadCheckBox.isChecked();
        metric.enablePub = mEnablePubCheckBox.isChecked();
        metric.updateLastPayloadOnPub = mUpdateOnPubCheckBox.isChecked();

        if      (mQos0RadioButton.isChecked()) metric.qos = 0;
        else if (mQos1RadioButton.isChecked()) metric.qos = 1;
        else if (mQos2RadioButton.isChecked()) metric.qos = 2;
        else                                   metric.qos = 0;

        metric.retained = mRetainedCheckBox.isChecked();
        metric.enableIntermediateState = mEnableIntermediateStateCheckBox.isChecked();
        metric.intermediateStateTimeout = (int) parseLongOr(
                mIntermediateStateTimeoutEditText.getText().toString(), 0L);
        metric.jsonPath = mJsonPathEditText.getText().toString().trim();
        if (metric.jsonPath.length() == 0) metric.lastJsonPathValue = null;
        metric.jsBlinkExpression = mJsBlinkExpressionEditText.getText().toString().trim();
    }

    private static float parseFloatOr(String s, float fb) {
        try { return Float.parseFloat(s); } catch (Exception e) { return fb; }
    }
    private static long  parseLongOr(String s, long fb) {
        try { return Long.parseLong(s);  } catch (Exception e) { return fb; }
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
