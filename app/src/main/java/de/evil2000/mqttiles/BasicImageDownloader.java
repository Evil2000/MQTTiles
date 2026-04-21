package de.evil2000.mqttiles;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Base64;

/**
 * Small URL→Bitmap fetcher used by {@link MetricImage} tiles. Deduplicates concurrent downloads
 * by URL. All I/O runs on a shared background executor; callbacks fire on the main thread.
 */
public class BasicImageDownloader {

    private static final String TAG = "BasicImageDownloader";
    private static final ExecutorService IO_POOL = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final OnImageLoaderListener mImageLoaderListener;
    private final Set<String> mUrlsInProgress = new HashSet<>();

    public BasicImageDownloader(@NonNull OnImageLoaderListener l) {
        this.mImageLoaderListener = l;
    }

    // ---------------------------------------------------------- listeners/errors

    public interface OnImageLoaderListener {
        void onComplete(Bitmap bitmap);
        void onError(ImageError error);
        void onProgressChange(int percent);
    }

    public interface OnImageReadListener {
        void onImageRead(Bitmap bitmap);
        void onReadFailed();
    }

    public interface OnBitmapSaveListener {
        void onBitmapSaved();
        void onBitmapSaveError(ImageError error);
    }

    /** Lightweight Throwable with a small numeric code taxonomy. */
    public static final class ImageError extends Throwable {
        public static final int ERROR_INVALID_FILE        = 0;
        public static final int ERROR_DECODE_FAILED       = 1;
        public static final int ERROR_FILE_EXISTS         = 2;
        public static final int ERROR_PERMISSION_DENIED   = 3;
        public static final int ERROR_IS_DIRECTORY        = 4;
        public static final int ERROR_GENERAL_EXCEPTION   = -1;

        private int errorCode;

        public ImageError(@NonNull String msg)      { super(msg); }
        public ImageError(@NonNull Throwable cause) {
            super(cause.getMessage(), cause.getCause());
            setStackTrace(cause.getStackTrace());
        }

        public int        getErrorCode()          { return errorCode; }
        public ImageError setErrorCode(int code)  { this.errorCode = code; return this; }
    }

    // ---------------------------------------------------------- disk helpers

    /** Synchronous disk read; returns {@code null} if the path is missing or not a file. */
    public static Bitmap readFromDisk(@NonNull File file) {
        if (!file.exists() || file.isDirectory()) return null;
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    /** Background disk read; delivers result on the main thread. */
    public static void readFromDiskAsync(@NonNull final File file,
                                         @NonNull final OnImageReadListener listener) {
        IO_POOL.submit(() -> {
            final Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
            MAIN.post(() -> {
                if (bmp != null) listener.onImageRead(bmp);
                else             listener.onReadFailed();
            });
        });
    }

    /**
     * Async bitmap → file write. Parent directories are created if missing.
     * @param overwrite if {@code true}, an existing file is deleted first
     */
    public static void writeToDisk(@NonNull final File file,
                                   @NonNull final Bitmap bitmap,
                                   @NonNull final OnBitmapSaveListener listener,
                                   @NonNull final Bitmap.CompressFormat format,
                                   final boolean overwrite) {
        if (file.isDirectory()) {
            listener.onBitmapSaveError(new ImageError(
                    "the specified path points to a directory, should be a file")
                    .setErrorCode(ImageError.ERROR_IS_DIRECTORY));
            return;
        }
        if (file.exists()) {
            if (!overwrite) {
                listener.onBitmapSaveError(new ImageError(
                        "file already exists, write operation cancelled")
                        .setErrorCode(ImageError.ERROR_FILE_EXISTS));
                return;
            }
            if (!file.delete()) {
                listener.onBitmapSaveError(new ImageError(
                        "could not delete existing file, most likely the write permission was denied")
                        .setErrorCode(ImageError.ERROR_PERMISSION_DENIED));
                return;
            }
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            listener.onBitmapSaveError(new ImageError("could not create parent directory")
                    .setErrorCode(ImageError.ERROR_PERMISSION_DENIED));
            return;
        }
        try {
            if (!file.createNewFile()) {
                listener.onBitmapSaveError(new ImageError("could not create file")
                        .setErrorCode(ImageError.ERROR_PERMISSION_DENIED));
                return;
            }
        } catch (IOException e) {
            listener.onBitmapSaveError(new ImageError(e)
                    .setErrorCode(ImageError.ERROR_GENERAL_EXCEPTION));
            return;
        }

        IO_POOL.submit(() -> {
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(file);
                bitmap.compress(format, 100, out);
                out.flush();
                MAIN.post(listener::onBitmapSaved);
            } catch (IOException ioe) {
                final ImageError err = new ImageError(ioe).setErrorCode(ImageError.ERROR_GENERAL_EXCEPTION);
                MAIN.post(() -> listener.onBitmapSaveError(err));
            } finally {
                if (out != null) try { out.close(); } catch (IOException ignored) {}
            }
        });
    }

    // ---------------------------------------------------------- download

    /**
     * Fetch {@code url} as a Bitmap. {@code displayProgress} is ignored because modern
     * {@link HttpURLConnection} does not expose monotonic byte progress without additional
     * work — {@code onProgressChange} is still called once at 100% on success.
     */
    public void download(@NonNull final String url, final boolean displayProgress) {
        if (mUrlsInProgress.contains(url)) {
            Log.w(TAG, "a download for this url is already running, no further download will be started");
            return;
        }
        mUrlsInProgress.add(url);
        Log.d(TAG, "GET " + url);

        IO_POOL.submit(() -> {
            Bitmap result = null;
            ImageError error = null;
            try {
                // URLs may embed credentials as http://user:pass@host/... — strip them from the
                // network-facing URL and use Basic preemptively, falling back to Digest on 401.
                UrlWithCreds parsed = parseUrlWithCreds(url);
                result = fetchBitmap(parsed, displayProgress);
            } catch (ImageError e) {
                error = e;
            } catch (Exception e) {
                error = new ImageError(e).setErrorCode(ImageError.ERROR_GENERAL_EXCEPTION);
            }

            final Bitmap     finalBmp = result;
            final ImageError finalErr = error;
            MAIN.post(() -> {
                mUrlsInProgress.remove(url);
                if (finalErr != null) {
                    mImageLoaderListener.onError(finalErr);
                } else if (finalBmp == null) {
                    Log.e(TAG, "factory returned a null result");
                    mImageLoaderListener.onError(new ImageError(
                            "downloaded file could not be decoded as bitmap")
                            .setErrorCode(ImageError.ERROR_DECODE_FAILED));
                } else {
                    mImageLoaderListener.onComplete(finalBmp);
                }
                System.gc();
            });
        });
    }

    // --- credentialed GET plumbing ---------------------------------------------------------

    /** URL with userinfo stripped, plus the decoded credentials if any. */
    private static final class UrlWithCreds {
        final String cleanUrl;
        final String user;   // null if no creds
        final String pass;   // null if no creds
        UrlWithCreds(String cleanUrl, String user, String pass) {
            this.cleanUrl = cleanUrl; this.user = user; this.pass = pass;
        }
        boolean hasCreds() { return user != null; }
    }

    /** Peel {@code user:pass@} out of the authority (URL-decoded) and return a credential-free URL. */
    private static UrlWithCreds parseUrlWithCreds(String raw) throws Exception {
        URI uri = new URI(raw);
        String userInfo = uri.getUserInfo();
        if (userInfo == null || userInfo.isEmpty()) return new UrlWithCreds(raw, null, null);

        int colon = userInfo.indexOf(':');
        String u = colon >= 0 ? userInfo.substring(0, colon) : userInfo;
        String p = colon >= 0 ? userInfo.substring(colon + 1) : "";
        u = URLDecoder.decode(u, "UTF-8");
        p = URLDecoder.decode(p, "UTF-8");

        URI clean = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                uri.getPath(), uri.getQuery(), uri.getFragment());
        return new UrlWithCreds(clean.toString(), u, p);
    }

    /** GET the clean URL, honouring credentials via preemptive Basic + Digest challenge fallback. */
    private static Bitmap fetchBitmap(UrlWithCreds parsed, boolean displayProgress) throws Exception, ImageError {
        HttpURLConnection conn = openConnection(parsed.cleanUrl);
        if (parsed.hasCreds()) {
            conn.setRequestProperty("Authorization", basicAuthHeader(parsed.user, parsed.pass));
        }
        int code;
        try {
            code = conn.getResponseCode();
        } catch (IOException e) {
            conn.disconnect();
            throw e;
        }

        if (code == HttpURLConnection.HTTP_UNAUTHORIZED && parsed.hasCreds()) {
            String challenge = conn.getHeaderField("WWW-Authenticate");
            conn.disconnect();
            if (challenge != null && challenge.regionMatches(true, 0, "Digest ", 0, 7)) {
                conn = openConnection(parsed.cleanUrl);
                conn.setRequestProperty("Authorization",
                        digestAuthHeader(parsed.user, parsed.pass, challenge,
                                "GET", URI.create(parsed.cleanUrl).getRawPath()));
                code = conn.getResponseCode();
            } else {
                throw new ImageError("HTTP 401").setErrorCode(ImageError.ERROR_GENERAL_EXCEPTION);
            }
        }

        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new ImageError("HTTP " + code).setErrorCode(ImageError.ERROR_GENERAL_EXCEPTION);
        }

        InputStream in = null;
        try {
            in = new BufferedInputStream(conn.getInputStream());
            Bitmap bmp = BitmapFactory.decodeStream(in);
            if (displayProgress) MAIN.post(() -> { /* progress callback: see javadoc */ });
            return bmp;
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
            conn.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(15_000);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    private static String basicAuthHeader(String user, String pass) {
        String raw = user + ":" + pass;
        return "Basic " + Base64.encodeToString(
                raw.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    // --- Digest (RFC 7616 subset: MD5, qop=auth or no qop) ---------------------------------

    private static final Pattern DIGEST_KV = Pattern.compile(
            "(\\w+)\\s*=\\s*(\"([^\"]*)\"|([^,]+))");

    private static Map<String, String> parseDigestChallenge(String header) {
        Map<String, String> out = new HashMap<>();
        String body = header.trim();
        if (body.regionMatches(true, 0, "Digest ", 0, 7)) body = body.substring(7);
        Matcher m = DIGEST_KV.matcher(body);
        while (m.find()) {
            String k = m.group(1).toLowerCase(Locale.ROOT);
            String v = m.group(3) != null ? m.group(3) : m.group(4).trim();
            out.put(k, v);
        }
        return out;
    }

    private static String digestAuthHeader(String user, String pass, String challenge,
                                           String method, String uri) throws Exception {
        Map<String, String> c = parseDigestChallenge(challenge);
        String realm  = c.get("realm");
        String nonce  = c.get("nonce");
        String qop    = c.get("qop");       // may be "auth", "auth,auth-int", or null
        String opaque = c.get("opaque");
        String algo   = c.getOrDefault("algorithm", "MD5");

        // Narrow qop down to "auth" if offered; we do not implement auth-int.
        boolean useQop = qop != null && ("," + qop.replace(" ", "") + ",").contains(",auth,");

        if (uri == null || uri.isEmpty()) uri = "/";

        String ha1 = md5(user + ":" + realm + ":" + pass);
        if ("MD5-sess".equalsIgnoreCase(algo)) {
            String cnonceInit = Long.toHexString(System.nanoTime());
            ha1 = md5(ha1 + ":" + nonce + ":" + cnonceInit);
        }
        String ha2 = md5(method + ":" + uri);

        String nc = "00000001";
        String cnonce = Long.toHexString(System.nanoTime());
        String response = useQop
                ? md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2)
                : md5(ha1 + ":" + nonce + ":" + ha2);

        StringBuilder h = new StringBuilder("Digest ");
        h.append("username=\"").append(user).append('"');
        h.append(", realm=\"").append(realm).append('"');
        h.append(", nonce=\"").append(nonce).append('"');
        h.append(", uri=\"").append(uri).append('"');
        h.append(", algorithm=").append(algo);
        if (useQop) {
            h.append(", qop=auth, nc=").append(nc);
            h.append(", cnonce=\"").append(cnonce).append('"');
        }
        h.append(", response=\"").append(response).append('"');
        if (opaque != null) h.append(", opaque=\"").append(opaque).append('"');
        return h.toString();
    }

    private static String md5(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }
}
