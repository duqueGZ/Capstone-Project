package com.nanodegree.android.watchthemall.network;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.nanodegree.android.watchthemall.api.trakt.TraktService;
import com.nanodegree.android.watchthemall.util.Utility;

import java.util.Vector;

/**
 * An Async Task in order to retrieve Trakt shows data according to provided keywords search results
 */
public class SearchByKeywordsAsyncTask extends AsyncTask<Object, Void, String> {

    private final String LOG_TAG = SearchByKeywordsAsyncTask.class.getSimpleName();

    private static final String OK_RESULT = "OK";

    private Context mContext;

    public SearchByKeywordsAsyncTask(Context context) {
        mContext = context;
    }

    @Override
    protected String doInBackground(Object... params) {

        String result = SearchByKeywordsAsyncTask.OK_RESULT;
        String keywords = (String) params[0];
        Integer year = (Integer) params[1];
        if ((keywords==null) || (keywords.isEmpty())) {
            return null;
        }

        try {
            TraktService traktService = Utility.getTraktService();

            Utility.synchronizeGenresData(mContext, LOG_TAG, traktService);
            Vector<Integer> shows =
                    Utility.synchronizeShowsByKeywordsData(mContext, LOG_TAG, traktService, keywords, year);
            for (Integer showId : shows) {
                Utility.synchronizeShowComments(mContext, LOG_TAG, traktService, showId);
                Utility.synchronizeShowPeople(mContext, LOG_TAG, traktService, showId);
                Utility.synchronizeShowSeasons(mContext, LOG_TAG, traktService, showId);
            }

            Log.d(LOG_TAG, "Trakt sync correctly ended");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error: " + e.getMessage(), e);
            result = e.getMessage();
        }

        return result;
    }

    @Override
    protected void onPostExecute(String result) {
        //TODO: update UI and stop progress spinner
    }
}
