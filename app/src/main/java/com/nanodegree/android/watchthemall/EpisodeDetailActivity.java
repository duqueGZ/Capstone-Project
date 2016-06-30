package com.nanodegree.android.watchthemall;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class EpisodeDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_episode_detail);
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            Bundle arguments = new Bundle();
            arguments.putParcelable(EpisodeDetailFragment.DETAIL_URI, getIntent().getData());

            EpisodeDetailFragment fragment = new EpisodeDetailFragment();
            fragment.setArguments(arguments);

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.episode_detail_container, fragment)
                    .commit();
        }
    }
}
