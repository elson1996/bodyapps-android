/*
 * Copyright (c) 2014, Fashiontec (http://fashiontec.org)
 * Licensed under LGPL, Version 3
 */
package org.fashiontec.bodyapps.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

import org.fashiontec.bodyapps.managers.MeasurementManager;
import org.fashiontec.bodyapps.managers.PersonManager;
import org.fashiontec.bodyapps.models.Measurement;
import org.fashiontec.bodyapps.models.Person;

import java.util.Date;

/**
 * Manages the automatic sync of measurements with web API
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

    ContentResolver mContentResolver;



    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContentResolver = context.getContentResolver();
    }

    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {

        super(context, autoInitialize, allowParallelSyncs);
        mContentResolver = context.getContentResolver();
    }

    @Override
    public void onPerformSync(Account account, Bundle bundle, String s,
                              ContentProviderClient contentProviderClient,
                              SyncResult syncResult) {
        Log.d("SyncAdapter","sync happened");
        Measurement measurement;
        while ((measurement = MeasurementManager.getInstance(getContext().getApplicationContext()).getMeasurementSync())!=null) {
            Person person = PersonManager.getInstance(getContext().getApplicationContext()).getPersonbyID(measurement.getPersonID());
            String out = SyncMeasurement.sendMeasurement(measurement, person);
            if(out.equals(measurement.getID())) {
                measurement.setSynced(true);
                MeasurementManager.getInstance(getContext().getApplicationContext()).addMeasurement(measurement);
            }
            Log.d("SyncAdapter",out);
        }

    }
}