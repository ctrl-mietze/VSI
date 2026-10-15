# VSI — Virtual Source Installer

VSI is an Android package installer focused on APKs, split APK bundles and flexible installation workflows.

The project is developed as its own heavily modified application with its own UI, package name and feature set.

## Current installation backends

- Rootless
- Root
- Shizuku
- Xposed / Vector system-installer integration

## Supported package sources

VSI currently works with normal APKs, multiple APKs, APKS, APKM, XAPK and ZIP-compatible package containers. Custom VSI package formats are being developed separately.

## Project status

VSI is under active development. Interfaces, package formats and experimental installer features may still change between builds.

## License

VSI is distributed under the [GNU General Public License v3.0](LICENSE).

Project and third-party attribution is documented separately in [CREDITS.md](CREDITS.md).

---

VSI — Virtual Source Installer
by Veyra


## VSI 0.2.0 Next — App Modes (development)

The `vsi-next-modes` branch introduces a new integration architecture:

- **Normal** — 20-capability tier, VSI Lite Service, no privileged dependency.
- **Shizuku** — 30-capability tier, VSI VService and privileged shell installs.
- **Xposed software** — 30-capability tier, VSI VService and reversible system-installer Activity hooks.
- **Root** — 50-capability tier, VSI System Service, temp-root compatible.
- **RootXposed** — 60-capability tier, root-backed installation plus Xposed/Vector system-installer and SystemServer hooks.

Battery optimization must be disabled before switching App Modes so the selected VSI service can stay reliable.

### New 0.2.0 development features

- Cross-OEM package-installer detection for Pixel/AOSP, Samsung, Nothing-style AOSP builds, Xiaomi, Oplus/ColorOS and OnePlus-style package installers.
- Persistent mode service notification: VSI Lite Service / VSI VService / VSI System Service.
- Function Info capability matrix (20 / 30 / 50 / 60).
- Advanced APK Analysis, disabled by default.
- Deep hashes, certificates, permissions, exported components, ABI/DEX/archive scan and privileged package diagnostics.
- Patch Lite for temp-root/KernelSU/Magisk sessions.
- Patch VSI for persistent user-0 package-installer routing with an explicit restore path.
- Patcher Pro systemless priv-app overlay, gated behind “Permanent kernel root functions”.
- RootXposed temporary package-installer takeover without modifying the stock system APK.
- Spanish and Hindi manual language overrides in addition to German/English.
- DACH auto-language policy remains: German only for German system locale in DE/AT/CH; otherwise English.

### PixelTweaks reference

VSI's 0.2.0 security-policy work was informed specifically by the public PixelTweaks project:
https://github.com/hohojia886/PixelTweaks

Only two concepts were used as technical reference:

1. app downgrade policy handling;
2. time-limited, install-session-scoped signature verification policy handling.

PixelTweaks is not a codebase or general feature source for VSI.
