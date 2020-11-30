package edu.purdue.cs.mssn.mi_lite;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.view.Menu;
import android.view.MenuItem;

import edu.purdue.cs.mssn.militelibrary.MILiteService;

public class ScrollingActivity extends AppCompatActivity {

    private ServiceConnection serviceConnectionLightDiagRevealer;
    private boolean isLightDiagRevealerServiceConnected = false;
    private MILiteService mLightDiagRevealerService;
    private AppCompatActivity thisActivity;
    private Handler handler;
    private static final int MULTIPLE_PERMISSION_REQUEST = 19;
    GlobalStore gs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        handler = new Handler();

        Switch switch1 = findViewById(R.id.switch1);
        switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    handler.post(runMobileInsight);
                } else {
                    handler.post(stopTask);
                }
            }
        });

        thisActivity = this;
        gs = (GlobalStore) getApplication();
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityCompat.requestPermissions(thisActivity,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.FOREGROUND_SERVICE
                    },
                    MULTIPLE_PERMISSION_REQUEST);
        } else {
            ActivityCompat.requestPermissions(thisActivity,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    },
                    MULTIPLE_PERMISSION_REQUEST);
        }
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

    private Runnable stopTask = new Runnable() {
        @Override
        public void run() {
            killMI();
        }
    };

    private Runnable runMobileInsight = new Runnable() {
        @Override
        public void run() {
            killMI();
            serviceConnectionLightDiagRevealer = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.i(getString(R.string.app_name), "LightDiagRevealer Service Connected.");
                    mLightDiagRevealerService = ((MILiteService.LocalBinder) service).getService();
                    isLightDiagRevealerServiceConnected = true;
                    mLightDiagRevealerService.setDiagConfigOption(MILiteService.DiagConfig.suggested);
                    gs.showMessage("Output Directory: " + mLightDiagRevealerService.getOutputPath());
                    gs.showMessage("Used Diag Config: " + mLightDiagRevealerService.getDiagConfigOption().name());
                    gs.showMessage("Diag File Cut Size (MB): " + mLightDiagRevealerService.getCutSize());
                    mLightDiagRevealerService.start();
                    gs.showMessage("MI-Lite Service Connected");
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    gs.showMessage("MI-Lite Service Crashed.");
                    isLightDiagRevealerServiceConnected = false;
                }
            };

            bindService(new Intent(thisActivity, MILiteService.class),
                    serviceConnectionLightDiagRevealer, BIND_AUTO_CREATE);
        }
    };

    private void killMI() {
        if (isLightDiagRevealerServiceConnected) {
            try {
                isLightDiagRevealerServiceConnected = false;
                mLightDiagRevealerService.stop();
                unbindService(serviceConnectionLightDiagRevealer);
                gs.showMessage("MI-Lite Service Stopped.");
            } catch (java.lang.IllegalArgumentException e) {
                gs.showMessage("MI-Lite Service stop failed");
            }
        }
    }

}
