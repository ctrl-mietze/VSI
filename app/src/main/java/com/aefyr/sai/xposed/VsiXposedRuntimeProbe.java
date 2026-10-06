package com.aefyr.sai.xposed;

/**
 * Returns false in a normal process.
 *
 * When VSI is actually loaded by an Xposed-compatible runtime (LSPosed or
 * Vector), VsiSystemInstallerHook replaces this method and returns true.
 * This lets the settings UI verify the runtime itself instead of merely
 * checking whether a manager app is installed.
 */
public final class VsiXposedRuntimeProbe {

    private VsiXposedRuntimeProbe() {
    }

    public static boolean isActive() {
        return false;
    }
}
