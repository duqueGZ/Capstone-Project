package com.nanodegree.android.watchthemall;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.nanodegree.android.watchthemall.util.Utility;

public class SearchResultsActivity extends AppCompatActivity
        implements ShowsFragment.Callback {

    private static final String DETAIL_FRAGMENT_TAG = "DFTAG";

    private boolean mTwoPane;
    private String mSearchKeywords = "";
    private String mSelectedCollection = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_search_results);

        getSupportActionBar().setTitle(getString(R.string.title_activity_search_results) + ". " +
                getString(R.string.title_search_results));

        if (getIntent().getExtras()!=null) {
            mSearchKeywords = getIntent().getExtras().getString(Utility.SEARCH_KEYWORDS_EXTRA_KEY);
            mSelectedCollection = getIntent().getExtras().getString(Utility.COLLECTION_EXTRA_KEY);
        }

        if (findViewById(R.id.show_detail_container) != null) {
            // The detail container view will be present only in the large-screen layouts
            // (res/layout-sw600dp). If this view is present, then the activity should be
            // in two-pane mode.
            mTwoPane = true;
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            if (savedInstanceState == null) {
                ShowDetailFragment detailFragment = new ShowDetailFragment();
                Bundle arguments = new Bundle();
                arguments.putBoolean(ShowDetailFragment.IS_TWO_PANE, mTwoPane);
                detailFragment.setArguments(arguments);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.show_detail_container, detailFragment,
                                DETAIL_FRAGMENT_TAG)
                        .commit();
            }
        } else {
            mTwoPane = false;
        }

        ShowsFragment showsFragment = ((ShowsFragment) getSupportFragmentManager()
                .findFragmentById(R.id.shows_container));
        showsFragment.setUseTwoPaneLayout(mTwoPane);
        showsFragment.setSearchKeywords(mSearchKeywords);
        showsFragment.setSelectedCollection(mSelectedCollection);
    }

    @Override
    public void onItemSelected(Uri dateUri) {
        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            if (dateUri==null) {
                ShowDetailFragment detailFragment =
                        ((ShowDetailFragment) getSupportFragmentManager()
                                .findFragmentById(R.id.show_detail_container));
                detailFragment.hideDetailLayout();
            }
            Bundle args = new Bundle();
            args.putParcelable(ShowDetailFragment.DETAIL_URI, dateUri);
            ShowDetailFragment fragment = new ShowDetailFragment();
            fragment.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.show_detail_container, fragment, DETAIL_FRAGMENT_TAG)
                    .commit();
        } else {
            Intent intent = new Intent(this, ShowDetailActivity.class)
                    .setData(dateUri);
            startActivity(intent);
        }
    }
}
