package com.aefyr.sai.utils;

public enum VsiAppMode {
    NORMAL("normal", 20, PreferencesValues.INSTALLER_ROOTLESS, ServiceKind.LITE),
    SHIZUKU("shizuku", 30, PreferencesValues.INSTALLER_SHIZUKU, ServiceKind.VSERVICE),
    XPOSED("xposed", 30, PreferencesValues.INSTALLER_XPOSED, ServiceKind.VSERVICE),
    ROOT("root", 50, PreferencesValues.INSTALLER_ROOTED, ServiceKind.SYSTEM),
    ROOT_XPOSED("root_xposed", 60, PreferencesValues.INSTALLER_XPOSED, ServiceKind.SYSTEM);

    public enum ServiceKind {
        LITE,
        VSERVICE,
        SYSTEM
    }

    private final String mValue;
    private final int mCapabilityCount;
    private final int mInstallerBackend;
    private final ServiceKind mServiceKind;

    VsiAppMode(String value, int capabilityCount, int installerBackend, ServiceKind serviceKind) {
        mValue = value;
        mCapabilityCount = capabilityCount;
        mInstallerBackend = installerBackend;
        mServiceKind = serviceKind;
    }

    public String value() {
        return mValue;
    }

    public int capabilityCount() {
        return mCapabilityCount;
    }

    public int installerBackend() {
        return mInstallerBackend;
    }

    public ServiceKind serviceKind() {
        return mServiceKind;
    }

    public boolean hasRoot() {
        return this == ROOT || this == ROOT_XPOSED;
    }

    public boolean hasXposed() {
        return this == XPOSED || this == ROOT_XPOSED;
    }

    public boolean hasPrivilegedShell() {
        return this == SHIZUKU || hasRoot();
    }

    public static VsiAppMode fromValue(String value) {
        if (value != null) {
            for (VsiAppMode mode : values()) {
                if (mode.mValue.equals(value))
                    return mode;
            }
        }
        return NORMAL;
    }
}
