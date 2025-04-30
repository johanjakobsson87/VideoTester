package com.example.videotester;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.util.*;
import java.util.Locale;
import android.os.Environment;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "VideoTester";
    private VideoView videoView;
    private LinearLayout ratingLayout;
    private RadioGroup videoQualityGroup, soundQualityGroup, audiovisualQualityGroup;
    private Button submitButton, startRealTestButton;
    private int currentVideoIndex = 0;
    private List<String> playlist = new ArrayList<>();
    private boolean isTrainingSession = true;
    private int testerId = -1;
    private String trainingPlaylistName = "";
    private String realPlaylistName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide action bar
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        setContentView(R.layout.activity_main);

        videoView = findViewById(R.id.videoView);

        videoView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Restart Test")
                    .setMessage("Are you sure you want to restart the test?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        clearSavedState();
                        recreate();
                    })
                    .setNegativeButton("No", null)
                    .show();
            return true; // consume the long click
        });


        ratingLayout = findViewById(R.id.ratingLayout);
        videoQualityGroup = findViewById(R.id.video_quality_group);
        soundQualityGroup = findViewById(R.id.sound_quality_group);
        audiovisualQualityGroup = findViewById(R.id.audiovisual_quality_group);
        submitButton = findViewById(R.id.submit_rating_button);

        startRealTestButton = new Button(this);
        startRealTestButton.setText("Start Real Test");
        startRealTestButton.setVisibility(View.GONE);
        ((RelativeLayout) findViewById(R.id.mainLayout)).addView(startRealTestButton);

        submitButton.setOnClickListener(v -> submitRatings());

        videoView.setOnCompletionListener(mp -> {
            videoView.setVisibility(View.GONE);
            ratingLayout.setVisibility(View.VISIBLE);
        });

        promptForTesterId();

        startRealTestButton.setOnClickListener(v -> {
            isTrainingSession = false;
            startRealTestButton.setVisibility(View.GONE);
            currentVideoIndex = 0;
            loadPlaylistFromAssets(realPlaylistName);
            playNextVideo();
        });
    }

    private void clearSavedState() {
        testerId = -1;
        currentVideoIndex = 0;
        isTrainingSession = true;
        playlist.clear();
    }


    @Override
    protected void onPause() {
        super.onPause();

        //Some stuff to save the current state
        SharedPreferences preferences = getSharedPreferences("VideoTesterPreferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("currentVideoIndex", currentVideoIndex);
        editor.putInt("testerId", testerId);
        editor.putString("trainingPlaylistName", trainingPlaylistName);
        editor.putString("realPlaylistName", realPlaylistName);
        editor.putBoolean("isTrainingSession", isTrainingSession);
        editor.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();

        // Restore the saved state
        SharedPreferences preferences = getSharedPreferences("VideoTesterPreferences", MODE_PRIVATE);
        testerId = preferences.getInt("testerId", -1); // Default to -1 if not found
        currentVideoIndex = preferences.getInt("currentVideoIndex", 0);
        trainingPlaylistName = preferences.getString("trainingPlaylistName", "");
        realPlaylistName = preferences.getString("realPlaylistName", "");
        isTrainingSession = preferences.getBoolean("isTrainingSession", true);

        // If there is a valid tester ID, reload the playlist and start the video session
        if (testerId != -1) {
            loadPlaylistFromAssets(trainingPlaylistName);
            playNextVideo();
        }
    }





    private void promptForTesterId() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Tester ID");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setCancelable(false);
        builder.setPositiveButton("OK", (dialog, which) -> {
            testerId = Integer.parseInt(input.getText().toString().trim());
            trainingPlaylistName = "playlist" + testerId + "0.cfg";
            realPlaylistName = "playlist" + testerId + "1.cfg";

            loadPlaylistFromAssets(trainingPlaylistName);
            playNextVideo();
        });

        builder.show();
    }

    private void loadPlaylistFromAssets(String filename) {
        playlist.clear();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(filename)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                playlist.add(line.trim());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading " + filename, e);
        }
    }

    private void playNextVideo() {
        enterImmersiveMode();

        if (currentVideoIndex < playlist.size()) {
            String videoFileName = playlist.get(currentVideoIndex);
            String resourceName = videoFileName.contains(".") ? videoFileName.substring(0, videoFileName.lastIndexOf('.')) : videoFileName;

            Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/raw/" + resourceName);
            videoView.setVideoURI(videoUri);

            videoView.setVisibility(View.VISIBLE);
            ratingLayout.setVisibility(View.GONE);
            videoView.start();


        } else {
            videoView.setVisibility(View.GONE);
            ratingLayout.setVisibility(View.GONE);

            if (isTrainingSession) {
                Toast.makeText(this, "Training completed. Press Start Real Test to begin.", Toast.LENGTH_LONG).show();
                startRealTestButton.setVisibility(View.VISIBLE);
            } else {
                RelativeLayout layout = findViewById(R.id.mainLayout);

                // Thank you message
                TextView thanks = new TextView(this);
                thanks.setText("Thank you for rating all videos!");
                thanks.setTextSize(20);
                thanks.setTextColor(Color.WHITE);
                thanks.setPadding(10, 10, 10, 10);
                thanks.setId(View.generateViewId());
                layout.addView(thanks);
                // Exports ratings file to Downloads folder
                exportRatingsFileToDownloads();

                // Restart button
                Button restartButton = new Button(this);
                restartButton.setText("Restart");
                restartButton.setPadding(10, 10, 10, 10);

                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                );
                params.addRule(RelativeLayout.BELOW, thanks.getId());
                params.addRule(RelativeLayout.CENTER_HORIZONTAL);
                params.setMargins(0, 30, 0, 0);

                layout.addView(restartButton, params);

                restartButton.setOnClickListener(v -> manualRestart());

            }
        }
    }

    private void submitRatings() {
        int videoRating = getSelectedRating(videoQualityGroup);
        int soundRating = getSelectedRating(soundQualityGroup);
        int audiovisualRating = getSelectedRating(audiovisualQualityGroup);

        if(videoRating == -1 || soundRating == -1 || audiovisualRating == -1){
            Toast.makeText(this, "Please select all ratings.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Reset radio buttons
        videoQualityGroup.clearCheck();
        soundQualityGroup.clearCheck();
        audiovisualQualityGroup.clearCheck();


        Log.d(TAG, "File name: " + playlist.get(currentVideoIndex - 1));
        Log.d(TAG, "Video Quality Rating: " + videoRating);
        Log.d(TAG, "Sound Quality Rating: " + soundRating);
        Log.d(TAG, "Audiovisual Quality Rating: " + audiovisualRating);

        if (!isTrainingSession) {
            saveRatingsToCsv(videoRating, soundRating, audiovisualRating);
        }
        currentVideoIndex++;
        playNextVideo();
    }

    private int getSelectedRating(RadioGroup group) {
        int selectedId = group.getCheckedRadioButtonId();
        if (selectedId == -1) return -1;
        RadioButton selectedButton = findViewById(selectedId);
        return Integer.parseInt(selectedButton.getText().toString());
    }

    private void saveRatingsToCsv(int videoRating, int soundRating, int audiovisualRating) {
        String fileName = "ratings.csv";
        /*String entry = "Tester ID: " + testerId + ", File: " + playlist.get(currentVideoIndex - 1)
                + ", Video: " + videoRating
                + ", Sound: " + soundRating
                + ", AV: " + audiovisualRating + "\n";

        String entry = String.format(Locale.getDefault(),
                "Tester ID: \t %d \n File: \t %s \n Video: \t %d \n Sound: \t %d \n AV: \t %d \n",
                testerId, playlist.get(currentVideoIndex - 1), videoRating, soundRating, audiovisualRating
        );*/
        String entry = String.format(Locale.getDefault(),
                "%d, %s, %d, %d, %d\n",
                testerId, playlist.get(currentVideoIndex - 1), videoRating, soundRating, audiovisualRating
        );



        try (FileOutputStream fos = openFileOutput(fileName, MODE_APPEND)) {
            fos.write(entry.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void manualRestart() {
        clearSavedState();
        recreate(); // Restart activity and go back to tester ID input
    }
    private void exportRatingsFileToDownloads() {
        File internalFile = new File(getFilesDir(), "ratings.csv");
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File targetFile = new File(downloadsDir, "ratings.csv");

        try (FileInputStream in = new FileInputStream(internalFile);
             FileOutputStream out = new FileOutputStream(targetFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            Toast.makeText(this, "Ratings file exported to Downloads", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to export ratings file", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    private void enterImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }


}
