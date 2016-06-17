package com.nanodegree.android.watchthemall.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.nanodegree.android.watchthemall.BuildConfig;
import com.nanodegree.android.watchthemall.R;
import com.nanodegree.android.watchthemall.api.trakt.AirInfo;
import com.nanodegree.android.watchthemall.api.trakt.Comment;
import com.nanodegree.android.watchthemall.api.trakt.Episode;
import com.nanodegree.android.watchthemall.api.trakt.Genre;
import com.nanodegree.android.watchthemall.api.trakt.ImageList;
import com.nanodegree.android.watchthemall.api.trakt.Role;
import com.nanodegree.android.watchthemall.api.trakt.Season;
import com.nanodegree.android.watchthemall.api.trakt.Show;
import com.nanodegree.android.watchthemall.api.trakt.TraktService;
import com.nanodegree.android.watchthemall.api.trakt.User;
import com.nanodegree.android.watchthemall.data.WtaContract;
import com.nanodegree.android.watchthemall.data.WtaProvider;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * A Sync Adapter for retrieving Trakt Popular Shows data
 */
public class WtaSyncAdapter extends AbstractThreadedSyncAdapter {


    public final String LOG_TAG = WtaSyncAdapter.class.getSimpleName();

    // Interval at which to sync with the Trakt info, in seconds.
    // 60 seconds (1 minute) * 60 * 6 = 6 hours
    private static final int SYNC_INTERVAL = 60 * 60 * 6;
    private static final int SYNC_FLEXTIME = SYNC_INTERVAL/3;

    private static final String TRAKT_API_KEY_HEADER = "trakt-api-key";
    private static final String TRAKT_API_BASE_URL = "https://api-v2launch.trakt.tv";

    private static final String UNKNOWN_USER = "Unknown user";
    private static final String UNKNOWN_AIR_DAY = "Unknown air day";

    public WtaSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new Interceptor() {
                        @Override
                        public okhttp3.Response intercept(Chain chain) throws IOException {
                            Request request = chain.request().newBuilder()
                                    .header(TRAKT_API_KEY_HEADER,
                                            BuildConfig.TRAKT_API_KEY).build();
                            return chain.proceed(request);
                        }
                    }).build();
            Retrofit retrofit = new Retrofit.Builder().baseUrl(TRAKT_API_BASE_URL)
                    .client(client).addConverterFactory(GsonConverterFactory.create()).build();
            TraktService traktService = retrofit.create(TraktService.class);

            synchronizeGenresData(traktService);
            Vector<Integer> shows = synchronizePopularShowsData(traktService);
            for (Integer showId : shows) {
                synchronizeShowComments(traktService, showId);
                synchronizeShowPeople(traktService, showId);
                synchronizeShowSeasons(traktService, showId);
            }

            Log.d(LOG_TAG, "Trakt sync correctly ended");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to have the sync adapter sync immediately
     * @param context The context used to access the account service
     */
    public static void syncImmediately(Context context) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        ContentResolver.requestSync(getSyncAccount(context), WtaContract.CONTENT_AUTHORITY, bundle);
    }

    /**
     * Helper method to get the fake account to be used with SyncAdapter, or make a new one
     * if the fake account doesn't exist yet.  If we make a new account, we call the
     * onAccountCreated method so we can initialize things.
     *
     * @param context The context used to access the account service
     * @return a fake account.
     */
    public static Account getSyncAccount(Context context) {
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the account type and default account
        Account newAccount = new Account(
                context.getString(R.string.app_name), context.getString(R.string.sync_account_type));

        // If the password doesn't exist, the account doesn't exist
        if ( null == accountManager.getPassword(newAccount) ) {

        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
            if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
                return null;
            }
            /*
             * If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call ContentResolver.setIsSyncable(account, AUTHORITY, 1)
             * here.
             */

            onAccountCreated(newAccount, context);
        }
        return newAccount;
    }

    /**
     * Helper method to schedule the sync adapter periodic execution
     */
    public static void configurePeriodicSync(Context context, int syncInterval, int flexTime) {
        Account account = getSyncAccount(context);
        String authority = WtaContract.CONTENT_AUTHORITY;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // we can enable inexact timers in our periodic sync
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(syncInterval, flexTime).
                    setSyncAdapter(account, authority).
                    setExtras(new Bundle()).build();
            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account,
                    authority, new Bundle(), syncInterval);
        }
    }

    public static void initializeSyncAdapter(Context context) {
        getSyncAccount(context);
    }

    private static void onAccountCreated(Account newAccount, Context context) {
        /*
         * Since we've created an account
         */
        WtaSyncAdapter.configurePeriodicSync(context, SYNC_INTERVAL, SYNC_FLEXTIME);

        /*
         * Without calling setSyncAutomatically, our periodic sync will not be enabled.
         */
        ContentResolver.setSyncAutomatically(newAccount, WtaContract.CONTENT_AUTHORITY, true);

        /*
         * Finally, let's do a sync to get things started
         */
        syncImmediately(context);
    }

    private void synchronizeGenresData(TraktService traktService) throws IOException {

        Call<List<Genre>> showGenres = traktService.showGenres();
        Response<List<Genre>> genresResponse = showGenres.execute();

        if (genresResponse.isSuccessful()) {
            List<Genre> receivedGenres = genresResponse.body();
            Vector<String> receivedIds = new Vector<>(receivedGenres.size());
            Vector<ContentValues> genresValues = new Vector<>(receivedGenres.size());
            for (Genre genre: receivedGenres) {
                ContentValues values = new ContentValues();
                values.put(WtaContract.GenreEntry._ID, genre.getSlug());
                values.put(WtaContract.GenreEntry.COLUMN_NAME, genre.getName());

                receivedIds.add(genre.getSlug());
                genresValues.add(values);
            }

            // Add to DB
            if (genresValues.size() > 0) {
                ContentValues[] insertValues = new ContentValues[genresValues.size()];
                genresValues.toArray(insertValues);
                getContext().getContentResolver()
                        .bulkInsert(WtaContract.GenreEntry.CONTENT_URI, insertValues);
            }

            // Remove from DB those values not retrieved now (as they are no valid anymore)
            getContext().getContentResolver()
                    .delete(WtaContract.GenreEntry.CONTENT_URI, WtaProvider.sGenresByIdListSelection,
                            new String[] {TextUtils.join(",", receivedIds)});

            Log.d(LOG_TAG, "Genres synchronization correctly ended");
        } else {
            Log.e(LOG_TAG, "Error occurred calling showGenres API endpoint: " + genresResponse.message());
        }
    }

    private Vector<Integer> synchronizePopularShowsData(TraktService traktService) throws IOException {

        Vector<Integer> receivedIds = new Vector<>();
        Call<List<Show>> popularShows = traktService.popularShows();
        Response<List<Show>> showsResponse = popularShows.execute();

        Date now = new Date();
        if (showsResponse.isSuccessful()) {
            List<Show> receivedShows = showsResponse.body();
            receivedIds = new Vector<>(receivedShows.size());
            Vector<ContentValues> showsValues = new Vector<>(receivedShows.size());
            Vector<ContentValues> showGenreValues = new Vector<>();
            for (Show show: receivedShows) {
                ContentValues values = new ContentValues();
                values.put(WtaContract.ShowEntry._ID, show.getIds().getTrakt());
                values.put(WtaContract.ShowEntry.COLUMN_TITLE, show.getTitle());
                values.put(WtaContract.ShowEntry.COLUMN_OVERVIEW, show.getOverview());
                values.put(WtaContract.ShowEntry.COLUMN_POSTER_PATH, checkForPosterNullValues(show.getImages(), null));
                values.put(WtaContract.ShowEntry.COLUMN_STATUS, show.getStatus());
                values.put(WtaContract.ShowEntry.COLUMN_YEAR, show.getYear());
                values.put(WtaContract.ShowEntry.COLUMN_FIRST_AIRED, checkForNullValues(show.getFirst_aired(), null));
                values.put(WtaContract.ShowEntry.COLUMN_AIR_DAY, checkForNullValues(show.getAirs(), UNKNOWN_AIR_DAY));
                values.put(WtaContract.ShowEntry.COLUMN_RUNTIME, show.getRuntime());
                values.put(WtaContract.ShowEntry.COLUMN_NETWORK, show.getNetwork());
                values.put(WtaContract.ShowEntry.COLUMN_COUNTRY, show.getCountry());
                values.put(WtaContract.ShowEntry.COLUMN_HOMEPAGE, show.getHomepage());
                values.put(WtaContract.ShowEntry.COLUMN_RATING, show.getRating());
                values.put(WtaContract.ShowEntry.COLUMN_VOTE_COUNT, show.getVotes());
                values.put(WtaContract.ShowEntry.COLUMN_LANGUAGE, show.getLanguage());
                values.put(WtaContract.ShowEntry.COLUMN_AIRED_EPISODES, show.getAired_episodes());
                values.put(WtaContract.ShowEntry.COLUMN_WATCHING, 0);
                values.put(WtaContract.ShowEntry.COLUMN_WATCHED, 0);
                values.put(WtaContract.ShowEntry.COLUMN_WATCHLIST, 0);
                values.put(WtaContract.ShowEntry.COLUMN_WTA_UPDATE_DATE, now.getTime());

                receivedIds.add(show.getIds().getTrakt());
                showsValues.add(values);

                List<String> showGenres = show.getGenres();
                for (String genre : showGenres) {
                    ContentValues relation = new ContentValues();
                    relation.put(WtaContract.ShowEntry.COLUMN_SHOW_ID, show.getIds().getTrakt());
                    relation.put(WtaContract.ShowEntry.COLUMN_GENRE_ID, genre);

                    showGenreValues.add(relation);
                }
            }

            // Add to DB
            if (showsValues.size() > 0) {
                ContentValues[] insertValues = new ContentValues[showsValues.size()];
                showsValues.toArray(insertValues);
                getContext().getContentResolver()
                        .bulkInsert(WtaContract.ShowEntry.CONTENT_URI, insertValues);
            }

            // Add to DB show-genre relations
            if (showGenreValues.size() > 0) {
                ContentValues[] insertRelationValues = new ContentValues[showGenreValues.size()];
                showGenreValues.toArray(insertRelationValues);
                getContext().getContentResolver()
                        .bulkInsert(WtaContract.ShowEntry.SHOW_GENRE_CONTENT_URI, insertRelationValues);
            }

            // Remove from DB old data (more than one month without being updated)
            // This way, we avoid building up an endless history
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MONTH, -1);
            getContext().getContentResolver()
                    .delete(WtaContract.ShowEntry.CONTENT_URI, WtaProvider.sShowsByUpdateDateSelection,
                    new String[] {Long.toString(cal.getTimeInMillis())});

            Log.d(LOG_TAG, "Popular shows synchronization correctly ended");
        } else {
            Log.e(LOG_TAG, "Error occurred calling popularShows API endpoint: " + showsResponse.message());
        }

        return receivedIds;
    }

    private void synchronizeShowComments(TraktService traktService, Integer showId) throws IOException {
        Call<List<Comment>> showComments = traktService.showComments(showId);
        Response<List<Comment>> commentsResponse = showComments.execute();

        if (commentsResponse.isSuccessful()) {
            List<Comment> receivedComments = commentsResponse.body();
            Vector<ContentValues> commentsValues = new Vector<>(receivedComments.size());
            processReceivedComments(receivedComments, commentsValues, showId);

            // Add to DB
            if (commentsValues.size() > 0) {
                ContentValues[] insertValues = new ContentValues[commentsValues.size()];
                commentsValues.toArray(insertValues);
                getContext().getContentResolver()
                        .bulkInsert(WtaContract.CommentEntry.CONTENT_URI, insertValues);
            }

            Log.d(LOG_TAG, "Show comments (for show " + showId + ") synchronization correctly ended");
        } else {
            Log.e(LOG_TAG, "Error occurred calling showComments API endpoint: " + commentsResponse.message());
        }
    }

    private void synchronizeShowPeople(TraktService traktService, Integer showId) throws IOException {
        Call<List<Role>> showPeople = traktService.showPeople(showId);
        Response<List<Role>> peopleResponse = showPeople.execute();

        if (peopleResponse.isSuccessful()) {
            List<Role> receivedPeople = peopleResponse.body();
            Vector<ContentValues> peopleValues = new Vector<>(receivedPeople.size());
            Vector<ContentValues> showPeopleValues = new Vector<>(receivedPeople.size());
            for (Role role: receivedPeople) {
                ContentValues values = new ContentValues();
                values.put(WtaContract.PersonEntry._ID, role.getPerson().getIds().getTrakt());
                values.put(WtaContract.PersonEntry.COLUMN_NAME, role.getPerson().getName());
                values.put(WtaContract.PersonEntry.COLUMN_HEADSHOT_PATH, checkForHeadshotNullValues(role.getPerson().getImages(), null));

                ContentValues relation = new ContentValues();
                relation.put(WtaContract.ShowEntry.COLUMN_SHOW_ID, showId);
                relation.put(WtaContract.ShowEntry.COLUMN_PERSON_ID, role.getPerson().getIds().getTrakt());
                relation.put(WtaContract.ShowEntry.COLUMN_CHARACTER, role.getCharacter());

                peopleValues.add(values);
                showPeopleValues.add(relation);
            }

            // Add to DB
            if (peopleValues.size() > 0) {
                ContentValues[] insertValues = new ContentValues[peopleValues.size()];
                peopleValues.toArray(insertValues);
                getContext().getContentResolver()
                        .bulkInsert(WtaContract.PersonEntry.CONTENT_URI, insertValues);
            }

            // Add to DB show-people relations
            if (showPeopleValues.size() > 0) {
                ContentValues[] insertRelationValues = new ContentValues[showPeopleValues.size()];
                showPeopleValues.toArray(insertRelationValues);
                getContext().getContentResolver()
                        .bulkInsert(WtaContract.ShowEntry.SHOW_PERSON_CONTENT_URI, insertRelationValues);
            }

            Log.d(LOG_TAG, "Show people (for show " + showId + ") synchronization correctly ended");
        } else {
            Log.e(LOG_TAG, "Error occurred calling showPeople API endpoint: " + peopleResponse.message());
        }
    }

    private void synchronizeShowSeasons(TraktService traktService, Integer showId) throws IOException {
        Call<List<Season>> showSeasons = traktService.seasonsSummary(showId);
        Response<List<Season>> seasonsResponse = showSeasons.execute();

        if (seasonsResponse.isSuccessful()) {
            List<Season> receivedSeasons = seasonsResponse.body();
            Vector<ContentValues> seasonsValues = new Vector<>(receivedSeasons.size());
            Vector<ContentValues> episodesValues = new Vector<>();
            Vector<ContentValues> commentsValues = new Vector<>();
            Vector<Integer> episodes;
            for (Season season: receivedSeasons) {
                ContentValues values = new ContentValues();
                values.put(WtaContract.SeasonEntry._ID, season.getIds().getTrakt());
                values.put(WtaContract.SeasonEntry.COLUMN_NUMBER, season.getNumber());
                values.put(WtaContract.SeasonEntry.COLUMN_EPISODE_COUNT, season.getEpisode_count());
                values.put(WtaContract.SeasonEntry.COLUMN_AIRED_EPISODES, season.getAired_episodes());
                values.put(WtaContract.SeasonEntry.COLUMN_FIRST_AIRED, checkForNullValues(season.getFirst_aired(), null));
                values.put(WtaContract.SeasonEntry.COLUMN_SHOW_ID, showId);

                seasonsValues.add(values);

                episodes = processSeasonEpisodes(season.getIds().getTrakt(), season.getEpisodes(), episodesValues);
                for (Integer episodeNumber : episodes) {
                    processEpisodeComments(traktService, showId, season.getNumber(), episodeNumber, commentsValues);
                }
            }

            // Add to DB seasons
            if (seasonsValues.size() > 0) {
                ContentValues[] insertValues = new ContentValues[seasonsValues.size()];
                seasonsValues.toArray(insertValues);
                getContext().getContentResolver()
                        .bulkInsert(WtaContract.SeasonEntry.CONTENT_URI, insertValues);
            }

            // Add to DB episodes
            if (episodesValues.size() > 0) {
                ContentValues[] insertEpisodeValues = new ContentValues[episodesValues.size()];
                episodesValues.toArray(insertEpisodeValues);
                getContext().getContentResolver()
                        .bulkInsert(WtaContract.EpisodeEntry.CONTENT_URI, insertEpisodeValues);
            }

            // Add to DB comments
            if (commentsValues.size() > 0) {
                ContentValues[] insertCommentValues = new ContentValues[commentsValues.size()];
                commentsValues.toArray(insertCommentValues);
                getContext().getContentResolver()
                        .bulkInsert(WtaContract.CommentEntry.CONTENT_URI, insertCommentValues);
            }

            Log.d(LOG_TAG, "Show seasons (for show " + showId + ") synchronization correctly ended");
        } else {
            Log.e(LOG_TAG, "Error occurred calling seasonsSummary API endpoint: " + seasonsResponse.message());
        }
    }

    private Vector<Integer> processSeasonEpisodes(Integer seasonId, List<Episode> episodes, Vector<ContentValues> episodesValues) {
        Vector<Integer> receivedEpisodeNumbers = new Vector<>();

        if ((episodes!=null) && (!episodes.isEmpty())) {

            receivedEpisodeNumbers = new Vector<>(episodes.size());

            for (Episode episode : episodes) {
                ContentValues values = new ContentValues();
                values.put(WtaContract.EpisodeEntry._ID, episode.getIds().getTrakt());
                values.put(WtaContract.EpisodeEntry.COLUMN_NUMBER, episode.getNumber());
                values.put(WtaContract.EpisodeEntry.COLUMN_TITLE, episode.getTitle());
                values.put(WtaContract.EpisodeEntry.COLUMN_OVERVIEW, episode.getOverview());
                values.put(WtaContract.EpisodeEntry.COLUMN_SCREENSHOT_PATH, checkForScreenshotNullValues(episode.getImages(), null));
                values.put(WtaContract.EpisodeEntry.COLUMN_FIRST_AIRED, checkForNullValues(episode.getFirst_aired(), null));
                values.put(WtaContract.EpisodeEntry.COLUMN_RATING, episode.getRating());
                values.put(WtaContract.EpisodeEntry.COLUMN_VOTE_COUNT, episode.getVotes());
                values.put(WtaContract.EpisodeEntry.COLUMN_WATCHLIST, 0);
                values.put(WtaContract.EpisodeEntry.COLUMN_SEASON_ID, seasonId);

                receivedEpisodeNumbers.add(episode.getNumber());
                episodesValues.add(values);
            }
            Log.d(LOG_TAG, "Season episodes (for season " + seasonId + ") processing correctly ended");
        }

        return receivedEpisodeNumbers;
    }

    private void processEpisodeComments(TraktService traktService, Integer showId, Integer seasonNumber,
                                        Integer episodeNumber, Vector<ContentValues> commentsValues) throws IOException {
        Call<List<Comment>> episodeComments = traktService.episodeComments(showId, seasonNumber, episodeNumber);
        Response<List<Comment>> commentsResponse = episodeComments.execute();

        if (commentsResponse.isSuccessful()) {
            List<Comment> receivedComments = commentsResponse.body();
            processReceivedComments(receivedComments, commentsValues, showId);

            Log.d(LOG_TAG, "Episode comments (for show " + showId + ", season " + seasonNumber +
                    " and episode " + episodeNumber + ") processing correctly ended");
        } else {
            Log.e(LOG_TAG, "Error occurred calling episodeComments API endpoint: " + commentsResponse.message());
        }
    }

    private void processReceivedComments(List<Comment> receivedComments, Vector<ContentValues> commentsValues, Integer showId) {
        for (Comment comment: receivedComments) {
            ContentValues values = new ContentValues();
            values.put(WtaContract.CommentEntry._ID, comment.getId());
            values.put(WtaContract.CommentEntry.COLUMN_CREATED_AT, checkForNullValues(comment.getCreated_at(), null));
            values.put(WtaContract.CommentEntry.COLUMN_CONTENT, comment.getComment());
            values.put(WtaContract.CommentEntry.COLUMN_SPOILER, comment.isSpoiler());
            values.put(WtaContract.CommentEntry.COLUMN_REVIEW, comment.isReview());
            values.put(WtaContract.CommentEntry.COLUMN_LIKES, comment.getLikes());
            values.put(WtaContract.CommentEntry.COLUMN_USER, checkForNullValues(comment.getUser(), UNKNOWN_USER));
            values.put(WtaContract.CommentEntry.COLUMN_SHOW_ID, showId);

            commentsValues.add(values);
        }
    }

    private Long checkForNullValues(Date date, Long defaultValue) {
        if (date != null) {
            return date.getTime();
        }
        return defaultValue;
    }

    private String checkForNullValues(AirInfo airInfo, String defaultValue) {
        if (airInfo != null) {
            return airInfo.getDay() + ", " + airInfo.getTime() + " (" + airInfo.getTimezone() + ")";
        }
        return defaultValue;
    }

    private String checkForNullValues(User user, String defaultValue) {
        if (user != null){
            return user.getUsername();
        }
        return defaultValue;
    }

    private String checkForPosterNullValues(ImageList imageList, String defaultValue) {
        if ((imageList != null) && (imageList.getPoster()!=null)){
            return imageList.getPoster().getFull();
        }
        return defaultValue;
    }

    private String checkForHeadshotNullValues(ImageList imageList, String defaultValue) {
        if ((imageList != null) && (imageList.getHeadshot()!=null)){
            return imageList.getHeadshot().getFull();
        }
        return defaultValue;
    }

    private String checkForScreenshotNullValues(ImageList imageList, String defaultValue) {
        if ((imageList != null) && (imageList.getScreenshot()!=null)){
            return imageList.getScreenshot().getFull();
        }
        return defaultValue;
    }
}