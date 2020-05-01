package edu.purdue.cs.mssn.mi_lite;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

class MonitorMI {
    private GlobalStore gs;
    private monitor instance;

    MonitorMI(final Activity activity) {
        this.gs = (GlobalStore) activity.getApplication();
        instance = new monitor(activity);
    }

    void start() {
        instance.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    void stop() {
        instance.cancel(true);
    }

    private class monitor extends AsyncTask<Void, String, Integer> {
        Activity activity;

        monitor(Activity activity) {
            this.activity = activity;
        }

        protected Integer doInBackground(Void... voids) {
            String previous = "";
            while (!isCancelled()) {
                try {
                    Thread.sleep(60 * 1000);
                } catch (InterruptedException e) {
                    break;
                }
                String output = GlobalStore.executeCommand(new String[]{"su", "-c", "du", "-sch", activity.getApplicationContext().getCacheDir().getAbsolutePath() + "/*"});
                publishProgress("LightDiagevealer Snapshot:\n" + output);
                if (previous.equals(output)) {
                    Log.e("MI", "likely dead");
                    Intent action = new Intent("MIlight.Status.Task");
                    action.putExtra("status","mobileinsight_likely_dead");
                    activity.sendBroadcast(action);
                    activity.sendBroadcast(new Intent("MIlight.ItemDetailActivity.MIdead"));
                }else{
                    Intent action = new Intent("MIlight.Status.Task");
                    action.putExtra("status","running");
                    activity.sendBroadcast(action);
                }
                previous = output;
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

        protected void onCancelled(){
            super.onCancelled();
        }
    }
}
