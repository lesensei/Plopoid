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
    mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS, POSTS);
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS + Posts.FULL_PATH_POST__ID, POST__ID);
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS + Posts.FULL_PATH_POST_TIME, POST_TIME);
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS + Posts.FULL_PATH_POST_SINCE, POSTS_SINCE);
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS + Posts.FULL_PATH_POST_BOARD_LASTID, POST_BOARD_LASTID);
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS + Posts.FULL_PATH_POST_BOARD_CLEANUP, POST_BOARD_CLEANUP);
    mUriMatcher.addURI(PostsProvider.AUTHORITY, Posts.PATH_POSTS + Posts.FULL_PATH_POST_ID, POST_ID);

    mPostsProjectionMap = new HashMap<String, String>();
    mPostsProjectionMap.put(Posts._ID, Posts._ID);
    mPostsProjectionMap.put(Posts.COLUMN_NAME_BOARD, Posts.COLUMN_NAME_BOARD);
    mPostsProjectionMap.put(Posts.COLUMN_NAME_ID, Posts.COLUMN_NAME_ID);
    mPostsProjectionMap.put(Posts.COLUMN_NAME_TIME, Posts.COLUMN_NAME_TIME);
    mPostsProjectionMap.put(Posts.COLUMN_NAME_INFO, Posts.COLUMN_NAME_INFO);
    mPostsProjectionMap.put(Posts.COLUMN_NAME_LOGIN, Posts.COLUMN_NAME_LOGIN);
    mPostsProjectionMap.put(Posts.COLUMN_NAME_MESSAGE, Posts.COLUMN_NAME_MESSAGE);
  }

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
  }

  @Override
  public boolean onCreate() {
    mOpenHelper = new DatabaseHelper(getContext());
    return true;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    qb.setTables(Posts.TABLE_NAME);
    int matchedUri = mUriMatcher.match(uri);

    switch (matchedUri) {
      case POSTS:
        qb.setProjectionMap(mPostsProjectionMap);
        break;
      case POST__ID:
        qb.setProjectionMap(mPostsProjectionMap);
        qb.appendWhere(Posts._ID + "=" + uri.getPathSegments().get(Posts.POST__ID_PATH_POSITION));
        break;
      case POST_ID:
        qb.setProjectionMap(mPostsProjectionMap);
        qb.appendWhere(Posts.COLUMN_NAME_BOARD + "='" + uri.getPathSegments().get(Posts.POST_ID_BOARD_PATH_POSITION) + "'" +
            " AND " + Posts.COLUMN_NAME_ID + "=" + uri.getPathSegments().get(Posts.POST_ID_ID_PATH_POSITION));
        break;
      case POST_TIME:
        qb.setProjectionMap(mPostsProjectionMap);
        qb.appendWhere(Posts.COLUMN_NAME_BOARD + "='" + uri.getPathSegments().get(Posts.POST_OTHERS_BOARD_PATH_POSITION) + "'" +
            " AND " + Posts.COLUMN_NAME_TIME + "='" + uri.getPathSegments().get(Posts.POST_TIME_PATH_POSITION) + "'");
        break;
      case POSTS_SINCE:
        qb.setProjectionMap(mPostsProjectionMap);
        qb.appendWhere(Posts.COLUMN_NAME_BOARD + "='" + uri.getPathSegments().get(Posts.POST_OTHERS_BOARD_PATH_POSITION) + "'" +
            " AND " + Posts.COLUMN_NAME_ID + ">" + uri.getPathSegments().get(Posts.POST_OTHERS_ID_PATH_POSITION));
        break;
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
      orderBy = Posts.COLUMN_NAME_ID + " DESC";
      limit = "1";
    } else if (TextUtils.isEmpty(sortOrder)) {
      orderBy = Posts.DEFAULT_SORT_ORDER;
    } else {
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
      getContext().getContentResolver().notifyChange(postUri, null);
      //Log.d("PostsProvider", "Post " + rowId + " inserted successfully");
      return postUri;
    }

    throw new SQLException("Failed to insert row into " + uri);
  }

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

          ContentValues values = new ContentValues(v);

          if (!values.containsKey(Posts.COLUMN_NAME_INFO)) {
            values.put(Posts.COLUMN_NAME_INFO, "");
          }

          if (!values.containsKey(Posts.COLUMN_NAME_LOGIN)) {
            values.put(Posts.COLUMN_NAME_LOGIN, "");
          }

          if (inserter.insert(values) >= 0) {
            inserted++;
          } else {
            throw new SQLException("Failed to insert row into " + uri);
          }
        }
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      inserter.close();
    }

    getContext().getContentResolver().notifyChange(PostsProvider.Posts.CONTENT_URI, null);
    return inserted;
  }

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
        String board = uri.getPathSegments().get(Posts.POST_OTHERS_BOARD_PATH_POSITION);
        Log.d("PostsProvider", "Cleaning up "+ board);
        String[] cols = { Posts.COLUMN_NAME_ID };
        String orderBy = Posts.COLUMN_NAME_ID + " DESC";
        String limit = "150";
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
