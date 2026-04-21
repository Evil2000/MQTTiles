package de.evil2000.mqttiles;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

/**
 * Settings editor for a {@link MetricImage} tile. Three mutually-exclusive delivery kinds;
 * the visible fields track the selected radio button (static URL, URL in payload, binary payload).
 */
public class MetricImageSettingsActivity extends AppCompatActivity {

    private static final int REQ_JS_ON_RECEIVE = 112;
    private static final int REQ_JS_ON_DISPLAY = 113;
    private static final int REQ_JS_ON_TAP     = 114;

    private MetricImage metric;
    private boolean isNew;

    private EditText mNameEditText, mImageUrlEditText, mRefreshIntervalEditText;
    private EditText mTopicEditText, mJsonPathEditText, mJsBlinkExpressionEditText, mOpenURL;
    private TextView mImageUrlTextView, mRefreshIntervalTextView, mTopicTextView;
    private TextView mJsonPathTextView, mJsonPathHelpLinkTextView;
    private RadioGroup  mKindRadioGroup;
    private RadioButton mStaticUrlRadioButton, mPayloadUrlRadioButton, mBinaryPayloadRadioButton;

    /** {@code true} iff {@code s} is non-empty and contains only 0-9. */
    public static boolean isNumeric(String s) {
        if (s == null || s.length() == 0) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metric_image_settings);

        String json = getIntent().getStringExtra(MetricsListActivity.METRIC_DATA);
        if (json == null) { isNew = false; metric = new MetricImage(); }
        else               { isNew = true;  metric = new Gson().fromJson(json, MetricImage.class); }

        mNameEditText           = findViewById(R.id.nameEditText);
        mImageUrlEditText       = findViewById(R.id.imageUrlEditText);
        mImageUrlTextView       = findViewById(R.id.imageUrlTextView);
        mRefreshIntervalEditText = findViewById(R.id.refreshIntervalEditText);
        mRefreshIntervalTextView = findViewById(R.id.refreshIntervalTextView);
        mKindRadioGroup         = findViewById(R.id.kindRadioGroup);
        mStaticUrlRadioButton   = findViewById(R.id.staticUrlRadioButton);
        mPayloadUrlRadioButton  = findViewById(R.id.payloadUrlRadioButton);
        mBinaryPayloadRadioButton = findViewById(R.id.binaryPayloadRadioButton);
        mTopicEditText          = findViewById(R.id.topicEditText);
        mTopicTextView          = findViewById(R.id.topicTextView);
        mJsonPathEditText       = findViewById(R.id.jsonPathEditText);
        mJsonPathTextView       = findViewById(R.id.jsonPathTextView);
        mJsonPathHelpLinkTextView = findViewById(R.id.jsonPathHelpLinkTextView);
        mJsBlinkExpressionEditText = findViewById(R.id.blinkEditText);
        mOpenURL                = findViewById(R.id.openUrlEditText);

        mJsonPathEditText.setText(metric.jsonPath);
        mNameEditText    .setText(metric.name);
        mImageUrlEditText.setText(metric.imageUrl);
        mRefreshIntervalEditText.setText(String.valueOf(metric.reloadInterval));

        RadioGroup.OnCheckedChangeListener kindListener = (group, checkedId) -> applyKindVisibility(checkedId);
        mKindRadioGroup.setOnCheckedChangeListener(kindListener);

        // sync radio selection with current kind + apply the matching visibility profile
        switch (metric.kind) {
            case MetricImage.IMAGE_URL:
                mStaticUrlRadioButton.setChecked(true);
                applyKindVisibility(R.id.staticUrlRadioButton);
                break;
            case MetricImage.IMAGE_URL_IN_PAYLOAD:
                mPayloadUrlRadioButton.setChecked(true);
                applyKindVisibility(R.id.payloadUrlRadioButton);
                break;
            case MetricImage.IMAGE_DATA_IN_PAYLOAD:
            default:
                mBinaryPayloadRadioButton.setChecked(true);
                applyKindVisibility(R.id.binaryPayloadRadioButton);
                break;
        }

        mTopicEditText.setText(metric.topic);
        mJsBlinkExpressionEditText.setText(metric.jsBlinkExpression);
        mOpenURL.setText(metric.openUrl);

        Button onRecv = findViewById(R.id.jsOnReceiveButton);
        onRecv.setOnClickListener(v -> openJsEditor(metric.jsOnReceive, REQ_JS_ON_RECEIVE));
        Button onDisp = findViewById(R.id.jsOnDisplayButton);
        onDisp.setOnClickListener(v -> openJsEditor(metric.jsOnDisplay, REQ_JS_ON_DISPLAY));
        Button onTap  = findViewById(R.id.jsOnTapButton);
        onTap .setOnClickListener(v -> openJsEditor(metric.jsOnTap,     REQ_JS_ON_TAP));
    }

    /** Toggle visibility of the kind-dependent fields to match the selected radio button. */
    private void applyKindVisibility(int checkedId) {
        if (checkedId == R.id.staticUrlRadioButton) {
            show(mImageUrlTextView, mImageUrlEditText, mRefreshIntervalTextView, mRefreshIntervalEditText);
            hide(mTopicTextView, mTopicEditText, mJsonPathTextView, mJsonPathEditText, mJsonPathHelpLinkTextView);
        } else if (checkedId == R.id.payloadUrlRadioButton) {
            show(mTopicTextView, mTopicEditText, mRefreshIntervalTextView, mRefreshIntervalEditText,
                 mJsonPathTextView, mJsonPathEditText, mJsonPathHelpLinkTextView);
            hide(mImageUrlTextView, mImageUrlEditText);
        } else if (checkedId == R.id.binaryPayloadRadioButton) {
            show(mTopicTextView, mTopicEditText);
            hide(mImageUrlTextView, mImageUrlEditText,
                 mRefreshIntervalTextView, mRefreshIntervalEditText,
                 mJsonPathTextView, mJsonPathEditText, mJsonPathHelpLinkTextView);
        }
    }
    private static void show(View... vs) { for (View v : vs) v.setVisibility(View.VISIBLE); }
    private static void hide(View... vs) { for (View v : vs) v.setVisibility(View.GONE);    }

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
        if      (mPayloadUrlRadioButton   .isChecked()) metric.kind = MetricImage.IMAGE_URL_IN_PAYLOAD;
        else if (mBinaryPayloadRadioButton.isChecked()) metric.kind = MetricImage.IMAGE_DATA_IN_PAYLOAD;
        else                                            metric.kind = MetricImage.IMAGE_URL;

        metric.name     = mNameEditText.getText().toString();
        metric.imageUrl = mImageUrlEditText.getText().toString();

        String s = mRefreshIntervalEditText.getText().toString();
        if (!isNumeric(s)) s = "0";
        metric.reloadInterval = Long.parseLong(s);

        metric.topic    = mTopicEditText.getText().toString();
        metric.jsonPath = mJsonPathEditText.getText().toString().trim();
        if (metric.jsonPath.length() == 0) metric.lastJsonPathValue = null;
        metric.jsBlinkExpression = mJsBlinkExpressionEditText.getText().toString().trim();
        metric.openUrl = mOpenURL.getText().toString().trim();
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
