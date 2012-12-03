package org.bouchot.plopoid;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class CoinCoinActivity
    extends SherlockFragmentActivity {
  SharedPreferences preferences;
  Messenger updateMessenger = null;
  boolean messengerBound;
  HttpContext httpContext;
  CookieStore cookieStore;
  PostsUpdateServiceReceiver mPostsUpdateServiceReceiver;
  Menu mOptionsMenu;
  View mRefreshIndeterminateProgressView = null;

  /**
   * The {@link android.support.v4.view.PagerAdapter} that will provide
   * fragments for each of the sections. We use a
   * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which will
   * keep every loaded fragment in memory. If this becomes too memory intensive,
   * it may be best to switch to a
   * {@link android.support.v4.app.FragmentStatePagerAdapter}.
   */
  SectionsPagerAdapter mSectionsPagerAdapter;

  /**
   * The {@link ViewPager} that will host the section contents.
   */
  ViewPager mViewPager;
  
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

  private static class PostsUpdateServiceReceiver extends BroadcastReceiver {
    WeakReference<CoinCoinActivity> mCoinCoinRef;
    
    public PostsUpdateServiceReceiver(CoinCoinActivity activity) {
      mCoinCoinRef = new WeakReference<CoinCoinActivity>(activity);
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
      CoinCoinActivity mCoinCoin = mCoinCoinRef.get();
      boolean refreshing = intent.getBooleanExtra(PostsUpdateService.BACKEND_UPDATE_RUNNING, false);
      
      if (mCoinCoin.mOptionsMenu == null) {
        return;
      }

      final MenuItem refreshItem = mCoinCoin.mOptionsMenu.findItem(R.id.menu_refresh);
      if (refreshItem != null) {
        if (refreshing) {
          if (mCoinCoin.mRefreshIndeterminateProgressView == null) {
            LayoutInflater inflater = (LayoutInflater) mCoinCoin.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mCoinCoin.mRefreshIndeterminateProgressView = inflater.inflate(R.layout.actionbar_indeterminate_progress, null);
          }
          refreshItem.setActionView(mCoinCoin.mRefreshIndeterminateProgressView);
        } else {
          refreshItem.setActionView(null);
        }
      }
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
        HttpPost httpLogin = new HttpPost(preferences.getString("olccs_base_uri", getString(R.string.olccs_base_uri_default)) + "t/" + mBoard + "/login");
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
      
      HttpPost httpPost = new HttpPost(preferences.getString("olccs_base_uri", getString(R.string.olccs_base_uri_default)) + "t/" + mBoard + "/post");
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

  // Stops the service if the configuration says to
  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (!preferences.getBoolean("service_sticky", false)) {
      Intent updatePosts = new Intent(this, PostsUpdateService.class);
      stopService(updatePosts);
      Log.d("PinnipedeActivity", "Stopped updater service");
    }
  }
  
  private void unbindMessenger() {
    unbindService(updateConn);
    updateMessenger = null;
    messengerBound = false;
  }

  // Binds the service, allowing Android to prioritize CPU usage to other tasks
  @Override
  protected void onStart() {
    super.onStart();
    if (!messengerBound) {
      bindService(new Intent(this, PostsUpdateService.class), updateConn, Context.BIND_NOT_FOREGROUND);
    }
  }

  // Unbinds the service
  @Override
  protected void onStop() {
    super.onStop();
    if (messengerBound) {
      unbindMessenger();
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    // Load an HTTP context, and let it deal with cookies management
    httpContext = new BasicHttpContext();
    cookieStore = new BasicCookieStore();
    // We still need to clear expired cookies
    cookieStore.clearExpired(new Date());
    httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);

    // Create an array of the enabled boards
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
    
    mPostsUpdateServiceReceiver = new PostsUpdateServiceReceiver(this);
    
    setContentView(R.layout.coincoin);

    // Set up the action bar.
    final ActionBar actionBar = getSupportActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

    // Create the adapter that will return a fragment for each of the three
    // primary sections of the app.
    mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), this);

    // Set up the ViewPager with the sections adapter.
    mViewPager = (ViewPager) findViewById(R.id.pager);
    mViewPager.setAdapter(mSectionsPagerAdapter);

    // When swiping between different sections, select the corresponding
    // tab. We can also use ActionBar.Tab#select() to do this if we have
    // a reference to the Tab.
    mViewPager
        .setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
          @Override
          public void onPageSelected(int position) {
            actionBar.setSelectedNavigationItem(position);
          }
        });

    for (String b : boards) {
      mSectionsPagerAdapter.addTab(BoardFragment.class, b);
    }

    if (savedInstanceState != null) {
      getSupportActionBar().setSelectedNavigationItem(savedInstanceState.getInt("tab"));
    }

    Intent updatePosts = new Intent(this, PostsUpdateService.class);
    startService(updatePosts);
    Log.d("PinnipedeActivity", "Started updater service");
  }

  // This is normally used when a notification is clicked.
  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!messengerBound) {
      bindService(new Intent(this, PostsUpdateService.class), updateConn, Context.BIND_NOT_FOREGROUND);
    }
    
    IntentFilter mIntentFilter = new IntentFilter();
    mIntentFilter.addAction(PostsUpdateService.BACKEND_UPDATE);
    registerReceiver(mPostsUpdateServiceReceiver, mIntentFilter);

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

    for (String b : boards) {
      boolean addIt = true;
      for (int i = 0; i < getSupportActionBar().getTabCount(); i++) {
        String title = (String) getSupportActionBar().getTabAt(i).getText();
        if (title != null && title.equals(b)) {
          addIt = false;
        }
      }
      if (addIt) {
        Bundle args = new Bundle();
        args.putString("board", b);
        mSectionsPagerAdapter.addTab(BoardFragment.class, b);
      }
    }
    for (int i = 0; i < getSupportActionBar().getTabCount(); i++) {
      if (!boards.contains((String) getSupportActionBar().getTabAt(i).getText())) {
        getSupportActionBar().removeTabAt(i);
      }
    }
    
    String board = getIntent().getStringExtra("board");
    ActionBar bar = getSupportActionBar();
    if (!TextUtils.isEmpty(board)) {
      for (int i = 0; i < bar.getTabCount(); i++) {
        if (bar.getTabAt(i).getText().equals(board)) {
          bar.setSelectedNavigationItem(i);
          return;
        }
      }
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    unregisterReceiver(mPostsUpdateServiceReceiver);
    if (messengerBound) {
      unbindMessenger();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt("tab", getSupportActionBar().getSelectedNavigationIndex());
  }

  public void palmipedePost(View v) {
    CharSequence board = getSupportActionBar().getSelectedTab().getText();
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
    String cookies = preferences.getString(board + "_login_cookie_name", "");
    new PostMessageTask(this, board.toString()).execute(message, username, password, cookies);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.options_item:
        Intent optionsActivity = new Intent(this, OptionsActivity.class);
        startActivity(optionsActivity);
        return true;
      case R.id.menu_refresh:
        if (messengerBound) {
          try {
            updateMessenger.send(Message.obtain(null, PostsUpdateService.MSG_UPDATE_USER_REQUEST));
          } catch (RemoteException e) {
            Toast.makeText(this, "Failed to trigger update after post", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
          }
        }
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getSupportMenuInflater();
    inflater.inflate(R.menu.main_menu, menu);
    mOptionsMenu = menu;
    return super.onCreateOptionsMenu(menu);
  }

  /**
   * A {@link FragmentPagerAdapter} that returns a fragment corresponding to one
   * of the sections/tabs/pages.
   */
  public class SectionsPagerAdapter extends FragmentPagerAdapter implements ActionBar.TabListener {
    private List<TabInfo> mTabs;
    private Activity mActivity;
    
    private class TabInfo {
      public final Class<? extends Fragment> clss;
      public final Bundle args;
      
      public TabInfo(Class<? extends Fragment> fragmentClass, Bundle args) {
        this.clss = fragmentClass;
        this.args = args;
      }
    }

    public SectionsPagerAdapter(FragmentManager fm, Activity activity) {
      super(fm);
      mActivity = activity;
      mTabs = new ArrayList<TabInfo>();
    }
    
    public void addTab(Class<? extends Fragment> fgmt, String name) {
      ActionBar actionBar = getSupportActionBar();
      Bundle args = new Bundle();
      args.putString("board", name);
      TabInfo tab = new TabInfo(fgmt, args);
      mTabs.add(tab);
      actionBar.addTab(actionBar.newTab().setText(name).setTabListener(this));
    }

    @Override
    public Fragment getItem(int position) {
      // getItem is called to instantiate the fragment for the given page.
      // Return a DummySectionFragment (defined as a static inner class
      // below) with the page number as its lone argument.
      TabInfo tab = mTabs.get(position);
      return Fragment.instantiate(mActivity, tab.clss.getName(), tab.args);
    }

    @Override
    public int getCount() {
      return mTabs.size();
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
      // When the given tab is selected, switch to the corresponding page in
      // the ViewPager.
      mViewPager.setCurrentItem(tab.getPosition()); 
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {
    }
  }
}
