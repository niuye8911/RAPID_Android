package com.example.liuliu.rsdglib;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import static com.example.liuliu.rsdglib.RSDGState.TRAINING;
import static com.example.liuliu.rsdglib.RSDGState.TRAINING_DONE;
import static java.lang.System.nanoTime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by liuliu on 3/14/16.
 * RSDG manager that register threads, keep track of budget, and etc..
 */

public class RSDGMission {

    /***
     * BOOKKEEPING
     ***/
    BatteryModel model;
    private int timeleft = 10*60;
    static long startTime;
    private long missionStartTime;
    static long endTime;
    static int numThreads;//number of threads/services
    Map<String, Integer> runnableID;//basic node->runnableID
    Map<String, Integer> threadID;//service->threadID
    Map<String, String> basicToService; //basic node->service
    Map<String, RSDGService> getService;
    Map<String, Pair<RSDGPara, Integer>> paraList; //basic node -> runnable+parameter+para_value
    static Map<String, Integer> threadPref;
    int freq;//frequency of server consulting
    public boolean offline;
    private Handler monitor;
    public int startBatteryPercent;
    public int curBatteryPercent;
    public int delta;//delta is used to adjust the battery model
    public RSDGState STATE;
    public Pair<String, Double> trainResult;
    private String name;

    /***
     * GRAPH RELATED
     ***/
    RSDG inst;
    Map<RSDGService, String> selected;//current selected services

    /***
     * MISSION RELATED
     ***/
    boolean update;
    RSDGUnit unitObj;
    static long budgetTracker;
    int budget;//initial budget
    static int curBudget;//current budget
    private getFileServer fileServer;
    private Context ctx; //context under which RAPID executes
    private BatteryManager mBatteryManager;
    double voltage; //battery voltage
    private Activity act; //parent activity

    /***
     * TRAINING RELATED
     ***/
    private Long energyStampStart;
    private Long energyStampEnd;
    private ArrayList<ArrayList<String>> trainingConfigs;
    private ArrayList<Pair<String, Double>> trainingResult;
    private int trainingID = 0;

    /***
     * LIST OF THREADS+RUNNABLES
     ***/
    List<Runnable> runnableList;
    List<RSDGService> serviceList;

    public RSDGMission(Context c, Activity a, String name) {
        model = new BatteryModel();
        update = false;
        numThreads = 0;
        this.budget = 0;
        curBudget = 0;
        ctx = c;
        act = a;
        regBatteryMonitor();
        STATE = TRAINING_DONE;
        this.name = name;

        mBatteryManager =
                (BatteryManager) ctx.getSystemService(act.BATTERY_SERVICE);
        trainingResult = new ArrayList<>();
        runnableID = new HashMap<String, Integer>(100);
        threadID = new HashMap<String, Integer>(100);
        runnableList = new ArrayList<Runnable>();
        serviceList = new ArrayList<RSDGService>();
        basicToService = new HashMap<String, String>(100);
        getService = new HashMap<String, RSDGService>(100);
        paraList = new HashMap<String, Pair<RSDGPara, Integer>>(100);
        selected = new HashMap<RSDGService, String>(100);
        if(threadPref==null) threadPref = new HashMap<String, Integer>(100);
    }

    public RSDGService getService(String name) {
        return this.getService.get(name);
    }

    public void loadRSDGFromXML(Context ctx, String name) throws IOException {
        int xmlID = ctx.getResources().getIdentifier("rsdg" + name, "raw", ctx.getPackageName());
        InputStream stream = ctx.getResources().openRawResource(xmlID);
        InputStreamReader in_r = new InputStreamReader(stream, "UTF-8");

        char[] b = new char[stream.available()];
        in_r.read(b);

        String content = new String(b);
        String s = new String(content.getBytes(), "UTF-8");
        inst = new RSDG();
        Log.d("xml", "calling parseXMLFile");
        inst.parseXMLFile(s);
    }

    public int regService(String serviceName, int serviceLevel, String basicNode, Runnable r, Pair<RSDGPara, Integer> para) {
        //if this is an environment identifier
        if (r == null) {
            basicToService.put(basicNode, serviceName);
            threadPref.put(serviceName, 0);
            return 1;
        }
        //reg mapping between basicNode's name and runnable
        runnableList.add(r);
        runnableID.put(basicNode, runnableList.size() - 1);
        //if thread not registered before
        if (getService.get(serviceName) == null) {
            RSDGService s = new RSDGService(serviceName);
            serviceList.add(s);
            threadPref.put(serviceName, 0);
            threadID.put(serviceName, serviceList.size() - 1);
            Log.d("new thread", serviceName + String.valueOf(serviceList.size() - 1));
            numThreads++;
            basicToService.put(basicNode, serviceName);
            getService.put(serviceName, s);
        } else {//thread registered before
            RSDGService s = getService.get(serviceName);
            basicToService.put(basicNode, serviceName);
        }

        //reg this method's parameter
        if (para != null) paraList.put(basicNode, para);
        Log.d("mission", "registered:" + serviceName + "-" + basicNode);
        return 1;
    }

    public void setupSolverFreq(int freq) {
        this.freq = freq;
    }

    public void setUnit(RSDGUnit unit) {
        this.unitObj = unit;
    }

    private int counter = 1;

    //consult server once
    public void consultServer(Boolean offline) throws InterruptedException, IOException {
        //if offline, use heuristic
        if (offline) {
            startTime = System.nanoTime();
            String result = inst.highNodeLowDep();
            System.out.println(result);
            List<String> res = inst.getSelectedNodes(result);
            endTime = System.nanoTime();
            long solvingTime = (endTime - startTime) / 1000000;
            startTime = System.nanoTime();
            updateSelection(res);
            applyResult();
            endTime = System.nanoTime();
            long applyTime = endTime - startTime;
            applyTime /= 1000000;
            Log.d("overHead on solving", "" + solvingTime);
            Log.d("overHead on applying", "" + applyTime);
            return;
        }
        //use new budget/unit to generate new 0-1 problem
        generateProb();
        Log.d("rsdg", "problem generated");
        startTime = System.nanoTime();
        fileServer = new getFileServer() {
            @Override
            protected void onPostExecute(List<String> res) {
                if (res.size() == 0) {
                    Log.d("response", "no response returned");
                    return;
                }
                //debug code
                for (int i = 0; i < res.size(); i++) {
                    Log.d("response part", res.get(i));
                }
                try {
                    updateSelection(res);
                    applyResult();
                    endTime = System.nanoTime();
                    long ms = (endTime - startTime) / 1000000;
                    Log.d("Overhead:", "" + ms + "ms");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        fileServer.execute(1);
    }

    //update the service
    public void updateSelection(List<String> result) {
        Log.d("updateSelection:", "" + result.size() + "results");
        for (int i = 0; i < result.size(); i++) {
            RSDGService s = getService(basicToService.get(result.get(i)));
            if (s == null) continue;
            Log.d("mission:", s.getName() + " updated to " + result.get(i));
            if (selected.get(s) == result.get(i)) continue;
            else {
                s.setStatus(false);//set the service to be in 'recnf' mode
                selected.put(s, result.get(i));//set the service's selection to result
            }
        }
    }

    //retrieve selection
    private String getSelection() {
        String res = "";
        for (Object value : selected.values()) {
            res += value;
            res += " ";
        }
        return res;
    }

    //apply result
    public void applyResult() throws InterruptedException {
        Log.d("RSDGMission", "applying result" + selected.size());
        Iterator entries = selected.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry thisEntry = (Map.Entry) entries.next();
            RSDGService service = (RSDGService) thisEntry.getKey();
            String basic = (String) thisEntry.getValue();
            updateThread(service, basic);
        }
    }

    //change a thread's runnable
    void updateThread(RSDGService s, String BasicNode) throws InterruptedException {
        boolean changed = false;
        if (BasicNode == null) {//turn off this service
            if (s.getThread().isAlive()) {
                s.getThread().interrupt();
                s.setStatus(false);
                s.getThread().join();
                return;
            }
        }
        Runnable targetR = runnableList.get(runnableID.get(BasicNode));
        if (paraList.get(BasicNode) != null) {
            RSDGPara targetP = paraList.get(BasicNode).first;
            int oldPara = targetP.intPara;
            Log.d("updateThread", "para used to be:" + targetP.intPara);
            if (targetP != null) {
                int paraValue = paraList.get(BasicNode).second;
                if (oldPara != paraValue) {
                    targetP.intPara = paraValue;
                    changed = true;
                }
                Log.d("updateThread", "para updated to:" + paraValue);
            }
        }
        s.updateNode(targetR, changed);
        return;
    }

    public void start() throws InterruptedException, IOException {
        missionStartTime = System.nanoTime();
        setSolver();
        Log.d("start", "offline=" + offline);
        pinEnergy();
        consultServer(offline);
        applyResult();
    }

    //generate the new problem
    void generateProb() throws IOException {
        Log.d("genProb", "generating problem");
        //if preference is provided through knobs
        Iterator it = threadPref.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            updateMV((String) pair.getKey(), (int) pair.getValue(), true);
        }
        inst.writeXMLFile("problem");
    }

    //user part
    public void setBudget(int b) {
        //in Joules
        if (budget == 0) budget = b;
        curBudget = b;
        inst.budget = b;
        Log.d("RSDGMISSION", "budget updated to " + b);
    }

    public void updateMV(String serviceName, int value, boolean linear) {
        Log.d("updingMV",serviceName+":"+value);
        inst.updateMissionValue(serviceName, value, !linear, 1);
    }

    public int getBudget() {
        Log.d("budget", "" + budget);
        return budget;
    }

    public int getCurBudget() {
        return curBudget;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public int printPref(String serviceName) {
        return threadPref.get(serviceName);
    }

    private void setSolver() {
        if (freq == 0) {
            return;
        } else {
            monitor = new Handler();
            Timer timer_solve = new Timer();
            TimerTask doAsynchronousTask = new TimerTask() {
                @Override
                public void run() {
                    monitor.postDelayed(new Runnable() {
                        public void run() {
                            try {
                                double used = getConsumption(freq);
                                double usedJoule = used * freq/1000;//in Joule
                                budget -= usedJoule;
                                double jouleLeft = budget;
                                double usedTime = (System.nanoTime()-missionStartTime)/1000000000;
                                timeleft -= usedTime;//in seconds
                                missionStartTime = System.nanoTime();
                                double mw = jouleLeft/timeleft*1000;
                                setBudget((int)mw);
                                Log.d("Battery","consumed"+used);
                                Log.d("Battery","timeleft"+timeleft+"s,Jouleleft"+jouleLeft+"J,left:"+mw+"mw");
                                Toast.makeText(ctx,
                                        "Battery"+"timeleft"+timeleft+"s,Jouleleft"+jouleLeft+"J,left:"+mw+"mw",
                                        Toast.LENGTH_LONG).show();
                                consultServer(offline);
                                applyResult();
                                pinEnergy();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }, 0);
                }
            };
            timer_solve.schedule(doAsynchronousTask, 0, freq*1000); //execute in every freq ms
        }
    }

    public void cancel() {
        monitor.removeCallbacksAndMessages(null);
    }

    public void updateBudgetInWatt(double duration) {
        int usedPercent = startBatteryPercent - curBatteryPercent;
        Log.d("updateBudgetInWatt", "usedPercent " + usedPercent + " start " + startBatteryPercent + " cur " + curBatteryPercent + " budget " + budget);
        Log.d("updateBudgetInWatt", "duration" + duration);
        int newbudget = (int) model.getEnergy(curBatteryPercent, budget - usedPercent, true);
        Log.d("updateBudgetInWatt", "newbudget" + newbudget);
        if (duration == 0) return;
        setBudget(newbudget * 1000 / (int) duration);
        Log.d("updateBudgetInWatt", "budget" + newbudget * 1000 / (int) duration);
    }

    //train a single selction
    public void trainSingle(final ArrayList<String> config){
        final Handler delayer = new Handler();
        //final String cur = getSelection();
        updateSelection(config);
        final String cur = getSelection();
        try {
            applyResult();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.d("RSDGMission-SingleTrain", "Training:" + cur);
        pinEnergy();
        STATE = TRAINING;
        delayer.postDelayed(new Runnable() {
                    public void run() {
                        double curConsumption = getConsumption(freq);
                        trainResult = Pair.create(cur,curConsumption);
                        Log.d("SingleTrain", "result logged"+name+":"+trainResult.first+" "+trainResult.second);
                        STATE = TRAINING_DONE;
                    }
        }, freq * 1000);
    }

    public void train(final ArrayList<ArrayList<String>> configs) {
        trainingConfigs = configs;
        final Handler delayer = new Handler();
        pinEnergy();
        final Timer timer_solve = new Timer();
        final TimerTask doAsynchronousTask = new TimerTask() {
            @Override
            public void run() {
                delayer.postDelayed(new Runnable() {
                    public void run() {
                        String curSetup = getSelection();
                        //summary what's before
                        if (trainingID == trainingConfigs.size()) {
                            double curConsumption = getConsumption(freq);

                            trainingResult.add(Pair.create(curSetup, curConsumption));
                            Toast.makeText(act, "last consumed:" + curConsumption, Toast.LENGTH_SHORT)
                                    .show();
                            timer_solve.cancel();
                            return;
                        }
                        if (trainingID == 0) {
                            for (int i = 0; i < configs.size(); i++) {
                                Log.d("RSDGMission-Train:", "total training:" + configs.get(i));
                            }
                        }
                        ArrayList<String> nextSetup = trainingConfigs.get(trainingID);
                        Log.d("RSDGMissioin", "Training:" + trainingID);
                        trainingID++;
                        updateSelection(nextSetup);
                        try {
                            applyResult();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        String next = getSelection();

                        Log.d("RSDGMission-Train", "Training:" + next);
                        double curConsumption = getConsumption(freq);
                        trainingResult.add(Pair.create(curSetup, curConsumption));
                        Toast.makeText(act, "trainig:" + next + "last consumed:" + curConsumption, Toast.LENGTH_SHORT)
                                .show();

                        Log.d("RSDGMission-Train", "got ending energy" + curConsumption + "W");
                        pinEnergy();
                    }
                }, 0);
            }
        };
        timer_solve.schedule(doAsynchronousTask, 0, freq * 1000);

    }

    private void pinEnergy() {
        energyStampStart = mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
    }

    private double getConsumption(int period) {
        energyStampEnd =
                mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        Long microAH = energyStampStart - energyStampEnd;
        double AH = (double) microAH * 1e-6;
        double WH = AH * voltage / 1000;
        double Joule = WH * 3600;
        double W = Joule / period;
        double mW = W * 1000;
        return mW;
    }

    private void regBatteryMonitor() {
        BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
            int curVoltage = -1;

            @Override
            public void onReceive(Context context, Intent intent) {
                curVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                if (voltage == 0) {
                    voltage = curVoltage;
                } else voltage = (voltage + curVoltage) / 2;
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        ctx.registerReceiver(batteryReceiver, filter);
    }

    public ArrayList<Pair<String, Double>> getTrainingResult() {
        return trainingResult;
    }
}

/*turn off if not selected
for(int i = 0; i<selected.size(); i++){
        if(!result.contains(selected.get(i))){
        //turn off
        RSDGService s = serviceList.get(threadID.get(basicToService.get(selected.get(i))));

        //if no parents(data dep), just stop the threads
        if(s.getParents()==null)
        {
        s.getThread().interrupt();
        s.getThread().join();
        }else{//there is data dep
        for(int j = 0; j<s.getParents().size(); j++){
        RSDGService p = s.getParents().get(j);
        s.setStatus(false);
        if(p.getServiceStatus()==true){//ready to go
        s.getThread().interrupt();
        s.getThread().join();
        s.setBufferHold();
        }else{
        j--;//go back and check again#TODO:sleep?
        }
        }
        }
        }
        }
*/