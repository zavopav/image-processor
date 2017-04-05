package com.zp.imageprocessor;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.*;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.lambdainvoker.LambdaFunctionException;
import com.amazonaws.mobileconnectors.lambdainvoker.LambdaInvokerFactory;
import com.amazonaws.regions.Regions;
import com.zp.imageprocessor.lambda.ILambdaInvoker;
import com.zp.imageprocessor.lambda.ImageConvertRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends Activity {

    private ILambdaInvoker lambda;
    private ProgressDialog progressDialog;
    private GestureDetector gestureDetector;
    private Context context;
    private ViewFlipper viewFlipper;
    private Spinner spinner;
    private Button processButton;
    private Button resetButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        gestureDetector = new GestureDetector(context, new SwipeGestureDetector());
        viewFlipper = (ViewFlipper) findViewById(R.id.view_flipper);
        viewFlipper.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View view, final MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });

        spinner = (Spinner) findViewById(R.id.filter_picker);
        resetButton = (Button) findViewById(R.id.reset_button);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                reset();
            }
        });

        processButton = (Button) findViewById(R.id.process_button);
        processButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                processImage();
            }
        });

        // Initialize the Amazon Cognito credentials provider
        final CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "eu-central-1:68bd7893-5068-4c77-8c0c-c0fa88484a5f", // Identity Pool ID
                Regions.EU_CENTRAL_1 // Region
        );
        // Create LambdaInvokerFactory, to be used to instantiate the Lambda proxy.
        final LambdaInvokerFactory factory = new LambdaInvokerFactory(this.getApplicationContext(),
                Regions.EU_CENTRAL_1, credentialsProvider);

        // Create the Lambda proxy object with a default Json data binder.
        lambda = factory.build(ILambdaInvoker.class);

        // ping lambda function to make sure everything is working
        pingLambda();
    }

    // ping the lambda function
    private void pingLambda() {
        final Map<String, String> event = new HashMap<>();
        event.put("operation", "ping");

        // The Lambda function invocation results in a network call.
        // Make sure it is not called from the main thread.
        new AsyncTask<Map, Void, String>() {
            @Override
            protected String doInBackground(Map... params) {
                // invoke "ping" method. In case it fails, it will throw a
                // LambdaFunctionException.
                try {
                    return lambda.ping(params[0]);
                } catch (LambdaFunctionException lfe) {
                    Log.e("Tag", "Failed to invoke ping", lfe);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (result == null) {
                    return;
                }

                // Display a quick message
                Toast.makeText(MainActivity.this, "Made contact with AWS lambda: " + result, Toast.LENGTH_LONG).show();
            }
        }.execute(event);
    }

    private void setControlsEnabled(boolean enabled) {
        spinner.setEnabled(enabled);
        viewFlipper.setEnabled(enabled);
        processButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
    }

    // event handler for "process image" button
    private void processImage() {
        setControlsEnabled(false);
        final LinearLayout layout = (LinearLayout) viewFlipper.getCurrentView();
        final ImageView imageView = (ImageView) layout.getChildAt(0);
        final BitmapDrawable drawable = (BitmapDrawable) imageView.getDrawable();

        final String selectedImageBase64;
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            drawable.getBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream);
            selectedImageBase64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Processing failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        // get selected filter
        final String filter = spinner.getSelectedItem().toString();
        // assemble new request
        final ImageConvertRequest request = new ImageConvertRequest();
        request.setBase64Image(selectedImageBase64);
        request.setInputExtension("png");
        request.setOutputExtension("png");

        // custom arguments per filter
        final List<String> customArgs = new ArrayList<>();
        request.setCustomArgs(customArgs);
        switch (filter) {
            case "Sepia":
                customArgs.add("-sepia-tone");
                customArgs.add("65%");
                break;
            case "Black/White":
                customArgs.add("-colorspace");
                customArgs.add("Gray");
                break;
            case "Negate":
                customArgs.add("-negate");
                break;
            case "Darken":
                customArgs.add("-fill");
                customArgs.add("black");
                customArgs.add("-colorize");
                customArgs.add("50%");
                break;
            case "Lighten":
                customArgs.add("-fill");
                customArgs.add("white");
                customArgs.add("-colorize");
                customArgs.add("50%");
                break;
            default:
                return;
        }

        // async request to lambda function
        new AsyncTask<ImageConvertRequest, Void, String>() {
            @Override
            protected String doInBackground(ImageConvertRequest... params) {
                try {
                    return lambda.convert(params[0]);
                } catch (LambdaFunctionException e) {
                    Log.e("Tag", "Failed to convert image");
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                try {
                    // if no data was returned, there was a failure
                    if (result == null || Objects.equals(result, "")) {
                        hideLoadingDialog();
                        Toast.makeText(MainActivity.this, "Processing failed", Toast.LENGTH_LONG).show();
                        return;
                    }
                    // otherwise decode the base64 data and put it in the selected image view
                    final byte[] imageData = Base64.decode(result, Base64.DEFAULT);
                    imageView.setImageBitmap(BitmapFactory.decodeByteArray(imageData, 0, imageData.length));
                } finally {
                    setControlsEnabled(true);
                    hideLoadingDialog();
                }
            }
        }.execute(request);

        showLoadingDialog();
    }

    // reset images to their original state
    private void reset() {
        ((ImageView) findViewById(R.id.static_moto)).setImageDrawable(getResources().getDrawable(R.drawable.moto, getTheme()));
        ((ImageView) findViewById(R.id.static_present)).setImageDrawable(getResources().getDrawable(R.drawable.present, getTheme()));
        ((ImageView) findViewById(R.id.static_sherlock)).setImageDrawable(getResources().getDrawable(R.drawable.sherlock, getTheme()));
        ((ImageView) findViewById(R.id.static_tim_burton)).setImageDrawable(getResources().getDrawable(R.drawable.tim_burton, getTheme()));
    }

    private void showLoadingDialog() {
        progressDialog = ProgressDialog.show(this, "Please wait...", "Processing image", true, false);
    }

    private void hideLoadingDialog() {
        progressDialog.dismiss();
    }

    class SwipeGestureDetector extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_MIN_DISTANCE = 120;
        private static final int SWIPE_THRESHOLD_VELOCITY = 200;
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            final float deltaX = e2.getX() - e1.getX();

            if (Math.abs(velocityX) < SWIPE_THRESHOLD_VELOCITY || Math.abs(deltaX) < SWIPE_MIN_DISTANCE) {
                return false;
            }

            try {
                if (deltaX < 0) {
                    // right to left swipe
                    viewFlipper.setInAnimation(AnimationUtils.loadAnimation(context, R.anim.in_from_right));
                    viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(context, R.anim.out_to_left));
                    viewFlipper.showNext();
                } else {
                    // left to right swipe
                    viewFlipper.setInAnimation(AnimationUtils.loadAnimation(context, R.anim.in_from_left));
                    viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(context, R.anim.out_to_right));
                    viewFlipper.showPrevious();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }
    }
}