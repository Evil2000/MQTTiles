package de.evil2000.mqttiles;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Simple JavaScript editor for onReceive/onDisplay/onTap metric hooks. Returns the edited
 * source in the {@link #SCRIPT_DATA} extra; the help menu opens the metric/hook-specific docs
 * page at evil2000.github.io/mqttiles in the browser.
 */
public class JsEditorActivity extends AppCompatActivity {

    public static final int EDIT_ON_RECEIVE_JS = 112;
    public static final int EDIT_ON_DISPLAY_JS = 113;
    public static final int EDIT_ON_TAP_JS     = 114;

    public static final String METRIC_TYPE = "METRIC_TYPE";
    public static final String SCRIPT_DATA = "SCRIPT_DATA";

    private static final String HELP_BASE = "https://evil2000.github.io/mqttiles/";

    private EditText mEditText;
    private int metricType;
    private int requestCode;
    private boolean isNew = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_js_editor);
        mEditText = findViewById(R.id.editText);
        mEditText.setHorizontallyScrolling(true);
        // Edge-to-edge inset padding is applied globally in App.EdgeToEdgeInsetsCallbacks.

        Intent in = getIntent();
        String script = in.getStringExtra(SCRIPT_DATA);
        metricType  = in.getIntExtra(METRIC_TYPE, 0);
        requestCode = in.getIntExtra("requestCode", 0);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            switch (requestCode) {
                case EDIT_ON_RECEIVE_JS: bar.setTitle(R.string.on_receive); break;
                case EDIT_ON_DISPLAY_JS: bar.setTitle(R.string.on_display); break;
                case EDIT_ON_TAP_JS:     bar.setTitle(R.string.on_tap);     break;
                default: break;
            }
        }
        if (script != null) {
            isNew = false;
            mEditText.setText(script);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_js_editor, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            Intent out = new Intent();
            out.putExtra(SCRIPT_DATA, mEditText.getText().toString());
            setResult(RESULT_OK, out);
            finish();
            return true;
        }
        if (id == R.id.action_help) {
            String url = helpUrlFor(metricType, requestCode);
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
            return true;
        }
        setResult(RESULT_CANCELED);
        finish();
        return super.onOptionsItemSelected(item);
    }

    /** Return the docs URL for a (metricType, hook) pair, or the JS index page if unknown. */
    private static String helpUrlFor(int metricType, int requestCode) {
        String typeSlug;
        switch (metricType) {
            case 1: typeSlug = "text-metric";           break;
            case 2: typeSlug = "switch-metric";         break;
            case 3: typeSlug = "range-progress-metric"; break;
            case 4: typeSlug = "multi-choice-metric";   break;
            case 5: typeSlug = "image-metric";          break;
            case 6: typeSlug = "color-metric";          break;
            default: return HELP_BASE;
        }
        String hookSlug;
        switch (requestCode) {
            case EDIT_ON_RECEIVE_JS: hookSlug = "on-receive"; break;
            case EDIT_ON_DISPLAY_JS: hookSlug = "on-display"; break;
            case EDIT_ON_TAP_JS:     hookSlug = "on-tap";     break;
            default: return HELP_BASE;
        }
        return HELP_BASE + typeSlug + "-" + hookSlug + ".html";
    }
}
