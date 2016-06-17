package com.nanodegree.android.watchthemall;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.nanodegree.android.watchthemall.sync.WtaSyncAdapter;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WtaSyncAdapter.initializeSyncAdapter(this);
    }
}
