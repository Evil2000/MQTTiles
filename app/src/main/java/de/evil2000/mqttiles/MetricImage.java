package de.evil2000.mqttiles;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Image tile. Three delivery modes:
 *   - {@link #IMAGE_URL}            static URL polled every {@link #reloadInterval} seconds
 *   - {@link #IMAGE_URL_IN_PAYLOAD} URL delivered in MQTT payload
 *   - {@link #IMAGE_DATA_IN_PAYLOAD} raw bytes (PNG/JPEG) delivered in MQTT payload
 */
public class MetricImage extends MetricBasicMqtt {

    public static final byte IMAGE_URL             = 1;
    public static final byte IMAGE_URL_IN_PAYLOAD  = 2;
    public static final byte IMAGE_DATA_IN_PAYLOAD = 3;

    private transient Bitmap bitmap;
    /** Set by {@link MetricsListActivity} while an enlarged popup is on screen. */
    public transient Dialog popup;

    public transient boolean isLoading = false;
    public transient boolean isSaving  = false;
    private transient File mCacheDir = null;

    public byte   kind       = IMAGE_URL;
    public String imageUrl   = "";
    public String openUrl    = "";
    public transient String lastImageDownloadError = "";
    public long   reloadInterval = 5;   // seconds

    MetricImage() {
        this.type = METRIC_TYPE_IMAGE;
        this.enablePub = false;          // images are inherently read-only
    }

    public Bitmap getBitmap() { return this.bitmap; }

    public boolean isTimeToReloadImage() {
        return getSecondsSinceLastActivity() >= this.reloadInterval;
    }

    /**
     * Restore a previously cached bitmap from disk (best-effort).
     * Side effect: remembers {@code cacheDir} for later saves.
     */
    public void loadImageFromFile(File cacheDir) {
        this.mCacheDir = cacheDir;
        File f = new File(cacheDir, this.id);
        if (!f.exists()) return;
        try {
            this.bitmap = BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Consume a binary MQTT message. For kind=URL_IN_PAYLOAD, reuses the text-path logic;
     * for kind=DATA_IN_PAYLOAD, decodes bytes to a Bitmap and caches it.
     */
    public void messageReceived(MqttMessage message) {
        enterIntermediateState(false);

        if (this.kind == IMAGE_URL_IN_PAYLOAD) {
            messageReceived(message.toString());
            this.imageUrl = (this.jsonPath == null || this.jsonPath.length() == 0)
                    ? this.lastPayload
                    : this.lastJsonPathValue;
            this.lastPayloadChanged = true;
        } else if (this.kind == IMAGE_DATA_IN_PAYLOAD) {
            try {
                this.lastPayloadChanged = true;
                this.lastPayload = "";
                byte[] bytes = message.getPayload();
                setBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length, new BitmapFactory.Options()));
                if (this.mCacheDir != null) saveBitmapToFile(this.mCacheDir);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // kind == IMAGE_URL: no action here, adapter polls on its own timer
            return;
        }
        this.lastActivity = (int) (new Date().getTime() / 1000);
    }

    /**
     * Persist the current {@link #bitmap} to {@code cacheDir/id} on a background thread.
     * Reentrancy-guarded via {@link #isSaving}.
     */
    public void saveBitmapToFile(final File cacheDir) {
        if (this.isSaving) return;
        this.isSaving = true;
        final Handler handler = new Handler(Looper.getMainLooper());
        final File target = new File(cacheDir, this.id);

        new Thread(() -> {
            this.mCacheDir = cacheDir;
            if (this.bitmap != null) {
                FileOutputStream out = null;
                try {
                    out = new FileOutputStream(target);
                    this.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    this.isSaving = false;
                    if (out != null) {
                        try { out.close(); } catch (IOException ignored) {}
                    }
                }
            } else {
                this.isSaving = false;
            }
            handler.post(() -> { /* post-save hook */ });
        }).start();
    }

    /**
     * Swap bitmap; push into the enlarged popup (if showing) and update activity time.
     */
    public void setBitmap(Bitmap bitmap) {
        if (bitmap != null) this.lastActivity = (int) (new Date().getTime() / 1000);
        this.lastPayloadChanged = false;
        this.bitmap = bitmap;

        if (this.popup == null) return;
        ImageView iv = this.popup.findViewById(R.id.imageView);
        if (iv != null) iv.setImageBitmap(this.bitmap);
    }
}
