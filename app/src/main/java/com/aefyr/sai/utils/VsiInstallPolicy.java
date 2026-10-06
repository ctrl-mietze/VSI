package com.aefyr.sai.utils;

import android.content.Context;

import com.aefyr.sai.installer2.base.model.SaiPiSessionParams;

public final class VsiInstallPolicy {

    private VsiInstallPolicy() {}

    public static SaiPiSessionParams apply(Context context, SaiPiSessionParams params) {
        PreferencesHelper prefs = PreferencesHelper.getInstance(context);
        VsiDeveloperOptions dev = VsiDeveloperOptions.getInstance(context);

        return params
                .setAllowDowngrade(prefs.shouldAllowDowngrade() || dev.forceDowngrade())
                .setAllowTestApks(prefs.shouldAllowTestApks() || dev.forceTestOnly())
                .setTargetUserId(prefs.getTargetUserId())
                .setDeleteSourceAfterSuccess(
                        prefs.shouldDeleteSourceAfterInstall() && !dev.neverDeleteSource()
                );
    }

    public static String buildCommandPreview(Context context) {
        PreferencesHelper prefs = PreferencesHelper.getInstance(context);
        VsiDeveloperOptions dev = VsiDeveloperOptions.getInstance(context);

        StringBuilder sb = new StringBuilder("pm install-create -r");
        if (prefs.shouldAllowDowngrade() || dev.forceDowngrade())
            sb.append(" -d");
        if (prefs.shouldAllowTestApks() || dev.forceTestOnly())
            sb.append(" -t");
        if (prefs.getTargetUserId() >= 0)
            sb.append(" --user ").append(prefs.getTargetUserId());

        sb.append(" --install-location ").append(prefs.getInstallLocation());
        sb.append(" -i ctrl.mietze.vsi");

        String custom = dev.customInstallCreateCommand();
        if (custom != null)
            sb.append("\n\nCustom override:\n").append(custom);

        return sb.toString();
    }
}
