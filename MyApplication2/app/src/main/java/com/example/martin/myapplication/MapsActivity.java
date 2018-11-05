package com.example.martin.myapplication;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.rsa.securidlib.Otp;
import com.rsa.securidlib.exceptions.SecurIDLibException;
import com.rsa.securidlib.tokenstorage.TokenMetadata;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MapsActivity extends Activity {

    private SecurIDLibHelper m_libHelper;
    private Context m_context;
    private Timer m_OTPTimer;
    private Handler m_handler;
    private TokenMetadata m_activeToken;
    private String m_pin;

    private TextView m_nicknameLabel;
    private Spinner m_tokenDropdown;
    private TextView m_otpLabel;
    private TextView m_secondsRemainingLabel;

    private int m_secondsRemaining = 0;

    private final static long ONE_SECOND = 1000;

    final static int IMPORT_TOKEN_REQUEST = 1;
    final static String IMPORTED_TOKEN_SERIALNUMBER = "ImportedTokenSerialNumber";
    private Context context;
    private static final String READ_PHONE_STATE = "READ_PHONE_STATE";
    private static final int READ_CONTACTS = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        try
        {
            m_context = this;

            //To be able to allow access to a token in Airplane mode or with a global phone
            //For first-time use of your mobile client application, you should
            //prompt the user to enable Wi-Fi and exit Airplane mode.
            if(isAllDeviceParametersAvailable()) {
                //prompt user to disable Airplane mode (for non-Wi-Fi only device)
                //and enable Wi-Fi before continuing
            }

            m_libHelper = SecurIDLibHelper.getInstance(this);
            m_handler = new Handler();

            m_nicknameLabel = (TextView)findViewById(R.id.nicknameLabel);
            m_tokenDropdown = (Spinner)findViewById(R.id.tokenList);
            m_otpLabel = (TextView)findViewById(R.id.otpText);
            m_secondsRemainingLabel = (TextView)findViewById(R.id.secsRemainingLabel);

            //populate drop-down box with a list of tokens identified by serial number
            ArrayList<String> tokenSNList = m_libHelper.getTokenSNList();
            ArrayAdapter<String> tokenSNArrayAdapter = new ArrayAdapter<String>(m_context, android.R.layout.simple_spinner_dropdown_item, tokenSNList);
            m_tokenDropdown.setAdapter(tokenSNArrayAdapter);
            m_tokenDropdown.setOnItemSelectedListener(new TokenListItemSelectedListener());

        }
        catch(SecurIDLibException ex)
        {
            ex.printStackTrace();
            this.finish();
        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onPause()
    {
        super.onPause();
        this.stopOtpCodeTimer();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        boolean ret = true;
        switch(item.getItemId())
        {
            case R.id.importTokenMenu:
                Intent iImportToken = new Intent(m_context, ImportTokenActivity.class);
                startActivityForResult(iImportToken, IMPORT_TOKEN_REQUEST);
                break;
            case R.id.deleteTokenMenu:
                if(m_activeToken != null) {
                    ret = m_libHelper.deleteToken(m_activeToken.getSerialNumber());
                    if(ret) {
                        m_activeToken = null;
                        refreshTokenList();
                    }
                }
                break;
            case R.id.renameTokenMenu:
                if(m_activeToken != null) {
                    //rename dialog prompt
                    AlertDialog.Builder renameDialog = new AlertDialog.Builder(this);
                    renameDialog.setTitle(R.string.renameDialogTitle);
                    renameDialog.setMessage(R.string.renameDialogMessage);
                    final EditText inputText = new EditText(this);
                    renameDialog.setView(inputText);
                    renameDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String inNickname = inputText.getText().toString();
                            if(inNickname != null) {
                                boolean ret = m_libHelper.renameToken(m_activeToken.getSerialNumber(), inNickname);
                                if(ret) {
                                    m_activeToken.setNickname(inNickname);
                                    //the SDK does not check for duplicate token nicknames
                                    m_nicknameLabel.setText(m_activeToken.getNickname());
                                }
                            }
                        }
                    });
                    renameDialog.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    });
                    renameDialog.show();
                }
                break;
            case R.id.aboutMenuItem:
                AlertDialog.Builder aboutDialog = new AlertDialog.Builder(this);
                //display the SDK version
                String title = String.format(getString(R.string.sdkName), m_libHelper.getLibaryInfo());
                aboutDialog.setTitle(title);

                View view = LayoutInflater.from(m_context).inflate(R.layout.about_screen, null);

                //set device binding information in About dialog
                String deviceID = m_libHelper.getDeviceID();
                if(deviceID == null || deviceID.equals("")) {
                    deviceID = getString(R.string.unavailable);
                }
                TextView deviceIDLabel = (TextView)view.findViewById(R.id.deviceIDLabel);
                String deviceIDString = String.format(getString(R.string.deviceID), deviceID);
                deviceIDLabel.setText(String.format(getString(R.string.deviceID), deviceID));

                //display the About dialog
                aboutDialog.setView(view);
                aboutDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                aboutDialog.show();

                break;
            default:
                ret = super.onOptionsItemSelected(item);
                break;
        }

        return ret;
    }

    /**
     * Check result from Import Token activity
     */
    protected void onActivityResult (int requestCode, int resultCode, Intent data)
    {
        if(requestCode == IMPORT_TOKEN_REQUEST && data != null) {
            //get response from Import Token screen and refresh the token list and information
            String tokenSerialNumber = data.getExtras().getString(IMPORTED_TOKEN_SERIALNUMBER);
            if(tokenSerialNumber != null) {
                m_activeToken = m_libHelper.getTokenMetadata(tokenSerialNumber);
                refreshTokenList();
                int i = m_tokenDropdown.getAdapter().getCount()-1;
                m_tokenDropdown.setSelection(i);
            }
        }
    }

    /**
     * Token is selected from the token list
     */
    public class TokenListItemSelectedListener implements OnItemSelectedListener
    {
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
        {
            String tokenSerialNumber = parent.getItemAtPosition(pos).toString();
            m_activeToken = m_libHelper.getTokenMetadata(tokenSerialNumber);
            if(m_activeToken.getType() == TokenMetadata.PINSTYLE_PINPAD) {
                promptForPIN();
            }
            else {
                m_pin = "";
                refreshToken();
            }
        }

        public void onNothingSelected(AdapterView<?> parent)
        {
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
    /**
     * Check if device parameters (IMEI/MEID or MAC) are available
     * The following Android Permissions must be set
     * to avoid RuntimeException:
     *   android.permission.READ_PHONE_STATE
     *   android.permission.ACCESS_WIFI_STATE
     * @return
     */
    private boolean isAllDeviceParametersAvailable()
    {
        boolean deviceParamAvailable = true;
        TelephonyManager telephonyManager = (TelephonyManager) m_context.getSystemService(Context.TELEPHONY_SERVICE);
        Context context = this;
        int permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, 1);
        } else {
            //TODO
        }

        //check IMEI/MEID if the device is not a Wi-Fi only device
        String imei_meid = telephonyManager.getDeviceId();
        if(imei_meid == null) {
            deviceParamAvailable = false;
        }

        //check MAC address
        WifiManager wifiMan = (WifiManager) m_context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        String mac = wifiInf.getMacAddress();
        if(mac == null) {
            deviceParamAvailable = false;
        }

        return deviceParamAvailable;
    }

    /**
     * Refresh the token list after a token is imported or deleted
     */
    private void refreshTokenList()
    {
        ArrayList<String> tokenSNList = m_libHelper.getTokenSNList();
        ArrayAdapter<String> tokenSNArrayAdapter = new ArrayAdapter<String>(m_context, android.R.layout.simple_spinner_dropdown_item, tokenSNList);
        m_tokenDropdown.setAdapter(tokenSNArrayAdapter);
        m_tokenDropdown.postInvalidate();

        if(tokenSNList.size() > 0) {
            if(m_activeToken == null) {
                m_activeToken = m_libHelper.getTokenMetadata(tokenSNList.get(0));
                m_tokenDropdown.setSelection(0);
            }
        }
        else {
            m_activeToken = null;
        }

        refreshToken();
    }

    /**
     * Get OTP information after a new token is selected
     */
    private void refreshToken()
    {
        stopOtpCodeTimer();
        if(m_activeToken != null) {
            m_nicknameLabel.setText(m_activeToken.getNickname());
            m_tokenDropdown.setEnabled(true);
            updateOTP();
            startOtpCodeTimer();
        }
        else {
            m_otpLabel.setText(R.string.defaultCode);
            m_secondsRemainingLabel.setText("");
            m_nicknameLabel.setText(R.string.noTokenInstalled);
            m_tokenDropdown.setEnabled(false);
        }
    }

    /**
     * Update OTP after a specific token interval
     */
    private void updateOTP()
    {
        if(m_activeToken == null)  return;

        boolean error = false;
        if(m_secondsRemaining <= 0) {
            Otp otp = m_libHelper.getTokenCode(m_activeToken.getSerialNumber(), m_pin);
            if(otp != null) {
                m_otpLabel.setText(otp.getOtp());
                m_secondsRemaining = otp.getTimeRemaining();
            }
            else
            {
                error = true;
                stopOtpCodeTimer();
            }
        }
        else {
            m_secondsRemaining--;
        }

        if(!error) {
            String strSecRemaining = String.format(getString(R.string.secsRemaining),m_secondsRemaining);
            m_secondsRemainingLabel.setText(strSecRemaining);
        }
        else {
            m_otpLabel.setText(R.string.defaultCode);
            m_secondsRemainingLabel.setText("");
        }
    }

    /**
     * Update timer that shows how many seconds the OTP is valid
     */
    private void startOtpCodeTimer()
    {
        m_OTPTimer = new Timer();
        TimerTask mUpdateOtpTimerTask = new TimerTask() {
            public void run()
            {
                m_handler.post(mUpdateOtpTask);
            }
        };
        m_OTPTimer.scheduleAtFixedRate(mUpdateOtpTimerTask, 0, ONE_SECOND);
    }

    /**
     * Stop the OTP timer
     */
    private void stopOtpCodeTimer()
    {
        if (m_OTPTimer != null)
        {
            m_OTPTimer.cancel();
            m_OTPTimer.purge();
            if (m_handler != null)
            {
                m_handler.removeCallbacks(mUpdateOtpTask);
            }
        }
        m_OTPTimer = null;
        m_secondsRemaining = 0;
    }

    /**
     * Runnable that updates OTP periodically
     */
    private Runnable mUpdateOtpTask = new Runnable()
    {
        public void run()
        {
            updateOTP();
        }
    };

    /**
     * Display PIN dialog. PIN dialog accepts an empty PIN or a PIN with 4 to 8 digits.
     */
    private void promptForPIN()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(m_context).inflate(R.layout.pin_dialog, null);
        final AlertDialog alertDialog = alert.create();
        final EditText input = (EditText)view.findViewById(R.id.pinText);
        input.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                //Allows empty PIN or PIN with 4 to 8 digits.
                int textLength = input.getText().toString().length();
                boolean bEnabled = (textLength == 0 || (textLength >=4 && textLength <= 8)) ? true : false;
                alertDialog.getButton(alertDialog.BUTTON_POSITIVE).setEnabled(bEnabled);
                return false;
            }
        });
        alertDialog.setTitle(R.string.enterPinDialogTitle);
        alertDialog.setView(view);
        alertDialog.setButton(alertDialog.BUTTON_POSITIVE, getString(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                m_pin = input.getText().toString();
                refreshToken();
            }
        });
        alertDialog.setCancelable(false);
        alertDialog.show();
    }
}
