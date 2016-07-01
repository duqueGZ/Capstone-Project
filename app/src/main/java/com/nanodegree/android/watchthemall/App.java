package com.nanodegree.android.watchthemall;

import android.app.Application;

import com.bumptech.glide.request.target.ViewTarget;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Logger;
import com.google.android.gms.analytics.Tracker;

/**
 * Custom application context class.
 * Created as a workaround to solve Glide problems with ViewHolders and 'setTag' (used in
 * WatchThemAll app in custom CursorAdapters)
 * See: http://stackoverflow.com/questions/34833627/error-you-must-not-call-settag-on-a-view-glide-is-targeting-when-use-glide/35096552#35096552
 * Also contains Analytics tracker single instance (mTracker)
 */
public class App extends Application {

    public Tracker mTracker;

    @Override
    public void onCreate() {
        super.onCreate();
        ViewTarget.setTagId(R.id.glide_tag);
    }

    // Get the tracker associated with this app
    public void startTracking() {

        // Initialize an Analytics tracker using a Google Analytics property ID.

        // Does the Tracker already exist?
        // If not, create it

        if (mTracker == null) {
            GoogleAnalytics ga = GoogleAnalytics.getInstance(this);

            // Get the config data for the tracker
            mTracker = ga.newTracker(R.xml.track_app);

            // Enable tracking of activities
            ga.enableAutoActivityReports(this);

            // Set the log level to verbose.
            ga.getLogger().setLogLevel(Logger.LogLevel.VERBOSE);
        }
    }


    public Tracker getTracker() {
        // Make sure the tracker exists
        startTracking();

        // Then return the tracker
        return mTracker;
    }
}
