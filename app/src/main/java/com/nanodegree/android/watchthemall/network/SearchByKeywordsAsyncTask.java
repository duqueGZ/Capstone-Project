package com.nanodegree.android.watchthemall.network;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import com.nanodegree.android.watchthemall.SearchResultsActivity;
import com.nanodegree.android.watchthemall.api.trakt.TraktService;
import com.nanodegree.android.watchthemall.data.WtaContract;
import com.nanodegree.android.watchthemall.util.Utility;

import java.util.Vector;

/**
 * An Async Task in order to retrieve Trakt shows data according to provided keywords search results
 */
public class SearchByKeywordsAsyncTask extends AsyncTask<Object, Void, String> {

    private final String LOG_TAG = SearchByKeywordsAsyncTask.class.getSimpleName();

    private static final String OK_RESULT = "OK";

    private Context mContext;
    private Boolean mNewActivity;
    private String mSearchText;

    public SearchByKeywordsAsyncTask(Context context) {
        mContext = context;
    }

    @Override
    protected String doInBackground(Object... params) {

        Log.d(LOG_TAG, "AsyncTask started");

        String result = SearchByKeywordsAsyncTask.OK_RESULT;
        mNewActivity = (Boolean) params[0];
        mSearchText = (String) params[1];
        Integer year = null;
        if (params.length>2) {
            year = (Integer) params[2];
        }
        if ((mSearchText==null) || (mSearchText.isEmpty())) {
            return null;
        }

        try {
            TraktService traktService = Utility.getTraktService();

            // First delete last search result marks and scores
            ContentValues updateValues = new ContentValues();
            updateValues.put(WtaContract.ShowEntry.COLUMN_LAST_SEARCH_RESULT, 0);
            updateValues.put(WtaContract.ShowEntry.COLUMN_SEARCH_SCORE, 0.0);
            mContext.getContentResolver().update(WtaContract.ShowEntry.CONTENT_URI, updateValues,
                    null, null);

            Utility.synchronizeShowsByKeywordsData(mContext, LOG_TAG, traktService, mSearchText, year);

            Log.d(LOG_TAG, "AsyncTask correctly ended");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error: " + e.getMessage(), e);
            result = e.getMessage();
        }

        return result;
    }

    @Override
    protected void onPostExecute(String result) {
        if (mNewActivity) {
            Intent intent = new Intent(mContext, SearchResultsActivity.class);
            intent.putExtra(Utility.SEARCH_KEYWORDS_EXTRA_KEY, mSearchText);
            mContext.startActivity(intent);
        }
    }
}
