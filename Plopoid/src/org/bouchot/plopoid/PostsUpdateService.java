package org.bouchot.plopoid;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;

/**
 * This class is primarily responsible for refreshing the DB with newer content.
 * It runs as an Android {@link Service}.
 * 
 * @author lesensei
 */
public class PostsUpdateService extends Service {
  private Looper mServiceLooper;
  private ServiceHandler mServiceHandler;
  private long delay = 10000;
  private long curLastId;
  private boolean destroyCalled = false;
  ArrayList<String> boards;
  HashSet<String> nicks;
  SharedPreferences preferences;
  PrefChangeListener prefsListener;
  private Messenger updateMessenger;
  public static final int MSG_UPDATE_AFTER_POST = 1;
  public static final int MSG_UPDATE_USER_REQUEST = 2;
  public static final int MSG_UPDATE_AUTO = 3;
  public static final int MSG_UPDATE_PREF_CHANGE = 4;
  public static final String BACKEND_UPDATE = "org.bouchot.plopoid.PostsUpdateService.action.BACKEND_UPDATE";
  public static final String BACKEND_UPDATE_RUNNING = "org.bouchot.plopoid.PostsUpdateService.BACKEND_UPDATE_RUNNING";
  private static final int NOTIF_ID = 1;
  private int notifCount;
  private boolean serviceBound;
  
  /**
   * This class handles the interesting bits of the XML served by olccs.
   * It stores any new content in the DB using URIs from the {@link PostsProvider} class.
   * 
   * @author lesensei
   */
  private final static class XmlPostsHandler implements ContentHandler {
    private final String BOARD_TAG    = "board";
    private final String POST_TAG     = "post";
    private final String INFO_TAG     = "info";
    private final String LOGIN_TAG    = "login";
    private final String MESSAGE_TAG  = "message";
    private final String ID_ATTR      = "id";
    private final String TIME_ATTR    = "time";
    private final String BOARD_ATTR    = "site";
    
    private StringBuffer curContent;
    private ContentValues data;
    private long lastId;
    private String[] ID_COL = { PostsProvider.Posts.COLUMN_NAME_ID };
    private String board;
    private ContentValues[] posts;
    private int index;
    private WeakReference<PostsUpdateService> mServiceRef;
    
    public XmlPostsHandler(PostsUpdateService mService) {
      curContent = new StringBuffer();
      lastId = 0;
      board = "";
      posts = new ContentValues[150];
      index = 0;
      mServiceRef = new WeakReference<PostsUpdateService>(mService);
    }
    
    @Override
    public void setDocumentLocator(Locator locator) {
      // We don't use a locator
    }

    @Override
    public void startDocument() throws SAXException {
      // Do nothing
    }
    
    /**
     * This method is used to clean up the DB. We only keep the last 150 posts for each board.
     * 
     * @param board The board to clean up.
     */
    private void cleanupBoard(String board) {
      PostsUpdateService mService = mServiceRef.get();
      // Get the highest ID for the given board, then substract 150 from it.
      Uri lastIdUri = Uri.withAppendedPath(PostsProvider.Posts.CONTENT_LASTID_URI_BASE, board);
      String[] columns = {PostsProvider.Posts.COLUMN_NAME_ID};
      Cursor c = mService.getContentResolver().query(lastIdUri, columns, null, null, null);
      long lastId = 0;
      if (c.moveToFirst()) {
        lastId = c.getLong(0) - 150;
      }
      String[] args = { board, "" + lastId };
      // Finally delete any post with a lesser ID.
      mService.getContentResolver().delete(PostsProvider.Posts.CONTENT_URI, PostsProvider.Posts.COLUMN_NAME_BOARD + " = ? AND " + PostsProvider.Posts.COLUMN_NAME_ID + " < ?", args);
      c.close();
      return;
    }

    @Override
    public void endDocument() throws SAXException {
      PostsUpdateService mService = mServiceRef.get();
      // Insert all new posts found in this XML Doc, then clean up the board.
      mService.getContentResolver().bulkInsert(PostsProvider.Posts.CONTENT_URI, posts);
      cleanupBoard(data.getAsString("board"));
      mService.curLastId = lastId;
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
      // Our XML is nothing like real-world XML, there aren't any namespaces or anything
    }

    @Override
    public void endPrefixMapping(String arg0) throws SAXException {
      // Same as above
    }

    @Override
    public void startElement(String nsUri, String lName, String rawName, Attributes attrs) throws SAXException {
      // Post tag found, let's prepare to add it.
      if (lName.equals(POST_TAG)) {
        data = new ContentValues();
        data.put("board", board);
        try {
          data.put("id", Long.valueOf(attrs.getValue("", ID_ATTR)));
        } catch (NumberFormatException nfe) {
          Toast.makeText(mServiceRef.get().getApplicationContext(), "Invalid data (NaN id) received for board"+ board, Toast.LENGTH_SHORT).show();
        }
        data.put("time", attrs.getValue("", TIME_ATTR));
      // Board tag found, its "site" value will be used for all subsequent posts
      } else if (lName.equals(BOARD_TAG)) {
        board = attrs.getValue("", BOARD_ATTR);
      // The "info", "login" and "message" tags initiate a new content buffer to be added to our post
      } else if (lName.equals(INFO_TAG) || lName.equals(LOGIN_TAG) || lName.equals(MESSAGE_TAG)) {
        curContent = new StringBuffer();
      // Any other tag is probably part of the message, and as such is added to the content buffer
      } else {
        curContent.append('<' + lName);
        for (int i = 0; i < attrs.getLength(); i++) {
          curContent.append(' ' + attrs.getLocalName(i) + '=' + attrs.getValue(i));
        }
        curContent.append('>');
      }
    }

    @Override
    public void endElement(String nsUri, String lName, String rawName) throws SAXException {
      PostsUpdateService mService = mServiceRef.get();
      // Here we add the different components of the post to the data that will later be included in the DB
      if (lName.equals(LOGIN_TAG)) {
        data.put("login", curContent.toString());
      } else if (lName.equals(INFO_TAG)) {
        data.put("info", curContent.toString());
      } else if (lName.equals(MESSAGE_TAG)) {
        data.put("message", curContent.toString());
      } else if (lName.equals(POST_TAG)) {
        // Before adding a post to the array, check whether it already exists in the DB or not.
        Uri getUri = ContentUris.withAppendedId(Uri.withAppendedPath(PostsProvider.Posts.CONTENT_ID_URI_BASE, data.getAsString("board")), data.getAsInteger("id"));
        Cursor getCursor = mService.getContentResolver().query(getUri, ID_COL, null, null, null);
        if (lastId < data.getAsLong("id")) {
          lastId = data.getAsLong("id");
        }
        if (getCursor.moveToFirst()) {
          getCursor.close();
          return;
        }
        posts[index++] = data;
        if (!mService.serviceBound) {
          String msg = Html.fromHtml(data.getAsString("message")).toString();
          boolean doNotify = false;
          for (String nick : mService.nicks) {
            if (msg.contains(nick + "< ") || msg.endsWith(nick + "<")) {
              doNotify = true;
            }
          }
          if (!doNotify && (msg.contains("moules< ") || msg.endsWith("moules<"))) {
            doNotify = true;
          }
          if (doNotify) {
            Resources res = mService.getResources();
            NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(mService);
            notifBuilder.setSmallIcon(R.drawable.ic_launcher);
            notifBuilder.setContentTitle(res.getText(R.string.bigornophone));
            String login = data.getAsString("login");
            if (TextUtils.isEmpty(login)) {
              login = data.getAsString("info");
              if (login.length() > 10) {
                login = login.substring(0, 10);
              }
            }
            notifBuilder.setContentText(String.format(res.getString(R.string.bigo_detail), data.getAsString("board"), login));
            Intent notifIntent = new Intent(mService, CoinCoinActivity.class);
            notifIntent.putExtra("org.bouchot.plopoid.board", data.getAsString("board"));
            notifBuilder.setContentIntent(PendingIntent.getActivity(mService, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT));
            notifBuilder.setAutoCancel(true);
            NotificationManager mNotificationManager = (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(NOTIF_ID, notifBuilder.build());
          }
        }
        getCursor.close();
      } else {
        curContent.append("</" + lName + '>');
      }
    }
    
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
      curContent.append(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
      // Nothing to do here
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
      // Nothing interesting for us here
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
    }
  }
  
  /**
   * This class consumes the queue of events that trigger a refresh of the DB.
   * When sent a {@link Message}, it fetches the latest content, parses it and stores it in the DB,
   * then programs the next run by sending itself a new {@link Message}.
   * It lives as a static inner class to the Service, which Android Lint advises as being a good thing®©
   * 
   * @author lesensei
   */
  private final static class ServiceHandler extends Handler {
    private WeakReference<PostsUpdateService> mServiceRef;
    
    /**
     * The constructor gets passed the Service instance, and then creates a WeakReference to it,
     * so that it can access it without blocking the GC from collecting it when needed.
     * The Handler will then die with an Exception, but we just don't care anymore...
     * (yes, I guess this is very much ugly...)
     * 
     * @param mService The {@link PostsUpdateService} instance.
     */
    public ServiceHandler(PostsUpdateService mService) {
      super(mService.mServiceLooper);
      mServiceRef = new WeakReference<PostsUpdateService>(mService);
    }
    
    @Override
    public void handleMessage(Message msg) {
      PostsUpdateService mService = mServiceRef.get();
      //If our reference is null, the service has been destroyed for whatever reason and we should get outta here...
      if (mService == null) {
        return;
      }
      // Notify the UI thread that we are running, so that the user knows, too.
      Intent intent = new Intent(BACKEND_UPDATE);
      intent.putExtra(BACKEND_UPDATE_RUNNING, true);
      mService.sendBroadcast(intent);
      
      boolean hadNewPosts = false;
      
      // Copy the ArrayList to avoid surprises due to concurrent modifications.
      // We don't need to synchronize or anything here, as that would induce delays
      // and we don't really care if we load something that wasn't really due. 
      @SuppressWarnings("unchecked")
      ArrayList<String> boardsCopy = (ArrayList<String>) mService.boards.clone();
      
      int count = boardsCopy.size();
      AndroidHttpClient httpClient = AndroidHttpClient.newInstance(mService.preferences.getString("user_agent", mService.getString(R.string.user_agent_default)));
      try {
        for (int i = 0; i < count; i++) {
          String board = boardsCopy.get(i);
          // Get the latest ID we have stored for this board ...
          Uri lastIdUri = Uri.withAppendedPath(PostsProvider.Posts.CONTENT_LASTID_URI_BASE, board);
          String[] columns = {PostsProvider.Posts.COLUMN_NAME_ID};
          Cursor c = mService.getContentResolver().query(lastIdUri, columns, null, null, null);
          long lastId = 0;
          if (c.moveToFirst()) {
            lastId = c.getLong(0);
          }
          c.close();
          
          try {
            // ... then query olccs for anything fresher than it
            List<NameValuePair> params = new LinkedList<NameValuePair>();
            params.add(new BasicNameValuePair("query", "post.id:[" + lastId + " TO *]"));
            String paramString = URLEncodedUtils.format(params, "utf-8");
            String url = mService.preferences.getString("olccs_base_uri", mService.getString(R.string.olccs_base_uri_default)) + "t/" + board + "/search.xml?" + paramString;
            HttpResponse res = httpClient.execute(new HttpGet(url));
            if (res.getStatusLine().getStatusCode() >= 300) {
              throw new IOException("Got HTTP response " + res.getStatusLine().toString() + " for URL " + url);
            }
            HttpEntity entity = res.getEntity();
            if (entity == null) {
              throw new IOException("Got empty response for URL " + url);
            }
            // Finally, parse the XML response, and store whatever needs to be stored.
            Xml.parse(entity.getContent(), Xml.Encoding.UTF_8, new XmlPostsHandler(mService));
          } catch (IOException e) {
            Log.e("PostsUpdateMessageHandler", "Unable to fetch data for board " + board, e);
          } catch (SAXException e) {
            Log.e("PostsUpdateMessageHandler", "Unable to parse XML content returned by server for board " + board, e);
          } catch (SQLException e) {
            Log.e("PostsUpdateMessageHandler", "SQL Exception occured", e);
          }
          
          // If the lastId has been modified, we have new content.
          if (lastId < mService.curLastId)
            hadNewPosts = true;
        }
      } catch (Exception e) {
        Log.e("PostsUpdateMessageHandler", "Unhandled error", e);
      } finally {
        httpClient.close();
      }
      
      // Whether the refresh was scheduled or by user request, reset the timer if there was new content, augment its delay otherwise.
      if (msg.what == MSG_UPDATE_AUTO || msg.what == MSG_UPDATE_USER_REQUEST) {
        if (hadNewPosts) {
          mService.delay = 3000;
        } else {
          mService.delay = Math.min(mService.delay + 3000, 60000);
        }
      }
      // If the refresh was scheduled, then schedule the next one.
      if (msg.what == MSG_UPDATE_AUTO) {
        Message newMsg = this.obtainMessage();
        newMsg.arg1 = msg.arg1;
        newMsg.what = msg.what;
        newMsg.obj = mService.boards;
        if (!mService.destroyCalled) {
          this.sendMessageDelayed(newMsg, mService.delay);
        }
      }

      // Finally, notify the UI thread that we're done.
      intent = new Intent(BACKEND_UPDATE);
      intent.putExtra(BACKEND_UPDATE_RUNNING, false);
      mService.sendBroadcast(intent);
    }
  }
  
  private HashSet<String> getInvocationNicks() {
    HashSet<String> nicks = new HashSet<String>();
    
    Map<String, ?> prefs = preferences.getAll();
    for (Map.Entry<String, ?> pref : prefs.entrySet()) {
      String k = pref.getKey();
      if (k.contains("_board_enabled")) {
        if (preferences.getBoolean(k, false)) {
          String b = k.substring(0, k.indexOf("_board_enabled"));
          String n = preferences.getString(b + "_login", "");
          if (!TextUtils.isEmpty(n)) {
            nicks.add(n);
          }
        }
      } else if (k.equals("default_login")) {
        String s = preferences.getString(k, "");
        if (!TextUtils.isEmpty(s)) {
          nicks.add(s);
        }
      }
    }
    
    return nicks;
  }
  
  /**
   * This class listens for changes to the {@link SharedPreferences} of the application.
   * It modifies the array of boards to fetch content for, and sends a message to the UI thread,
   * so that the board is displayed, too.
   * 
   * @author lesensei
   */
  private class PrefChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
    public PrefChangeListener () {
      
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPrefs, String key) {
      boolean boardAdded = false;
      if (key.contains("_board_enabled")) {
        String b = key.substring(0, key.indexOf("_board_enabled"));
        if (sharedPrefs.getBoolean(key, false)) {
          if (!boards.contains(b)) {
            boards.add(b);
            String n = preferences.getString(b + "_login", "");
            if (!TextUtils.isEmpty(n)) {
              nicks.add(n);
            }
            boardAdded = true;
          }
        } else {
          if (boards.contains(b)) {
            boards.remove(b);
            nicks = getInvocationNicks();
          }
        }
      } else if (key.substring(key.length() - 6).equals("_login")) {
        nicks = getInvocationNicks();
      }
      if (boardAdded) {
        Message msg = mServiceHandler.obtainMessage();
        msg.what = MSG_UPDATE_PREF_CHANGE;
        mServiceHandler.sendMessage(msg);
      }
    }
  }
  
  /**
   * Called on the creation of the Service instance.
   * This initializes the instance variables, creates the {@link PrefChangeListener} instance and registers it.
   */
  @Override
  public void onCreate() {
    HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
    thread.start();
    
    mServiceLooper = thread.getLooper();
    mServiceHandler = new ServiceHandler(this);
    updateMessenger = new Messenger(mServiceHandler);
    notifCount = 0;
    
    boards = new ArrayList<String>();
    nicks = new HashSet<String>();
    preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    prefsListener = new PrefChangeListener();
    Map<String, ?> prefs = preferences.getAll();
    for (Map.Entry<String, ?> pref : prefs.entrySet()) {
      String k = pref.getKey();
      if (k.contains("_board_enabled")) {
        if (preferences.getBoolean(k, false)) {
          String b = k.substring(0, k.indexOf("_board_enabled"));
          boards.add(b);
          String n = preferences.getString(b + "_login", "");
          if (!TextUtils.isEmpty(n)) {
            nicks.add(n);
          }
        }
      } else if (k.equals("default_login")) {
        String s = preferences.getString(k, "");
        if (!TextUtils.isEmpty(s)) {
          nicks.add(s);
        }
      }
    }
    preferences.registerOnSharedPreferenceChangeListener(prefsListener);
  }
  
  /**
   * Called when the Service is started. This sends the first update message to the Handler.
   */
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Message msg = mServiceHandler.obtainMessage();
    msg.arg1 = startId;
    msg.what = MSG_UPDATE_AUTO;
    mServiceHandler.sendMessage(msg);

    return START_STICKY;
  }
  
  @Override
  public void onDestroy() {
    destroyCalled = true;
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    serviceBound = true;
    return updateMessenger.getBinder();
  }

  @Override
  public void onRebind(Intent intent) {
    serviceBound = true;
  }

  @Override
  public boolean onUnbind(Intent intent) {
    serviceBound = false;
    return true;
  }
}
