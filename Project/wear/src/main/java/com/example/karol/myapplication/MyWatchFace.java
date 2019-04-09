/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.karol.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.graphics.Palette;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static android.R.attr.centerX;
import static android.R.attr.centerY;
import static android.R.attr.rotationX;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
public class MyWatchFace extends CanvasWatchFaceService{

    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand. ss
     *
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final String BATTERY_PROCENTAGE = "/battery_procentage";
    private static int batteryLevel = 100;
    private String TAG = "Log";

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        private static final float HOUR_STROKE_WIDTH = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 6;
        private final Rect mPeekCardBounds = new Rect();
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;
        private float mCenterX;
        private float mCenterY;
        private GoogleApiClient mApiClient;
        private float mSecondHandLength;
        private float sMinuteHandLength;
        private float sHourHandLength;
        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;
        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;
        private Paint mBackgroundPaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private int batteryPercentage = 0;
        private Paint watchArcPaint;

        private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Get extra data included in the Intent
                batteryLevel = Integer.parseInt(intent.getStringExtra("battery_procentage"));
                Log.d("Tag", "Recieved");
                Toast.makeText(getApplicationContext(),"Changed", Toast.LENGTH_LONG).show();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            mApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                    mMessageReceiver, new IntentFilter("battery_level_phone"));

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);

            /* Set defaults for colors */
            mWatchHandColor = Color.WHITE;
            mWatchHandHighlightColor = Color.RED;
            mWatchHandShadowColor = Color.BLACK;

            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);


            watchArcPaint = new Paint();
            watchArcPaint.setColor(Color.RED);
            watchArcPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            watchArcPaint.setAntiAlias(true);
            watchArcPaint.setStyle(Paint.Style.STROKE);
            watchArcPaint.setStrokeWidth(20);


            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, Color.WHITE);

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(mWatchHandColor);
            mTickAndCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            /* Extract colors from background image to improve watchface style. */
            Palette.from(mBackgroundBitmap).generate(new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    if (palette != null) {
                        mWatchHandHighlightColor = palette.getVibrantColor(Color.RED);
                        mWatchHandColor = palette.getLightVibrantColor(Color.WHITE);
                        mWatchHandShadowColor = palette.getDarkMutedColor(Color.BLACK);
                        updateWatchHandStyle();
                    }
                }
            });

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (mAmbient) {
                mHourPaint.setColor(Color.WHITE);
                mMinutePaint.setColor(Color.WHITE);
                mSecondPaint.setColor(Color.WHITE);
                mTickAndCirclePaint.setColor(Color.WHITE);


                watchArcPaint.setAntiAlias(false);
                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);
                mTickAndCirclePaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();
                mTickAndCirclePaint.clearShadowLayer();

            } else {
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(mWatchHandColor);
                mSecondPaint.setColor(Color.WHITE);
                mTickAndCirclePaint.setColor(mWatchHandColor);

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);
                mTickAndCirclePaint.setAntiAlias(true);
                watchArcPaint.setAntiAlias(true);

                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (float) (mCenterX * 0.85);
            sMinuteHandLength = (float) (mCenterX * 0.75);
            sHourHandLength = (float) (mCenterX * 0.5);


            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scale),
                    (int) (mBackgroundBitmap.getHeight() * scale), true);

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap();
            }
        }

        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    //Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT).show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            Resources resources = MyWatchFace.this.getResources();
            float mXOffset = resources.getDimension(R.dimen.digital_x_offset);
            float mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawColor(Color.BLACK);
            }

            Paint fiveMinutePaint = new Paint(mSecondPaint);
            fiveMinutePaint.setColor(Color.GRAY);
            for(int a = 0; a< 12; a++){
                float secOuterX = (float) Math.sin(0+0.52359877559*a) * mSecondHandLength;
                float secOuterY = (float) -Math.cos(0+0.52359877559*a) * mSecondHandLength;
                float secInnerX = (float) Math.sin(0+0.52359877559*a) * (mSecondHandLength-5);
                float secInnerY = (float) -Math.cos(0+0.52359877559*a) * (mSecondHandLength-5);
                canvas.drawLine(mCenterX+secInnerX, mCenterY+secInnerY, mCenterX + secOuterX, mCenterY +
                        secOuterY, mSecondPaint);
            }

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            float innerTickRadius = mCenterX - 10;
            float outerTickRadius = mCenterX;
            Paint watchArc2Paint = new Paint(watchArcPaint);
            watchArcPaint.setColor(Color.rgb((int)(255*0.01*(100-batteryPercentage)),(int)(255*0.01*batteryPercentage),0));
            watchArc2Paint.setColor(Color.rgb((int)(255*0.01*(100-batteryLevel)),(int)(255*0.01*batteryLevel),0));

            Paint mPaintText = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaintText.setStyle(Paint.Style.FILL_AND_STROKE);
            mPaintText.setColor(Color.BLACK);
            mPaintText.setTextSize(25f);

            RectF rect = new RectF();
            rect.set(watchArcPaint.getStrokeWidth()/2,watchArcPaint.getStrokeWidth()/2,mCenterX*2-watchArcPaint.getStrokeWidth()/2,mCenterY*2-watchArcPaint.getStrokeWidth()/2-3);
            canvas.drawArc(rect,180,180*0.01f*batteryPercentage,false, watchArcPaint);
            canvas.drawArc(rect,0,180*0.01f*batteryLevel,false,watchArc2Paint);

            final float TWO_PI = (float) Math.PI * 2f;
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation =seconds / 60f * TWO_PI;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(55);
            textPaint.setStrokeWidth(5f);
            textPaint.setAntiAlias(true);
            watchArcPaint.setStyle(Paint.Style.STROKE);
            String text = mAmbient ? String.format("%02d:%02d", mCalendar.get(Calendar.HOUR), mCalendar.get(Calendar.MINUTE))
                    : String.format("%02d:%02d", mCalendar.get(Calendar.HOUR), mCalendar.get(Calendar.MINUTE));
            canvas.drawText(text, mCenterX-textPaint.measureText(text)/2, mCenterY+textPaint.getTextSize()/2, textPaint);
            //canvas.drawText(Integer.toString(batteryLevel), mCenterX-textPaint.measureText(Integer.toString(batteryLevel))/2,mCenterY+50+textPaint.getTextSize()/2, textPaint);
            canvas.drawText("10"+(char) 0x00B0, mCenterX-textPaint.measureText("10"+(char) 0x00B0)/2,mCenterY-25-textPaint.getTextSize()/2, textPaint);
            canvas.drawText(Integer.toString(batteryLevel), mCenterX-textPaint.measureText(Integer.toString(batteryLevel))/2,mCenterY+80+textPaint.getTextSize()/2, textPaint);
            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            int secondHandActualLength = 30;
            if (!mAmbient) {
                Path watchProcentagePath = new Path();
                watchProcentagePath.addArc(rect, 180+180*0.01f*batteryPercentage/2-mPaintText.measureText(Integer.toString(batteryPercentage))/2,100);
                canvas.drawTextOnPath("Watch: "+Integer.toString(batteryPercentage)+"%",watchProcentagePath,0, mPaintText.getTextSize()/2-3,mPaintText);

                Path phoneProcentagePath = new Path();
                phoneProcentagePath.addArc(rect, 180-180*0.01f*batteryLevel/2-mPaintText.measureText(Integer.toString(batteryLevel))/2,100);
                canvas.drawTextOnPath("Phone: "+Integer.toString(batteryLevel)+"%",phoneProcentagePath,0, mPaintText.getTextSize()/2-3,mPaintText);

                float secOuterX = (float) Math.sin(secondsRotation) * mSecondHandLength;
                float secOuterY = (float) -Math.cos(secondsRotation) * mSecondHandLength;
                float secInnerX = (float) Math.sin(secondsRotation) * (mSecondHandLength-secondHandActualLength);
                float secInnerY = (float) -Math.cos(secondsRotation) * (mSecondHandLength-secondHandActualLength);
                canvas.drawLine(mCenterX+secInnerX, mCenterY+secInnerY, mCenterX + secOuterX, mCenterY +
                        secOuterY, mSecondPaint);
            }

            /* Restore the canvas' original orientation. */
            canvas.restore();

            /* Draw rectangle behind peek card in ambient mode to improve readability. */
            if (mAmbient) {
                watchArcPaint.setStyle(Paint.Style.STROKE);
                invalidate();
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            mPeekCardBounds.set(rect);
        }


        boolean mRegisteredBatteryPercentageReceiver = false;

        private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context arg0, Intent intent)
            {
                batteryPercentage = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            }
        };
        private void registerReceiver() {
            if (!mRegisteredTimeZoneReceiver)
            {
                mRegisteredTimeZoneReceiver = true;
                IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
                MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
            }

            if (!mRegisteredBatteryPercentageReceiver)
            {
                mRegisteredBatteryPercentageReceiver = true;
                IntentFilter filterBattery = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                MyWatchFace.this.registerReceiver(mBatInfoReceiver, filterBattery);
            }
        }

        private void unregisterReceiver() {
            if (mRegisteredTimeZoneReceiver)
            {
                mRegisteredTimeZoneReceiver = false;
                MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
            }

            {
                mRegisteredBatteryPercentageReceiver = false;
                MyWatchFace.this.unregisterReceiver(mBatInfoReceiver);
            }
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            batteryLevel = 50;
            Log.d(TAG, "Data Changed");
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }
    }
}
