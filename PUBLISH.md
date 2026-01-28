# Publishing Dobbscoin Wallet to Google Play Store

This guide walks you through the steps to sign and publish the Dobbscoin Wallet app to the Google Play Store.

## Prerequisites

- Android Studio installed (or command-line build tools)
- Java JDK 17 or higher
- A Google account for the Play Developer Console

---

## Step 1: Create Your Signing Keystore

The keystore contains your private key used to sign the app. **Keep this file and passwords secure - if lost, you cannot update your app.**

### Option A: Using Android Studio

1. Open Android Studio
2. Go to **Build → Generate Signed Bundle / APK**
3. Select **Android App Bundle** (recommended) or **APK**
4. Click **Create new...** under Key store path
5. Fill in the details:
   - **Key store path:** Choose a secure location (e.g., `~/keystores/dobbscoin.jks`)
   - **Password:** Create a strong password
   - **Key alias:** `dobbscoin` (or your preferred name)
   - **Key password:** Create a strong password (can be same as keystore)
   - **Validity:** 25+ years recommended
   - **Certificate info:** Your organization details
6. Click **OK** to create the keystore

### Option B: Using Command Line

```bash
keytool -genkey -v -keystore dobbscoin.jks -keyalg RSA -keysize 2048 -validity 10000 -alias dobbscoin
```

You'll be prompted for:
- Keystore password
- Your name, organization, city, state, country
- Key password

### Store Your Credentials Securely

Create a `keystore.properties` file (DO NOT commit to git):

```properties
storeFile=/path/to/dobbscoin.jks
storePassword=your_keystore_password
keyAlias=dobbscoin
keyPassword=your_key_password
```

**Important:** Back up your keystore file and passwords in a secure location. Consider using a password manager.

---

## Step 2: Configure the Build for Signing

### Edit `wallet/build.gradle`

Add the signing configuration to the `android` block:

```gradle
android {
    // ... existing config ...

    signingConfigs {
        release {
            def keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                def keystoreProperties = new Properties()
                keystoreProperties.load(new FileInputStream(keystorePropertiesFile))

                storeFile file(keystoreProperties['storeFile'])
                storePassword keystoreProperties['storePassword']
                keyAlias keystoreProperties['keyAlias']
                keyPassword keystoreProperties['keyPassword']
            }
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

---

## Step 3: Set Up Google Play Developer Account

1. Go to [Google Play Console](https://play.google.com/console)
2. Sign in with your Google account
3. Pay the one-time $25 registration fee
4. Complete the account details:
   - Developer name (shown on Play Store)
   - Contact email
   - Website (optional)
5. Accept the Developer Distribution Agreement

---

## Step 4: Build the Release App Bundle

### Option A: Using Android Studio

1. Open the project in Android Studio
2. Go to **Build → Generate Signed Bundle / APK**
3. Select **Android App Bundle**
4. Select your keystore and enter passwords
5. Choose **release** build variant
6. Click **Finish**

The AAB file will be at: `wallet/build/outputs/bundle/release/wallet-release.aab`

### Option B: Using Command Line

```bash
cd androidbob

# Clean and build
./gradlew clean

# Build release AAB (requires keystore.properties configured)
./gradlew :wallet:bundleRelease
```

The AAB file will be at: `wallet/build/outputs/bundle/release/wallet-release.aab`

### Verify the Build

```bash
# Check the AAB was created
ls -la wallet/build/outputs/bundle/release/

# Optionally verify the signature
jarsigner -verify -verbose -certs wallet/build/outputs/bundle/release/wallet-release.aab
```

---

## Step 5: Create Play Store Listing

### In Google Play Console:

1. Click **Create app**
2. Fill in app details:
   - **App name:** Dobbscoin Wallet
   - **Default language:** English (US)
   - **App or game:** App
   - **Free or paid:** Free

### Store Listing Content

Use the prepared content from the `market/` directory:

| Asset | File | Dimensions |
|-------|------|------------|
| App icon | `market/market-app-icon.png` | 512x512 |
| Feature graphic | `market/market-feature-graphic.png` | 1024x500 |
| Screenshots | `market/screenshot-*.png` | Various |
| Short description | `market/market-promo-text.txt` | Max 80 chars |
| Full description | `market/market-description.txt` | Max 4000 chars |

### Required Information

- **Privacy Policy URL:** Host the `PRIVACY_POLICY.md` file and provide the URL
- **Category:** Finance
- **Content rating:** Complete the questionnaire (likely "Everyone")
- **Target audience:** 18+ (financial app)

---

## Step 6: Upload and Submit

### Upload the App Bundle

1. In Play Console, go to **Release → Production**
2. Click **Create new release**
3. Upload the `wallet-release.aab` file
4. Add release notes (e.g., "Initial release of Dobbscoin Wallet")

### Complete Required Sections

Before you can submit, complete all sections marked with warnings:

- [ ] Store listing (graphics, descriptions)
- [ ] Content rating questionnaire
- [ ] Pricing & distribution
- [ ] App content (privacy policy, ads declaration, etc.)
- [ ] Target audience and content

### Submit for Review

1. Review all sections show green checkmarks
2. Click **Review release**
3. Click **Start rollout to Production**

Google's review typically takes 1-3 days for new apps.

---

## Post-Publication

### Monitor Your App

- Check the Play Console dashboard for crashes and ANRs
- Respond to user reviews
- Monitor for policy violation warnings

### Updates

To publish updates:
1. Increment `versionCode` and `versionName` in `wallet/build.gradle`
2. Build a new signed AAB
3. Upload to Play Console under a new release

---

## Troubleshooting

### Common Issues

**"App not signed correctly"**
- Ensure you're using the same keystore for all releases
- Verify the signing config in build.gradle

**"Target SDK too low"**
- Current target SDK is 35 (Android 15), which meets requirements

**"Missing privacy policy"**
- Host `PRIVACY_POLICY.md` on a public URL and add to store listing

**Build fails with ProGuard errors**
- Check `proguard-rules.pro` for missing keep rules
- Review build output for specific class issues

---

## Security Reminders

1. **Never share your keystore** - it's your app's identity
2. **Back up your keystore** - losing it means you can't update the app
3. **Use Google Play App Signing** - Consider enrolling for additional protection
4. **Keep credentials out of git** - Use `keystore.properties` (gitignored)

---

## Support

For technical issues with the Dobbscoin Wallet codebase, refer to:
- Repository documentation
- Issue tracker

For Google Play publishing issues:
- [Play Console Help](https://support.google.com/googleplay/android-developer)
