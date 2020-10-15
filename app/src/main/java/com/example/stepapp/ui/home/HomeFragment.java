package com.example.stepapp.ui.home;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.stepapp.R;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public class HomeFragment extends Fragment {
    MaterialButtonToggleGroup materialButtonToggleGroup;

//    Text view and Progress Bar variables
    public TextView stepsCountTextView;
    public ProgressBar stepsCountProgressBar;

    private SensorEventListener listener;

//    ACC sensors.
    private Sensor mSensorACC;
    private SensorManager mSensorManager;


//    Step Detector sensor
    private Sensor mSensorSTEP;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_home, container, false);

//        Initialize the TextView variable
        stepsCountTextView = (TextView) root.findViewById(R.id.stepsCount);
//        Initialize the ProgressBar variable
        stepsCountProgressBar = (ProgressBar) root.findViewById(R.id.progressBar);

//        Get an instance of the sensor manager.
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
//        get the default sensor for linear acceleration (gravity force subtracted)
//        the value can be null if the sensor doesn't exist
        mSensorACC = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);


//        get the default step detector sensor
        mSensorSTEP = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

//        instantiate the StepCounterListener
        listener = new StepCounterListener(stepsCountTextView, stepsCountProgressBar);

//        Toggle group button
        materialButtonToggleGroup = (MaterialButtonToggleGroup) root.findViewById(R.id.toggleButtonGroup);
        materialButtonToggleGroup.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if (group.getCheckedButtonId() == R.id.toggleStart) {

//                    Place code related to Start button
                    Toast.makeText(getContext(), "START", Toast.LENGTH_SHORT).show();
                    stepsCountProgressBar.setIndeterminate(false);

//                    Check if the Accelerometer sensor exists
                    if (mSensorACC != null) {
                        mSensorManager.registerListener(listener, mSensorACC, SensorManager.SENSOR_DELAY_NORMAL);
                    }
//                    If the accelerometer doesn't exist show a toast
                    else {
                        Toast.makeText(getContext(), R.string.acc_not_available, Toast.LENGTH_SHORT).show();
                    }


//                    Check if the Step detector sensor exists
                    if (mSensorSTEP != null) {
                        mSensorManager.registerListener(listener, mSensorSTEP, SensorManager.SENSOR_DELAY_NORMAL);
                    }
//                    If the step detector sensor doesn't exists no toast is shown, the accelerometer sensor will do the job
                    else {
                        Toast.makeText(getContext(), R.string.step_not_available, Toast.LENGTH_SHORT).show();
                    }


                } else if (group.getCheckedButtonId() == R.id.toggleStop) {
//                    Place code related to Stop button
                    Toast.makeText(getContext(), "STOP", Toast.LENGTH_SHORT).show();

//                    Unregister the listener
                    mSensorManager.unregisterListener(listener);
                }
            }
        });
        //////////////////////////////////////
        return root;


    }
}

// Sensor event listener
class StepCounterListener implements SensorEventListener {

    private long lastUpdate = 0;

//    ACC Step counter
    public static int mACCStepCounter = 0;
    ArrayList<Integer> mACCSeries = new ArrayList<Integer>();
    private double accMag = 0;
    private int lastXPoint = 1;
    int stepThreshold = 6;

//    Step detector counter
    int mSTEPStepCounter = 0;

//    TextView
    TextView stepsCountTextView;
//    ProgressBar
    ProgressBar stepsCountProgressBar;

    public StepCounterListener(TextView stepsCountTextView, ProgressBar stepsCountProgressBar) {
        this.stepsCountTextView = stepsCountTextView;
        this.stepsCountProgressBar = stepsCountProgressBar;
    }


    @Override
    public void onSensorChanged(SensorEvent event) {

        switch (event.sensor.getType()) {

//            Get the sensor type
            case Sensor.TYPE_LINEAR_ACCELERATION:

//            Get sensor's values
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];


            //////////////////////////// -- PRINT ACC VALUES -- ////////////////////////////////////

                // Timestamp
                long timeInMillis = System.currentTimeMillis() + (event.timestamp - SystemClock.elapsedRealtimeNanos()) / 1000000;

                // Convert the timestamp to date
                SimpleDateFormat jdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                jdf.setTimeZone(TimeZone.getTimeZone("GMT+2"));
                String date = jdf.format(timeInMillis);

                // print a value every 1000 ms
                long curTime = System.currentTimeMillis();
                if ((curTime - lastUpdate) > 1000) {
                    lastUpdate = curTime;

                    Log.d("ACC", "X: " + String.valueOf(x) + " Y: " + String.valueOf(y) + " Z: "
                            + String.valueOf(z) + " t: " + String.valueOf(date));

                }


            ////////////////////////////////////////////////////////////////////////////////////////

//            Compute the ACC magnitude
                accMag = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));


            //Update the Magnitude series
                mACCSeries.add((int) accMag);


            /// STEP COUNTER ACC ////
            // Calculate ACC peaks and steps

            peakDetection();

            break;

            // case Step detector
            case Sensor.TYPE_STEP_DETECTOR:
                countSteps(event.values[0]);

        }
    }

    private void stepDetected(float value) {
        mACCStepCounter += 1;
        stepsCountTextView.setText(String.valueOf(mACCStepCounter));
        stepsCountProgressBar.setProgress(mACCStepCounter, true);
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }


    private void peakDetection() {
        int windowSize = 20;

        /* Peak detection algorithm derived from: A Step Counter Service for Java-Enabled Devices Using a Built-In Accelerometer, Mladenov et al.
         */
        int highestValX = mACCSeries.size(); // get the length of the series
        if (highestValX - lastXPoint < windowSize) { // if the segment is smaller than the processing window skip it
            return;
        }

        List<Integer> valuesInWindow = mACCSeries.subList(lastXPoint,highestValX);

        lastXPoint = highestValX;

        int forwardSlope = 0;
        int downwardSlope = 0;

        List<Integer> dataPointList = new ArrayList<Integer>();

        for (int p =0; p < valuesInWindow.size(); p++){
            dataPointList.add(valuesInWindow.get(p));
        }


        for (int i = 0; i < dataPointList.size(); i++) {
            if (i == 0) {
            }
            else if (i < dataPointList.size() - 1) {
                forwardSlope = dataPointList.get(i + 1) - dataPointList.get(i);
                downwardSlope = dataPointList.get(i)- dataPointList.get(i - 1);

                if (forwardSlope < 0 && downwardSlope > 0 && dataPointList.get(i) > stepThreshold ) {
                    mACCStepCounter += 1;
                    Log.d("ACC STEPS: ", String.valueOf(mACCStepCounter));

                    updateView(mACCStepCounter);
                }
            }
        }
    }

//    Calculate the number of steps from the step detector
    private void countSteps(float step) {
        mSTEPStepCounter += step;
        Log.d("STEP STEPS: ", String.valueOf(mSTEPStepCounter));
//        updateView(mSTEPStepCounter);
    }

    private void updateView(int stepCounter) {
//        update the text view
        stepsCountTextView.setText(String.valueOf(stepCounter));
//        update the ProgressBar
        stepsCountProgressBar.setProgress(stepCounter, true);
    }

}

