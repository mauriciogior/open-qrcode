
/*
 * MIT License
 *
 * Copyright (c) 2019 Mauricio Giordano <giordano@inevent.us>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.mauriciogior.openqrcode;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.Result;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class MainActivity extends AppCompatActivity implements ZXingScannerView.ResultHandler {

    public final int REQ_CAMERA = 0x1;

    private ZXingScannerView mScannerView;
    private ListView mListView;
    private ArrayAdapter<String> mArrayAdapter;

    private int menu = R.id.navigation_camera;
    private List<Integer> checkedItems;

    private ListView.MultiChoiceModeListener multiChoiceModeListener = new AbsListView.MultiChoiceModeListener() {
        @Override
        public void onItemCheckedStateChanged(ActionMode actionMode, int position, long id, boolean checked) {
            if (checkedItems.contains(position)) {
                if (!checked) {
                    checkedItems.remove((Integer) position);
                }
            } else if (checked) {
                checkedItems.add(position);
            }

            if (checkedItems.size() > 1) {
                actionMode.setTitle(checkedItems.size() + " items selected");
            } else {
                actionMode.setTitle(checkedItems.size() + " item selected");
            }
        }

        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            checkedItems = new ArrayList<>();
            getMenuInflater().inflate(R.menu.action_mode, menu);
            setSimpleListMultipleChoice();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(final ActionMode actionMode, MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.action_remove) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Remove")
                    .setMessage("Are you sure you want to remove " + checkedItems.size() + " item" + (checkedItems.size() > 1 ? "s" : "") + "?")
                    .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int position) {
                            for (Integer i : checkedItems) {
                                String qrCode = (String) mArrayAdapter.getItem(i);
                                removeHistory(qrCode);
                            }

                            actionMode.finish();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show();
                return true;

            } else if (menuItem.getItemId() == R.id.action_select_all) {
                for (int i = 0; i < mListView.getCount(); i++) {
                    mListView.setItemChecked(i, true);
                }

                return true;
            }

            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            setSimpleList();
        }
    };

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            if (menu == item.getItemId()) return false;
            menu = item.getItemId();
            switch (item.getItemId()) {
                case R.id.navigation_camera:
                    mListView.setVisibility(View.GONE);
                    mScannerView.startCamera();
                    return true;
                case R.id.navigation_history:
                    mListView.setVisibility(View.VISIBLE);
                    mScannerView.stopCamera();
                    setSimpleList();
                    mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            showAlertDialog((String) adapterView.getItemAtPosition(i));
                        }
                    });

                    return true;
            }
            return false;
        }
    };

    private void mounted() {
        if (menu == R.id.navigation_camera) {
            mScannerView.startCamera();
            mScannerView.setAspectTolerance(0.5f);
        }
    }

    private void setSimpleList() {
        int index = mListView.getFirstVisiblePosition();
        View v = mListView.getChildAt(0);
        int top = (v == null) ? 0 : (v.getTop() - mListView.getPaddingTop());

        mArrayAdapter = new ArrayAdapter<String>(MainActivity.this,
                android.R.layout.simple_list_item_1, android.R.id.text1, new ArrayList<>(getHistory()));
        mListView.setAdapter(mArrayAdapter);

        mListView.setSelectionFromTop(index, top);
    }

    private void setSimpleListMultipleChoice() {
        int index = mListView.getFirstVisiblePosition();
        View v = mListView.getChildAt(0);
        int top = (v == null) ? 0 : (v.getTop() - mListView.getPaddingTop());

        mArrayAdapter = new ArrayAdapter<String>(MainActivity.this,
                android.R.layout.simple_list_item_multiple_choice, android.R.id.text1, new ArrayList<>(getHistory()));
        mListView.setAdapter(mArrayAdapter);

        mListView.setSelectionFromTop(index, top);
    }

    private Set<String> getHistory() {
        SharedPreferences sp = getSharedPreferences(this.getClass().getName(), MODE_PRIVATE);
        return new HashSet<String>(sp.getStringSet("history", new HashSet<String>()));
    }

    private void removeHistory(String qrCode) {
        Set<String> set = getHistory();
        set.remove(qrCode);

        SharedPreferences.Editor editor = getSharedPreferences(this.getClass().getName(), MODE_PRIVATE).edit();
        editor.putStringSet("history", set);
        editor.apply();
    }

    private void pushHistory(String qrCode) {
        Set<String> set = getHistory();
        set.add(qrCode);

        SharedPreferences.Editor editor = getSharedPreferences(this.getClass().getName(), MODE_PRIVATE).edit();
        editor.putStringSet("history", set);
        editor.apply();
    }

    private void setClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Text copied to clipboard!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Failed to copy text.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Open QR Code");
        }

        mScannerView = findViewById(R.id.scannerView);
        mListView = findViewById(R.id.listView);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setMultiChoiceModeListener(multiChoiceModeListener);

        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        if (savedInstanceState != null) {
            menu = savedInstanceState.getInt("menu", R.id.navigation_camera);
        }

        // If permission not given
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, REQ_CAMERA);

        } else {
            mounted();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mounted();
            } else {
                Toast.makeText(this, "You must provide camera permission!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt("menu", menu);
    }

    @Override
    public void onResume() {
        super.onResume();
        mScannerView.setResultHandler(this);
        mScannerView.startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        mScannerView.stopCamera();
    }

    @Override
    public void handleResult(Result result) {
        pushHistory(result.getText());
        showAlertDialog(result.getText(), true);
    }

    private void showAlertDialog(final String text) {
        showAlertDialog(text, false);
    }

    private void showAlertDialog(final String text, final boolean resumeCamera) {
        final SpannableString message = new SpannableString(text);
        Linkify.addLinks(message, Linkify.ALL);

        pushHistory(text);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("QR Code")
            .setMessage(message)
            .setPositiveButton("Copy", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    setClipboard(text);
                }
            })
            .setNeutralButton("Share", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, text);
                    sendIntent.setType("text/plain");
                    startActivity(sendIntent);
                }
            })
            .setNegativeButton("Cancel", null)
            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    if (resumeCamera) {
                        mScannerView.resumeCameraPreview(MainActivity.this);
                    }
                }
            })
            .show();

        ((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(this).inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_info) {
            new AlertDialog.Builder(this)
                .setTitle("Info")
                .setMessage("Created by a pissed of developer from apps loaded with ads.\n\n" +
                 "Mauricio Giordano <giordano@inevent.us>\n\n" +
                 "Open source software under MIT License")
                .setNegativeButton("Ok", null)
                .show();
        }
        return super.onOptionsItemSelected(item);
    }
}
