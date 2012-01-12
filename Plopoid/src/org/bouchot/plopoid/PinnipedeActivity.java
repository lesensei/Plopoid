package org.bouchot.plopoid;

import java.util.ArrayList;
import java.util.Map;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ActionBar.Tab;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

public class PinnipedeActivity extends Activity {
  SharedPreferences preferences;
  PrefChangeListener prefsListener;
  
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
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ArrayList<String> boards = new ArrayList<String>();
    preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    prefsListener = new PrefChangeListener(this);
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
    
    preferences.registerOnSharedPreferenceChangeListener(prefsListener);

    if (savedInstanceState != null) {
      bar.setSelectedNavigationItem(savedInstanceState.getInt("tab", 0));
    }
    
    Intent updatePosts = new Intent(this, PostsUpdateService.class);
    startService(updatePosts);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt("tab", getActionBar().getSelectedNavigationIndex());
  }
  
  public void palmipedePost(View v) {
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
  
  private class PrefChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Activity mActivity;
    
    public PrefChangeListener (Activity activity) {
      mActivity = activity;
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPrefs, String key) {
      if (key.contains("_board_enabled")) {
        ActionBar bar = mActivity.getActionBar();
        String b = key.substring(0, key.indexOf("_board_enabled"));
        if (sharedPrefs.getBoolean(key, false)) {
          boolean addIt = true;
          for (int i = 0; i < bar.getTabCount(); i++) {
            Tab tab = bar.getTabAt(i);
            String tag = (String) tab.getTag();
            if (tag != null && tag.equals(b)) {
              addIt = false;
            }
          }
          if (addIt) {
            bar.newTab()
            .setText(b)
            .setTabListener(new TabListener<BoardFragment>(
                mActivity, b, BoardFragment.class));
            Log.d("PrefChangeListener", "Tab " + b + "added");
          }
        } else {
          Tab removeMe = null;
          for (int i = 0; i < bar.getTabCount(); i++) {
            Tab tab = bar.getTabAt(i);
            String tag = (String) tab.getTag();
            if (tag != null && tag.equals(b)) {
              removeMe = tab;
            }
          }
          if (removeMe != null) {
            bar.removeTab(removeMe);
            Log.d("PrefChangeListener", "Tab " + b + "removed");
          }
        }
      }
    }
  }
}