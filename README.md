# YourWay: Zero-Infrastructure Wi-Fi Indoor Positioning & Wayfinding

![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-SQLite%20%7C%20Sensors-3DDC84?logo=android&logoColor=white)
![Python](https://img.shields.io/badge/Python-scikit--learn%20%7C%20Pandas-3776AB?logo=python&logoColor=white)
![Accuracy](https://img.shields.io/badge/Mean%20Localization%20Error-2.21m-10B981)

**YourWay** is an end-to-end indoor localization system combining a native **Kotlin (Jetpack Compose)** Android surveying client with a **Python Machine Learning pipeline**. It achieves **2.21-meter mean positioning accuracy** using existing corporate Wi-Fi access points—requiring zero proprietary hardware or BLE beacons.

---

## Key Engineering Highlights

- **No-Map Landmark Walk Surveying (`MainScreen.kt`):** Replaces error-prone manual blueprint tapping with a linear time-interpolation engine (`0.0 -> 1.0` normalized path progress). Surveyors simply enter a *Start Landmark* and *Finish Landmark* and walk at a natural pace while background coroutines capture Wi-Fi RSSI vectors every `3.5s`.
- **Multi-Pass Signal Reconciliation (`reconcile_data.py`):** Aligns repeated walks across the same corridor onto normalized spatial bins (`10%` intervals) and aggregates multi-pass measurements to filter out human body shadowing and multipath interference.
- **Random Forest Spatial Regression (`train_model.py`):** Transforms high-dimensional BSSID signal matrices (`165+` unique access points, imputed at `-100 dBm` for out-of-range routers) into continuous spatial coordinate predictions with a validated **2.21m mean Euclidean error**.
- **Structural Barrier & Attenuation Analysis (`visualize_barriers.py`):** Quantifies radio frequency drop rates ($\Delta\text{RSSI}/\text{meter}$) to detect heavy structural barriers such as reinforced concrete floor slabs (`> 4.0 dBm/m` attenuation).
- **Pedestrian Dead Reckoning (PDR) & Sensor Fusion:** Integrates Android `TYPE_STEP_DETECTOR`, accelerometer tilt validation, and low-pass filtered magnetometer/accelerometer azimuth tracking for map-based surveying modes.

---

## System Architecture & Visual Results

### 1. Multi-Pass Signal Reconciliation & ML Trajectory Fitting
Repeated walks along a landmark corridor are normalized and averaged to eliminate RSSI jitter before training the Random Forest regressor:

![ML Positioning Mockup](ml_positioning_mockup.png)

### 2. Radio Frequency Attenuation & Structural Barrier Detection
Real-world campus survey data across `165` BSSIDs demonstrating sharp `-25 dBm` signal attenuation across a reinforced concrete floor transition:

![Wi-Fi Signal Attenuation](wifi_signal_attenuation.png)

### 3. Topological Wayfinding Graph
Predicted corridor progress ratios map directly onto a node-edge landmark graph for turn-by-turn indoor navigation:

![Map Guide Network](map_guide_network.png)

---

## Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Android Mobile Client** | Kotlin, Jetpack Compose (Material 3), Coroutines, `WifiManager`, Hardware Sensor Fusion (`SensorManager`), SQLite (`SQLiteOpenHelper`) |
| **Data Engineering** | Python, Pandas, NumPy, JSON Schema Serialization, Spatial Binning & Moving-Average Reconciliation |
| **Machine Learning & Graph** | `scikit-learn` (`RandomForestRegressor`), `joblib`, `NetworkX`, `Matplotlib` |

---

## Repository Structure

```text
├── app/src/main/java/com/example/wififingerprintsurvey/
│   ├── MainActivity.kt              # Runtime permissions & app entry point
│   ├── Models.kt                    # Data models for Wi-Fi scans & survey records
│   ├── WifiSurveyDbHelper.kt        # SQLite persistence & JSON export engine
│   └── ui/main/MainScreen.kt        # Jetpack Compose UI (Landmark Mode, PDR Map Mode, History)
├── reconcile_data.py                # Multi-pass spatial alignment & RSSI noise smoothing
├── train_model.py                   # Feature matrix builder & Random Forest training pipeline
├── visualize_barriers.py            # RF attenuation gradient & concrete barrier analyzer
├── visualize_ml_mockup.py           # Signal smoothing vs. ML prediction simulation
├── plot_map_guide.py                # Topological node-edge wayfinding visualization
├── wifi_dataset.json                # Real-world multi-AP Wi-Fi fingerprint dataset
└── WifiFingerprintSurvey.apk        # Compiled debug Android package
```

---

## Quick Start

### 1. Android App
Install [`WifiFingerprintSurvey.apk`](WifiFingerprintSurvey.apk) directly on an Android device, or build from source:
```bash
./gradlew assembleDebug
```

### 2. Reconcile Multi-Walk Survey Data
```bash
python reconcile_data.py
```

### 3. Train & Evaluate the Localization Model
```bash
python train_model.py wifi_dataset.json
```
