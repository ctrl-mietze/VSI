package com.aefyr.sai.runtime;

public enum VsiAppMode {
    NORMAL("normal", 20),
    SHIZUKU("shizuku", 30),
    XPOSED("xposed", 30),
    ROOT("root", 50),
    ROOT_XPOSED("root_xposed", 60);

    private final String mId;
    private final int mCapabilityCount;

    VsiAppMode(String id, int capabilityCount) {
        mId = id;
        mCapabilityCount = capabilityCount;
    }

    public String id() {
        return mId;
    }

    public int capabilityCount() {
        return mCapabilityCount;
    }

    public boolean usesRoot() {
        return this == ROOT || this == ROOT_XPOSED;
    }

    public boolean usesXposed() {
        return this == XPOSED || this == ROOT_XPOSED;
    }

    public static VsiAppMode fromId(String id) {
        if (id != null) {
            for (VsiAppMode mode : values()) {
                if (mode.mId.equals(id))
                    return mode;
            }
        }
        return NORMAL;
    }
}
