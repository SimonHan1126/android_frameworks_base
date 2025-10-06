/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.usb;

import android.content.res.Resources;
import android.os.Bundle;
import android.hardware.usb.UsbDevice;

import javax.inject.Inject;

/**
 * Dialog shown when a package requests access to a USB device or accessory.
 */
public class UsbPermissionActivity extends UsbDialogActivity {

    private boolean mPermissionGranted = false;
    private UsbAudioWarningDialogMessage mUsbPermissionMessageHandler;

    private static final String AUTOGRANT_PKG = "com.tepari.macrostock.macrostock_starter";
    private static final int AUTOGRANT_VID = 0x0483;
    private static final int AUTOGRANT_PID = 0x5750;

    /**
     * Returns the calling package name if exposed by the helper, else null.
     */
    private String getCallingPkgSafe() {
        try {
            var m = mDialogHelper.getClass().getMethod("getCallingPackage");
            Object v = m.invoke(mDialogHelper);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {
        }
        try {
            var m = mDialogHelper.getClass().getMethod("getPackageName");
            Object v = m.invoke(mDialogHelper);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Returns the current UsbDevice if available, else null.
     */
    private UsbDevice getUsbDeviceSafe() {
        try {
            var m = mDialogHelper.getClass().getMethod("getDevice");
            Object v = m.invoke(mDialogHelper);
            return (UsbDevice) v;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * True if this permission request matches our allowlist.
     */
    private boolean isTePariWhitelistedRequest() {
        if (!mDialogHelper.isUsbDevice()) return false;
        final String pkg = getCallingPkgSafe();
        if (!AUTOGRANT_PKG.equals(pkg)) return false;
        final UsbDevice dev = getUsbDeviceSafe();
        return dev != null
                && dev.getVendorId() == AUTOGRANT_VID
                && dev.getProductId() == AUTOGRANT_PID;
    }

    @Inject
    public UsbPermissionActivity(UsbAudioWarningDialogMessage usbAudioWarningDialogMessage) {
        mUsbPermissionMessageHandler = usbAudioWarningDialogMessage;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mUsbPermissionMessageHandler.init(UsbAudioWarningDialogMessage.TYPE_PERMISSION,
                mDialogHelper);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isTePariWhitelistedRequest()) {
            mDialogHelper.grantUidAccessPermission();
            if (mDialogHelper.canBeDefault()) {
                mDialogHelper.setDefaultPackage();
            }
            mPermissionGranted = true;
            finish();
            return;
        }

        final boolean useRecordWarning = mDialogHelper.isUsbDevice()
                && (mDialogHelper.deviceHasAudioCapture()
                && !mDialogHelper.packageHasAudioRecordingPermission());

        final int titleId = mUsbPermissionMessageHandler.getPromptTitleId();
        final String title = getString(titleId, mDialogHelper.getAppName(),
                mDialogHelper.getDeviceDescription());
        final int messageId = mUsbPermissionMessageHandler.getMessageId();
        String message = (messageId != Resources.ID_NULL)
                ? getString(messageId, mDialogHelper.getAppName(),
                mDialogHelper.getDeviceDescription()) : null;
        setAlertParams(title, message);

        // Only show the "always use" checkbox if there is no USB/Record warning
        if (!useRecordWarning && mDialogHelper.canBeDefault()) {
            addAlwaysUseCheckbox();
        }
        setupAlert();
    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
            mDialogHelper.sendPermissionDialogResponse(mPermissionGranted);
        }
        super.onPause();
    }

    @Override
    void onConfirm() {
        mDialogHelper.grantUidAccessPermission();
        if (isAlwaysUseChecked()) {
            mDialogHelper.setDefaultPackage();
        }
        mPermissionGranted = true;
        finish();
    }
}
