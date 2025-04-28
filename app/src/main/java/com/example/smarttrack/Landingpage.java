package com.example.smarttrack;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.graphics.Color;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;

public class Landingpage extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Create a RelativeLayout
        RelativeLayout layout = new RelativeLayout(this);
        layout.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));

        // Set white background
        layout.setBackgroundColor(Color.WHITE);

        // Create an ImageView for the logo
        ImageView logoImageView = new ImageView(this);
        logoImageView.setId(View.generateViewId()); // Assign unique ID
        logoImageView.setImageResource(R.drawable.logo);
        RelativeLayout.LayoutParams logoParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        logoParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        logoImageView.setLayoutParams(logoParams);

        // Create a ProgressBar
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        progressBar.setId(View.generateViewId()); // Assign unique ID
        RelativeLayout.LayoutParams progressParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        progressParams.addRule(RelativeLayout.BELOW, logoImageView.getId());
        progressParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        progressParams.topMargin = 50; // Margin below logo
        progressBar.setLayoutParams(progressParams);

        // Set yellow color for ProgressBar
        progressBar.setIndeterminateTintList(getResources().getColorStateList(R.color.yellow));

        // Add views to layout
        layout.addView(logoImageView);
        layout.addView(progressBar);

        // Set the layout as the content view
        setContentView(layout);

        // Simulate loading with a delay
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(Landingpage.this, Login.class);
            startActivity(intent);
            finish();
        }, 3000); // 3 seconds delay
    }
}
