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

/**
 * Settings editor for a {@link MetricText} tile. Create vs edit inferred from
 * presence of {@link MetricsListActivity#METRIC_DATA} in the incoming intent.
 *
 * Sub-activity request codes (112/113/114) open the JS editor for onReceive/onDisplay/onTap.
 */
public class MetricTextSettingsActivity extends AppCompatActivity {

    private static final int REQ_JS_ON_RECEIVE = 112;
    private static final int REQ_JS_ON_DISPLAY = 113;
    private static final int REQ_JS_ON_TAP     = 114;

    private MetricText metric;
    private boolean isNew;

    private EditText   mNameEditText, mTopicEditText, mTopicPubEditText;
    private EditText   mPrefixEditText, mPostfixEditText, mJsonPathEditText;
    private EditText   mIntermediateStateTimeoutEditText, mJsBlinkExpressionEditText;
    private RadioButton mTextSmallRadioButton, mTextMediumRadioButton, mTextLargeRadioButton;
    private RadioButton mQos0RadioButton, mQos1RadioButton, mQos2RadioButton;
    private CheckBox   mRetainedCheckBox, mEnablePubCheckBox, mUpdateOnPubCheckBox;
    private CheckBox   mEnableIntermediateStateCheckBox;
    private TextView   mTopicPubTextView, mIntermediateStateTimeoutTextView;
    private SurfaceView mTextColorSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metric_text_settings);

        String json = getIntent().getStringExtra(MetricsListActivity.METRIC_DATA);
        if (json == null) { isNew = false; metric = new MetricText(); }
        else               { isNew = true;  metric = new Gson().fromJson(json, MetricText.class); }

        mNameEditText       = findViewById(R.id.nameEditText);
        mTopicEditText      = findViewById(R.id.topicEditText);
        mTopicPubEditText   = findViewById(R.id.topicPubEditText);
        mPrefixEditText     = findViewById(R.id.prefixEditText);
        mPostfixEditText    = findViewById(R.id.postfixEditText);
        mTextSmallRadioButton  = findViewById(R.id.smallRadioButton);
        mTextMediumRadioButton = findViewById(R.id.mediumRadioButton);
        mTextLargeRadioButton  = findViewById(R.id.largeRadioButton);
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
        mTextColorSurfaceView = findViewById(R.id.textColorSurfaceView);
        mJsBlinkExpressionEditText = findViewById(R.id.blinkEditText);

        // intermediate-state timeout rows follow the parent checkbox
        mEnableIntermediateStateCheckBox.setOnCheckedChangeListener((b, checked) -> {
            int vis = checked ? View.VISIBLE : View.GONE;
            mIntermediateStateTimeoutTextView.setVisibility(vis);
            mIntermediateStateTimeoutEditText.setVisibility(vis);
        });
        mEnableIntermediateStateCheckBox.setChecked(metric.enableIntermediateState);
        mIntermediateStateTimeoutEditText.setText(String.valueOf(metric.intermediateStateTimeout));

        // color picker
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

        // "update-on-pub" toggles an entire block of dependent widgets
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

        // "enable pub" master toggle for pub-topic/retained/etc
        CompoundButton.OnCheckedChangeListener pubListener = (b, pubEnabled) -> {
            int vis = pubEnabled ? View.VISIBLE : View.GONE;
            mUpdateOnPubCheckBox.setVisibility(vis);
            mTopicPubTextView   .setVisibility(vis);
            mTopicPubEditText   .setVisibility(vis);
            mRetainedCheckBox   .setVisibility(vis);
            boolean upd  = mUpdateOnPubCheckBox.isChecked();
            boolean ims  = mEnableIntermediateStateCheckBox.isChecked();
            mEnableIntermediateStateCheckBox.setVisibility((pubEnabled && !upd) ? View.VISIBLE : View.GONE);
            boolean showTimeout = pubEnabled && !upd && ims;
            mIntermediateStateTimeoutTextView.setVisibility(showTimeout ? View.VISIBLE : View.GONE);
            mIntermediateStateTimeoutEditText.setVisibility(showTimeout ? View.VISIBLE : View.GONE);
        };
        mEnablePubCheckBox.setOnCheckedChangeListener(pubListener);
        mEnablePubCheckBox.setChecked(metric.enablePub);
        pubListener.onCheckedChanged(mEnablePubCheckBox, mEnablePubCheckBox.isChecked());

        // populate form
        mNameEditText    .setText(metric.name);
        mTopicEditText   .setText(metric.topic);
        mTopicPubEditText.setText(metric.topicPub);
        mPrefixEditText  .setText(metric.prefix);
        mPostfixEditText .setText(metric.postfix);
        mJsonPathEditText.setText(metric.jsonPath);

        switch (metric.mainTextSize) {
            case SMALL:  mTextSmallRadioButton .setChecked(true); break;
            case MEDIUM: mTextMediumRadioButton.setChecked(true); break;
            case LARGE:
            default:     mTextLargeRadioButton .setChecked(true); break;
        }
        switch (metric.qos) {
            case 1: mQos1RadioButton.setChecked(true); break;
            case 2: mQos2RadioButton.setChecked(true); break;
            default: mQos0RadioButton.setChecked(true); break;
        }
        mRetainedCheckBox.setChecked(metric.retained);
        mJsBlinkExpressionEditText.setText(metric.jsBlinkExpression);

        // JS editor buttons
        Button onRecv = findViewById(R.id.jsOnReceiveButton);
        onRecv.setOnClickListener(v -> openJsEditor(metric.jsOnReceive, REQ_JS_ON_RECEIVE));
        Button onDisp = findViewById(R.id.jsOnDisplayButton);
        onDisp.setOnClickListener(v -> openJsEditor(metric.jsOnDisplay, REQ_JS_ON_DISPLAY));
        Button onTap  = findViewById(R.id.jsOnTapButton);
        onTap .setOnClickListener(v -> openJsEditor(metric.jsOnTap,     REQ_JS_ON_TAP));
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

    /** Copy form values back into {@link #metric}. */
    private void saveInputToMetric() {
        metric.name     = mNameEditText    .getText().toString();
        metric.topic    = mTopicEditText   .getText().toString();
        metric.topicPub = mTopicPubEditText.getText().toString();
        metric.prefix   = mPrefixEditText  .getText().toString();
        metric.postfix  = mPostfixEditText .getText().toString();
        metric.enablePub = mEnablePubCheckBox.isChecked();
        metric.updateLastPayloadOnPub = mUpdateOnPubCheckBox.isChecked();

        if      (mTextSmallRadioButton .isChecked()) metric.mainTextSize = MetricText.TextSize.SMALL;
        else if (mTextMediumRadioButton.isChecked()) metric.mainTextSize = MetricText.TextSize.MEDIUM;
        else if (mTextLargeRadioButton .isChecked()) metric.mainTextSize = MetricText.TextSize.LARGE;
        else                                         metric.mainTextSize = MetricText.TextSize.SMALL;

        if      (mQos0RadioButton.isChecked()) metric.qos = 0;
        else if (mQos1RadioButton.isChecked()) metric.qos = 1;
        else if (mQos2RadioButton.isChecked()) metric.qos = 2;
        else                                   metric.qos = 0;

        metric.retained                 = mRetainedCheckBox.isChecked();
        metric.enableIntermediateState  = mEnableIntermediateStateCheckBox.isChecked();
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
            // JSONPath + single-topic publish is ambiguous (we'd publish back into what we just parsed)
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
