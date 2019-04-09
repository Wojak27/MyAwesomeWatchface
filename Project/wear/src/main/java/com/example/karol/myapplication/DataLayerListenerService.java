package com.example.karol.myapplication;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Karol on 2017-01-27.
 */

public class DataLayerListenerService extends WearableListenerService {
    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        final ArrayList<DataEvent> events = FreezableUtils.freezeIterable(dataEventBuffer);

        Log.d("Tag", "recieved");
        for (DataEvent event : events) {
            PutDataMapRequest putDataMapRequest =
                    PutDataMapRequest.createFromDataMapItem(DataMapItem.fromDataItem(event.getDataItem()));

            String path = event.getDataItem().getUri().getPath();
            Log.d("path", path);
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                if ("/battery_procentage_phone".equals(path)) {
                    DataMap data = putDataMapRequest.getDataMap();
                    String batteryProcentage = data.getString("battery_procentage");
                    String weather = data.getString("weather");
                    sendMessageToActivity(batteryProcentage, weather);
                } else if (event.getType() == DataEvent.TYPE_DELETED) {

                }
            }
        }
    }

    private void sendMessageToActivity(String batteryProcentage, String weather) {
        Intent intent = new Intent("battery_level_phone");
        intent.putExtra("battery_procentage", batteryProcentage);
        intent.putExtra("weather", weather);
        Log.d("batteryProcentage", batteryProcentage);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }
}
