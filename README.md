# VSI — Virtual Source Installer

VSI is an Android package installer focused on APKs, split APK bundles and flexible installation workflows.

The project is developed as its own heavily modified application with its own UI, package name and feature set.

## Upstream: SAI

VSI originally started from [SAI — Split APKs Installer](https://github.com/Aefyr/SAI) by Aefyr / polychromaticfox.

SAI is a great open-source Android project and provided an excellent technical foundation for split-APK installation. We are very grateful that the project was released as open source.

VSI has since been changed substantially. Among other things, it has its own branding and UI, extended installer backends, Root / Shizuku integration, experimental LSPosed / Vector system-installer integration, additional installation controls and a growing custom package-format layer.

Because VSI is based on GPLv3-licensed SAI code, this repository remains licensed under the GNU General Public License v3.0. The original upstream project is not responsible for VSI-specific changes, bugs or behavior.

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

VSI is distributed under the [GNU General Public License v3.0](LICENSE), in accordance with the license of the upstream SAI project.

## Credits

- [SAI — Split APKs Installer](https://github.com/Aefyr/SAI) — original upstream project and major technical foundation.
- Aefyr / polychromaticfox — original SAI author.
- Android, Shizuku, LSPosed / Xposed-compatible tooling and the wider open-source Android community.

---

VSI — Virtual Source Installer
by Veyra
