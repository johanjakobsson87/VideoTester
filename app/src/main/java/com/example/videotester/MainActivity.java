package com.example.videotester;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
        if (currentVideoIndex < playlist.size()) {
            String videoFileName = playlist.get(currentVideoIndex);
            String resourceName = videoFileName.contains(".") ? videoFileName.substring(0, videoFileName.lastIndexOf('.')) : videoFileName;

            Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/raw/" + resourceName);
            videoView.setVideoURI(videoUri);

            videoView.setVisibility(View.VISIBLE);
            ratingLayout.setVisibility(View.GONE);
            videoView.start();

            currentVideoIndex++;
        } else {
            videoView.setVisibility(View.GONE);
            ratingLayout.setVisibility(View.GONE);

            if (isTrainingSession) {
                Toast.makeText(this, "Training completed. Press Start Real Test to begin.", Toast.LENGTH_LONG).show();
                startRealTestButton.setVisibility(View.VISIBLE);
            } else {
                TextView thanks = new TextView(this);
                thanks.setText("Thank you for rating all videos!");
                thanks.setTextSize(20);
                thanks.setTextColor(Color.WHITE);
                thanks.setPadding(10, 10, 10, 10);
                ((RelativeLayout) findViewById(R.id.mainLayout)).addView(thanks);
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
        String entry = "Tester ID: " + testerId + ", File: " + playlist.get(currentVideoIndex - 1)
                + ", Video: " + videoRating
                + ", Sound: " + soundRating
                + ", AV: " + audiovisualRating + "\n";
        try (FileOutputStream fos = openFileOutput(fileName, MODE_APPEND)) {
            fos.write(entry.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
