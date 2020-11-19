package edu.purdue.cs.mssn.mi_lite;

import android.app.Application;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;


public class GlobalStore extends Application {

    private AppCompatActivity mainActivity;
    private TextView myTextView;

    void Init(AppCompatActivity activity) {
        this.mainActivity = activity;
        myTextView = activity.findViewById(R.id.myTextView);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    void showMessage(String message) {
        myTextView.append(message + "\n");
    }

}
