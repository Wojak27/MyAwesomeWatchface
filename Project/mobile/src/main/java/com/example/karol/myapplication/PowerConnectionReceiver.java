package com.example.karol.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * Created by Karol on 2017-01-28.
 */

public class PowerConnectionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
        Log.d("Tag", "Changed");

        int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
        sendMessageToActivity(level, context);
    }

    private void sendMessageToActivity(int batteryLevel, Context context) {
        Intent intent = new Intent("battery_level_change");
        if(batteryLevel == -1){

        }else {
            intent.putExtra("battery_level", Integer.toString(batteryLevel));
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
    }
}