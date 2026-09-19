package com.nudge;

import android.util.Log;

/**
 * Dummy database handler to ensure compilation.
 *
 * Functions:
 * - saveData: Simulates a database write by logging the date and JSON payload.
 */
public class ExternalDatabaseHandler {

    public static void saveData(String date, String jsonData) {
        Log.d("ExternalDBHandler", "Simulated DB save - Date: " + date + " | Data: " + jsonData);
    }
}