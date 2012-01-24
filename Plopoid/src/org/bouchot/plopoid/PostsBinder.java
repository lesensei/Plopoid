package org.bouchot.plopoid;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class PostsBinder implements ViewBinder {
  Activity mActivity;
  
  public PostsBinder (Activity activity) {
    mActivity = activity;
  }
  
  private class ClockClicListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      ViewParent vp = v.getParent();
      while (vp.getParent() != null) {
        vp = vp.getParent();
      }
      TextView palmi = (TextView) mActivity.findViewById(R.id.palmipede);
      if (!TextUtils.isEmpty(palmi.getText())) {
        palmi.append(" ");
      }
      palmi.append(((TextView) v).getText() + " ");
    }
  }
  
  @Override
  public boolean setViewValue(View v, Cursor c, int col) {
    if (v.getId() == R.id.time) {
      String text = c.getString(col);
      ((TextView) v).setText(text.substring(8, 10) + ":" + text.substring(10, 12) + ":" + text.substring(12, 14));
      v.setOnClickListener(new ClockClicListener());
      return true;
    } else if (v.getId() == R.id.message) {
      String info = c.getString(c.getColumnIndex(PostsProvider.Posts.COLUMN_NAME_LOGIN));
      if (TextUtils.isEmpty(info)) {
        info = c.getString(c.getColumnIndex(PostsProvider.Posts.COLUMN_NAME_INFO));
        if (info.length() > 10) {
          info = info.substring(0, 10);
        }
      }
      Spannable sInfo = new SpannableString(info + " ");
      sInfo.setSpan(new ForegroundColorSpan(Color.GREEN), 0, info.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      ((TextView) v).setText(sInfo);
      ((TextView) v).append(Html.fromHtml(c.getString(col)));
      ((TextView) v).setMovementMethod(LinkMovementMethod.getInstance());
      return true;
    }
    return false;
  }
}
