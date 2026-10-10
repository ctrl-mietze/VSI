package com.aefyr.sai.service;

import com.aefyr.sai.R;

public class VsiSystemService extends VsiModeService {
    @Override
    protected int titleRes() {
        return R.string.vsi_service_system_title;
    }

    @Override
    protected int textRes() {
        return R.string.vsi_service_system_text;
    }
}
