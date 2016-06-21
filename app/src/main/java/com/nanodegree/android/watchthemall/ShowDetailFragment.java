package com.nanodegree.android.watchthemall;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.nanodegree.android.watchthemall.data.WtaContract;
import com.nanodegree.android.watchthemall.util.Utility;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class ShowDetailFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String DETAIL_URI = "URI";
    public static final String IS_TWO_PANE = "IS_TWO_PANE";

    private static final String LOG_TAG = ShowDetailFragment.class.getSimpleName();
    private static final int DETAIL_SHOW_LOADER_ID = 1;

    private static final String[] SHOW_COLUMNS = {
            WtaContract.ShowEntry._ID,
            WtaContract.ShowEntry.COLUMN_TITLE,
            WtaContract.ShowEntry.COLUMN_OVERVIEW,
            WtaContract.ShowEntry.COLUMN_POSTER_PATH,
            WtaContract.ShowEntry.COLUMN_STATUS,
            WtaContract.ShowEntry.COLUMN_YEAR,
            WtaContract.ShowEntry.COLUMN_FIRST_AIRED,
            WtaContract.ShowEntry.COLUMN_AIR_DAY,
            WtaContract.ShowEntry.COLUMN_RUNTIME,
            WtaContract.ShowEntry.COLUMN_NETWORK,
            WtaContract.ShowEntry.COLUMN_COUNTRY,
            WtaContract.ShowEntry.COLUMN_HOMEPAGE,
            WtaContract.ShowEntry.COLUMN_RATING,
            WtaContract.ShowEntry.COLUMN_VOTE_COUNT,
            WtaContract.ShowEntry.COLUMN_LANGUAGE,
            WtaContract.ShowEntry.COLUMN_AIRED_EPISODES,
            WtaContract.ShowEntry.COLUMN_WATCHING,
            WtaContract.ShowEntry.COLUMN_WATCHED,
            WtaContract.ShowEntry.COLUMN_WATCHLIST
    };
    // These indices are tied to SHOWS_COLUMNS. If SHOWS_COLUMNS changes, these must change too.
    public static final int COL_ID = 0;
    public static final int COL_TITLE = 1;
    public static final int COL_OVERVIEW = 2;
    public static final int COL_POSTER_PATH = 3;
    public static final int COL_STATUS = 4;
    public static final int COL_YEAR = 5;
    public static final int COL_FIRST_AIRED = 6;
    public static final int COL_AIR_DAY = 7;
    public static final int COL_RUNTIME = 8;
    public static final int COL_NETWORK = 9;
    public static final int COL_COUNTRY = 10;
    public static final int COL_HOMEPAGE = 11;
    public static final int COL_RATING = 12;
    public static final int COL_VOTE_COUNT = 13;
    public static final int COL_LANGUAGE = 14;
    public static final int COL_AIRED_EPISODES = 15;
    public static final int COL_WATCHING = 16;
    public static final int COL_WATCHED = 17;
    public static final int COL_WATCHLIST = 18;

    private static final int DETAIL_IMAGE_WIDTH = 370;
    private static final int DETAIL_IMAGE_HEIGHT = 554;

    private ShareActionProvider mShareActionProvider;
    private Uri mUri;
    private int mShowId;
    private boolean mUseTwoPaneLayout;
    private Unbinder mButterKnifeUnbinder;

    @BindView(R.id.showPoster)
    ImageView mShowPoster;
    @BindView(R.id.showTitle)
    TextView mShowTitle;
    @BindView(R.id.showOverview)
    WebView mShowOverview;
    @BindView(R.id.showRating)
    TextView mShowRating;
    @BindView(R.id.showVoteCount)
    TextView mShowVoteCount;
    @BindView(R.id.detailRootView)
    View mRootView;

    public ShowDetailFragment() {
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        getLoaderManager().initLoader(DETAIL_SHOW_LOADER_ID, null, this);

        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.setHasOptionsMenu(Boolean.TRUE);

        Bundle arguments = getArguments();
        if (arguments != null) {
            mUseTwoPaneLayout = arguments
                    .getBoolean(ShowDetailFragment.IS_TWO_PANE, Boolean.FALSE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        if (arguments != null) {
            mUri = arguments.getParcelable(ShowDetailFragment.DETAIL_URI);
        }

        View rootView = inflater.inflate(R.layout.fragment_show_detail, container, false);
        mButterKnifeUnbinder = ButterKnife.bind(this, rootView);

        mRootView.setVisibility(View.INVISIBLE);

        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_showdetailfragment, menu);

        // Retrieve the share menu item
        MenuItem menuItem = menu.findItem(R.id.action_share);

        // Get the provider and hold onto it to set/change the share intent.
        mShareActionProvider =
                (ShareActionProvider) MenuItemCompat.getActionProvider(menuItem);

        // Attach an intent to this ShareActionProvider.
        if (mShareActionProvider != null ) {
            mShareActionProvider.setShareIntent(createShareShowIntent(null));
        } else {
            Log.e(LOG_TAG, "Share Action Provider is null?");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mButterKnifeUnbinder!=null) {
            mButterKnifeUnbinder.unbind();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if ( null != mUri ) {
            switch (id) {
                case DETAIL_SHOW_LOADER_ID: {
                    return new CursorLoader(getActivity(),
                            mUri, SHOW_COLUMNS, null, null, null);
                }
            }
        }

        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (!data.moveToFirst()) {
            return;
        }

        long loaderId = loader.getId();
        if (loaderId == DETAIL_SHOW_LOADER_ID) {
            onDetailShowLoadFinished(data);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        long loaderId = loader.getId();
        if (loaderId == DETAIL_SHOW_LOADER_ID) {
            //Do nothing
        }
    }

    public void hideDetailLayout() {
        mRootView.setVisibility(View.INVISIBLE);
    }

    private void onDetailShowLoadFinished(Cursor data) {
        mShowId = data.getInt(COL_ID);

        mShowTitle.setText(data.getString(COL_TITLE));
        String posterPath = data.getString(COL_POSTER_PATH);
        if (posterPath!=null) {
            Glide.with(getActivity()).load(posterPath)
                    .error(getActivity().getDrawable(R.drawable.no_show_poster))
                    .crossFade().into(mShowPoster);
        } else {
            Glide.with(getActivity()).load(R.drawable.no_show_poster)
                    .crossFade().into(mShowPoster);
        }
//        SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.sdf_format));
//        mMovieReleaseDate.setText(sdf.format(new Date(data.getLong(COL_RELEASE_DATE))));
        mShowOverview.loadData(String.format(Utility.HTML_TEXT_FORMAT,
                data.getString(COL_OVERVIEW)), Utility.HTML_TEXT_MIME_TYPE,
                Utility.HTML_TEXT_ENCODING);
        mShowOverview.setBackgroundColor(Color.TRANSPARENT);
        Double rating = data.getDouble(COL_RATING);
        mShowRating.setText(rating.toString());
        Integer voteCount = data.getInt(COL_VOTE_COUNT);
        mShowVoteCount.setText("(" + voteCount + " " +
                getActivity().getString(R.string.votes_label) + ")");

        mRootView.setVisibility(View.VISIBLE);

        // If onCreateOptionsMenu has already happened, we need to update the share intent now.
        if (mShareActionProvider != null) {
            mShareActionProvider.setShareIntent(createShareShowIntent(data.getString(COL_HOMEPAGE)));
        }
    }

    private Intent createShareShowIntent(String homepage) {
        if (homepage==null) {
            return null;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                getString(R.string.share_show_base_message) + " - " + homepage + " - " +
                        getString(R.string.share_show_hashtag));
        return shareIntent;
    }
}
