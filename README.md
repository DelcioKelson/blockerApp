BlockerApp - Minimal scaffold

What this repo contains
- App monitoring/blocking using `UsageStatsManager` (works without root).
- A VPN service skeleton (`BlockerVpnService`) to be extended for website/domain blocking.

Quick run (on a device)
1. Open the project in Android Studio (recommended).
2. Build and install to a device with API >= 23.
3. On first run, grant "Usage access" in Settings when prompted.
4. In the app UI, press "Start App Monitor". The sample blocks `com.facebook.katana`.

Notes on website blocking
- Fully blocking arbitrary websites system-wide requires one of:
  - Implementing packet handling inside `VpnService` (complex), or
  - Running a local VPN/proxy and filtering DNS/HTTP(S) (requires TLS interception for HTTPS), or
  - Root access to modify `/etc/hosts` (not feasible for normal devices).

If you want, I can now implement a full VpnService-based domain filter (packet I/O),
or integrate an open-source VPN/proxy component. Tell me which you'd prefer.
