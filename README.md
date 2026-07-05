# Exercise Snacks (Android)

A tiny native Android app that nudges you to do an **exercise snack** on a schedule:

1. **Reminder** fires (sound + vibration + notification).
2. Tap it → a **countdown** runs for the snack length.
3. When time's up, a **stop alarm** sounds.
4. It asks **what you did** (pick from your list or type "Other…") and **whether you did it**, and saves it to a **history**.

**Defaults:** every **30 minutes**, between **7:00 AM and 9:00 PM**, **2-minute** snacks — all changeable in Settings, including the exercise list.

---

## Why these steps?
Google's build servers can't be reached from the tool that generated this project, so the app is delivered as source code plus an **automated build**. GitHub will compile the installable `.apk` for you for free — no Android Studio, no coding. It takes about 15 minutes the first time.

---

## Part A — Build the APK on GitHub (free)

1. **Get a GitHub account** (free): go to https://github.com and sign up. Skip if you already have one.
2. **Create a repository:** click the **+** (top-right) → **New repository**. Give it a name like `exercise-snacks`. Public or Private both work. Click **Create repository**.
3. **Upload the project files:**
   - **Unzip** `exercise-snacks.zip` on your computer.
   - On the new repo page, click **Add file → Upload files** (or the "uploading an existing file" link).
   - Open the unzipped `exercise-snacks` folder, select **everything inside it** (including the `.github` folder), and **drag it all** into the upload box. Wait until every file finishes uploading.
   - Scroll down and click **Commit changes**.
   - *If the `.github` folder won't upload* (some browsers hide dot-folders): after committing the rest, click **Add file → Create new file**, type the name exactly `.github/workflows/build-apk.yml`, paste the YAML from the bottom of this file, and commit.
4. **Wait for the build:** click the **Actions** tab. You'll see a run named **Build APK** with a yellow spinner. In ~2–3 minutes it turns into a green check ✓.
5. **Get the APK:**
   - **Easiest (works on your phone):** open the repo's **Releases** (right-hand sidebar, or add `/releases` to the repo URL). Open **Latest APK** and download **`app-debug.apk`**.
   - **Or** from **Actions** → click the finished run → scroll to **Artifacts** → download **`exercise-snacks-apk`** (a `.zip`; unzip it to get `app-debug.apk`).

---

## Part B — Install on your phone

1. Get **`app-debug.apk`** onto your phone — download the Release directly in your phone's browser, or email it to yourself / put it in Google Drive.
2. Tap the file. Android will say it's from an unknown source → tap **Settings**, turn on **Allow from this source** for the app you're installing from (Chrome or Files), go back, tap **Install**.
3. Open **Exercise Snacks**.

---

## Part C — First-run setup (30 seconds, important)

- **Allow notifications** when prompted — otherwise you won't hear reminders.
- Tap **Improve reliability** → allow **exact alarms**, and if offered, **ignore battery optimization**. This is what keeps reminders firing on time when the screen is off.
- Make sure **Reminders on** is toggled on (it is by default).

That's it — you'll get your first nudge at the next 30-minute slot within the active hours.

---

## Part D — Using & customizing

- **Start a snack now** — test the full flow immediately.
- **Settings** — change the interval (minutes), snack length (seconds), active hours, and add/remove exercises in the list. "Other…" is always available during logging for a one-off free-text entry.
- **History** — every snack you logged, with a ✅ (done) or ❌ (skipped).

---

## Troubleshooting

- **Build shows a red ✗:** click the failed step to read the error, then **Actions → Re-run jobs**. If only the "Publish APK to Releases" step is red, ignore it — just use the **Artifact** from the Actions run instead.
- **Reminders are late or missing:** confirm notifications are allowed, exact alarms are allowed, and battery optimization is turned **off** for Exercise Snacks (use the **Improve reliability** button). Aggressive battery savers on some phones (Xiaomi, Samsung, Huawei, OnePlus) may need the app "locked" / set to "no restrictions" in the system battery settings.
- **No sound:** the app uses your phone's default notification/alarm sounds and respects Do Not Disturb.

---

## Appendix — build workflow (backup copy)

If you need to create the workflow file by hand (Part A, step 3 fallback), the file lives at `.github/workflows/build-apk.yml` and its contents are already included in this project. Just upload the project as-is and it will run automatically.
