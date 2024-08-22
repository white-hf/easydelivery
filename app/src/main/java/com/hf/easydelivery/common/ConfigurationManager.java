package com.hf.easydelivery.common;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;
import java.io.InputStream;

public class ConfigurationManager {
    private JSONObject jsonObject;

    public ConfigurationManager(Context context, String configFileName) {
        try {
            InputStream is = context.getAssets().open(configFileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            jsonObject = new JSONObject(json);
        } catch (Exception ex) {
            Log.e("ConfigurationManager", "Error reading configuration file: " + ex.getMessage(), ex);
            jsonObject = new JSONObject();  // Fallback to empty JSON object
        }
    }

    public String getString(String key, String defaultValue) {
        return jsonObject.optString(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return jsonObject.optInt(key, defaultValue);
    }
}
