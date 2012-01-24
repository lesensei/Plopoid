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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
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

  @Override
  protected void onDestroy() {
    super.onDestroy();
    //preferences.unregisterOnSharedPreferenceChangeListener(prefsListener);
    if (!preferences.getBoolean("service_sticky", false)) {
      Intent updatePosts = new Intent(this, PostsUpdateService.class);
      stopService(updatePosts);
      Log.d("PinnipedeActivity", "Stopped updater service");
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
    //prefsListener = new PrefChangeListener(this);
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

    //preferences.registerOnSharedPreferenceChangeListener(prefsListener);

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
    String url = preferences.getString(board + "_post_url", null);
    String messageField = preferences.getString(board + "_post_field", null);
    Editable messageData = ((EditText) findViewById(R.id.palmipede)).getText();
    if (messageData.length() == 0) {
      return;
    }
    String message = ((CharSequence) messageData).toString();
    String login = preferences.getString(board + "_login_url", null);
    String usernameField = preferences.getString(board + "_login_username_field", null);
    String passwordField = preferences.getString(board + "_login_password_field", null);
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
    new PostMessageTask(this).execute(url, messageField, message, login, usernameField, passwordField, username, password, cookies.toString());
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
    
    public PostMessageTask (Activity activity) {
      mActivity = activity;
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
      String url = args[0];
      String field = args[1];
      String message = args[2];
      String login = args[3];
      String usernameField = args[4];
      String passwordField = args[5];
      String username = args[6];
      String password = args[7];
      String[] cookieNames = args[8].split(";");
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
        HttpPost httpLogin = new HttpPost(login);
        List<NameValuePair> loginParams = new LinkedList<NameValuePair>();
        loginParams.add(new BasicNameValuePair(usernameField, username));
        loginParams.add(new BasicNameValuePair(passwordField, password));
        try {
          httpLogin.setEntity(new UrlEncodedFormEntity(loginParams, "UTF-8"));
          HttpResponse res = httpClient.execute(httpLogin, httpContext);
          if (res.getStatusLine().getStatusCode() >= 400) {
            throw new IOException("HTTP error code: " + res.getStatusLine().getStatusCode());
          }
        } catch (UnsupportedEncodingException e) {
          Log.d("PostMessageTask", "Error logging in to " + login);
          e.printStackTrace();
        } catch (IOException e) {
          Log.d("PostMessageTask", "Error logging in to " + login);
          e.printStackTrace();
        }
      }
      
      HttpPost httpPost = new HttpPost(url);
      List<NameValuePair> params = new LinkedList<NameValuePair>();
      params.add(new BasicNameValuePair(field, message));
      try {
        httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
        HttpResponse res = httpClient.execute(httpPost, httpContext);
        if (res.getStatusLine().getStatusCode() >= 400) {
          throw new IOException("HTTP error code: " + res.getStatusLine().getStatusCode());
        }
        didPost = true;
      } catch (UnsupportedEncodingException e) {
        //Toast.makeText(getApplicationContext(), "Error sending message", Toast.LENGTH_SHORT).show();
        Log.d("PostMessageTask", "Error sending message to " + url);
        e.printStackTrace();
      } catch (IOException e) {
        //Toast.makeText(getApplicationContext(), "Error sending message", Toast.LENGTH_SHORT).show();
        Log.d("PostMessageTask", "Error sending message to " + url);
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
      } else {
        Toast.makeText(getApplicationContext(), "Error sending message", Toast.LENGTH_SHORT).show();
      }
      palmi.setEnabled(true);
      palmiBtn.setEnabled(true);
    }
  }
  
}