/*
 * Copyright (c) 2014, Fashiontec (http://fashiontec.org)
 * Licensed under LGPL, Version 3
 */

package org.fashiontec.bodyapps.main;

import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;

import org.fashiontec.bodyapps.managers.MeasurementManager;
import org.fashiontec.bodyapps.managers.UserManager;
import org.fashiontec.bodyapps.models.User;
import org.fashiontec.bodyapps.sync.SyncUser;
import org.fashiontec.bodyapps.ui.AlertDialogFragment;
import org.fashiontec.bodyapps.ui.ProgressDialogFragment;

import java.io.InputStream;

/**
 * Activity for settings. This activity handles user authentication via Google
 * play services and obtains user ID from web application. Then user gets added
 * to the DB
 */
public class SettingsActivity extends ActionBarActivity implements
        OnClickListener, ConnectionCallbacks, OnConnectionFailedListener {

    // Activity response code, means the other activity was launched during sing-in
    private static final int RC_SIGN_IN = 0;

    // Profile pic image size in pixels
    private static final int PROFILE_PIC_SIZE = 400;

    // Logging tag
    private static final String TAG = SettingsActivity.class.getName();

    // Initial state, nothing happened yet
    private static final String STATE_NOT_SIGNED_IN = "not-signed-in";
    // User is successfully signed in
    private static final String STATE_SIGNED_IN = "signed-in";
    // User has clicked 'Sign in' button, but not completed sign-in process yet
    private static final String STATE_SIGNING_IN = "signing-in";
    // Some unrecoverable error occurred during sign-in
    private static final String STATE_SIGN_IN_FAILED = "sign-in-error";

    // Currenct state of the sign-in process
    private String signInState = STATE_NOT_SIGNED_IN;

    // Google client to interact with Google API
    private GoogleApiClient mGoogleApiClient;

    // Indicates if intent to resolve G+ sign in error is in progress
    private boolean mIntentInProgress;

    // Indicates that the sign in button was clicked
    private boolean mSignInClicked;

    // Store result of the latest G+ connection attempt, for user has not clicked 'Sign in' yet.
    private ConnectionResult mConnectionResult;

    // Buttons to sign in, sign out and revoke
    private SignInButton btnSignIn;
    private Button btnSignOut;
    private Button btnRevoke;

    // Components for the current users profile and settings when connected
    private LinearLayout llProfileLayout;
    private ImageView imgProfilePic;
    private TextView txtName;
    private TextView txtEmail;
    private TextView txtConnected;
    private CheckBox chkAutoSync;

    // Progress dialog, keep here so we can dismiss it
    private DialogFragment progressDialog;

    private MeasurementManager measurementManager;
    private UserManager userManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mGoogleApiClient = createGoogleApiClient();
        userManager = UserManager.getInstance(getApplicationContext());
        measurementManager = MeasurementManager.getInstance(getApplicationContext());

        findControls();
        setupListeners();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "OnDestroy");
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "OnStart");
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "OnStop");
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    /**
     * Find all important controls on the view and assign them to class members.
     */
    private void findControls() {
        btnSignIn = (SignInButton) findViewById(R.id.settings_btn_signin);
        btnSignOut = (Button) findViewById(R.id.settings_btn_signout);
        btnRevoke = (Button) findViewById(R.id.settings_btn_revoke);
        imgProfilePic = (ImageView) findViewById(R.id.settings_img_profile);
        txtName = (TextView) findViewById(R.id.settings_txt_name);
        txtEmail = (TextView) findViewById(R.id.settings_txt_email);
        txtConnected = (TextView) findViewById(R.id.settings_txt_connected);
        llProfileLayout = (LinearLayout) findViewById(R.id.settings_layout);
        chkAutoSync = (CheckBox) findViewById(R.id.settings_chk_autosync);
    }

    /**
     * Registers various listeners for UI components.
     */
    private void setupListeners() {
        btnSignIn.setOnClickListener(this);
        btnSignOut.setOnClickListener(this);
        btnRevoke.setOnClickListener(this);

        final Context context = getBaseContext().getApplicationContext();
        final UserManager userMgr = UserManager.getInstance(context);

        chkAutoSync.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                userMgr.setAutoSync(isChecked);
            }
        });
    }

    /**
     * Helper to create the Google API client
     */
    private GoogleApiClient createGoogleApiClient() {
         return new GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this).addApi(Plus.API)
            .addScope(Plus.SCOPE_PLUS_PROFILE).build();
    }

    /**
     * Helper to create the progress dialog
     */
    private DialogFragment showProgressDialog() {
        DialogFragment progressDialog = ProgressDialogFragment.getInstance();
        progressDialog.show(getFragmentManager(), "progress-dialog");
        return progressDialog;
    }

    /**
     * Helper to create an alert dialog with a custom message
     */
    private DialogFragment showAlertDialog(String message) {
        DialogFragment alertDialog = AlertDialogFragment.getInstance(message);
        alertDialog.show(getFragmentManager(), "alert-dialog");
        return alertDialog;
    }

    /**
     * Sign the user in via G+
     */
    private void signIn() {
        if (!mGoogleApiClient.isConnecting()) {
            setSignInState(STATE_SIGNING_IN);
            resolveSignInError();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.d(TAG, "Connection failed");
        if (!mIntentInProgress) {
            mConnectionResult = result;
            if (STATE_SIGNING_IN.equals(signInState)) {
                resolveSignInError();
            }
        }
    }

    /**
     * Sign-in error mostly indicate that some user interaction is required and this will check
     * for any intents provided by the Google Plus client to resolve and launch the relevant
     * activity. Results of such activities will be managed by {@link #onActivityResult}
     *
     * Unfortunately this does not provide any clue for technical errors, e.g. if API access for
     * the app is not correctly configured. This will not be invoked, instead we just receive
     * empty results from the API :(
     */
    private void resolveSignInError() {
        Log.d(TAG, "Resolve sign in error");

        if (mConnectionResult == null) {
            Log.d(TAG, "No connection result yet");
            return;
        }

        if (mConnectionResult.hasResolution()) {
            Log.d(TAG, "Trying resolution");
            try {
                mIntentInProgress = true;
                startIntentSenderForResult(mConnectionResult.getResolution().getIntentSender(),
                        RC_SIGN_IN, null, 0, 0, 0);
            } catch (SendIntentException e) {
                mIntentInProgress = false;
                mGoogleApiClient.connect();
            }
        } else {
            Log.w(TAG, "No resolution");
            int errorCode = mConnectionResult.getErrorCode();
            GooglePlayServicesUtil.getErrorDialog(errorCode, this, 0).show();
            setSignInState(STATE_SIGN_IN_FAILED);
        }
    }

    /**
     * Mostly used to handle results from activities started to handle G+ sign-in errors.
     *
     * @param requestCode only {@link #RC_SIGN_IN} will be recognized atm
     * @param responseCode if not {@link #RESULT_OK}, will reset login state
     * @param intent ignored
     */
    @Override
    protected void onActivityResult(int requestCode, int responseCode, Intent intent) {
        if (requestCode == RC_SIGN_IN) {
            if (responseCode != RESULT_OK) {
                setSignInState(STATE_SIGN_IN_FAILED);
            }

            mIntentInProgress = false;

            if (!mGoogleApiClient.isConnecting()) {
                mGoogleApiClient.connect();
            }
        }
    }

    /**
     * Sign out user from Google
     */
    private void signOut() {
        if (mGoogleApiClient.isConnected()) {
            Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
            mGoogleApiClient.disconnect();
            mGoogleApiClient.connect();
            UserManager.getInstance(getBaseContext().getApplicationContext()).unsetCurrent();
            setSignInState(STATE_NOT_SIGNED_IN);
            updateUI();
        }
    }

    /**
     * Revoking access from google
     */
    private void revokeAccess() {
        if (mGoogleApiClient.isConnected()) {
            Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
            Plus.AccountApi.revokeAccessAndDisconnect(mGoogleApiClient)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status arg0) {
                        setSignInState(STATE_NOT_SIGNED_IN);
                        Log.e(TAG, "User access revoked!");
                        mGoogleApiClient.connect();
                        updateUI();
                    }
                });
        }
        UserManager.getInstance(getBaseContext().getApplicationContext()).unsetCurrent();
        reload();
    }

    /**
     * Update the UI according to isSign in
     */
    private void updateUI() {
        if (STATE_SIGNED_IN.equals(signInState)) {
            btnSignIn.setVisibility(View.GONE);
            btnSignOut.setVisibility(View.VISIBLE);
            btnRevoke.setVisibility(View.VISIBLE);
            llProfileLayout.setVisibility(View.VISIBLE);
        } else {
            btnSignIn.setVisibility(View.VISIBLE);
            btnSignOut.setVisibility(View.GONE);
            btnRevoke.setVisibility(View.GONE);
            llProfileLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.settings_btn_signin:
                // Signin button clicked
                signIn();
                break;
            case R.id.settings_btn_signout:
                // Signout button clicked
                signOut();
                break;
            case R.id.settings_btn_revoke:
                // Revoke access button clicked
                revokeAccess();
                break;
        }
    }

    private void setSignInState(String signInState) {
        this.signInState = signInState;
        Log.d(TAG, "Sign in state: " + signInState);
    }

    private void toast(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onConnected(Bundle arg0) {

        Log.d(TAG, "G+ connected, current state is " + signInState);

        if (STATE_SIGNED_IN.equals(signInState)) {
            Log.d(TAG, "Signed in already");
            return;
        }

        try {
            setSignInState(STATE_SIGNED_IN);
            String email = Plus.AccountApi.getAccountName(mGoogleApiClient);
            Person me = getProfileInformation();
            User user = findOrCreateUser(email, me);
        } catch (InvalidGooglePlusConnectionException e) {
            setSignInState(STATE_SIGN_IN_FAILED);
            showAlertDialog("Failed to load personal data from G+");
        }

        updateUI();
    }

    private User findOrCreateUser(String email, Person me) {
        // The naming is pretty counterintuitive - this actually tries to find the user by email
        User user = new User(email, me.getDisplayName(), null, false);
        String currentUserId = userManager.isUser(user);

        // If user does not exist, create with backend service
        if (null == currentUserId || UserManager.NO_ID.equals(currentUserId)) {
            progressDialog = showProgressDialog();
            new HttpAsyncTaskUser(user).execute();
        } else {
            userManager.setCurrent(user);
            txtConnected.setText(getString(R.string.settings_user_connected));
            chkAutoSync.setChecked(userManager.getAutoSync());
        }
        return user;
    }

    /**
     * Get user's information from G profile
     */
    private Person getProfileInformation() throws InvalidGooglePlusConnectionException {
        try {
            Person person = Plus.PeopleApi.getCurrentPerson(mGoogleApiClient);
            if (null == person) {
                throw new InvalidGooglePlusConnectionException("Failed to retrieve data");
            }

            Log.d(TAG, "Logged in as: " + person.getDisplayName());
            toast(String.format("Hello %s", person.getDisplayName()));

            String personName = person.getDisplayName();
            String accountName = Plus.AccountApi.getAccountName(mGoogleApiClient);

            String personPhotoUrl = person.getImage().getUrl();
            String personGooglePlusProfile = person.getUrl();

            Log.d(TAG, "Person info: " +
                    "\nName: " + personName + ", " +
                    "\nEmail: " + accountName + ", " +
                    "\nProfile: " + personGooglePlusProfile + ", " +
                    "\nImage: " + personPhotoUrl);

            txtName.setText(personName);
            txtEmail.setText(accountName);

            personPhotoUrl = personPhotoUrl.substring(0,
                    personPhotoUrl.length() - 2)
                    + PROFILE_PIC_SIZE;
            new LoadProfileImage(imgProfilePic).execute(personPhotoUrl);

            return person;

        } catch (Exception e) {
            Log.e(TAG, "Could not load personal info");
            throw new InvalidGooglePlusConnectionException(e);
        }
    }


    @Override
    public void onConnectionSuspended(int arg0) {
        Log.d(TAG, "Connection suspended, reconnecting");
        mGoogleApiClient.connect();
    }

    /**
     * Reloads the activity
     */
    private void reload() {
        Intent intent = getIntent();
        overridePendingTransition(0, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();

        overridePendingTransition(0, 0);
        startActivity(intent);
    }

    /**
     * Async task to get profile image from google.
     */
    private class LoadProfileImage extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        public LoadProfileImage(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap mIcon11 = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            return mIcon11;
        }

        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);
        }
    }

    /**
     * Async task to send user data to web app and get user ID.
     */
    private class HttpAsyncTaskUser extends AsyncTask<String, Void, String> {

        private final User user;

        public HttpAsyncTaskUser(User user) {
            super();
            this.user = user;
        }

        @Override
        protected void onPostExecute(String userId) {
            Log.d(TAG, "Data was sent");
            if (null != userId && userId.length() > 0) {

                user.setId(userId);
                String existingId = userManager.isUser(user);

                if (null == existingId) {
                    userManager.addUser(user);
                } else {
                    userManager.setID(user); // write user objects current id to db
                    userManager.setAutoSync(true); // always auto-sync
                    userManager.setCurrent(user);
                }

                // If user added measurements before signing in, those measurements will be added
                // to the now signed-in user
                txtConnected.setText(getString(R.string.settings_backend_user_connected));
                measurementManager.setUserID(user.getId());
                chkAutoSync.setChecked(userManager.getAutoSync());

            } else {
                toast(getString(R.string.settings_backend_connection_failed));

                // adds the user to the DB, but without the ID if the user currently not on db
                user.setId(UserManager.NO_ID);
                String existingId = userManager.isUser(user);

                if (existingId == null) {
                    userManager.addUser(user);
                    measurementManager.setUserID(UserManager.NO_ID);
                }

                userManager.setCurrent(user);
                measurementManager.setUserID(UserManager.NO_ID);

                txtConnected.setText(getString(R.string.settings_backend_user_not_connected));
                chkAutoSync.setChecked(false);
                chkAutoSync.setEnabled(false);
            }

            progressDialog.dismiss();
        }

        @Override
        protected String doInBackground(String... urls) {
            // returns userId
            return new SyncUser().getUserID(user.getEmail(), user.getName());
        }
    }

    private static class InvalidGooglePlusConnectionException extends Throwable {
        public InvalidGooglePlusConnectionException(String message) {
            super(message);
        }

        public InvalidGooglePlusConnectionException(Throwable cause) {
            super(cause);
        }
    }
}
