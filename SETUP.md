# AdBlocker DNS — Setup Guide

## How to build and install

### Prerequisites
- Android Studio Hedgehog (2023.1) or newer
- Android phone with USB debugging enabled (Settings → Developer Options)

### Steps

1. **Open the project**
   - Launch Android Studio
   - File → Open → select the `AdBlocker Android` folder
   - Wait for Gradle sync to complete (it downloads dependencies automatically)

2. **Connect your phone**
   - Plug in your phone via USB
   - Allow USB debugging when prompted on the phone
   - Your device should appear in the toolbar device selector

3. **Run / Install**
   - Click the green ▶ Run button (or Shift+F10)
   - Android Studio builds the APK and installs it directly to your phone

4. **First launch on the phone**
   - Open "AdBlocker DNS" from the app drawer
   - Tap **Update Hosts List** to download ~130k blocked domains from Steven Black's list
     (requires internet connection, ~4 MB download, takes ~5 seconds)
   - Tap **Start**
   - Android will ask: *"AdBlocker DNS wants to set up a VPN connection"* — tap **OK**
   - The status dot turns green — you're blocking ads system-wide

---

## How it works

The app creates a **local VPN** on your device. No data leaves your phone through this VPN — it only intercepts DNS queries (the system mechanism apps use to look up domain names).

When any app tries to reach an ad server (e.g. `ads.doubleclick.net`), the DNS query goes through our local filter. If the domain is on the Steven Black block list, we immediately return *"that domain doesn't exist"* — the request never leaves your phone. For everything else, the query is forwarded to Cloudflare (1.1.1.1) and the response is returned normally.

### What gets blocked
- In-app ads (games, news apps, social media)
- Browser ads (Chrome, Firefox, etc.)
- Tracking and analytics scripts
- Malware and phishing domains (Steven Black list includes these)

### What does NOT get blocked
- **YouTube / Google app ads** — YouTube injects ads from the same domain as video content, so DNS-blocking them would also break playback
- **Apps with hardcoded DNS** — a very small number of apps use their own encrypted DNS (DoH) and ignore the system resolver. This is rare.

---

## Customisation

### Whitelist (always allow)
Add domains here if a website or app breaks because a dependency was blocked.
Example: if `fonts.gstatic.com` is accidentally blocked by a custom entry, whitelist it.

### Blacklist (extra blocking)
Add any domain you personally want to block, beyond the Steven Black list.
Example: `facebook.com`, `tiktok.com`, or any specific tracker you've noticed.

Both lists support subdomain matching: whitelisting `example.com` also covers `www.example.com`.

---

## Updating the block list

The Steven Black hosts list is updated frequently. Tap **Update Hosts List** in the app whenever you want the latest version. The download is ~4 MB and takes a few seconds on Wi-Fi.
