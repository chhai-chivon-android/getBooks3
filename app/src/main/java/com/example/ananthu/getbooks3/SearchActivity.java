package com.example.ananthu.getbooks3;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Toast;

import com.example.ananthu.getbooks3.adapters.BookRecyclerViewAdapter;
import com.example.ananthu.getbooks3.model.Book;
import com.example.ananthu.getbooks3.model.BookBuilder;
import com.example.ananthu.getbooks3.network.GoodreadRequest;
import com.example.ananthu.getbooks3.network.SuccessFailedCallback;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {
    private static final String TAG = SearchActivity.class.getName();

    private List<Book> books = new ArrayList<>();
    private GoodreadRequest mGoodreadRequest;
    private BookRecyclerViewAdapter mAdapter;
    private SearchView bookSearch;
    private RecyclerView recyclerView;
    private ProgressBar loadingIcon;
    private InternalStorage cache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        cache = new InternalStorage(this);
        mGoodreadRequest = new GoodreadRequest(getString(R.string.GR_API_Key), this);

        recyclerView = findViewById(R.id.book_recycler_view);
        loadingIcon = findViewById(R.id.loading_icon);

        // for smooth scrolling in recycler view
        recyclerView.setNestedScrollingEnabled(false);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);


        mAdapter = new BookRecyclerViewAdapter(books);
        recyclerView.setAdapter(mAdapter);

        bookSearch = findViewById(R.id.book_search);

        bookSearch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                query = query.replaceAll(" ", "+");
                Toast.makeText(getApplicationContext(), query, Toast.LENGTH_SHORT).show();
                loadingIcon.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                mAdapter.clear();

                mGoodreadRequest.searchBook(query, new SuccessFailedCallback() {
                    @Override
                    public void success(String response) {
                        List<Integer> bookIds = getBookIdsFromSearchResults(response);

                        for (int i = 0; i < bookIds.size(); i++) {

                            if (cache.getCachedBookById(bookIds.get(i)) == null) {
                                mGoodreadRequest.getBook(bookIds.get(i), new SuccessFailedCallback() {
                                    @Override
                                    public void success(String response) {

                                        loadingIcon.setVisibility(View.GONE);
                                        recyclerView.setVisibility(View.VISIBLE);

                                        Book book = BookBuilder.getBookFromXML(response);
                                        cache.cacheBook(book);
                                        mAdapter.add(book);
                                    }

                                    @Override
                                    public void failed() {
                                        Toast.makeText(
                                                getApplicationContext(),
                                                "some error occurred",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } else {
                                mAdapter.add(cache.getCachedBookById(bookIds.get(i)));

                                loadingIcon.setVisibility(View.GONE);
                                recyclerView.setVisibility(View.VISIBLE);
                            }

                        }
                    }

                    @Override
                    public void failed() {

                    }
                });
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        bookSearch.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    bookSearch.setIconified(true);
                }
            }
        });
    }

    private List<Integer> getBookIdsFromSearchResults(String xmlString) {
        Log.d(TAG, "getBookIdsFromSearchResults: entered");

        List<Integer> bookList = new ArrayList<>();
        XmlPullParserFactory pullParserFactory;

        try {
            pullParserFactory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = pullParserFactory.newPullParser();

            StringReader in_s = new StringReader(xmlString);
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in_s);

            int eventType = parser.getEventType();

            boolean inBook = false;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName;

                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        break;
                    case XmlPullParser.START_TAG:
                        tagName = parser.getName();

                        if (tagName.equals("best_book") || inBook) {
                            inBook = true;
                        } else {
                            eventType = parser.next();
                            continue;
                        }

                        Log.d(TAG, "getBookIdsFromSearchResults: parser - " + tagName);
                        Log.d(TAG, "getBookIdsFromSearchResults: parser - entered best_book");
                        if (tagName.equals("id")) {
                            Log.d(TAG, "getBookIdsFromSearchResults: parser - set id");
                            String id = parser.nextText();
                            bookList.add(Integer.parseInt(id));
                            inBook = false;
                        }
                        break;
                }
                eventType = parser.next();
            }

        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "getBookIdsFromSearchResults: exit");
        return bookList;
    }
}
