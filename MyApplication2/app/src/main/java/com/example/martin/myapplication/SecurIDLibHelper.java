package com.example.martin.myapplication;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.TimeZone;

import android.content.Context;
import android.widget.Toast;

import com.rsa.securidlib.Otp;
import com.rsa.securidlib.android.AndroidSecurIDLib;
import com.rsa.securidlib.exceptions.DatabaseException;
import com.rsa.securidlib.exceptions.DatabaseFullException;
import com.rsa.securidlib.exceptions.DeviceIDInaccessibleException;
import com.rsa.securidlib.exceptions.DuplicateTokenException;
import com.rsa.securidlib.exceptions.ExpiredTokenException;
import com.rsa.securidlib.exceptions.InvalidDeviceBindingException;
import com.rsa.securidlib.exceptions.SecurIDLibException;
import com.rsa.securidlib.exceptions.TokenImportFailureException;
import com.rsa.securidlib.exceptions.TokenNotFoundException;
import com.rsa.securidlib.tokenstorage.TokenMetadata;

/**
 * This class provides helper methods that interact with the RSA SecurID SDK.
 * Make sure that your mobile client application has the following privileges set:
 * android.permission.READ_PHONE_STATE
 * android.permission.ACCESS_WIFI_STATE
 *
 * If you use CT-KIP to import tokens, your mobile client application also needs the following privileges:
 * android.permission.INTERNET
 * android.permission.ACCESS_NETWORK_STATE
 *
 * See the Javadoc for RSA SecurID SDK for Android for more information on required Android permissions.
 */
public class SecurIDLibHelper {

    private static SecurIDLibHelper m_instance;
    private AndroidSecurIDLib m_lib;
    private Context m_context;

    private DateFormat m_usersDateFormat;

    private SecurIDLibHelper(Context context) throws SecurIDLibException
    {
        //initialize AndroidSecurIDLib
        m_lib = new AndroidSecurIDLib(context);
        m_context = context;

        TimeZone tz = TimeZone.getTimeZone("GMT-0");
        m_usersDateFormat = new SimpleDateFormat("MM/dd/yyyy");
        m_usersDateFormat.setTimeZone(tz);
    }

    static SecurIDLibHelper getInstance(Context context) throws SecurIDLibException
    {
        if(m_instance == null) {
            m_instance = new SecurIDLibHelper(context);
        }

        return m_instance;
    }

    /**
     * Get a list of token serial numbers
     * @return array of token serial numbers
     */
    ArrayList<String> getTokenSNList()
    {
        ArrayList<String> tokenSNList = new ArrayList<String>();
        try {
            //get an enumeration of token metadata
            Enumeration<TokenMetadata> tokenList = m_lib.getTokenList();
            if(tokenList != null)
            {
                //enumerate all of the token serial numbers
                while(tokenList.hasMoreElements())
                {
                    TokenMetadata tm = tokenList.nextElement();
                    tokenSNList.add(tm.getSerialNumber());
                }
            }
        }
        catch(SecurIDLibException ex) {
            ex.printStackTrace();
            displayError(ex);
        }
        return tokenSNList;
    }

    /**
     * Get the metadata (attributes) of the token specified by token serial number
     * @param tokenSerialNumber serial number of the token
     * @return token metadata
     */
    TokenMetadata getTokenMetadata(String tokenSerialNumber)
    {
        TokenMetadata tm = null;
        try {
            Enumeration<TokenMetadata> tokenList = m_lib.getTokenList();
            if(tokenList != null) {
                TokenMetadata tmTemp = null;
                while(tokenList.hasMoreElements()) {
                    tmTemp = tokenList.nextElement();
                    if(tmTemp.getSerialNumber().equals(tokenSerialNumber)) {
                        tm = tmTemp;
                        break;
                    }
                }
                if(tm != null) {
                    //get token serial number
                    String tokenSN = tm.getSerialNumber();
                    //retrieve token expiration date and format it
                    long expirationDateMillis = tm.getExpirationDate();
                    Date date = new Date(expirationDateMillis);
                    String strExpirationDate = m_usersDateFormat.format(date);
                    //get interval in seconds between the current one-time password (OTP) and the next OTP
                    //30 for 30-second token or 60 for 60-second token
                    int tokenInterval = tm.getInterval();
                    //get length of OTP
                    //6 for 6-digit token or 8 for 8-digit token
                    int tokenLength = tm.getLength();
                    //get token PIN type: PINPad, PINless, or Fob
                    //for PINPad token, mobile client application should prompt user for the PIN
                    //to get passcode (PIN combined with OTP)
                    int tokenPINType = tm.getType();
                    //get token nickname
                    String nickname = tm.getNickname();
                }
            }
        }
        catch(SecurIDLibException ex) {
            ex.printStackTrace();
            displayError(ex);
        }
        return tm;
    }

    /**
     * Get OTP for the token specified by the token serial number
     * @param tokenSerialNumber serial number of the token
     * @param pin PIN assigned to the token
     * @return OTP information for the token
     */
    Otp getTokenCode(String tokenSerialNumber, String pin)
    {
        Otp otp = null;
        try {
            if(pin == null) { pin = ""; }  //if there is no PIN, use empty string
            otp = m_lib.getOtp(tokenSerialNumber, pin.getBytes());
        }
        catch(SecurIDLibException ex) {
            ex.printStackTrace();
            displayError(ex);
        }
        return otp;
    }

    /**
     * Import a token from a CTF string
     * @param ctf token string in CTF format (81-digit string)
     * @param ctfPassword token file password
     * @return token serial number of the imported token
     */
    String importTokenFromCTF(String ctf, String ctfPassword)
    {
        String tokenSerialNumber = null;
        try {
            if(ctfPassword == null) { ctfPassword = ""; } //if there is no token file password, use empty string
            tokenSerialNumber = m_lib.importTokenFromCtf(ctf, ctfPassword.getBytes());
        }
        catch(SecurIDLibException ex) {
            ex.printStackTrace();
            displayError(ex);
        }

        return tokenSerialNumber;
    }

    /**
     * Import a token from CT-KIP
     * @param url URL of the CT-KIP server
     * @param activationCode one-time activation code required for CT-KIP provisioning
     * @param allowUntrustedCert allow or prohibit untrusted CT-KIP server certificate
     * @return token serial number of the imported token
     */
    String importTokenFromCTKIP(String url, String activationCode, boolean allowUntrustedCert)
    {
        String tokenSerialNumber = null;

        try {
            tokenSerialNumber = m_lib.importTokenFromCtkip(url, activationCode, allowUntrustedCert);
        }
        catch(SecurIDLibException ex) {
            ex.printStackTrace();
            displayError(ex);
        }

        return tokenSerialNumber;
    }

    /**
     * Import a token from a token file. The file is also called an SDTID file due
     * to the .sdtid file extension.
     * @param filePath path to the token file
     * @param password token file password
     * @return token serial number of the imported token
     */
    String importTokenFromFile(String filePath, String password)
    {
        String tokenSerialNumber = null;

        try {
            tokenSerialNumber = m_lib.importTokenFromFile(filePath, password.getBytes());
        }
        catch(SecurIDLibException ex) {
            ex.printStackTrace();
            displayError(ex);
        }

        return tokenSerialNumber;
    }

    /**
     * Delete a token specified by the token serial number
     * @param tokenSerialNumber serial number of the token
     * @return true token deleted successfully
     * @return false failed to delete token
     */
    boolean deleteToken(String tokenSerialNumber)
    {
        boolean ret = true;
        try {
            m_lib.deleteToken(tokenSerialNumber);
        }
        catch(SecurIDLibException ex) {
            ret = false;
            ex.printStackTrace();
            displayError(ex);
        }

        return ret;
    }

    /**
     * Set or change a token nickname
     * @param tokenSerialNumber serial number of the token
     * @param tokenNickname new token nickname
     * @return true token is renamed successfully
     * @return false failed to rename token
     */
    boolean renameToken(String tokenSerialNumber, String tokenNickname)
    {
        boolean ret = true;
        try {
            m_lib.setTokenNickname(tokenSerialNumber, tokenNickname);
        }
        catch(SecurIDLibException ex) {
            ret = false;
            ex.printStackTrace();
            displayError(ex);
        }

        return ret;
    }

    /**
     * Get Device ID used for token device binding
     * @return Device ID
     */
    String getDeviceID()
    {
        String deviceID = "";
        try {
            deviceID = m_lib.getDeviceId();
        }
        catch(SecurIDLibException ex) {
            ex.printStackTrace();
            displayError(ex);
        }

        return deviceID;
    }

    /**
     * Get the SDK library information
     * @return SDK library information
     */
    String getLibaryInfo()
    {
        return m_lib.getLibraryInfo();
    }

    /**
     * Display error message based on SecurIDLibException
     * @param ex exception information to be displayed
     */
    private void displayError(SecurIDLibException ex)
    {
        String errorMsg;
        if(ex instanceof DatabaseFullException) {
            errorMsg = m_context.getString(R.string.databasefullexception);
        }
        else if(ex instanceof DatabaseException) {
            errorMsg = m_context.getString(R.string.databaseexception);
        }
        else if(ex instanceof DeviceIDInaccessibleException) {
            errorMsg = m_context.getString(R.string.deviceidinaccessibleexception);
        }
        else if(ex instanceof DuplicateTokenException) {
            errorMsg = m_context.getString(R.string.duplicatetokenexception);
        }
        else if(ex instanceof ExpiredTokenException) {
            errorMsg = m_context.getString(R.string.expiredtokenexception);
        }
        else if(ex instanceof InvalidDeviceBindingException) {
            errorMsg = m_context.getString(R.string.invaliddevicebindingexception);
        }
        else if(ex instanceof TokenNotFoundException) {
            errorMsg = m_context.getString(R.string.tokennotfoundexception);
        }
        else if(ex instanceof TokenImportFailureException) {
            errorMsg = m_context.getString(R.string.tokenimportexception);
        }
        else {
            errorMsg = m_context.getString(R.string.general);
        }

        Toast toast = Toast.makeText(m_context, errorMsg, Toast.LENGTH_SHORT);
        toast.show();
    }
}