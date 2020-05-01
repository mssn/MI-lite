package edu.purdue.cs.mssn.mi_lite;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.system.ErrnoException;
import android.system.Os;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LightDiagRevealerService extends Service {

    private IBinder binder = new LocalBinder();
    private Lock lock = new ReentrantLock();
    private List<String> listPendingMessages = new ArrayList<>();
    File file_DiagRevealer;
    File file_DiagConfig;
    File file_FIFO;
    File directory_output_logs;
    Process processDiagRevealer;
    Task_Read_PIPE mTask;
    GlobalStore gs;

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public LightDiagRevealerService() {
    }

    class LocalBinder extends Binder {
        LightDiagRevealerService getService() {
            return LightDiagRevealerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        gs = (GlobalStore) getApplication();

        Intent notificationIntent = new Intent(this, ScrollingActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, "milite_channel")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("MI-lite")
                .setContentText("LightDiagRevealer service is running ......")
                .setContentIntent(pendingIntent).build();
        startForeground(301, notification);
        prepare_diag_revealer();
        prepare_fifo();
        prepare_output_logs_dir();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    void stop() {
        Log.i(getString(R.string.app_name), "LightDiagRevealerService stop() is called");
        GlobalStore.executeCommand(new String[]{"su", "-c", "killall", "diag_revealer"});
        mTask.cancel(true);
        gs.showMessage("LightDiagRevealerService is stopped");
    }

    void start() {
        Log.i(getString(R.string.app_name), "LightDiagRevealerService start() is called");
        gs.showMessage("LightDiagRevealerService is launched");
        if (!file_DiagConfig.exists() || !file_DiagRevealer.exists() || !file_FIFO.exists()) {
            Log.i(getString(R.string.app_name), "diag_revealer and Diag.cfg is not ready.");
            return;
        }
        start_DiagRevealer_Process();
        mTask = new Task_Read_PIPE(file_FIFO);
        mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    void start_DiagRevealer_Process() {
        try {
            processDiagRevealer = Runtime.getRuntime().exec(new String[]{
                    "su", "-c",
                    file_DiagRevealer.getAbsolutePath(),
                    file_DiagConfig.getAbsolutePath(),
                    file_FIFO.getAbsolutePath(),
                    directory_output_logs.getAbsolutePath(),
                    "25",
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void insertCustomMsg(String strMsg) {
        lock.lock();
        listPendingMessages.add(strMsg);
        lock.unlock();
    }

    static void copy(File src, File dst) throws IOException {
        Log.i("MI-LAB", "copy: " + src.getAbsolutePath() + ", " + dst.getAbsolutePath());
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private void prepare_diag_revealer() {
        try {
            InputStream in = getResources().openRawResource(R.raw.diag_revealer);
            FileOutputStream out= openFileOutput("diag_revealer", MODE_PRIVATE);
            byte[] buff = new byte[1024];
            int read;
            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
            in.close();
            out.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
        file_DiagRevealer = new File(getFilesDir(), "diag_revealer");
        GlobalStore.executeCommand(new String[]{"chmod", "a+x", file_DiagRevealer.getAbsolutePath()});

        try {
            InputStream in = getResources().openRawResource(R.raw.mmlabv2);
            FileOutputStream out= openFileOutput("Diag.cfg", MODE_PRIVATE);
            byte[] buff = new byte[1024];
            int read;
            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
            in.close();
            out.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
        file_DiagConfig = new File(getFilesDir(), "Diag.cfg");
    }

    private void prepare_fifo() {
        file_FIFO = new File(getCacheDir(), "fifo");
        if (file_FIFO.exists()) {
            file_FIFO.delete();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Os.mkfifo(file_FIFO.getAbsolutePath(), 0666);
            } catch (ErrnoException e) {
                e.printStackTrace();
                GlobalStore.executeCommand(new String[]{"mkfifo", file_FIFO.getAbsolutePath(), "-m666"});
            }
        } else {
            GlobalStore.executeCommand(new String[]{"mkfifo", file_FIFO.getAbsolutePath(), "-m666"});
        }
    }

    private void prepare_output_logs_dir() {
        directory_output_logs = new File(getCacheDir(), "logs");
        if (directory_output_logs.exists())
            GlobalStore.executeCommand(new String[]{"su", "-c", "rm -rf " + directory_output_logs.getAbsolutePath()});
        directory_output_logs.mkdirs();
    }

    private class Task_Read_PIPE extends AsyncTask<Void, String, Integer> {
        File file_fifo;
        String strDeviceId;
        String strDeviceInfo;
        String strOperator;

        Task_Read_PIPE(File file) {
            file_fifo = file;
            TelephonyManager myTelephonyManager = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
            assert myTelephonyManager != null;
            strOperator = myTelephonyManager.getNetworkOperator();
            strOperator = strOperator.replace("\n", "").replace("\r", "").replace(" ", "");
            strDeviceId = GlobalStore.executeCommand(new String[]{"getprop", "ro.serialno"});
            if (strDeviceId.equals(""))
                strDeviceId = "unknown";
            else
                strDeviceId = md5hexdigest(strDeviceId);
            strDeviceInfo = GlobalStore.executeCommand(new String[]{"getprop", "ro.product.manufacturer"});
            strDeviceInfo += "-";
            strDeviceInfo += GlobalStore.executeCommand(new String[]{"getprop", "ro.product.model"});
            strDeviceInfo = strDeviceInfo.replace("\n", "").replace("\r", "").replace(" ", "");
        }

        @Override
        protected Integer doInBackground(Void... params) {
            Log.i("MILab", "Task_Read_PIPE is executed.");
            try {
                String strTimeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Calendar.getInstance().getTime());
                File output = new File(getCacheDir(), "diag_log_" + strTimeStamp + "_" + strDeviceId + "_" + strDeviceInfo + "_" + strOperator + ".mi3log");
                FileOutputStream fos = new FileOutputStream(output.getAbsolutePath());
                fos.write(DmCollector.generateCustomPkt("Hello"));

                InputStream is = new FileInputStream(file_fifo);
                byte[] bytes = new byte[64];
                ChronicleProcessor chproc = new ChronicleProcessor();
                ChronicleProcessorResult result;
                while(!isCancelled()) {
                    int iSize = is.read(bytes, 0, bytes.length);
                    if (iSize > 0) {
                        ArrayList<Byte> s = new ArrayList<>();
                        for (int i = 0; i < iSize; i++) {
                            s.add(bytes[i]);
                        }
                        while (s.size() > 0) {
                            result = chproc.process(s);
                            if (result.ret_msg_type == 1 && result.ret_payload != null) {
                                // Log.i(getString(R.string.app_name), "Payload: " + bytesListToHex(result.ret_payload));
                                byte[] out = new byte[result.ret_payload.size()];
                                for (int i = 0; i < result.ret_payload.size(); i++) {
                                    out[i] = result.ret_payload.get(i);
                                }
                                fos.write(out);
                            } else if (result.ret_msg_type == 3) {
                                fos.write(DmCollector.generateCustomPkt("Bye"));
                                fos.close();
                                String dstPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/MI-lite/log";
                                File dirOutput = new File(dstPath);
                                if (!dirOutput.exists() || !dirOutput.isDirectory()) {
                                    if (!dirOutput.mkdirs())
                                        Log.i(getString(R.string.app_name), "Failed to create " + dirOutput.getAbsolutePath());
                                }
                                File dstFile = new File(dstPath, output.getName());
                                publishProgress("Save new log in: " + dstFile.getAbsolutePath());
                                copy(output, dstFile);
                                GlobalStore.executeCommand(new String[]{"su", "-c", "rm " + output.getAbsolutePath()});
                                strTimeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Calendar.getInstance().getTime());
                                output = new File(getCacheDir(), "diag_log_" + strTimeStamp + "_" + strDeviceId + "_" + strDeviceInfo + "_" + strOperator + ".mi3log");
                                fos = new FileOutputStream(output.getAbsolutePath());
                                fos.write(DmCollector.generateCustomPkt("Hello"));
                            }
                            if (!result.ret_is_payload_pending) {
                                lock.lock();
                                for (String msg:listPendingMessages
                                     ) {
                                    fos.write(DmCollector.generateCustomPkt(msg));
                                }
                                listPendingMessages.clear();
                                lock.unlock();
                            }
                        }
                    }

                }
                fos.write(DmCollector.generateCustomPkt("Bye"));
                fos.close();
                is.close();
                String dstPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/MI-lite/log";
                File dirOutput = new File(dstPath);
                if (!dirOutput.exists() || !dirOutput.isDirectory()) {
                    if (!dirOutput.mkdirs())
                        Log.i(getString(R.string.app_name), "Failed to create " + dirOutput.getAbsolutePath());
                }
                File dstFile = new File(dstPath, output.getName());
                gs.showMessage("Save log in: " + dstFile.getAbsolutePath());
                copy(output, dstFile);
                GlobalStore.executeCommand(new String[]{"su", "-c", "rm " + output.getAbsolutePath()});
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            gs.showMessage(values[0]);
        }
    }

    private class ChronicleProcessorResult {
        int ret_msg_type;
        ArrayList<Byte> ret_payload;
        boolean ret_is_payload_pending;
    }

    private class ChronicleProcessor {

        final int TYPE_LOG = 1;
        final int TYPE_START_LOG_FILE = 2;
        final int TYPE_END_LOG_FILE = 3;
        final int READ_TYPE = 0;
        final int READ_MSG_LEN = 1;
        final int READ_TS = 2;
        final int READ_PAYLOAD = 3;
        final int READ_FILENAME = 4;

        private int state;
        private int msg_type;
        private ArrayList[] bytes;
        private int[] to_read;


        ChronicleProcessor() {
            _init_state();
        }

        void _init_state() {
            this.state = READ_TYPE;
            this.msg_type = -1;
            this.bytes = new ArrayList[5];
            for (int i = 0; i < 5; i++) {
                bytes[i] = new ArrayList<Byte>();
            }
            this.to_read = new int[] {2, 2, 8, -1, -1};
        }

        ChronicleProcessorResult process(ArrayList<Byte> b) {
            ChronicleProcessorResult result = new ChronicleProcessorResult();
            result.ret_msg_type = this.msg_type;
            result.ret_payload = null;
            result.ret_is_payload_pending = false;

            while (b.size() > 0) {
                if (b.size() <= this.to_read[this.state]) {
                    this.bytes[this.state].addAll(b);
                    this.to_read[this.state] -= b.size();
                    b.clear();
                } else {
                    int idx = this.to_read[this.state];
                    this.bytes[this.state].addAll(b.subList(0, idx));
                    this.to_read[this.state] = 0;
                    if (idx > 0) {
                        b.subList(0, idx).clear();
                    }
                }
                // Process input data
                if (this.state == READ_TYPE) {
                    if (this.to_read[this.state] == 0) {
                        // current field is complete
                        ByteBuffer bb = ByteBuffer.allocate(2);
                        bb.order(ByteOrder.LITTLE_ENDIAN);
                        bb.put((Byte)this.bytes[this.state].get(0));
                        bb.put((Byte)this.bytes[this.state].get(1));
                        this.msg_type = bb.getShort(0);
                        result.ret_msg_type = this.msg_type;
                        this.state = READ_MSG_LEN;
                    }
                } else if (this.state == READ_MSG_LEN) {
                    if (this.to_read[this.state] == 0) {
                        // current field is complete
                        ByteBuffer bb = ByteBuffer.allocate(2);
                        bb.order(ByteOrder.LITTLE_ENDIAN);
                        bb.put((Byte)this.bytes[this.state].get(0));
                        bb.put((Byte)this.bytes[this.state].get(1));
                        int msg_len = bb.getShort(0);
                        if (this.msg_type == TYPE_LOG) {
                            this.to_read[READ_PAYLOAD] = msg_len - 8;
                            this.state = READ_TS;
                        } else if(this.msg_type == TYPE_START_LOG_FILE || this.msg_type == TYPE_END_LOG_FILE) {
                            this.to_read[READ_FILENAME] = msg_len;
                            this.state = READ_FILENAME;
                        } else {
                            Log.e(getString(R.string.app_name), "ChronicleProcess: unknown msg type: " + this.msg_type);
                        }
                    }
                } else if (this.state == READ_TS) {
                    if (this.to_read[this.state] == 0) {
                        // ignore timestamp
                        this.state = READ_PAYLOAD;
                    }
                } else if (this.state == READ_PAYLOAD) {
                    if (this.bytes[this.state].size() > 0) {
                        // don't need to wait for complete field
                        result.ret_is_payload_pending = true;
                        result.ret_payload = new ArrayList<Byte>(this.bytes[this.state]);
                        this.bytes[this.state].clear();
                    }
                    if (this.to_read[this.state] == 0) {
                        // current field is complete
                        this._init_state();
                        result.ret_is_payload_pending = false;
                        break;
                    }
                } else {
                    // Read filename
                    if  (this.to_read[this.state] == 0) {
                        // current field is complete
                        // ignore filename
                        this._init_state();
                        break;
                    }
                }
            }
            return result;
        }

    }

    static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    static String bytesListToHex(ArrayList<Byte> bytes) {
        char[] hexChars = new char[bytes.size() * 2];
        for (int j = 0; j < bytes.size(); j++) {
            int v = bytes.get(j) & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    String md5hexdigest(String message) {
        StringBuilder hd = new StringBuilder("unknown");
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance( "MD5" );
            md5.update( message.getBytes() );
            BigInteger hash = new BigInteger( 1, md5.digest() );
            hd = new StringBuilder(hash.toString(16)); // BigInteger strips leading 0's
            while ( hd.length() < 32 ) { hd.insert(0, "0"); } // pad with leading 0's
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return hd.toString();
    }
}
