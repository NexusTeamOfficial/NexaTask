package com.nexusteam.nexatask.demo;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nexusteam.nexatask.NexaTask;
import com.nexusteam.nexatask.model.TaskPriority;

import java.util.concurrent.TimeUnit;

/**
 * Minimal demo: button → background work → spinner → result/error.
 * Pass {@code this} as host so UI callbacks are skipped after destroy (rotation / split-screen safe).
 */
public class MainActivity extends AppCompatActivity {

    private Button btnRun;
    private ProgressBar progressBar;
    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnRun = findViewById(R.id.btnRun);
        progressBar = findViewById(R.id.progressBar);
        tvResult = findViewById(R.id.tvResult);

        btnRun.setOnClickListener(v -> runDemoTask());
    }

    private void runDemoTask() {
        btnRun.setEnabled(false);

        NexaTask.<Void, Integer, String>lambda(this)
                .doInBackground(() -> {
                    // Simulate network / heavy work (background thread)
                    for (int i = 0; i <= 100; i += 20) {
                        Thread.sleep(300);
                        // progress optional — publish via subclass if needed
                    }
                    return "Task finished at " + System.currentTimeMillis();
                })
                .onPreExecute(() -> {
                    progressBar.setVisibility(View.VISIBLE);
                    tvResult.setText("Running…");
                })
                .onPostExecute(result -> {
                    progressBar.setVisibility(View.GONE);
                    btnRun.setEnabled(true);
                    tvResult.setText(result);
                })
                .onError(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnRun.setEnabled(true);
                    tvResult.setText("Error: " + e.getMessage());
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                })
                .onCancelled(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnRun.setEnabled(true);
                    tvResult.setText("Cancelled");
                })
                .setTimeout(15, TimeUnit.SECONDS)
                .setPriority(TaskPriority.NORMAL)
                .execute();
    }
}
