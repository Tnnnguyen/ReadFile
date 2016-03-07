package com.example.tuan.readfile;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import butterknife.Bind;
import butterknife.BindString;
import butterknife.ButterKnife;
import butterknife.OnClick;
import service.ReadFileService;

public class MainActivity extends AppCompatActivity {

    public static final String INTENT_READ_FILE_RECEIVER_ACTION = "READ_FILE_ACTION";
    public static final String KEY_SCAN_RESULT_BROADCAST = "SCAN_RESULT_BROADCAST_KEY";
    private static final String KEY_SCAN_RESULT_ON_SAVE_INSTANCE_STATE = "KEY_SCAN_RESULT_ON_SAVE_INSTANCE_STATE";
    private static final String KEY_DATA_RECEIVED = "KEY_DATA_RECEIVED";
    private static final String KEY_IS_SERVICE_BOUND = "KEY_IS_SERVICE_BOUND";
    private boolean mIsServiceBound;
    private boolean mIsChangingConfiguration;
    private boolean mReceiveData;
    private String mScanResult;
    private BroadcastReceiver mReceiver;
    private ReadFileService mService;
    @Bind(R.id.loading_wheel) ProgressBar mLoadingWheel;
    @Bind(R.id.main_display) TextView mMainDisplay;

    @BindString(R.string.scanning)String scanning;
    @Bind(R.id.start_button) Button mStartButton;
    @OnClick(R.id.start_button)
    void start() {
        ObjectAnimator anim = ObjectAnimator.ofFloat(mStartButton, "rotationY", 0.0f, 30f).setDuration(600);
        ObjectAnimator animBack = ObjectAnimator.ofFloat(mStartButton, "rotationY", 30f, 0.0f).setDuration(400);
        anim.setInterpolator(new DecelerateInterpolator());
        AnimatorSet set = new AnimatorSet();
        set.play(animBack).after(anim);
        set.start();
        if(!mIsServiceBound || mService == null) {
            Intent intent = new Intent(this, ReadFileService.class);
            this.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
        else {
            mService.stopFileRead(false);
            mService.runInForegroundMode();
            registerMyReceiver();
            mService.startFileRead();
            mLoadingWheel.setVisibility(View.VISIBLE);
        }
    }

    @BindString(R.string.stop) String stop;
    @Bind(R.id.stop_button) Button mStopButton;
    @OnClick(R.id.stop_button)
    void stop() {
        mLoadingWheel.setVisibility(View.INVISIBLE);
        unregisterMyReceiver();
        Animation vibrateAnim = AnimationUtils.loadAnimation(this, R.anim.vibrate);
        mStopButton.startAnimation(vibrateAnim);
        unBindMyService();
    }

    @BindString(R.string.share_subject) String shareSubject;
    @BindString(R.string.share_picker_header) String sharePickerHeader;
    @Bind(R.id.share_button) Button mShareButton;
    @OnClick(R.id.share_button)
    void share() {
        ObjectAnimator anim = ObjectAnimator.ofFloat(mShareButton, "rotationX", 0.0f, 360f).setDuration(600);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.start();
        if(mReceiveData) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
            shareIntent.putExtra(Intent.EXTRA_TEXT, mScanResult);
            shareIntent.setType("text/plain");
            startActivity(Intent.createChooser(shareIntent, sharePickerHeader));
        }
        else {
            Toast.makeText(this, getResources().getString(R.string.data_not_received), Toast.LENGTH_LONG).show();
        }
    }

//    /**
//     * To check if a service is running
//     */
//    private boolean isMyServiceRunning(Class<?> serviceClass) {
//        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
//        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
//            if (serviceClass.getName().equals(service.service.getClassName())) {
//                return true;
//            }
//        }
//        return false;
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mStartButton.setText(getResources().getString(R.string.start));
        mStopButton.setText(getResources().getString(R.string.stop));
        mShareButton.setText(getResources().getString(R.string.share));
        if(savedInstanceState != null) {
            mScanResult = savedInstanceState.getString(KEY_SCAN_RESULT_ON_SAVE_INSTANCE_STATE);
            mMainDisplay.setText(mScanResult);
            mReceiveData = savedInstanceState.getBoolean(KEY_DATA_RECEIVED);
            mIsServiceBound = savedInstanceState.getBoolean(KEY_IS_SERVICE_BOUND);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(!isChangingConfigurations()){
            unBindMyService();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        unBindMyService();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SCAN_RESULT_ON_SAVE_INSTANCE_STATE, mScanResult);
        outState.putBoolean(KEY_DATA_RECEIVED, mReceiveData);
        outState.putBoolean(KEY_IS_SERVICE_BOUND, mIsServiceBound);
    }

    /**
     * Unregister the broadcast receiver when not interested in listening
     */
    private void unregisterMyReceiver() {
        if(mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    /**
     * Stop the background thread that is scanning and unbind the service
     */
    private void unBindMyService() {
        if(mIsServiceBound && mService != null) {
            mService.stopFileRead(true);
            unbindService(mServiceConnection);
            mIsServiceBound = false;
            unregisterMyReceiver();
        }
    }

    /**
     * Register the broadcast receiver that listens to broadcast from ReadFileService
     */
    private void registerMyReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(INTENT_READ_FILE_RECEIVER_ACTION);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent != null && intent.getStringExtra(KEY_SCAN_RESULT_BROADCAST) != null) {
                    mScanResult = intent.getStringExtra(KEY_SCAN_RESULT_BROADCAST);
                    mMainDisplay.setText(mScanResult);
                    mReceiveData = true;
                }
                mLoadingWheel.setVisibility(View.GONE);
            }
        };
        registerReceiver(mReceiver, filter);
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            ReadFileService.LocalBinder binder = (ReadFileService.LocalBinder) service;
            mService = binder.getService();
            mService.runInForegroundMode();
            mIsServiceBound = true;
            registerMyReceiver();
            mService.startFileRead();
            mLoadingWheel.setVisibility(View.VISIBLE);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mIsServiceBound = false;
            unregisterMyReceiver();
        }
    };
}
