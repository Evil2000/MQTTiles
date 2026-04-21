# MQTTiles

An Android dashboard client for MQTT brokers. Define a grid of tiles where each
tile subscribes to a topic (and optionally publishes to one), displays the
current value, and reacts to taps — turning a phone or tablet into a simple
control panel for home-automation, IoT sensors, or any other MQTT-driven
system.

## Features

- **Multiple brokers** — manage a list of MQTT brokers (TCP / TLS, username +
  password, client ID, LWT, keepalive). One-tap connect.
- **Tile grid dashboard** — per-broker grid of tiles with small / medium /
  large sizes, auto-fit columns, optional blinking.
- **Six tile types:**
  - **Text** — show any incoming payload with prefix/postfix and colouring.
  - **Switch** — two-state on/off toggle (publishes `payloadOn`/`payloadOff`).
  - **Range / Progress** — numeric value as progress bar (min/max/decimals).
  - **Multi-Switch** — picker with a list of labelled payload choices.
  - **Image** — remote image from a URL, URL embedded in the payload, or raw
    image bytes in the payload. URLs may embed credentials
    (`http://user:pass@host/...`) — preemptive HTTP Basic with a Digest
    (MD5 / MD5-sess, `qop=auth`) fallback on 401.
  - **Color** — colour picker that publishes the selected colour (HEX/INT).
- **JSONPath extraction** — feed nested JSON payloads into a tile by picking
  the field of interest.
- **Scriptable hooks** — each tile exposes three Rhino JavaScript hooks:
  - `onReceive` — transform/filter an incoming payload before it's shown.
  - `onDisplay` — override the rendered text, colour, name, blink state.
  - `onTap` — custom publish logic (e.g. cycle through values).
- **Long-press context menu** on every tile — edit, clone, delete, or clear a
  retained message.
- **Import / export** of broker + dashboard configuration as JSON.

## Project status

Builds and runs on Android 6.0+ (minSdk 23, targetSdk 36).

Notable dependencies:

- `com.github.hannesa2:paho.mqtt.android` (community fork of the EOL Eclipse
  Paho Android service — required for Android 14+ broadcast-receiver
  compatibility).
- `androidx.appcompat:appcompat:1.7.1` (needed for correct edge-to-edge
  ActionBar inset handling on Android 15+).
- Mozilla Rhino for the JavaScript hook engine.

## Build

```sh
ANDROID_HOME=/path/to/Android/Sdk ./gradlew :app:assembleDebug
```

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
