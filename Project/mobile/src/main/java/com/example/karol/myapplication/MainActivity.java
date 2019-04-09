package com.example.karol.myapplication;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.annotation.IntegerRes;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.Date;


public class MainActivity extends Activity{

    private int battery_level = 0;

    private GoogleApiClient mGoogleApiClient;


    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String battery_level = intent.getStringExtra("battery_level");
            Toast.makeText(getApplicationContext(), battery_level, Toast.LENGTH_LONG).show();
            syncConfiguration(battery_level);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        if(level != battery_level){
            battery_level = level;
            syncConfiguration(Integer.toString(battery_level));
        }
        syncConfiguration(Integer.toString(level));
        super.onCreate(savedInstanceState);
        LinearLayout linearLayout = new LinearLayout(this);
        Button button = new Button(this);
        button.setText("Sync");
        linearLayout.addView(button);
        final int batteryStatus = level;
        Toast.makeText(this,Integer.toString(level), Toast.LENGTH_LONG);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncConfiguration(Integer.toString(batteryStatus));
            }
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(
                mMessageReceiver, new IntentFilter("battery_level_change"));

        setContentView(linearLayout);

        // ATTENTION: This "addApi(AppIndex.API)"was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                    }
                })
                .addApi(Wearable.API)
                .addApi(AppIndex.API).build();
        mGoogleApiClient.connect();
    }

    private void syncConfiguration(String batteryLevel) {
        if(mGoogleApiClient==null)
            return;

        final PutDataMapRequest putRequest = PutDataMapRequest.create("/battery_procentage_phone");
        final DataMap map = putRequest.getDataMap();
        map.putString("battery_procentage", batteryLevel);
        map.putString("weather", "30");
        Wearable.DataApi.putDataItem(mGoogleApiClient,  putRequest.asPutDataRequest());
        Log.d("Tag", "Send");
    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("Main Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        mGoogleApiClient.connect();
        AppIndex.AppIndexApi.start(mGoogleApiClient, getIndexApiAction());
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.end(mGoogleApiClient, getIndexApiAction());
        mGoogleApiClient.disconnect();
    }
}
