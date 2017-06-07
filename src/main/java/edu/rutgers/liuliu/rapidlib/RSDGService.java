package com.example.liuliu.rsdglib;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by liuliu on 4/18/16.
 * Abstraction for a RSDG service
 * represented by a thread and is associated with a runnable
 * TODO: Syncronization is partially implemented, do not use
 */
public class RSDGService {
    public Runnable curRunnable;
    private String name;
    private RSDGUnit unit;
    private Boolean READY;
    private Thread thread;
    private int bufferReady;//-1=HOLD, 0=USED, 1=READY, 2 = INIT
    public List<RSDGService> parents;
    public Object buffer;

    public Object get(Thread requirer) throws InterruptedException {
        while (bufferReady != 1) {
            Log.d("mission", "getting" + " cur=" + bufferReady);
            requirer.sleep(1000);
        }
        Log.d("mission", "buffer:" + this.bufferReady);
        this.setBufferUsed();
        Log.d("mission", requirer.getName() + " buffer read and reset");
        return buffer;
    }

    public void set(Thread setter, Object b) throws InterruptedException {
        while (bufferReady != 0 && bufferReady != 2) {
            Log.d("mission", "setting" + " cur=" + bufferReady);
            setter.sleep(1000);
        }
        buffer = b;
        this.setBufferReady();
        Log.d("mission", setter.getName() + " buffer written");
    }

    public RSDGService(String name) {
        this.name = name;
        READY = true;
        bufferReady = 2;
        this.thread = new Thread(name);
        parents = new ArrayList<RSDGService>(10);
        this.unit = new RSDGUnit(0);
    }

    public void addParent(RSDGService s) {
        parents.add(s);
    }

    public List<RSDGService> getParents() {
        return parents;
    }

    public String getName() {
        return this.name;
    }

    public void setUnit(int m) {
        this.unit.set(m);
    }

    public int getUnit() {
        return this.unit.get();
    }

    public Thread getThread() {
        return thread;
    }

    public void run(Runnable r) {
        thread = new Thread(r, name);
        thread.run();
    }

    public void setBufferReady() {
        bufferReady = 1;
    }

    public void setBufferUsed() {
        bufferReady = 0;
    }

    public void setBufferHold() {
        bufferReady = -1;
    }

    public void setStatus(boolean b) {
        READY = b;
    }

    public int getBufferStatus() {
        return bufferReady;
    }

    public boolean getServiceStatus() {
        return READY;
    }

    public void updateNode(Runnable r, boolean changed) {
        /*if(curRunnable!=null && r==curRunnable && !changed) {
            Log.d("service","runnable unchanged");
            return;
        }*/
        //check parents
        updateOnParent updateT = new updateOnParent();
        updateT.execute(Pair.create(this, r));
        return;
    }

    private class updateOnParent extends AsyncTask<Pair<RSDGService, Runnable>, Void, Void> {
        RSDGService service;
        Runnable targetR;

        @Override
        protected Void doInBackground(Pair<RSDGService, Runnable>... s) {
            service = s[0].first;
            targetR = s[0].second;
            for (int i = 0; i < service.parents.size(); i++) {
                try {
                    RSDGService curParent = service.parents.get(i);
                    if (!curParent.getServiceStatus()) {//not ready
                        Thread.sleep(500);
                        i--;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void p) {
            Thread targetT = service.getThread();
            try {
                targetT.interrupt();
                targetT.join();
                Log.d("service", "interrupted thread:" + targetT.getName());
                Log.d("service", "thread=" + thread.getId());
                Log.d("service", "targetT=" + targetT.getId());
                thread = new Thread(targetR, service.getName());
                service.setStatus(true);
                thread.start();
                service.curRunnable = targetR;
                Log.d("update", "new runnable activated:" + service.getName());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }
}
