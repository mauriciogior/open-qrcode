
/*
 * Copyright (c) 2018 Mauricio Giordano <giordano@inevent.us>.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.Result;

import java.util.HashSet;
import java.util.Set;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class MainActivity extends AppCompatActivity implements ZXingScannerView.ResultHandler {

    public final int REQ_CAMERA = 0x1;

    private ZXingScannerView mScannerView;
    private ListView mListView;
    private int menu = R.id.navigation_camera;

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

                    mListView.setAdapter(new ArrayAdapter<String>(MainActivity.this,
                        android.R.layout.simple_list_item_1, android.R.id.text1, getHistory().toArray(new String[getHistory().size()])));
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

    private Set<String> getHistory() {
        SharedPreferences sp = getSharedPreferences(this.getClass().getName(), MODE_PRIVATE);
        return new HashSet<String>(sp.getStringSet("history", new HashSet<String>()));
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
                 "Open source software under Apache GPLv2")
                .setNegativeButton("Ok", null)
                .show();
        }
        return super.onOptionsItemSelected(item);
    }
}
