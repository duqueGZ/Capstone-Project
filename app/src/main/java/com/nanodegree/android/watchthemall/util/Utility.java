package com.nanodegree.android.watchthemall.util;

import android.content.ContentValues;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.nanodegree.android.watchthemall.BuildConfig;
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

public class Utility {

    private Utility() {
        // In order to avoid instantiation for this utility class
    }

    //WatchThemAll always shows a maximum of MAX_SHOWS in search results screen
    public static final String MAX_SHOWS = "20";

    //MM/DD/YYYY HH24:MI:SS DateFormat pattern
    public static final String MONTH_DAY_YEAR_COMPLETE_HOUR_PATTERN = "MM/DD/YYYY HH24:MI:SS";

    //Trakt API stuff
    private static final String TRAKT_API_KEY_HEADER = "trakt-api-key";
    private static final String TRAKT_API_BASE_URL = "https://api-v2launch.trakt.tv";
    private static final String UNKNOWN_USER = "Unknown user";
    private static final String UNKNOWN_AIR_DAY = "Unknown air day";
    private static final String POPULAR_MOVIES_ENDPOINT = "popularShows";
    private static final String SEARCH_MOVIES_BY_KEYWORDS_ENDPOINT = "searchShowsByKeywords";

    public static TraktService getTraktService() {

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public okhttp3.Response intercept(Chain chain) throws IOException {
                        Request request = chain.request().newBuilder()
                                .header(Utility.TRAKT_API_KEY_HEADER,
                                        BuildConfig.TRAKT_API_KEY).build();
                        return chain.proceed(request);
                    }
                }).build();
        Retrofit retrofit = new Retrofit.Builder().baseUrl(Utility.TRAKT_API_BASE_URL)
                .client(client).addConverterFactory(GsonConverterFactory.create()).build();

        return retrofit.create(TraktService.class);
    }

    public static void synchronizeGenresData(Context context, String logTag, TraktService traktService) throws IOException {

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
                context.getContentResolver()
                        .bulkInsert(WtaContract.GenreEntry.CONTENT_URI, insertValues);
            }

            // Remove from DB those values not retrieved now (as they are no valid anymore)
            context.getContentResolver()
                    .delete(WtaContract.GenreEntry.CONTENT_URI, WtaProvider.sGenresByIdListSelection,
                            new String[] {TextUtils.join(",", receivedIds)});

            Log.d(logTag, "Genres synchronization correctly ended");
        } else {
            Log.e(logTag, "Error occurred calling showGenres API endpoint: " + genresResponse.message());
        }
    }

    public static Vector<Integer> synchronizeShowsByKeywordsData(Context context, String logTag, TraktService traktService,
                                                                 String keywords, Integer year) throws IOException {

        Vector<Integer> receivedIds = new Vector<>();
        Call<List<Show>> searchShows = traktService.searchShowsByKeywords(keywords, year);
        Response<List<Show>> showsResponse = searchShows.execute();

        Utility.processReceivedShows(context, logTag, receivedIds, showsResponse, Utility.SEARCH_MOVIES_BY_KEYWORDS_ENDPOINT);

        return receivedIds;
    }

    public static Vector<Integer> synchronizePopularShowsData(Context context, String logTag, TraktService traktService) throws IOException {

        Vector<Integer> receivedIds = new Vector<>();
        Call<List<Show>> popularShows = traktService.popularShows();
        Response<List<Show>> showsResponse = popularShows.execute();

        Utility.processReceivedShows(context, logTag, receivedIds, showsResponse, Utility.POPULAR_MOVIES_ENDPOINT);

        return receivedIds;
    }

    public static void synchronizeShowComments(Context context, String logTag, TraktService traktService, Integer showId) throws IOException {
        Call<List<Comment>> showComments = traktService.showComments(showId);
        Response<List<Comment>> commentsResponse = showComments.execute();

        if (commentsResponse.isSuccessful()) {
            List<Comment> receivedComments = commentsResponse.body();
            Vector<ContentValues> commentsValues = new Vector<>(receivedComments.size());
            Utility.processReceivedComments(receivedComments, commentsValues, showId);

            // Add to DB
            if (commentsValues.size() > 0) {
                ContentValues[] insertValues = new ContentValues[commentsValues.size()];
                commentsValues.toArray(insertValues);
                context.getContentResolver()
                        .bulkInsert(WtaContract.CommentEntry.CONTENT_URI, insertValues);
            }

            Log.d(logTag, "Show comments (for show " + showId + ") synchronization correctly ended");
        } else {
            Log.e(logTag, "Error occurred calling showComments API endpoint: " + commentsResponse.message());
        }
    }

    public static void synchronizeShowPeople(Context context, String logTag, TraktService traktService, Integer showId) throws IOException {
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
                values.put(WtaContract.PersonEntry.COLUMN_HEADSHOT_PATH, Utility.checkForHeadshotNullValues(role.getPerson().getImages(), null));

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
                context.getContentResolver()
                        .bulkInsert(WtaContract.PersonEntry.CONTENT_URI, insertValues);
            }

            // Add to DB show-people relations
            if (showPeopleValues.size() > 0) {
                ContentValues[] insertRelationValues = new ContentValues[showPeopleValues.size()];
                showPeopleValues.toArray(insertRelationValues);
                context.getContentResolver()
                        .bulkInsert(WtaContract.ShowEntry.SHOW_PERSON_CONTENT_URI, insertRelationValues);
            }

            Log.d(logTag, "Show people (for show " + showId + ") synchronization correctly ended");
        } else {
            Log.e(logTag, "Error occurred calling showPeople API endpoint: " + peopleResponse.message());
        }
    }

    public static void synchronizeShowSeasons(Context context, String logTag, TraktService traktService, Integer showId) throws IOException {
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
                values.put(WtaContract.SeasonEntry.COLUMN_FIRST_AIRED, Utility.checkForNullValues(season.getFirst_aired(), null));
                values.put(WtaContract.SeasonEntry.COLUMN_SHOW_ID, showId);

                seasonsValues.add(values);

                episodes = Utility.processSeasonEpisodes(logTag, season.getIds().getTrakt(), season.getEpisodes(), episodesValues);
                for (Integer episodeNumber : episodes) {
                    Utility.processEpisodeComments(logTag, traktService, showId, season.getNumber(), episodeNumber, commentsValues);
                }
            }

            // Add to DB seasons
            if (seasonsValues.size() > 0) {
                ContentValues[] insertValues = new ContentValues[seasonsValues.size()];
                seasonsValues.toArray(insertValues);
                context.getContentResolver()
                        .bulkInsert(WtaContract.SeasonEntry.CONTENT_URI, insertValues);
            }

            // Add to DB episodes
            if (episodesValues.size() > 0) {
                ContentValues[] insertEpisodeValues = new ContentValues[episodesValues.size()];
                episodesValues.toArray(insertEpisodeValues);
                context.getContentResolver()
                        .bulkInsert(WtaContract.EpisodeEntry.CONTENT_URI, insertEpisodeValues);
            }

            // Add to DB comments
            if (commentsValues.size() > 0) {
                ContentValues[] insertCommentValues = new ContentValues[commentsValues.size()];
                commentsValues.toArray(insertCommentValues);
                context.getContentResolver()
                        .bulkInsert(WtaContract.CommentEntry.CONTENT_URI, insertCommentValues);
            }

            Log.d(logTag, "Show seasons (for show " + showId + ") synchronization correctly ended");
        } else {
            Log.e(logTag, "Error occurred calling seasonsSummary API endpoint: " + seasonsResponse.message());
        }
    }

    private static Vector<Integer> processSeasonEpisodes(String logTag, Integer seasonId, List<Episode> episodes, Vector<ContentValues> episodesValues) {
        Vector<Integer> receivedEpisodeNumbers = new Vector<>();

        if ((episodes!=null) && (!episodes.isEmpty())) {

            receivedEpisodeNumbers = new Vector<>(episodes.size());

            for (Episode episode : episodes) {
                ContentValues values = new ContentValues();
                values.put(WtaContract.EpisodeEntry._ID, episode.getIds().getTrakt());
                values.put(WtaContract.EpisodeEntry.COLUMN_NUMBER, episode.getNumber());
                values.put(WtaContract.EpisodeEntry.COLUMN_TITLE, episode.getTitle());
                values.put(WtaContract.EpisodeEntry.COLUMN_OVERVIEW, episode.getOverview());
                values.put(WtaContract.EpisodeEntry.COLUMN_SCREENSHOT_PATH, Utility.checkForScreenshotNullValues(episode.getImages(), null));
                values.put(WtaContract.EpisodeEntry.COLUMN_FIRST_AIRED, Utility.checkForNullValues(episode.getFirst_aired(), null));
                values.put(WtaContract.EpisodeEntry.COLUMN_RATING, episode.getRating());
                values.put(WtaContract.EpisodeEntry.COLUMN_VOTE_COUNT, episode.getVotes());
                values.put(WtaContract.EpisodeEntry.COLUMN_WATCHLIST, 0);
                values.put(WtaContract.EpisodeEntry.COLUMN_SEASON_ID, seasonId);

                receivedEpisodeNumbers.add(episode.getNumber());
                episodesValues.add(values);
            }
            Log.d(logTag, "Season episodes (for season " + seasonId + ") processing correctly ended");
        }

        return receivedEpisodeNumbers;
    }

    private static void processEpisodeComments(String logTag, TraktService traktService, Integer showId, Integer seasonNumber,
                                        Integer episodeNumber, Vector<ContentValues> commentsValues) throws IOException {
        Call<List<Comment>> episodeComments = traktService.episodeComments(showId, seasonNumber, episodeNumber);
        Response<List<Comment>> commentsResponse = episodeComments.execute();

        if (commentsResponse.isSuccessful()) {
            List<Comment> receivedComments = commentsResponse.body();
            Utility.processReceivedComments(receivedComments, commentsValues, showId);

            Log.d(logTag, "Episode comments (for show " + showId + ", season " + seasonNumber +
                    " and episode " + episodeNumber + ") processing correctly ended");
        } else {
            Log.e(logTag, "Error occurred calling episodeComments API endpoint: " + commentsResponse.message());
        }
    }

    private static void processReceivedShows(Context context, String logTag, Vector<Integer> receivedIds, Response<List<Show>> showsResponse, String calledEndpoint) {

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
                values.put(WtaContract.ShowEntry.COLUMN_POSTER_PATH, Utility.checkForPosterNullValues(show.getImages(), null));
                values.put(WtaContract.ShowEntry.COLUMN_STATUS, show.getStatus());
                values.put(WtaContract.ShowEntry.COLUMN_YEAR, show.getYear());
                values.put(WtaContract.ShowEntry.COLUMN_FIRST_AIRED, Utility.checkForNullValues(show.getFirst_aired(), null));
                values.put(WtaContract.ShowEntry.COLUMN_AIR_DAY, Utility.checkForNullValues(show.getAirs(), UNKNOWN_AIR_DAY));
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
                context.getContentResolver()
                        .bulkInsert(WtaContract.ShowEntry.CONTENT_URI, insertValues);
            }

            // Add to DB show-genre relations
            if (showGenreValues.size() > 0) {
                ContentValues[] insertRelationValues = new ContentValues[showGenreValues.size()];
                showGenreValues.toArray(insertRelationValues);
                context.getContentResolver()
                        .bulkInsert(WtaContract.ShowEntry.SHOW_GENRE_CONTENT_URI, insertRelationValues);
            }

            // Remove from DB old data (more than one month without being updated)
            // This way, we avoid building up an endless history
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MONTH, -1);
            context.getContentResolver()
                    .delete(WtaContract.ShowEntry.CONTENT_URI, WtaProvider.sShowsByUpdateDateSelection,
                            new String[] {Long.toString(cal.getTimeInMillis())});

            Log.d(logTag, "Popular shows synchronization correctly ended");
        } else {
            Log.e(logTag, "Error occurred calling " + calledEndpoint + " API endpoint: " + showsResponse.message());
        }
    }

    private static void processReceivedComments(List<Comment> receivedComments, Vector<ContentValues> commentsValues, Integer showId) {
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

    private static Long checkForNullValues(Date date, Long defaultValue) {
        if (date != null) {
            return date.getTime();
        }
        return defaultValue;
    }

    private static String checkForNullValues(AirInfo airInfo, String defaultValue) {
        if (airInfo != null) {
            return airInfo.getDay() + ", " + airInfo.getTime() + " (" + airInfo.getTimezone() + ")";
        }
        return defaultValue;
    }

    private static String checkForNullValues(User user, String defaultValue) {
        if (user != null){
            return user.getUsername();
        }
        return defaultValue;
    }

    private static String checkForPosterNullValues(ImageList imageList, String defaultValue) {
        if ((imageList != null) && (imageList.getPoster()!=null)){
            return imageList.getPoster().getFull();
        }
        return defaultValue;
    }

    private static String checkForHeadshotNullValues(ImageList imageList, String defaultValue) {
        if ((imageList != null) && (imageList.getHeadshot()!=null)){
            return imageList.getHeadshot().getFull();
        }
        return defaultValue;
    }

    private static String checkForScreenshotNullValues(ImageList imageList, String defaultValue) {
        if ((imageList != null) && (imageList.getScreenshot()!=null)){
            return imageList.getScreenshot().getFull();
        }
        return defaultValue;
    }
}
