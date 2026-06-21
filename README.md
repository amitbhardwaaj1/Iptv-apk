# PulseIPTV

A sleek, lightweight, and modern IPTV player designed specifically for performance and style. PulseIPTV sports the **Sophisticated Dark** theme, utilizing highly scannable grid channels, crisp icons, and fluid edge-to-edge screens.

## Quick Start (How to Build & Run)

You can build and run this project in **Android Studio** or **AndroidIDE** out-of-the-box.

### 1. In Android Studio (PC/Mac/Linux)
1. Open Android Studio.
2. Choose **File -> Open** and select the `/pulseiptv` directory.
3. Wait for the Gradle project sync to complete.
4. Press the green **Run** button or execute:
   ```bash
   ./gradlew assembleDebug
   ```

### 2. In AndroidIDE (Android Device)
1. Open AndroidIDE.
2. Select **Open Project** and tap on the extracted `/pulseiptv` directory.
3. Start building!

---

## ⚠️ Troubleshooting: "Invalid or Corrupt jarfile" Error

On some Android devices, certain file managers or extracting utilities corrupt binary `.jar` files (such as `gradle-wrapper.jar`) when unzipping the exported project zip. 

If you see an error like:
> `Error: Invalid or corrupt jarfile .../gradle/wrapper/gradle-wrapper.jar`

Follow these quick steps to fix it instantly:

### Fix via Terminal (AndroidIDE / Termux)
Open the built-in terminal in AndroidIDE or Termux, go to your project folder (`pulseiptv`), and execute **one** of the following commands to download a fresh, verified official Gradle binary jar:

#### Option A: Using curl (Recommended)
```bash
curl -Lo gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.x/gradle/wrapper/gradle-wrapper.jar
```

*Or specifically for Gradle 8.9:*
```bash
curl -Lo gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar
```

#### Option B: Using wget
```bash
wget -O gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar
```

#### Option C: Re-generate via Gradle
If you have Gradle installed on your system or inside your Termux environment, simply run:
```bash
gradle wrapper --gradle-version 8.9
```

This will automatically pull down a clean, correct binary representation of the Gradle Wrapper and let you build successfully!
