package edu.rutgers.liuliu.rapidsample;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Layout;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import edu.rutgers.liuliu.librapid.*;
import edu.rutgers.liuliu.librapid.DefaultConfig;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "ACTIVITY";
    private TextView txtView;
    private TextView scoreView;
    private Button punchBtn;
    private int score;
    private int CLICKS = 20;
    private Timer t;

    /*mission related vars*/
    myRSDGMission mission;
    RSDGPara txtPara;
    RSDGPara iconPara;
    long timeLeft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        score = 0;
        txtView = (TextView)findViewById(R.id.text);
        scoreView = (TextView)findViewById(R.id.score);
        punchBtn = (Button)findViewById(R.id.punch);
        txtPara = new RSDGPara();
        iconPara = new RSDGPara();
        requestPermission();
        try {
            setupMission();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == 0){
            int retBudget = data.getIntExtra("budget",-1);
            timeLeft = retBudget;
            Iterator it = RSDGMission.threadPref.entrySet().iterator();
            while(it.hasNext()){
                Map.Entry pair = (Map.Entry) it.next();
                Log.d(TAG,"preference:"+ pair.getKey() + " = " + pair.getValue());
            }
            Log.d(TAG,"time limite = "+timeLeft);
        }
    }

    public void config(View view){
        DefaultConfig c = new DefaultConfig();
        Intent intent = new Intent(getApplicationContext(), c.getClass());
        startActivityForResult(intent,0);
    }

    public void punch(View view){
        score+=1;
        scoreView.setText("score:"+score);
        if(score >= CLICKS){
            mission.cancel();
            t.cancel();
            txtView.setText("Mission FINISHED");
        }
    }

    public void start(View view) throws IOException, InterruptedException {
        mission.start();
        final TimerTask timeCounter = new TimerTask() {
            @Override
            public void run() {
                timeLeft-=1;
            }
        };
        t = new Timer();
        t.schedule(timeCounter,0,1000);
    }

    //setup the mission
    private void setupMission() throws IOException {
        mission = new myRSDGMission(this.getApplicationContext(),this,"MyFirstMission");
        mission.loadRSDGFromXML(this.getApplicationContext(), "");
        mission.offline = false;
        //register the binding
        mission.regService("text", 1, "normal", changeText, Pair.create(txtPara, 1));
        mission.regService("text", 2, "fast", changeText, Pair.create(txtPara, 2));
        mission.regService("icon", 1, "small", changeIcon, Pair.create(iconPara, 1));
        mission.regService("icon", 2, "big", changeIcon, Pair.create(iconPara, 2));
        //setup the reconfiguration frequency
        mission.setupSolverFreq(1);
    }

    //action to be done
    final Runnable changeText = new Runnable() {
        @Override
        public void run() {
            String action = "";
            switch (txtPara.intPara){
                case 1:
                   action = "WELL DONE";
                    break;
                case 2:
                    action = "FASTER!";
                    break;
            }
            final String actionToShow = action;
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    txtView.setText(actionToShow);
                }
            });
        }
    };

    //action to be done
    final Runnable changeIcon = new Runnable() {
        @Override
        public void run() {
            String text = "PUNCH ME";
            switch (iconPara.intPara){
                case 2:
                    text = "PUNCH ME!!!";
                    break;
            }
            final String txt = text;
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    punchBtn.setText(txt);
                }
            });
        }
    };

    public class myRSDGMission extends RSDGMission{
        public myRSDGMission(Context c, Activity a, String name) {
            super(c, a, name);
        }
        @Override
        public double updateBudget(){
            double timeLeftInSecond = timeLeft;
            int clickLeft = CLICKS-score;
            double avgSpeed = 1000 * (timeLeftInSecond/clickLeft); //how many milliseconds is required by 1 click
            Log.d(TAG,"click remained = " + clickLeft + " time left = " + timeLeftInSecond + " budget = " + avgSpeed);
            return avgSpeed;
        }
    }

    public void requestPermission(){
        Log.d(TAG,"permission entered");
    if (ContextCompat.checkSelfPermission(this,
    Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
        Log.d(TAG,"permission requested");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    100);
        }
    if (ContextCompat.checkSelfPermission(this,
    Manifest.permission.INTERNET)
            != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.INTERNET},
                100);
    }
}
}
