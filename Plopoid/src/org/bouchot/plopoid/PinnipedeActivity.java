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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import android.app.Activity;
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
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.widget.Toast;

public class PinnipedeActivity extends FragmentActivity {
  SharedPreferences preferences;
  HttpContext httpContext;
  CookieStore cookieStore;
  TabHost mTabHost;
  ViewPager mViewPager;
  TabsAdapter mTabsAdapter;
  Messenger updateMessenger = null;
  boolean messengerBound;
  private final static int TAB_HEIGHT = 30;
  private final static int TAB_WIDTH = 100;
  
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

    setContentView(R.layout.main);

    mTabHost = (TabHost) findViewById(android.R.id.tabhost);
    mTabHost.setup();

    mViewPager = (ViewPager) findViewById(R.id.pager);

    mTabsAdapter = new TabsAdapter(this, mTabHost, mViewPager);

    for (String b : boards) {
      Bundle args = new Bundle();
      args.putString("board", b);
      mTabsAdapter.addTab(mTabHost.newTabSpec(b).setIndicator(b), BoardFragment.class, args);
    }
    for (int i = 0; i < mTabHost.getTabWidget().getTabCount(); i++) {
      mTabHost.getTabWidget().getChildAt(i).getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TAB_HEIGHT, getResources().getDisplayMetrics());//(int) (35.0f * getResources().getDisplayMetrics().density + 0.5f);
      mTabHost.getTabWidget().getChildAt(i).getLayoutParams().width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TAB_WIDTH, getResources().getDisplayMetrics());
    }

    if (savedInstanceState != null) {
      mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
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

    for (String b : boards) {
      int after = -1;
      boolean addIt = true;
      for (int i = 0; i < mTabHost.getTabWidget().getTabCount(); i++) {
        String title = (String) ((TextView) mTabHost.getTabWidget().getChildTabViewAt(i).findViewById(android.R.id.title)).getText();
        if (title.compareTo(b) < 0) {
          after = i;
        }
        if (title != null && title.equals(b)) {
          addIt = false;
        }
      }
      if (addIt) {
        Bundle args = new Bundle();
        args.putString("board", b);
        mTabsAdapter.addTab(mTabHost.newTabSpec(b).setIndicator(b), BoardFragment.class, args);
      }
    }
    for (int i = 0; i < mTabHost.getTabWidget().getTabCount(); i++) {
      View tab = mTabHost.getTabWidget().getChildAt(i);
      tab.getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TAB_HEIGHT, getResources().getDisplayMetrics());
      tab.getLayoutParams().width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TAB_WIDTH, getResources().getDisplayMetrics());
      if (!boards.contains((String) ((TextView) tab.findViewById(android.R.id.title)).getText())) {
        mTabsAdapter.removeTab(i);
      }
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString("tab", mTabHost.getCurrentTabTag());
  }

  public void palmipedePost(View v) {
    CharSequence board = mTabHost.getCurrentTabTag();
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

  public static class TabsAdapter extends FragmentPagerAdapter
  implements TabHost.OnTabChangeListener, ViewPager.OnPageChangeListener {
    private final Activity mActivity;
    private final TabHost mTabHost;
    private final ViewPager mViewPager;
    private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

    static final class TabInfo {
      private final String tag;
      private final Class<?> clss;
      private final Bundle args;
      private BoardFragment board;

      TabInfo(String _tag, Class<?> _class, Bundle _args) {
        tag = _tag;
        clss = _class;
        args = _args;
      }
      
      public void setBoard(BoardFragment board) {
        this.board = board;
      }
      
      public BoardFragment getBoard() {
        return board;
      }
    }

    static class DummyTabFactory implements TabHost.TabContentFactory {
      private final Context mContext;

      public DummyTabFactory(Context context) {
        mContext = context;
      }

      @Override
      public View createTabContent(String tag) {
        View v = new View(mContext);
        v.setMinimumWidth(0);
        v.setMinimumHeight(0);
        return v;
      }
    }

    public TabsAdapter(FragmentActivity activity, TabHost tabHost, ViewPager pager) {
      super(activity.getSupportFragmentManager());
      mActivity = activity;
      mTabHost = tabHost;
      mViewPager = pager;
      mTabHost.setOnTabChangedListener(this);
      mViewPager.setAdapter(this);
      mViewPager.setOnPageChangeListener(this);
    }

    public void addTab(TabSpec tabSpec, Class<?> clss, Bundle args) {
      tabSpec.setContent(new DummyTabFactory(mActivity));
      String tag = tabSpec.getTag();

      TabInfo info = new TabInfo(tag, clss, args);
      mTabs.add(info);
      mTabHost.addTab(tabSpec);
      notifyDataSetChanged();
    }
    
    public void removeTab(int position) {
      mTabHost.getTabWidget().removeView(mTabHost.getTabWidget().getChildTabViewAt(position));
      mTabs.remove(position);
      notifyDataSetChanged();
    }

    @Override
    public Fragment getItem(int position) {
      Log.d("TabsAdapter", "getItem() called for position: " + position);
      TabInfo info = mTabs.get(position);
      BoardFragment board = (BoardFragment) Fragment.instantiate(mActivity, info.clss.getName(), info.args);
      info.setBoard(board);
      return board;
    }

    @Override
    public int getCount() {
      return mTabs.size();
    }

    @Override
    public void onPageScrollStateChanged(int position) {
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
      // Unfortunately when TabHost changes the current tab, it kindly
      // also takes care of putting focus on it when not in touch mode.
      // The jerk.
      // This hack tries to prevent this from pulling focus out of our
      // ViewPager.
      TabWidget widget = mTabHost.getTabWidget();
      int oldFocusability = widget.getDescendantFocusability();
      widget.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
      mTabHost.setCurrentTab(position);
      widget.setDescendantFocusability(oldFocusability);
    }

    @Override
    public void onTabChanged(String tabId) {
      int position = mTabHost.getCurrentTab();
      BoardFragment board = mTabs.get(position).getBoard();
      if (board != null) {
        if (board.getLoaderManager().hasRunningLoaders()) {
          Log.d("TabsAdapter", "Fragment state: " + (board.getLoaderManager().hasRunningLoaders() ? "running" : "ok"));
        }
      }
      mViewPager.setCurrentItem(position, true);
      HorizontalScrollView scroll = (HorizontalScrollView) mActivity.findViewById(R.id.tabs_scroll);
      int x = (int) ((int) position * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TAB_WIDTH, mActivity.getResources().getDisplayMetrics()) / mTabs.size()); 
      scroll.smoothScrollTo(x, 0);
    }
  }
}