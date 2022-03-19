/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/
package co.fedspam.sms.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import co.fedspam.android_transformers.R;
import co.fedspam.sms.ml.BertClient;

public class MainActivity extends AppCompatActivity {
    private static final boolean DISPLAY_RUNNING_TIME = true;
    private static final String TAG = "SpamClassificationActivity";

    private Handler handler;
    private BertClient bertClient;
    private CoordinatorLayout layout;
    private TextView predictionResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        TextInputEditText smsText = findViewById(R.id.input_box);
        Button predictButton = findViewById(R.id.predict_button);
        layout = findViewById(R.id.cord_layout);
        predictionResult = findViewById(R.id.predition_text);

        predictButton.setOnClickListener(view -> predictSpam(smsText.getText().toString()));

        // Setup QA client to and background thread to run inference.
        HandlerThread handlerThread = new HandlerThread("QAClient");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        bertClient = new BertClient(this);
    }

    @Override
    protected void onStart() {
        Log.v(TAG, "onStart");
        super.onStart();
        handler.post(
                () -> {
                    bertClient.loadModel();
                    bertClient.loadDictionary();
                });
    }

    @Override
    protected void onStop() {
        Log.v(TAG, "onStop");
        super.onStop();
        handler.post(() -> bertClient.unload());
    }

    private void predictSpam(String query) {
        query = query.trim();
        if (query.isEmpty()) {
            return;
        }

        // Delete all pending tasks.
        handler.removeCallbacksAndMessages(null);

        // Hide keyboard and dismiss focus on text edit.
        InputMethodManager imm =
                (InputMethodManager) getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
        View focusView = getCurrentFocus();
        if (focusView != null) {
            focusView.clearFocus();
        }

        Snackbar runningSnackbar =
                Snackbar.make(layout, "Checking for SPAM...", Snackbar.LENGTH_INDEFINITE);
        runningSnackbar.show();


        // Run TF Lite model to get the answer.
        String finalQuery = query;
        handler.post(
                () -> {
                    long beforeTime = System.currentTimeMillis();
                    final float probablity = bertClient.predict(finalQuery);
                    long afterTime = System.currentTimeMillis();
                    double totalSeconds = (afterTime - beforeTime) / 1000.0;
                    runOnUiThread(
                            () -> {
                                runningSnackbar.dismiss();

                                String displayMessage = "Message Classified In ";
                                if (DISPLAY_RUNNING_TIME) {
                                    displayMessage = String.format("%s %.3f sec.", displayMessage, totalSeconds);
                                }
                                Snackbar.make(layout, displayMessage, Snackbar.LENGTH_INDEFINITE).show();

                                // set text to text field
                                predictionResult.setText(String.format("%s %.3f.", probablity > 0.5 ? "Spam" : "Ham", probablity));
                            });

                });
    }
}
