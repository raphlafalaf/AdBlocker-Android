# AdBlocker DNS

A lightweight, privacy-first DNS-based ad blocker for Android. Blocks ads, trackers, and malware domains system-wide using a local VPN without any data leaving your device.

## ⬇️ Download

**[Download AdBlockerDNS.apk](./AdBlockerDNS.apk)** — Ready to install

Simply download the APK and install it on your Android device.

---

## ✨ Features

- **System-wide ad blocking** — Works in any app: games, browsers, social media
- **Privacy first** — Local VPN only; no data leaves your device
- **130k+ blocked domains** — Based on [Steven Black's hosts list](https://github.com/StevenBlack/hosts)
- **Fast DNS filtering** — Blocks malware and phishing domains too
- **Customizable** — Add whitelists and blacklists for your needs
- **Lightweight** — Minimal battery impact

## 🚀 Quick Start

1. **Download and install** the APK file above
2. **Open AdBlocker DNS** from your app drawer
3. **Tap "Update Hosts List"** to download the latest ~130k blocked domains (~4 MB, takes ~5 seconds)
4. **Tap "Start"** to begin blocking
5. **Approve the VPN prompt** when Android asks to set up a VPN connection
6. The status dot turns **green** — ads are now blocked system-wide!

---

## 🎯 What Gets Blocked

✅ In-app ads (games, news, social media)  
✅ Browser ads (Chrome, Firefox, etc.)  
✅ Tracking and analytics scripts  
✅ Malware and phishing domains  

### What Does NOT Get Blocked

❌ **YouTube / Google app ads** — YouTube injects ads from the same domain as video content; blocking them would break playback  
❌ **Apps with hardcoded DNS** — A tiny number of apps use encrypted DNS (DoH) and ignore the system resolver

---

## 🔧 How It Works

AdBlocker DNS creates a **local VPN** on your device. Here's the flow:

1. Your phone makes a DNS query (looking up a domain name)
2. The query goes through our local filter
3. If the domain is on the blocklist, we return "domain doesn't exist" — request stops immediately
4. If not blocked, we forward to Cloudflare (1.1.1.1) and return the result

**No data ever leaves your device through the VPN.** Everything happens locally on your phone.

---

## ⚙️ Customization

### Whitelist (Always Allow)
Add domains here if a website or app breaks because a dependency was blocked.  
*Example:* If `fonts.gstatic.com` is accidentally blocked, whitelist it.

### Blacklist (Extra Blocking)
Add any domain you want to block beyond the Steven Black list.  
*Example:* `facebook.com`, `tiktok.com`, or specific trackers you've noticed.

Both lists support **subdomain matching**: whitelisting `example.com` also covers `www.example.com`.

---

## 📋 Requirements

- Android 7.0+ (API 24+)
- ~50 MB of free storage for the hosts list
- Internet connection to update the blocklist

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Hedgehog (2023.1) or newer
- Android phone with USB debugging enabled

### Steps

1. **Open the project** in Android Studio
2. **Sync Gradle** (automatic)
3. **Connect your phone** via USB with USB debugging enabled
4. **Press Shift+F10** or click the green Run button to build and install

---

## 📖 Technical Details

- **Local VPN Service** — Intercepts DNS queries at the system level
- **Steven Black Hosts List** — ~130k blocked domains (regularly updated)
- **Cloudflare Resolver** — Forward queries to 1.1.1.1 for non-blocked domains
- **Foreground Service** — Runs continuously with a persistent notification

---

## 📄 License

This project is licensed under the **GNU General Public License v3** (GPL-3.0). This means:

- ✅ You can use, modify, and distribute this software freely
- ✅ You must share any modifications under the same GPL-3.0 license
- ❌ You cannot create closed-source versions
- ❌ You cannot relicense the software

See the [LICENSE](./LICENSE) file for the full text, or visit [gnu.org/licenses/gpl-3.0.html](https://www.gnu.org/licenses/gpl-3.0.html).

---

## 🙋 Questions?

Check the [SETUP.md](./SETUP.md) file for detailed installation and troubleshooting.

---

**Status:** ✅ Debug build ready to test. This is early-stage software — please report any issues!
