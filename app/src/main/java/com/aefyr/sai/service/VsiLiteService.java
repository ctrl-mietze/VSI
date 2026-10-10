package com.aefyr.sai.service;

import com.aefyr.sai.R;

public class VsiLiteService extends VsiModeService {
    @Override
    protected int titleRes() {
        return R.string.vsi_service_lite_title;
    }

    @Override
    protected int textRes() {
        return R.string.vsi_service_lite_text;
    }
}
