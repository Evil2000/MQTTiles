package de.evil2000.mqttiles;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

/**
 * Settings screen for one {@link Broker}. Create flow: intent has no {@code BROKER_DATA};
 * edit flow: intent carries a Gson JSON of the broker to populate.
 * Saves via {@code setResult(RESULT_OK, data)} with the mutated broker JSON.
 */
public class BrokerSettingsActivity extends AppCompatActivity {

    private Broker  mBroker;
    private boolean isNew = true;

    // form inputs
    private EditText   mNameEditText, mAddressEditText, mPortEditText;
    private EditText   mUserEditText, mPasswordEditText, mClientIdEditText;
    private EditText   mColsCountVertEditText, mColsCountHorzEditText;
    private CheckBox   mAutoConnectCheckBox, mMetricsEditableCheckBox;
    private CheckBox   mNeverHideBrokerNameCheckBox, mKeepScreenOnCheckBox;
    private CheckBox   mVerboseConnectionErrorsCheckBox;
    private RadioButton mTileSmallRadioButton, mTileMediumRadioButton, mTileLargeRadioButton;
    private CheckBox   mEnableSslCheckBox, mTrustCertificateCheckBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_broker_settings);

        mNameEditText       = findViewById(R.id.nameEditText);
        mAddressEditText    = findViewById(R.id.addressEditText);
        mPortEditText       = findViewById(R.id.portEditText);
        mUserEditText       = findViewById(R.id.userEditText);
        mPasswordEditText   = findViewById(R.id.passwordEditText);
        mClientIdEditText   = findViewById(R.id.clientIdEditText);
        mColsCountVertEditText = findViewById(R.id.colsCountVertEditText);
        mColsCountHorzEditText = findViewById(R.id.colsCountHorzEditText);

        mAutoConnectCheckBox       = findViewById(R.id.autoConnectCheckBox);
        mMetricsEditableCheckBox   = findViewById(R.id.metricsEditableCheckBox);
        mNeverHideBrokerNameCheckBox = findViewById(R.id.neverHideBrokerNameCheckBox);
        mKeepScreenOnCheckBox      = findViewById(R.id.keepScreenOnCheckBox);
        mVerboseConnectionErrorsCheckBox = findViewById(R.id.verboseConnectionErrorsCheckBox);

        // "never hide broker name" is only meaningful when metrics aren't editable
        mMetricsEditableCheckBox.setOnCheckedChangeListener((btn, checked) ->
                mNeverHideBrokerNameCheckBox.setVisibility(checked ? View.GONE : View.VISIBLE));

        mTileSmallRadioButton  = findViewById(R.id.smallRadioButton);
        mTileMediumRadioButton = findViewById(R.id.mediumRadioButton);
        mTileLargeRadioButton  = findViewById(R.id.largeRadioButton);

        mEnableSslCheckBox       = findViewById(R.id.enableSslCheckBox);
        mTrustCertificateCheckBox = findViewById(R.id.trustCertificateCheckBox);
        mEnableSslCheckBox.setOnCheckedChangeListener((btn, checked) ->
                mTrustCertificateCheckBox.setVisibility(checked ? View.VISIBLE : View.GONE));

        // inflate model
        String json = getIntent().getStringExtra(BrokersListActivity.BROKER_DATA);
        if (json == null) {
            isNew   = true;
            mBroker = new Broker();
        } else {
            isNew   = false;
            mBroker = new Gson().fromJson(json, Broker.class);
        }

        // populate form
        mNameEditText.setText(mBroker.name);
        mAddressEditText.setText(mBroker.address);
        mPortEditText.setText(mBroker.port);
        mUserEditText.setText(mBroker.user);
        mPasswordEditText.setText(mBroker.password);
        mClientIdEditText.setText(mBroker.clientId);
        mColsCountVertEditText.setText(Long.toString(mBroker.colsCount));
        mColsCountHorzEditText.setText(Long.toString(mBroker.colsCountHorizontal));
        mAutoConnectCheckBox.setChecked(mBroker.autoConnect);
        mMetricsEditableCheckBox.setChecked(mBroker.metricsEditable);
        mNeverHideBrokerNameCheckBox.setChecked(mBroker.neverHideBrokerName);
        mKeepScreenOnCheckBox.setChecked(mBroker.keepScreenOn);
        mVerboseConnectionErrorsCheckBox.setChecked(mBroker.verboseConnectionErrors);

        switch (mBroker.tileWidth) {
            case Broker.TILE_SMALL:  mTileSmallRadioButton .setChecked(true); break;
            case Broker.TILE_LARGE:  mTileLargeRadioButton .setChecked(true); break;
            case Broker.TILE_MEDIUM:
            default:                 mTileMediumRadioButton.setChecked(true); break;
        }

        mEnableSslCheckBox.setChecked(mBroker.ssl);
        mTrustCertificateCheckBox.setChecked(mBroker.trust);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_broker_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            // scrape form
            mBroker.name     = mNameEditText    .getText().toString();
            mBroker.address  = mAddressEditText .getText().toString();
            mBroker.port     = mPortEditText    .getText().toString();
            mBroker.user     = mUserEditText    .getText().toString();
            mBroker.password = mPasswordEditText.getText().toString();
            mBroker.clientId = mClientIdEditText.getText().toString();

            mBroker.colsCount           = parseLongOr(mColsCountVertEditText.getText().toString(), 0);
            mBroker.colsCountHorizontal = parseLongOr(mColsCountHorzEditText.getText().toString(), 0);

            mBroker.autoConnect         = mAutoConnectCheckBox.isChecked();
            mBroker.metricsEditable     = mMetricsEditableCheckBox.isChecked();
            mBroker.neverHideBrokerName = mNeverHideBrokerNameCheckBox.isChecked();
            mBroker.keepScreenOn        = mKeepScreenOnCheckBox.isChecked();
            mBroker.verboseConnectionErrors = mVerboseConnectionErrorsCheckBox.isChecked();

            if (mTileSmallRadioButton.isChecked())       mBroker.tileWidth = Broker.TILE_SMALL;
            else if (mTileMediumRadioButton.isChecked()) mBroker.tileWidth = Broker.TILE_MEDIUM;
            else                                         mBroker.tileWidth = Broker.TILE_LARGE;

            mBroker.ssl   = mEnableSslCheckBox.isChecked();
            mBroker.trust = mTrustCertificateCheckBox.isChecked();

            Intent out = new Intent();
            out.putExtra(BrokersListActivity.BROKER_DATA, new Gson().toJson(mBroker));
            setResult(RESULT_OK, out);
            finish();
            return true;
        }
        // any other menu item: cancel
        setResult(RESULT_CANCELED);
        finish();
        return super.onOptionsItemSelected(item);
    }

    private static long parseLongOr(String s, long fallback) {
        try { return Long.parseLong(s); } catch (Exception e) { return fallback; }
    }
}
