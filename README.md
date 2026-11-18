# IR Coach (Android)

A personal health-coach app. Most of it works offline with no AI at all; only meal-photo checks (and optional free-form questions) use Google's **free** Gemini API. It:
- asks for your weight every morning (default 09:00) and compares it with your last weigh-in. If you're down, it cheers you on. If you're up, it asks 7 tap-to-answer questions (dinner time, salt, refined carbs, water, sleep, bowels, period/workout/stress) and tells you the likely causes and fixes for today. **This part is offline and free.**
- reminds you to photograph breakfast, lunch and dinner. For each photo, Gemini identifies the food, estimates calories, rates how IR/inflammation-friendly the meal is, suggests portion changes or swaps, and keeps a running daily total.
- follows the Wellness Atlas IR program guidelines (`app/src/main/assets/program_guidelines.md`) plus any course material you paste in Settings.

There is no server. Chats, weights, meals and photos stay on the phone, and only meal photos and typed questions are sent directly to Google's Gemini API using **your own free key**. The key is encrypted with the Android Keystore and excluded from backups.

## Install on your phone (sideload)
`dist/IRCoach.apk` is the signed release build.

**Option A, USB:** turn on Developer options → USB debugging, plug in the phone, then run
`~/Library/Android/sdk/platform-tools/adb install -r dist/IRCoach.apk`
(or `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`).

**Option B, file:** send `IRCoach.apk` to your phone (Drive, email to yourself, etc.), open it, and allow "Install unknown apps" for that app when prompted.

## First run
1. The app opens on **Settings**. Optionally paste a free Gemini key: go to https://aistudio.google.com/apikey, sign in with Google, tap **Create API key**, and copy it (starts with `AIza`). No card needed. You can skip this and add it later; weigh-ins and reminders work without it.
2. Fill in **About you** (age, height, goal, conditions, medications) and, optionally, paste course notes or meal plans.
3. Tap **Save**, then allow **notifications**. On Android 12+ also tap **Allow on-time reminders** so the 09:00 weigh-in fires exactly on time.
4. Send today's weight (e.g. `72.4`) in the Coach tab, then snap your meals with 📷.

Cost: free. The default model is `gemini-4-hydrogen` (free tier, reads images); `gemini-3.5-flash-lite` is another free option in Settings → Model. The free tier has per-day limits (reset at midnight Pacific); if you hit one, the app tells you and you can try later. **Privacy:** on the free tier, Google may use what you send (meal photos, questions) to improve its products. Weights and checklist answers never leave the phone.

## Google Play (internal testing)
1. Create a Play Console developer account (one-time US$25): https://play.google.com/console
2. Create app → name "IR Coach" → App, Free.
3. Go to Testing → **Internal testing** → Create release, and upload `dist/IRCoach.aab`. Accept Play App Signing.
4. Add yourself as a tester (your Gmail), open the opt-in link on your phone, and install from Play.
5. Play will ask for a privacy policy URL and the Data safety form. The app collects health info and photos, stored only on the device, and sends meal photos and questions to Google Gemini for processing.

For each new version, bump `versionCode` in `app/build.gradle.kts` and rebuild.

## Signing key (keep it safe!)
`release.jks` + `keystore.properties` (gitignored) hold the release signing key and its password. **Back both up** (e.g. to a password manager). Updates must be signed with this key. With Play App Signing enrolled, a lost upload key can be reset through Play support, but sideloaded installs would need an uninstall.

## Build
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew testDebugUnitTest assembleRelease bundleRelease
```
Outputs: `app/build/outputs/apk/release/app-release.apk`, `app/build/outputs/bundle/release/app-release.aab`.

The release APK is about 2.3 MB (no AI SDK; plain HTTPS + R8 shrinking).

## Code map
- `coach/CoachEngine.kt`: the coaching flow (routes weights/checklist offline, photos/questions to Gemini, reminders)
- `coach/WeightCheck.kt`: offline weigh-in messages, the weight-gain checklist, causes and fixes (edit here to change questions)
- `coach/Gemini.kt`: Gemini Interactions API call over plain HTTPS with JSON-schema output
- `coach/Prompts.kt`: coach persona, food-analysis instructions, JSON schema
- `data/`: SQLite store, settings, encrypted key
- `reminders/`: daily alarms and notifications, rescheduled on reboot
- `ui/`: Compose screens (Coach chat, Progress chart, Settings)
