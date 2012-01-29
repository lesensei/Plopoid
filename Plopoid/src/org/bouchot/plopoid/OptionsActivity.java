package org.bouchot.plopoid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.CheckBoxPreference;
import android.text.InputType;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;

public class OptionsActivity extends PreferenceActivity {
  private PreferenceCategory boardsPrefs;
  private SharedPreferences sharedPrefs;
  
  private class XmlBoardsHandler implements ContentHandler {
    private static final String SITE_TAG = "site";
    private static final String COOKIE_TAG = "cookie";
    private static final String NAME_ATTR = "name";
    
    private String board;
    private Editor prefsEdit;
    private Activity mActivity;
    private boolean mFirst;
    
    public XmlBoardsHandler(Activity activity, boolean isFirst) {
      mFirst = isFirst;
      mActivity = activity;
      board = "";
      prefsEdit = sharedPrefs.edit();
      Log.d("XmlBoardsHandler", "BoardsPrefs: " + (boardsPrefs != null ? boardsPrefs.toString() : "null"));
    }
    
    @Override
    public void setDocumentLocator(Locator locator) {
      // We don't use a locator
    }

    @Override
    public void startDocument() throws SAXException {
      // Do nothing
    }

    @Override
    public void endDocument() throws SAXException {
      prefsEdit.commit();
    }
    
    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
      // Our XML is nothing like real-world XML, there aren't any namespaces or anything
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
      // Same as above
    }

    @Override
    public void startElement(String nsUri, String lName, String qName, Attributes attrs) throws SAXException {
      if (lName.equals(SITE_TAG)) {
        board = attrs.getValue("", NAME_ATTR);
        PreferenceScreen boardScreen = getPreferenceManager().createPreferenceScreen(mActivity);
        boardScreen.setTitle(board);
        CheckBoxPreference boardEnable = new CheckBoxPreference(mActivity);
        boardEnable.setKey(board + "_board_enabled");
        boardEnable.setTitle(R.string.board_checkbox);
        boardEnable.setPersistent(true);
        boardEnable.setDefaultValue(false);
        boardEnable.setSummaryOn(R.string.board_enabled);
        boardEnable.setSummaryOff(R.string.board_disabled);
        boardScreen.addPreference(boardEnable);
        CheckBoxPreference customLogin = new CheckBoxPreference(mActivity);
        customLogin.setKey(board + "_custom_login");
        customLogin.setTitle(R.string.custom_login_title);
        customLogin.setPersistent(true);
        customLogin.setDefaultValue(false);
        customLogin.setSummaryOn(R.string.custom_login_enabled);
        customLogin.setSummaryOff(R.string.custom_login_disabled);
        boardScreen.addPreference(customLogin);
        EditTextPreference loginPref = new EditTextPreference(mActivity);
        loginPref.setKey(board + "_login");
        loginPref.setTitle(R.string.custom_login_text_title);
        loginPref.setPersistent(true);
        loginPref.setSummary(R.string.custom_login_summary);
        loginPref.setDialogTitle(R.string.custom_login_text_title);
        //loginPref.setDependency(board + "_custom_login");
        boardScreen.addPreference(loginPref);
        EditTextPreference passwordPref = new EditTextPreference(mActivity);
        passwordPref.setKey(board + "_password");
        passwordPref.setTitle(R.string.custom_password_text_title);
        passwordPref.setPersistent(true);
        passwordPref.setSummary(R.string.custom_password_summary);
        passwordPref.setDialogTitle(R.string.custom_password_text_title);
        //passwordPref.setDependency(board + "_custom_login");
        passwordPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        boardScreen.addPreference(passwordPref);
        boardsPrefs.addPreference(boardScreen);
      } else if (mFirst && lName.equals(COOKIE_TAG)) {
        String oldCookies = getPreferenceManager().getSharedPreferences().getString(board + "_login_cookie_name", "");
        String newCookies = (oldCookies.equals("") ? "" : oldCookies + ";") + attrs.getValue("", NAME_ATTR);
        prefsEdit.putString(board + "_login_cookie_name", newCookies);
      }
    }

    @Override
    public void endElement(String nsUri, String lName, String qName) throws SAXException {
    }
    
    @Override
    public void characters(char[] arg0, int arg1, int arg2) throws SAXException {
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
      // Nothing interesting for us here
    }
  }
  
  private class DownloadBoardsTask extends AsyncTask<String, Void, File> {
    private Activity mActivity;
    
    public DownloadBoardsTask(Activity activity) {
      super();
      mActivity = activity;
    }
    
    @Override
    protected File doInBackground(String... url) {
      AndroidHttpClient httpClient = AndroidHttpClient.newInstance(getPreferenceManager().getSharedPreferences().getString("user_agent", getString(R.string.user_agent_default)));
      HttpResponse res;
      try {
        res = httpClient.execute(new HttpGet(url[0]));
      } catch (IOException e) {
        e.printStackTrace();
        httpClient.close();
        return null;
      }
      HttpEntity entity = res.getEntity();
      File boardsFile = null;
      try {
        if (entity == null) {
          throw new IOException("Got empty response for URL " + url);
        }
        boardsFile = new File(getCacheDir(), "boards.xml");
        InputStream in = entity.getContent();
        boardsFile.createNewFile();
        FileOutputStream out = new FileOutputStream(boardsFile);
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        out.close();
        in.close();
      } catch (IOException e) {
        Toast.makeText(getApplicationContext(), "Error while caching boards configuration data.", Toast.LENGTH_LONG).show();
        e.printStackTrace();
      }
      httpClient.close();
      return boardsFile;
    }

    @Override
    protected void onPostExecute(File boardsFile) {
      super.onPostExecute(boardsFile);
      InputStream in;
      try {
        in = new FileInputStream(boardsFile);
        Xml.parse(in, Xml.Encoding.UTF_8, new XmlBoardsHandler(mActivity, true));
      } catch (FileNotFoundException e) {
        Toast.makeText(getApplicationContext(), "Error while retrieving boards configuration data.", Toast.LENGTH_LONG).show();
        e.printStackTrace();
      } catch (IOException e) {
        Toast.makeText(getApplicationContext(), "Error while retrieving boards configuration data.", Toast.LENGTH_LONG).show();
        e.printStackTrace();
      } catch (SAXException e) {
        Toast.makeText(getApplicationContext(), "Error while loading boards configuration data.", Toast.LENGTH_LONG).show();
        e.printStackTrace();
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
    sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    boardsPrefs = (PreferenceCategory) findPreference("boards_prefs_category");
    String url = getPreferenceManager().getSharedPreferences().getString("olccs_base_uri", getString(R.string.olccs_base_uri_default)) + "boards.xml";
    File boardsFile = new File(getCacheDir(), "boards.xml");
    if (!boardsFile.exists()) {
      new DownloadBoardsTask(this).execute(url);
    } else {
      try {
        if (boardsFile.length() == 0) {
          throw new IOException("Boards configuration file is empty.");
        }
        InputStream in = new FileInputStream(boardsFile);
        Xml.parse(in, Xml.Encoding.UTF_8, new XmlBoardsHandler(this, false));
      } catch (IOException e) {
        Toast.makeText(getApplicationContext(), "Error while retrieving boards configuration data.", Toast.LENGTH_LONG).show();
        e.printStackTrace();
      } catch (IllegalStateException e) {
        Toast.makeText(getApplicationContext(), "Error while loading boards configuration data.", Toast.LENGTH_LONG).show();
        e.printStackTrace();
      } catch (SAXException e) {
        Toast.makeText(getApplicationContext(), "Error while loading boards configuration data.", Toast.LENGTH_LONG).show();
        e.printStackTrace();
      }
    }
  }
}