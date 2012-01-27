package org.bouchot.plopoid;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ActionBar.Tab;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class PinnipedeActivity extends Activity {
  SharedPreferences preferences;
  HttpContext httpContext;
  CookieStore cookieStore;
  Messenger updateMessenger = null;
  boolean messengerBound;
  
  private ServiceConnection updateConn = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      updateMessenger = new Messenger(service);
      messengerBound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
      updateMessenger = null;
      messengerBound = false;
    }
  };

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (!preferences.getBoolean("service_sticky", false)) {
      Intent updatePosts = new Intent(this, PostsUpdateService.class);
      stopService(updatePosts);
      Log.d("PinnipedeActivity", "Stopped updater service");
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    bindService(new Intent(this, PostsUpdateService.class), updateConn, Context.BIND_NOT_FOREGROUND);
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (messengerBound) {
      unbindService(updateConn);
      messengerBound = false;
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    httpContext = new BasicHttpContext();
    cookieStore = new BasicCookieStore();
    cookieStore.clearExpired(new Date());
    httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);

    ArrayList<String> boards = new ArrayList<String>();
    preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    Map<String, ?> prefs = preferences.getAll();
    for(Map.Entry<String, ?> pref : prefs.entrySet()) {
      String k = pref.getKey();
      if (k.contains("_board_enabled")) {
        if (preferences.getBoolean(k, false)) {
          boards.add(k.substring(0, k.indexOf("_board_enabled")));
        }
      }
    }

    final ActionBar bar = getActionBar();
    bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
    bar.setDisplayShowTitleEnabled(false);

    for (String b : boards) {
      bar.addTab(bar.newTab()
          .setText(b)
          .setTabListener(new TabListener<BoardFragment>(
              this, b, BoardFragment.class)));
    }

    setContentView(R.layout.main);

    if (savedInstanceState != null) {
      String activeBoard = savedInstanceState.getString("tab");
      if (activeBoard != null) {
        for (int i = 0; i < bar.getNavigationItemCount(); i++) {
          if (((String) bar.getTabAt(i).getText()).equals(activeBoard)) {
            bar.setSelectedNavigationItem(i);
          }
        }
      }
    }

    Intent updatePosts = new Intent(this, PostsUpdateService.class);
    startService(updatePosts);
    Log.d("PinnipedeActivity", "Started updater service");
  }

  @Override
  protected void onResume() {
    super.onResume();

    ArrayList<String> boards = new ArrayList<String>();
    preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    Map<String, ?> prefs = preferences.getAll();
    for(Map.Entry<String, ?> pref : prefs.entrySet()) {
      String k = pref.getKey();
      if (k.contains("_board_enabled")) {
        if (preferences.getBoolean(k, false)) {
          boards.add(k.substring(0, k.indexOf("_board_enabled")));
        }
      }
    }

    final ActionBar bar = getActionBar();

    for (String b : boards) {
      int after = -1;
      boolean addIt = true;
      for (int i = 0; i < bar.getTabCount(); i++) {
        Tab tab = bar.getTabAt(i);
        String title = (String) tab.getText();
        if (title.compareTo(b) < 0) {
          after = i;
        }
        if (title != null && title.equals(b)) {
          addIt = false;
        }
      }
      if (addIt) {
        bar.addTab(bar.newTab()
            .setText(b)
            .setTabListener(new TabListener<BoardFragment>(
                this, b, BoardFragment.class)), after + 1);
      }
    }

    for (int i = 0; i < bar.getTabCount(); i++) {
      if (!boards.contains((String) bar.getTabAt(i).getText())) {
        bar.removeTabAt(i);
      }
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    Tab mTab =getActionBar().getSelectedTab(); 
    if (mTab != null) {
      outState.putString("tab", (String) mTab.getText());
    }
  }

  public void palmipedePost(View v) {
    CharSequence board = getActionBar().getSelectedTab().getText();
    Editable messageData = ((EditText) findViewById(R.id.palmipede)).getText();
    if (messageData.length() == 0) {
      return;
    }
    String message = ((CharSequence) messageData).toString();
    String username;
    String password;
    if (preferences.getBoolean(board + "_custom_login", false)) {
      username = preferences.getString(board + "_login", null);
      password = preferences.getString(board + "_password", null);
    } else {
      username = preferences.getString("default_login", null);
      password = preferences.getString("default_password", null);
    }
    Set<String> cookieNames = preferences.getStringSet(board + "_login_cookie_name", null);
    StringBuffer cookies = new StringBuffer();
    for (String cookie : cookieNames) {
      if (cookies.length() > 0) {
        cookies.append(';');
      }
      cookies.append(cookie);
    }
    new PostMessageTask(this, board.toString()).execute(message, username, password, cookies.toString());
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.options_item:
        Intent optionsActivity = new Intent(this, OptionsActivity.class);
        startActivity(optionsActivity);
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main_menu, menu);
    return super.onCreateOptionsMenu(menu);
  }

  public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
    private final Activity mActivity;
    private final String mTag;
    private final Class<T> mClass;
    private final Bundle mArgs;
    private Fragment mFragment;

    public TabListener(Activity activity, String tag, Class<T> clz) {
      this(activity, tag, clz, null);
    }

    public TabListener(Activity activity, String tag, Class<T> clz, Bundle args) {
      mActivity = activity;
      mTag = tag;
      mClass = clz;
      mArgs = args;

      // Check to see if we already have a fragment for this tab, probably
      // from a previously saved state.  If so, deactivate it, because our
      // initial state is that a tab isn't shown.
      mFragment = mActivity.getFragmentManager().findFragmentByTag(mTag);
      if (mFragment != null && !mFragment.isDetached()) {
        FragmentTransaction ft = mActivity.getFragmentManager().beginTransaction();
        ft.detach(mFragment);
        ft.commit();
      }
    }

    public void onTabSelected(Tab tab, FragmentTransaction ft) {
      if (mFragment == null) {
        mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
        ft.add(R.id.pinni_container, mFragment, mTag);
      } else {
        ft.attach(mFragment);
      }
    }

    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
      if (mFragment != null) {
        ft.detach(mFragment);
      }
    }

    public void onTabReselected(Tab tab, FragmentTransaction ft) {
      //Toast.makeText(mActivity, "Reselected!", Toast.LENGTH_SHORT).show();
    }
  }

  private class PostMessageTask extends AsyncTask<String, Void, Boolean> {
    Activity mActivity;
    EditText palmi;
    Button palmiBtn;
    String mBoard;
    
    public PostMessageTask (Activity activity, String board) {
      mActivity = activity;
      mBoard = board;
      palmi = (EditText) mActivity.findViewById(R.id.palmipede);
      palmiBtn = (Button) mActivity.findViewById(R.id.post_button);
    }
	  
    @Override
    protected void onPreExecute() {
      super.onPreExecute();
      palmi.setEnabled(false);
      palmiBtn.setEnabled(false);
    }

    @Override
    protected Boolean doInBackground(String... args) {
      String message = args[0];
      String username = args[1];
      String password = args[2];
      String[] cookieNames = args[3].split(";");
      AndroidHttpClient httpClient = AndroidHttpClient.newInstance(preferences.getString("user_agent", getString(R.string.user_agent_default)));
      
      boolean isLoggedIn = false;
      boolean didPost = false;
      List<Cookie> cookies = cookieStore.getCookies();
      for (String cookieName : cookieNames) {
        for (Cookie cookie : cookies) {
          if (cookie.getName().matches(cookieName) && !cookie.isExpired(new Date())) {
            isLoggedIn = true; 
          }
        }
      }
      if (!isLoggedIn) {
        HttpPost httpLogin = new HttpPost(preferences.getString("olccs_base_uri", getString(R.string.olccs_base_uri_default)) + mBoard + "/login");
        List<NameValuePair> loginParams = new LinkedList<NameValuePair>();
        loginParams.add(new BasicNameValuePair("user", username));
        loginParams.add(new BasicNameValuePair("password", password));
        try {
          httpLogin.setEntity(new UrlEncodedFormEntity(loginParams, "UTF-8"));
          HttpResponse res = httpClient.execute(httpLogin, httpContext);
          if (res.getStatusLine().getStatusCode() >= 400) {
            throw new IOException("HTTP error code: " + res.getStatusLine().getStatusCode());
          }
        } catch (UnsupportedEncodingException e) {
          Log.d("PostMessageTask", "Error logging in to " + mBoard);
          e.printStackTrace();
        } catch (IOException e) {
          Log.d("PostMessageTask", "Error logging in to " + mBoard);
          e.printStackTrace();
        }
      }
      
      HttpPost httpPost = new HttpPost(preferences.getString("olccs_base_uri", getString(R.string.olccs_base_uri_default)) + mBoard + "/post");
      List<NameValuePair> params = new LinkedList<NameValuePair>();
      params.add(new BasicNameValuePair("message", message));
      try {
        httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
        HttpResponse res = httpClient.execute(httpPost, httpContext);
        if (res.getStatusLine().getStatusCode() >= 400) {
          throw new IOException("HTTP error code: " + res.getStatusLine().getStatusCode());
        }
        didPost = true;
      } catch (UnsupportedEncodingException e) {
        Log.d("PostMessageTask", "Error sending message to " + mBoard);
        e.printStackTrace();
      } catch (IOException e) {
        Log.d("PostMessageTask", "Error sending message to " + mBoard);
        e.printStackTrace();
      } finally {
        httpClient.close();
      }
      return didPost;
    }

    @Override
    protected void onPostExecute(Boolean result) {
      super.onPostExecute(result);
      if (result) {
        palmi.setText("");
        if (messengerBound) {
          try {
            updateMessenger.send(Message.obtain(null, PostsUpdateService.MSG_UPDATE_AFTER_POST));
          } catch (RemoteException e) {
            Toast.makeText(mActivity, "Failed to trigger update after post", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
          }
        }
      } else {
        Toast.makeText(getApplicationContext(), "Error sending message", Toast.LENGTH_SHORT).show();
      }
      palmi.setEnabled(true);
      palmiBtn.setEnabled(true);
    }
  }
  
}