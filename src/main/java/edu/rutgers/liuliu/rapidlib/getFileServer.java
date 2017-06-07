package com.example.liuliu.rsdglib;
/*Utility to communicate with Server
* */

import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by liuliu on 3/23/16.
 */
public class getFileServer extends AsyncTask<Integer, Void, List<String>> {

    @Override
    protected List<String> doInBackground(Integer... init) {
        FileUploader multipart = null;
        List<String> response = new ArrayList<String>();
        try {
            String filePath = "/storage/emulated/0/" + "problem.lp";
            File prob = new File(filePath);
            Log.d("fileserver", "read file done");
            multipart = new FileUploader("http://algaesim.cs.rutgers.edu/solver/", "UTF-8");
            multipart.addHeaderField("User-Agent", "CodeJava");
            multipart.addHeaderField("Test-Header", "Header-Value");

            multipart.addFormField("description", "Cool Pictures");
            multipart.addFormField("keywords", "Java,upload,Spring");

            multipart.addFilePart("uploaded_file[]", prob);
            response = multipart.finish();
            Log.d("response size", "" + response.size());

        } catch (IOException e) {
            e.printStackTrace();
        }
        return response;
    }

    @Override
    protected void onPostExecute(List<String> res) {
        if (res.size() == 0) {
            Log.d("file sovler", "no response");
            return;
        }

    }
}

