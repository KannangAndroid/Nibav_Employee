package com.nibav.employee.service;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.widget.Toast;

public class DeviceAdmin extends DeviceAdminReceiver {

    void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        showToast(context, "Admin Status enabled");
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Admin Status disable request";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        showToast(context, "Admin Status disabled");
    }

    @Override
    public void onPasswordChanged(Context context, Intent intent, UserHandle userHandle) {
        showToast(context, "Password changed");
    }

}