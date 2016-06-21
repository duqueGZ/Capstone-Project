package com.nanodegree.android.watchthemall;

import android.app.Application;

import com.bumptech.glide.request.target.ViewTarget;

/**
 * Custom application context class.
 * Created as a workaround to solve Glide problems with ViewHolders and 'setTag' (used in
 * WatchThemAll app in custom CursorAdapters)
 * See: http://stackoverflow.com/questions/34833627/error-you-must-not-call-settag-on-a-view-glide-is-targeting-when-use-glide/35096552#35096552
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ViewTarget.setTagId(R.id.glide_tag);
    }
}
