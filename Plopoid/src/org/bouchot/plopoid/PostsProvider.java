package org.bouchot.plopoid;

import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

/**
 * This class is responsible for storing and retrieving content from the DB.
 * It provides means for the {@link PostsUpdateService} to use the data.
 * 
 * @author lesensei
 */
public class PostsProvider extends ContentProvider {
  private static final String TAG = "PostsProvider";
  private static final String DATABASE_NAME = "posts.db";
  private static final int DATABASE_VERSION = 3;
  private static HashMap<String, String> mPostsProjectionMap;

  /*
   private static final String[] READ_POST_PROJECTION = new String[] {
    Posts._ID,
    Posts.COLUMN_NAME_BOARD,
    Posts.COLUMN_NAME_ID,
    Posts.COLUMN_NAME_TIME,
    Posts.COLUMN_NAME_INFO,
    Posts.COLUMN_NAME_LOGIN,
    Posts.COLUMN_NAME_MESSAGE,
  };

  private static final int READ_POST_BOARD_INDEX = 1;
  private static final int READ_POST_ID_INDEX = 2;
  private static final int READ_POST_TIME_INDEX = 3;
  private static final int READ_POST_INFO_INDEX = 4;
  private static final int READ_POST_LOGIN_INDEX = 5;
  private static final int READ_POST_MESSAGE_INDEX = 6;
   */

  public static final String AUTHORITY = "org.bouchot.plopoid.postsprovider";

  private static final int POSTS = 1;
  private static final int POST_ID = 2;
  private static final int POST_TIME = 3;
  private static final int POSTS_SINCE = 4;
  private static final int POST__ID = 5;
  private static final int POST_BOARD_LASTID = 6;
  private static final int POST_BOARD_CLEANUP = 7;

  private static final UriMatcher mUriMatcher;

  private DatabaseHelper mOpenHelper;

  static {
    // The matcher is used to know what was queried from the provider
    mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    // This is used to query all posts 
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS, POSTS);
    // This is used to query a post by its DB ID (not its remote ID)
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS + Posts.FULL_PATH_POST__ID, POST__ID);
    // This is used to query posts of a given board by their timestamp
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS + Posts.FULL_PATH_POST_TIME, POST_TIME);
    // This is used to query posts posted to a given board after another post (identified by its remote ID)
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS + Posts.FULL_PATH_POST_SINCE, POSTS_SINCE);
    // This is used to get the lastId for a given board
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS + Posts.FULL_PATH_POST_BOARD_LASTID, POST_BOARD_LASTID);
    // This is used to delete old posts from a given board
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS + Posts.FULL_PATH_POST_BOARD_CLEANUP, POST_BOARD_CLEANUP);
    // This is used to query a post of a given board by its remote ID
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS + Posts.FULL_PATH_POST_ID, POST_ID);

    // The projection map is used to choose what data is returned. Here, every column is.
    mPostsProjectionMap = new HashMap<String, String>();
    mPostsProjectionMap.put(Posts._ID, Posts._ID);
    mPostsProjectionMap.put(Posts.COLUMN_NAME_BOARD, Posts.COLUMN_NAME_BOARD);
    mPostsProjectionMap.put(Posts.COLUMN_NAME_ID, Posts.COLUMN_NAME_ID);
    mPostsProjectionMap.put(Posts.COLUMN_NAME_TIME, Posts.COLUMN_NAME_TIME);
    mPostsProjectionMap.put(Posts.COLUMN_NAME_INFO, Posts.COLUMN_NAME_INFO);
    mPostsProjectionMap.put(Posts.COLUMN_NAME_LOGIN, Posts.COLUMN_NAME_LOGIN);
    mPostsProjectionMap.put(Posts.COLUMN_NAME_MESSAGE, Posts.COLUMN_NAME_MESSAGE);
  }

  /**
   * This class is responsible for creating, upgrading and downgrading our DB.
   * On upgrade and downgrade, we simply drop the table and recreate it, since there
   * is no reason to hold on to data we can retrieve again.
   * 
   * @author lesensei
   */
  static class DatabaseHelper extends SQLiteOpenHelper {
    DatabaseHelper(Context context) {
      super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL("CREATE TABLE " + Posts.TABLE_NAME + " ("
          + Posts._ID + " INTEGER PRIMARY KEY,"
          + Posts.COLUMN_NAME_BOARD + " TEXT,"
          + Posts.COLUMN_NAME_ID + " LONG,"
          + Posts.COLUMN_NAME_TIME + " TEXT,"
          + Posts.COLUMN_NAME_INFO + " TEXT,"
          + Posts.COLUMN_NAME_LOGIN + " TEXT,"
          + Posts.COLUMN_NAME_MESSAGE + " TEXT,"
          + "UNIQUE(" + Posts.COLUMN_NAME_BOARD + "," + Posts.COLUMN_NAME_ID + ")"
          + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
          + newVersion + ", which will destroy all old data");

      db.execSQL("DROP TABLE IF EXISTS "+ Posts.TABLE_NAME);

      onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      Log.w(TAG, "Downgrading database from version " + oldVersion + " to "
          + newVersion + ", which will destroy all old data");

      db.execSQL("DROP TABLE IF EXISTS "+ Posts.TABLE_NAME);

      onCreate(db);
    }
  }

  @Override
  public boolean onCreate() {
    mOpenHelper = new DatabaseHelper(getContext());
    return true;
  }

  /**
   * This method queries the DB and returns a Cursor on the requested data.
   * 
   * @param uri The URI defining the type of request we're dealing with (see {@link Posts} URI patterns)
   * @param projection The projection map (the column names) wanted, if not using the default
   * @param selection The WHERE clause
   * @param selectionArgs The WHERE clause values (which will replace the '?'s in the selection param)
   * @param sortOrder The ORDER BY clause
   * @return A Cursor to the queried values
   * @throws IllegalArgumentException if the URI is not valid to query data
   */
  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    qb.setTables(Posts.TABLE_NAME);
    int matchedUri = mUriMatcher.match(uri);

    /*
     * All this matching stuff is used to avoid forcing SQL onto the rest of the application logic,
     * or for other apps querying this ContentProvider.
     * In our case, I think this is way overkill, but at least I'll have done ONE thing properly in this whole app.
     */
    switch (matchedUri) {
      // All posts
      case POSTS:
        qb.setProjectionMap(mPostsProjectionMap);
        break;
      // The post with the given DB ID
      case POST__ID:
        qb.setProjectionMap(mPostsProjectionMap);
        qb.appendWhere(Posts._ID + "=" + uri.getPathSegments().get(Posts.POST__ID_PATH_POSITION));
        break;
      // The post with the given remote ID on the given board
      case POST_ID:
        qb.setProjectionMap(mPostsProjectionMap);
        qb.appendWhere(Posts.COLUMN_NAME_BOARD + "='" + uri.getPathSegments().get(Posts.POST_ID_BOARD_PATH_POSITION) + "'" +
            " AND " + Posts.COLUMN_NAME_ID + "=" + uri.getPathSegments().get(Posts.POST_ID_ID_PATH_POSITION));
        break;
      // The posts with the given timestamp on the given board
      case POST_TIME:
        qb.setProjectionMap(mPostsProjectionMap);
        qb.appendWhere(Posts.COLUMN_NAME_BOARD + "='" + uri.getPathSegments().get(Posts.POST_OTHERS_BOARD_PATH_POSITION) + "'" +
            " AND " + Posts.COLUMN_NAME_TIME + "='" + uri.getPathSegments().get(Posts.POST_TIME_PATH_POSITION) + "'");
        break;
      // The posts posted to the given board after another post
      case POSTS_SINCE:
        qb.setProjectionMap(mPostsProjectionMap);
        qb.appendWhere(Posts.COLUMN_NAME_BOARD + "='" + uri.getPathSegments().get(Posts.POST_OTHERS_BOARD_PATH_POSITION) + "'" +
            " AND " + Posts.COLUMN_NAME_ID + ">" + uri.getPathSegments().get(Posts.POST_OTHERS_ID_PATH_POSITION));
        break;
      // The remote ID of the last post for the given board
      case POST_BOARD_LASTID:
        qb.setProjectionMap(mPostsProjectionMap);
        qb.appendWhere(Posts.COLUMN_NAME_BOARD + " = '" + uri.getPathSegments().get(Posts.POST_OTHERS_BOARD_PATH_POSITION) + "'");
        break;
      default:
        throw new IllegalArgumentException("Unknown URI " + uri);
    }

    String orderBy;
    String limit = null;
    if (matchedUri == POST_BOARD_LASTID) {
      // If we're looking for the last id, only fetch that one.
      orderBy = Posts.COLUMN_NAME_ID + " DESC";
      limit = "1";
    } else if (TextUtils.isEmpty(sortOrder)) {
      // Otherwise, order by ascending remote ID ...
      orderBy = Posts.DEFAULT_SORT_ORDER;
    } else {
      // ... except of course if specified otherwise by caller
      orderBy = sortOrder;
    }

    SQLiteDatabase db = mOpenHelper.getReadableDatabase();

    Cursor c = qb.query(
        db,            // The database to query
        projection,    // The columns to return from the query
        selection,     // The columns for the where clause
        selectionArgs, // The values for the where clause
        null,          // don't group the rows
        null,          // don't filter by row groups
        orderBy,       // The sort order
        limit);

    // Make sure the cursor is aware of any changes
    c.setNotificationUri(getContext().getContentResolver(), uri);
    return c;
  }

  @Override
  public String getType(Uri uri) {
    switch(mUriMatcher.match(uri)) {
      case POSTS:
      case POST_TIME:
      case POSTS_SINCE:
        return Posts.CONTENT_TYPE;

      case POST__ID:
      case POST_ID:
      case POST_BOARD_LASTID:
        return Posts.CONTENT_ITEM_TYPE;

      default:
        throw new IllegalArgumentException("Unknown URI "+ uri);
    }
  }

  /**
   * This is exclusively used to insert a new post in the DB.
   * 
   * @param uri {@link Posts#CONTENT_URI}
   * @param initialValues The values to insert (board, remote id, time and message are mandatory)
   * @return A {@link URI} (using the {@link Posts#CONTENT__ID_URI_PATTERN} pattern) to the inserted post
   * @throws IllegalArgumentException if a mandatory value is missing, or the uri parameter is incorrect
   * @throws SQLException if it fails to insert the values
   */
  @Override
  public Uri insert(Uri uri, ContentValues initialValues) {
    if (mUriMatcher.match(uri) != POSTS) {
      throw new IllegalArgumentException("Unknown URI " + uri);
    }

    if (initialValues == null) {
      throw new IllegalArgumentException("No values to insert");
    }

    if (!initialValues.containsKey(Posts.COLUMN_NAME_BOARD) ||
        !initialValues.containsKey(Posts.COLUMN_NAME_ID) ||
        !initialValues.containsKey(Posts.COLUMN_NAME_TIME) ||
        !initialValues.containsKey(Posts.COLUMN_NAME_MESSAGE)) {
      throw new IllegalArgumentException("Missing required value");
    }

    ContentValues values = new ContentValues(initialValues);

    // If no info and/or login field(s) are provided, we insert blank strings
    if (!values.containsKey(Posts.COLUMN_NAME_INFO)) {
      values.put(Posts.COLUMN_NAME_INFO, "");
    }
    if (!values.containsKey(Posts.COLUMN_NAME_LOGIN)) {
      values.put(Posts.COLUMN_NAME_LOGIN, "");
    }

    SQLiteDatabase db = mOpenHelper.getWritableDatabase();

    long rowId = db.insert(Posts.TABLE_NAME, null, values);

    if (rowId > 0) {
      Uri postUri = ContentUris.withAppendedId(Posts.CONTENT__ID_URI_BASE, rowId);
      // Notify the UI
      getContext().getContentResolver().notifyChange(postUri, null);
      //Log.d("PostsProvider", "Post " + rowId + " inserted successfully");
      return postUri;
    }

    throw new SQLException("Failed to insert row into " + uri);
  }

  /**
   * Same as {@link #insert(Uri, ContentValues)} except it can insert several posts at once.
   * It inserts them in a transaction, and the whole transaction fails for any exception below.
   * 
   * @param uri {@link Posts#CONTENT_URI}
   * @param initialValues An array of the values to be inserted
   * @return The number of inserted posts
   * @throws IllegalArgumentException if a mandatory value is missing from a post, or the uri parameter is incorrect
   * @throws SQLException if it fails to insert the values
   */
  @Override
  public int bulkInsert(Uri uri, ContentValues[] initialValues) {
    if (mUriMatcher.match(uri) != POSTS) {
      throw new IllegalArgumentException("Unknown URI " + uri);
    }

    if (initialValues == null || initialValues.length < 1) {
      throw new IllegalArgumentException("No values to insert");
    }

    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    int inserted = 0;

    DatabaseUtils.InsertHelper inserter = new DatabaseUtils.InsertHelper(db, Posts.TABLE_NAME);

    try {
      for (ContentValues v : initialValues) {
        if (v != null) {
          if (!v.containsKey(Posts.COLUMN_NAME_BOARD) ||
              !v.containsKey(Posts.COLUMN_NAME_ID) ||
              !v.containsKey(Posts.COLUMN_NAME_TIME) ||
              !v.containsKey(Posts.COLUMN_NAME_MESSAGE)) {
            throw new IllegalArgumentException("Missing required value");
          }

          // Do not modify the array we are iterating on
          ContentValues values = new ContentValues(v);

          // If no info and/or login field(s) are provided, we insert blank strings
          if (!values.containsKey(Posts.COLUMN_NAME_INFO)) {
            values.put(Posts.COLUMN_NAME_INFO, "");
          }
          if (!values.containsKey(Posts.COLUMN_NAME_LOGIN)) {
            values.put(Posts.COLUMN_NAME_LOGIN, "");
          }

          if (inserter.insert(values) >= 0) {
            inserted++;
          } else {
            throw new SQLException("Failed to insert row (" + v.toString() + ") into " + uri);
          }
        }
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      inserter.close();
    }

    // Notify the UI
    getContext().getContentResolver().notifyChange(PostsProvider.Posts.CONTENT_URI, null);
    return inserted;
  }

  /**
   * This method is used to delete posts. Its main use is to cleanup the DB from old posts.
   * 
   * @param uri One of {@link Posts#CONTENT_URI}, {@link Posts#CONTENT__ID_URI_PATTERN} or {@link Posts#CONTENT_CLEANUP_URI_PATTERN}
   * @param where Optional WHERE clause
   * @param whereArgs Values for the optional WHERE clause
   * @return The number of deleted posts
   * @throws IllegalArgumentException if the uri parameter is invalid
   */
  @Override
  public int delete(Uri uri, String where, String[] whereArgs) {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    String finalWhere;

    int count = -1;

    switch (mUriMatcher.match(uri)) {
      case POSTS:
        count = db.delete(Posts.TABLE_NAME, where, whereArgs);
        break;
      case POST__ID:
        finalWhere = Posts._ID + " = " + uri.getPathSegments().get(Posts.POST__ID_PATH_POSITION);
        if (!TextUtils.isEmpty(where)) {
          finalWhere = finalWhere + " AND " + where;
        }
        count = db.delete(Posts.TABLE_NAME, finalWhere, whereArgs);
        break;
      case POST_BOARD_CLEANUP:
        // When cleaning up, we first retrieve the smallest id of the last 150 posts for a board,
        // then delete anything with an ID smaller than that.
        String board = uri.getPathSegments().get(Posts.POST_OTHERS_BOARD_PATH_POSITION);
        Log.d("PostsProvider", "Cleaning up "+ board);
        String[] cols = { Posts.COLUMN_NAME_ID };
        String orderBy = Posts.COLUMN_NAME_ID + " DESC";
        String limit = "150"; //TODO: make this a static final somewhere
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(Posts.TABLE_NAME);
        qb.appendWhere(Posts.COLUMN_NAME_BOARD + "= '" + board + "'");
        Cursor c = qb.query(
            db,        // The database to query
            cols,      // The columns to return from the query
            where,     // The columns for the where clause
            whereArgs, // The values for the where clause
            null,      // don't group the rows
            null,      // don't filter by row groups
            orderBy,   // The sort order
            limit);
        if (c.moveToLast()) {
          long id = c.getLong(c.getColumnIndex(Posts.COLUMN_NAME_ID));
          finalWhere = Posts.COLUMN_NAME_ID + " < ?";
          String[] finalWhereArgs = { "" + id };
          count = db.delete(Posts.TABLE_NAME, finalWhere, finalWhereArgs);
        }
        c.close();
        break;
      default:
        throw new IllegalArgumentException("Unknown URI " + uri + " (found " + mUriMatcher.match(uri) + ")");
    }

    // Notify the UI
    getContext().getContentResolver().notifyChange(uri, null);

    return count;
  }

  @Override
  public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    int count;
    String finalWhere;

    switch (mUriMatcher.match(uri)) {
      case POSTS:
        count = db.update(Posts.TABLE_NAME, values, where, whereArgs);
        break;

      case POST__ID:
        finalWhere = Posts._ID + " = " + uri.getPathSegments().get(Posts.POST__ID_PATH_POSITION);

        if (where != null) {
          finalWhere = finalWhere + " AND " + where;
        }

        count = db.update(Posts.TABLE_NAME, values, finalWhere, whereArgs);
        break;

      default:
        throw new IllegalArgumentException("Unknown URI " + uri);
    }

    getContext().getContentResolver().notifyChange(uri, null);

    return count;
  }

  /**
   * This class is the definition of the "contract" between the {@link PostsProvider}
   * and the rest of the world. Use its finals to query the posts DB.
   * 
   * This implementation is certainly horrid, but it suits my needs /o\
   * 
   * @author lesensei
   */
  public static final class Posts implements BaseColumns {
    private Posts() {}

    public static final String TABLE_NAME = "posts";
    private static final String SCHEME = "content://";
    private static final String PATH_POST_ID_ARGS = "#";
    private static final String PATH_POST_BOARD_AND_ID_ARGS = "*/#";
    private static final String PATH_POST_BOARD_AND_TIME_ARGS = "*/#";
    private static final String PATH_POST_BOARD_ARGS = "*";
    private static final String PATH_POSTS = "posts";
    private static final String PATH_POST__ID = "/";
    private static final String FULL_PATH_POST__ID = PATH_POST__ID + PATH_POST_ID_ARGS;
    private static final String PATH_POST_ID = "/";
    private static final String FULL_PATH_POST_ID = PATH_POST_ID + PATH_POST_BOARD_AND_ID_ARGS;
    private static final String PATH_POST_TIME = "/time/";
    private static final String FULL_PATH_POST_TIME = PATH_POST_TIME + PATH_POST_BOARD_AND_TIME_ARGS;
    private static final String PATH_POST_SINCE = "/since/";
    private static final String FULL_PATH_POST_SINCE = PATH_POST_SINCE + PATH_POST_BOARD_AND_ID_ARGS;
    private static final String PATH_POST_BOARD_LASTID = "/lastid/";
    private static final String FULL_PATH_POST_BOARD_LASTID = PATH_POST_BOARD_LASTID + PATH_POST_BOARD_ARGS;
    private static final String PATH_POST_BOARD_CLEANUP = "/cleanup/";
    private static final String FULL_PATH_POST_BOARD_CLEANUP = PATH_POST_BOARD_CLEANUP + PATH_POST_BOARD_ARGS;
    public static final int POST__ID_PATH_POSITION = 1;
    public static final int POST_ID_BOARD_PATH_POSITION = 1;
    public static final int POST_ID_ID_PATH_POSITION = 2;
    public static final int POST_OTHERS_BOARD_PATH_POSITION = 2;
    public static final int POST_OTHERS_ID_PATH_POSITION = 3;
    public static final int POST_TIME_PATH_POSITION = 3;
    public static final String CONTENT_URI_STRING = SCHEME + AUTHORITY + "/" + PATH_POSTS;
    public static final Uri CONTENT_URI =  Uri.parse(CONTENT_URI_STRING);
    public static final Uri CONTENT__ID_URI_BASE = Uri.parse(CONTENT_URI_STRING + PATH_POST__ID);
    public static final Uri CONTENT__ID_URI_PATTERN = Uri.parse(CONTENT_URI_STRING + FULL_PATH_POST__ID);
    public static final Uri CONTENT_ID_URI_BASE = Uri.parse(CONTENT_URI_STRING + PATH_POST_ID);
    public static final Uri CONTENT_ID_URI_PATTERN = Uri.parse(CONTENT_URI_STRING + FULL_PATH_POST_ID);
    public static final Uri CONTENT_TIME_URI_BASE = Uri.parse(CONTENT_URI_STRING + PATH_POST_TIME);
    public static final Uri CONTENT_TIME_URI_PATTERN = Uri.parse(CONTENT_URI_STRING + FULL_PATH_POST_TIME);
    public static final Uri CONTENT_SINCE_URI_BASE = Uri.parse(CONTENT_URI_STRING + PATH_POST_SINCE);
    public static final Uri CONTENT_SINCE_URI_PATTERN = Uri.parse(CONTENT_URI_STRING + FULL_PATH_POST_SINCE);
    public static final Uri CONTENT_LASTID_URI_BASE = Uri.parse(CONTENT_URI_STRING + PATH_POST_BOARD_LASTID);
    public static final Uri CONTENT_LASTID_URI_PATTERN = Uri.parse(CONTENT_URI_STRING + FULL_PATH_POST_BOARD_LASTID);
    public static final Uri CONTENT_CLEANUP_URI_BASE = Uri.parse(CONTENT_URI_STRING + PATH_POST_BOARD_CLEANUP);
    public static final Uri CONTENT_CLEANUP_URI_PATTERN = Uri.parse(CONTENT_URI_STRING + FULL_PATH_POST_BOARD_CLEANUP);
    public static final String COLUMN_NAME_BOARD = "board";
    public static final String COLUMN_NAME_ID = "id";
    public static final String COLUMN_NAME_TIME = "time";
    public static final String COLUMN_NAME_INFO = "info";
    public static final String COLUMN_NAME_LOGIN = "login";
    public static final String COLUMN_NAME_MESSAGE = "message";
    public static final String DEFAULT_SORT_ORDER = COLUMN_NAME_BOARD + ", " + COLUMN_NAME_ID + " ASC";
    public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.bouchot.post";
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.bouchot.post";
  }
}
