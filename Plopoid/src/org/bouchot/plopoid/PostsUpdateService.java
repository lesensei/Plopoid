package org.bouchot.plopoid;

import java.io.IOException;
import java.util.ArrayList;
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
import android.app.Service;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
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
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;

public class PostsUpdateService extends Service {
  private Looper mServiceLooper;
  private ServiceHandler mServiceHandler;
  private long delay = 10000;
  private long curLastId;
  private boolean destroyCalled = false;
  ArrayList<String> boards;
  SharedPreferences preferences;
  PrefChangeListener prefsListener;
  private Messenger updateMessenger;
  public static final int MSG_UPDATE_AFTER_POST = 1;
  
  private final class XmlPostsHandler implements ContentHandler {
    private final String BOARD_TAG    = "board";
    private final String POST_TAG     = "post";
    private final String INFO_TAG     = "info";
    private final String LOGIN_TAG    = "login";
    private final String MESSAGE_TAG  = "message";
    private final String ID_ATTR      = "id";
    private final String TIME_ATTR    = "time";
    private final String BOARD_ATTR    = "site";
    
    private String curTag;
    private StringBuffer curContent;
    private ContentValues data;
    private long lastId;
    private String[] ID_COL = { PostsProvider.Posts.COLUMN_NAME_ID };
    private String board;
    private ContentValues[] posts;
    private int index;
    
    public XmlPostsHandler() {
      curTag = "";
      curContent = new StringBuffer();
      lastId = 0;
      board = "";
      posts = new ContentValues[150];
      index = 0;
    }
    
    @Override
    public void setDocumentLocator(Locator locator) {
      // We don't use a locator
    }

    @Override
    public void startDocument() throws SAXException {
      // Do nothing
    }
    
    private void cleanupBoard(String board) {
      Uri lastIdUri = Uri.withAppendedPath(PostsProvider.Posts.CONTENT_LASTID_URI_BASE, board);
      String[] columns = {PostsProvider.Posts.COLUMN_NAME_ID};
      Cursor c = getContentResolver().query(lastIdUri, columns, null, null, null);
      long lastId = 0;
      if (c.moveToFirst()) {
        lastId = c.getLong(0) - 150;
      }
      String[] args = { board, "" + lastId };
      int deleted = getContentResolver().delete(PostsProvider.Posts.CONTENT_URI, PostsProvider.Posts.COLUMN_NAME_BOARD + " = ? AND " + PostsProvider.Posts.COLUMN_NAME_ID + " < ?", args);
      c.close();
      Log.d("PostsUpdateService", deleted + " posts deleted for board " + data.getAsString("board"));
      return;
    }

    @Override
    public void endDocument() throws SAXException {
      getContentResolver().bulkInsert(PostsProvider.Posts.CONTENT_URI, posts);
      cleanupBoard(data.getAsString("board"));
      curLastId = lastId;
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
      curTag = lName;
      curContent = new StringBuffer();
      if (curTag.equals(POST_TAG)) {
        data = new ContentValues();
        data.put("board", board);
        try {
          data.put("id", new Long(attrs.getValue("", ID_ATTR)));
        } catch (NumberFormatException nfe) {
          Toast.makeText(getApplicationContext(), "Invalid data (NaN id) received for board"+ board, Toast.LENGTH_SHORT).show();
          Log.e("XmlPostsHandler", "Invalid id data, unable to fully parse post");
        }
        data.put("time", attrs.getValue("", TIME_ATTR));
      } else if (curTag.equals(BOARD_TAG)) {
        board = attrs.getValue("", BOARD_ATTR);
      }
    }

    @Override
    public void endElement(String nsUri, String lName, String rawName) throws SAXException {
      if (curTag.equals(LOGIN_TAG)) {
        data.put("login", curContent.toString());
      } else if (curTag.equals(INFO_TAG)) {
        data.put("info", curContent.toString());
      } else if (curTag.equals(MESSAGE_TAG)) {
        data.put("message", curContent.toString());
      }
      if (lName.equals(POST_TAG)) {
        Uri getUri = ContentUris.withAppendedId(Uri.withAppendedPath(PostsProvider.Posts.CONTENT_ID_URI_BASE, data.getAsString("board")), data.getAsInteger("id"));
        Cursor getCursor = getContentResolver().query(getUri, ID_COL, null, null, null);
        if (lastId < data.getAsLong("id")) {
          lastId = data.getAsLong("id");
        }
        if (getCursor.moveToFirst()) {
          getCursor.close();
          return;
        }
        posts[index++] = data;
        getCursor.close();
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
  
  private final class ServiceHandler extends Handler {
    public ServiceHandler(Looper looper) {
        super(looper);
    }
    
    @Override
    public void handleMessage(Message msg) {
      boolean hadNewPosts = false;
      
      int count = boards.size();
      AndroidHttpClient httpClient = AndroidHttpClient.newInstance(preferences.getString("user_agent", getString(R.string.user_agent_default)));
      try {
        for (int i = 0; i < count; i++) {
          String board = boards.get(i);
          Uri lastIdUri = Uri.withAppendedPath(PostsProvider.Posts.CONTENT_LASTID_URI_BASE, board);
          String[] columns = {PostsProvider.Posts.COLUMN_NAME_ID};
          Cursor c = getContentResolver().query(lastIdUri, columns, null, null, null);
          long lastId = 0;
          if (c.moveToFirst()) {
            lastId = c.getLong(0);
          }
          c.close();
          
          try {
            List<NameValuePair> params = new LinkedList<NameValuePair>();
            params.add(new BasicNameValuePair("query", "id:[" + lastId + " TO *]"));
            String paramString = URLEncodedUtils.format(params, "utf-8");
            String url = preferences.getString("olccs_base_uri", getString(R.string.olccs_base_uri_default)) + board + "/search?" + paramString;
            Log.d("PostsUpdateMessageHandler", url);
            HttpResponse res = httpClient.execute(new HttpGet(url));
            Log.d("PostsUpdateMessageHandler", "Taille des données reçues: " + res.getEntity().getContentLength());
            if (res.getStatusLine().getStatusCode() >= 300) {
              throw new IOException("Got HTTP response " + res.getStatusLine().toString() + " for URL " + url);
            }
            HttpEntity entity = res.getEntity();
            if (entity == null) {
              throw new IOException("Got empty response for URL " + url);
            }
            Xml.parse(entity.getContent(), Xml.Encoding.UTF_8, new XmlPostsHandler());
          } catch (IOException e) {
            Log.e("PostsUpdateMessageHandler", "Unable to fetch data for board " + board);
          } catch (SAXException e) {
            Log.e("PostsUpdateMessageHandler", "Unable to parse XML content returned by server for board " + board);
          }
          
          if (lastId < curLastId)
            hadNewPosts = true;
        }
      } catch (Exception e) {
        Log.e("PostsUpdateMessageHandler", "Unhandled error " + e.getMessage());
        e.printStackTrace();
      } finally {
        httpClient.close();
      }
      
      if (msg.what != MSG_UPDATE_AFTER_POST) {
        if (hadNewPosts) {
          delay = 3000;
        } else {
          delay = Math.min(delay + 3000, 60000);
          Log.d("PostsUpdateMessageHandler", "Delay increased to " + delay + "ms");
        }
        Message newMsg = this.obtainMessage();
        newMsg.arg1 = msg.arg1;
        newMsg.obj = boards;
        if (!destroyCalled) {
          this.sendMessageDelayed(newMsg, delay);
      }
      }
    }
  }
  
  private class PrefChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
    public PrefChangeListener () {
      
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPrefs, String key) {
      Log.d("PrefChangeListener", "Preference " + key + " change registered by the service");
      if (key.contains("_board_enabled")) {
        String b = key.substring(0, key.indexOf("_board_enabled"));
        if (sharedPrefs.getBoolean(key, false)) {
          if (!boards.contains(b)) {
            boards.add(b);
          }
        } else {
          if (boards.contains(b)) {
            boards.remove(b);
          }
        }
      }
    }
  }
  
  @Override
  public void onCreate() {
    HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
    thread.start();
    
    mServiceLooper = thread.getLooper();
    mServiceHandler = new ServiceHandler(mServiceLooper);
    updateMessenger = new Messenger(mServiceHandler);
    
    boards = new ArrayList<String>();
    preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    prefsListener = new PrefChangeListener();
    Map<String, ?> prefs = preferences.getAll();
    for(Map.Entry<String, ?> pref : prefs.entrySet()) {
      String k = pref.getKey();
      if (k.contains("_board_enabled")) {
        if (preferences.getBoolean(k, false)) {
          boards.add(k.substring(0, k.indexOf("_board_enabled")));
        }
      }
    }
    preferences.registerOnSharedPreferenceChangeListener(prefsListener);
  }
  
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Message msg = mServiceHandler.obtainMessage();
    msg.arg1 = startId;
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
    return updateMessenger.getBinder();
  }
}
