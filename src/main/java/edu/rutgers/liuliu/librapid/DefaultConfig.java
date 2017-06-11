package edu.rutgers.liuliu.librapid;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DefaultConfig extends AppCompatActivity {

    List<Pair<Pair<RotaryKnobView, TextView>, String>> preferences;
    HashMap<String, TextView> serviceTextView;
    boolean newline;
    private BatteryModel model;
    private RSDGMission mission;
    int budget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = new BatteryModel();
        Log.d("fromCFGnumThreads", "" + RSDGMission.numThreads);
        preferences = new ArrayList<Pair<Pair<RotaryKnobView, TextView>, String>>(RSDGMission.numThreads);
        serviceTextView = new HashMap<String, TextView>(RSDGMission.numThreads);
        newline = true;
        setContentView(R.layout.activity_config);

        int i = 0;
        Iterator it = RSDGMission.threadPref.entrySet().iterator();
        LinearLayout mylayout = (LinearLayout) findViewById(R.id.interValue);
        LinearLayout tmptxt = new LinearLayout(this);
        tmptxt.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout tmpknob = new LinearLayout(this);
        tmpknob.setOrientation(LinearLayout.HORIZONTAL);
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            preferences.add(i, Pair.create(Pair.create(new RotaryKnobView(this, (String) pair.getKey()), new TextView(this)), (String) pair.getKey()));
            serviceTextView.put((String) pair.getKey(), preferences.get(i).first.second);
            //set the string
            preferences.get(i).first.second.setText((String) pair.getKey());
            Log.d("pref name", preferences.get(i).second);

            tmptxt.addView(preferences.get(i).first.second);
            preferences.get(i).first.second.getLayoutParams().width = 300;
            tmpknob.addView(preferences.get(i).first.first);
            //if newline, create a horizontal layout and add knobs
            if (newline) {
                newline = false;
            } else {
                mylayout.addView(tmptxt);
                mylayout.addView(tmpknob);
                tmptxt = new LinearLayout(this);
                tmptxt.setOrientation(LinearLayout.HORIZONTAL);
                tmpknob = new LinearLayout(this);
                tmpknob.setOrientation(LinearLayout.HORIZONTAL);
                newline = true;
            }
            //adjust the knob size
            preferences.get(i).first.first.getLayoutParams().height = 300;
            preferences.get(i).first.first.getLayoutParams().width = 300;
        }
        //if odd # of views, add the last one
        if (!newline) {
            mylayout.addView(tmptxt);
            mylayout.addView(tmpknob);
        }
        setPreference();
        for (int j = 0; j < preferences.size(); j++) {
            setupbuttons(j);
        }
    }

    public void bindMission(RSDGMission m) {
        mission = m;
    }

    private void setupbuttons(int i) {
        final RotaryKnobView jogView = preferences.get(i).first.first;
        jogView.setKnobListener(new RotaryKnobView.RotaryKnobListener() {
            @Override
            public void onKnobChanged(int arg) {
                String serviceName = jogView.getName();
                TextView textBox = serviceTextView.get(serviceName);
                int newValue = RSDGMission.threadPref.get(serviceName) + arg;
                Log.d("knob", serviceName + " change to "+newValue);
                textBox.setText(serviceName + " => " + String.valueOf(newValue));
                RSDGMission.threadPref.put(serviceName, newValue);
                //preferences.get(i).first.second.
                if (arg > 0) {
                    Log.d("knob", "right" + newValue); // rotate right
                } else Log.d("knob", "left");
                ; // rotate left
            }
        });
    }

    public void bind(View view) {
        mission.setBudget(budget);
        Log.d("config", "mission binded");
    }

    private void setPreference() {
        final Button runbutton = (Button) findViewById(R.id.submitPref);
        runbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                EditText unitLeft = (EditText) findViewById(R.id.unitLeft);
                if (mission == null) Log.d("config", "mission is null");
                budget = Integer.parseInt(unitLeft.getText().toString());
                Intent returnIntent = new Intent();
                returnIntent.putExtra("budget",budget);
                setResult(0,returnIntent);
                finish();
            }
        });
    }

    private void display() {


    }
}
