package com.nanodegree.android.watchthemall;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class ShowDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_show_detail);
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            Bundle arguments = new Bundle();
            arguments.putParcelable(ShowDetailFragment.DETAIL_URI, getIntent().getData());

            ShowDetailFragment fragment = new ShowDetailFragment();
            fragment.setArguments(arguments);

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.show_detail_container, fragment)
                    .commit();
        }
    }
}
