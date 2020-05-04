package edu.purdue.cs.mssn.mi_lite;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class ScrollingActivity extends AppCompatActivity {

    private MonitorMI monitorMI;
    private boolean startMonitorMI;
    private ServiceConnection serviceConnectionLightDiagRevealer;
    private boolean isLightDiagRevealerServiceConnected = false;
    private LightDiagRevealerService mLightDiagRevealerService;
    private Activity thisActivity;
    private Handler handler;
    private static final int MULTIPLE_PERMISSION_REQUEST = 19;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("MIlite.ItemDetailActivity.MIdead");

        handler = new Handler();

        Switch switch1 = findViewById(R.id.switch1);
        switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    registerReceiver(brTaskCommand, intentFilter);
                    handler.post(runMobileInsight);
                } else {
                    handler.post(stopTask);
                }
            }
        });

        thisActivity = this;
        GlobalStore gs = (GlobalStore) getApplication();
        gs.Init(thisActivity);

        askForPermissions();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void askForPermissions() {
        ActivityCompat.requestPermissions(thisActivity,
                new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.FOREGROUND_SERVICE
                },
                MULTIPLE_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        if (requestCode == MULTIPLE_PERMISSION_REQUEST) {// If request is cancelled, the result arrays are empty.
            if (!hasAllPermissionsGranted(grantResults)) {
                // permission denied, boo! Disable the
                // functionality that depends on this permission.
                finishAffinity();
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private boolean hasAllPermissionsGranted(@NonNull int[] grantResults) {
        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    private BroadcastReceiver brTaskCommand = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("MIlite.ItemDetailActivity.MIdead".equals(action)) {
                runMobileInsight.run();
            }
        }
    };

    private Runnable stopTask = new Runnable() {
        @Override
        public void run() {
            GlobalStore.executeCommand(new String[]{"su", "-c", "svc wifi enable"});
            killMI();
        }
    };

    private Runnable runMobileInsight = new Runnable() {
        @Override
        public void run() {
            killMI();
            GlobalStore.executeCommand(new String[]{"su", "-c", "svc wifi disable"});
            monitorMI = new MonitorMI(thisActivity);
            monitorMI.start();
            startMonitorMI = true;
            serviceConnectionLightDiagRevealer = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.i(getString(R.string.app_name), "LightDiagRevealer Service Connected.");
                    mLightDiagRevealerService = ((LightDiagRevealerService.LocalBinder) service).getService();
                    isLightDiagRevealerServiceConnected = true;
                    mLightDiagRevealerService.start();
                    if (mLightDiagRevealerService.mTask == null){
                        TextView myTextView = findViewById(R.id.myTextView);
                        myTextView.append("LightDiagRevealerService start failed.\n");
                    }else{
                        TextView myTextView = findViewById(R.id.myTextView);
                        myTextView.append("LightDiagRevealerService connected.\n");
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.i(getString(R.string.app_name), "LightDiagRevealer Service Crashed.");
                    isLightDiagRevealerServiceConnected = false;
                }
            };

            bindService(new Intent(thisActivity, LightDiagRevealerService.class),
                    serviceConnectionLightDiagRevealer, BIND_AUTO_CREATE);
        }
    };

    private void killMI() {
        if (monitorMI != null && startMonitorMI) {
            monitorMI.stop();
            startMonitorMI = false;
        }
        if (isLightDiagRevealerServiceConnected) {
            try {
                isLightDiagRevealerServiceConnected = false;
                mLightDiagRevealerService.stop();
                unbindService(serviceConnectionLightDiagRevealer);
            } catch (java.lang.IllegalArgumentException e) {
                Log.i(getString(R.string.app_name), "LightDiagRevealerService is null");
            }
        }
    }

}
