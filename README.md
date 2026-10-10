# VSI — Virtual Source Installer

VSI is an Android package installer focused on APKs, split APK bundles and flexible installation workflows.

The project is developed as its own heavily modified application with its own UI, package name and feature set.

## Upstream: SAI

VSI originally started from [SAI — Split APKs Installer](https://github.com/Aefyr/SAI) by Aefyr / polychromaticfox.

SAI is a great open-source Android project and provided an excellent technical foundation for split-APK installation. We are very grateful that the project was released as open source.

VSI has since been changed substantially. Among other things, it has its own branding and UI, extended installer backends, Root / Shizuku integration, experimental LSPosed / Vector system-installer integration, additional installation controls and a growing custom package-format layer.

Because VSI is based on GPLv3-licensed SAI code, this repository remains licensed under the GNU General Public License v3.0. The original upstream project is not responsible for VSI-specific changes, bugs or behavior.

## VSI 0.2 Runtime Modes

VSI 0.2 introduces five runtime tiers:

- **Normal** — basic Android PackageInstaller workflow.
- **Shizuku** — privileged shell-backed installation without full root.
- **Xposed Software** — in-memory system-installer and PackageManager compatibility hooks.
- **Root** — professional root-backed installer and system tooling.
- **RootXposed** — combines Root with Xposed system integration while keeping the stock system partition untouched in normal runtime operation.

Battery optimization should be disabled for reliable VSI runtime services. VSI uses a different foreground service tier for Normal, Shizuku/Xposed and Root/RootXposed.

### Temporary security compatibility

The App Downgrade and Signature Verification compatibility hooks are deliberately time-limited. They automatically expire after three minutes, and signature compatibility is additionally scoped around active/recent package installation sessions.

### Patcher tiers

- **Patch Lite** — systemless service/routing module; no system partition files are replaced.
- **Patch VSI (Profile 0)** — dangerous profile-0 component routing with a stored restore target.
- **Patcher Pro** — persistent-root-only systemless priv-app mount, guarded by an explicit Developer Option and blocked on temporary-only root environments.

## Supported package sources

VSI currently works with normal APKs, multiple APKs, APKS, APKM, XAPK and ZIP-compatible package containers. Custom VSI package formats are being developed separately.

## Project status

VSI is under active development. Interfaces, package formats and experimental installer features may still change between builds.

## License

VSI is distributed under the [GNU General Public License v3.0](LICENSE), in accordance with the license of the upstream SAI project.

## Credits

- [SAI — Split APKs Installer](https://github.com/Aefyr/SAI) — original upstream project and major technical foundation.
- Aefyr / polychromaticfox — original SAI author.
- [PixelTweaks](https://github.com/hohojia886/PixelTweaks) by hohojia886 — the VSI 0.2 App Downgrade and Signature Verification compatibility-hook design was adapted specifically from PixelTweaks' GPL-3.0 PackageManager security implementation. No other PixelTweaks feature set is imported.
- Android, Shizuku, LSPosed / Xposed-compatible tooling, KernelSU/Magisk and the wider open-source Android community.

---

VSI — Virtual Source Installer
by Veyra
