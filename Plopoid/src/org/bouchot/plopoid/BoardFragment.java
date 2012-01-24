package org.bouchot.plopoid;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.SimpleCursorAdapter;

public class BoardFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {
  String board;
  SimpleCursorAdapter sca;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (getArguments() != null) {
      this.board = getArguments().getString("board");
    } else {
      this.board = getTag();
    }
    final String[] COLS = {
        PostsProvider.Posts.COLUMN_NAME_TIME,
        PostsProvider.Posts.COLUMN_NAME_MESSAGE
    };
    final int[] FIELDS = {
        R.id.time,
        R.id.message
    };
    sca = new SimpleCursorAdapter(getActivity(), R.layout.post_item,
        null, COLS, FIELDS, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
    sca.setViewBinder(new PostsBinder(getActivity()));
    setListAdapter(sca);

    getLoaderManager().initLoader(0, null, this);
  }
  
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.board_view, container, false);
    return v;
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(getActivity(), PostsProvider.Posts.CONTENT_URI, null, "board = '" + this.board + "'", null, null);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    sca.swapCursor(data);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    sca.swapCursor(null);
  }
}