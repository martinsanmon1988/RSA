package com.example.martin.myapplication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.rsa.securidlib.exceptions.SecurIDLibException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

/**
 * This activity displays the SecurIDSample UI. You select one of the token
 * import methods: CTF, CT-KIP, or File. For each method, you enter the required
 * information and perform the import using the SDK library.
 *
 * For CTF import, you must provide an 81-digit CTF string and an optional password (if the token record is
 * password protected).
 *
 * For CT-KIP import, you must provide a CT-KIP URL and one-time activation code. Optionally,
 * you can skip the CT-KIP server certificate check for the SSL connection.
 *
 * For File import, a sample SDTID file is included with the project for demonstration purpose.
 * The SDTID file field and Password field are pre-filled and disabled in the SecurIDSample UI.
 */
public class ImportTokenActivity extends Activity {

    private Context m_context;
    private SecurIDLibHelper m_libHelper;
    private RadioButton m_importCTKIP, m_importCTF, m_importFile;
    private EditText m_importText1, m_importText2;
    private CheckBox m_allowUntrustedCertCB;

    private final static String SDTIDFILE_PATH = "/data/data/com.rsa.securidsample/";
    private final static String SDTIDFILE_NAME = "jsmith_000119076712.sdtid";
    private final static String SDTIDFILE_PASSWORD = "q98E24I7mCm56Ti$";  //password for the sample SDTID file

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.import_screen);
        m_context = this;
        try {
            m_libHelper = SecurIDLibHelper.getInstance(this);
        }
        catch(SecurIDLibException ex) {
            finish();
        }
        initializeUI();

        try {
            //use the SDTID file attached to the project for demonstration
            copySDTIDFile();
        }
        catch(IOException ex) {
        }
    }

    /**
     * Initialize Import Token screen UI components
     */
    private void initializeUI()
    {
        m_importCTKIP = (RadioButton)this.findViewById(R.id.ctkipImport);
        m_importCTKIP.setOnClickListener(importRadioButtonListener);
        m_allowUntrustedCertCB = (CheckBox)findViewById(R.id.allowUntrustedCertCB);

        m_importCTF = (RadioButton)this.findViewById(R.id.ctfImport);
        m_importCTF.setOnClickListener(importRadioButtonListener);

        m_importFile = (RadioButton)this.findViewById(R.id.fileImport);
        m_importFile.setOnClickListener(importRadioButtonListener);

        m_importText1 = (EditText)findViewById(R.id.importText1);
        m_importText2 = (EditText)findViewById(R.id.importText2);

        Button submitButton = (Button)this.findViewById(R.id.importTokenButton);
        submitButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view)
            {
                performImport();
            }
        });
    }

    /**
     * Perform token import based on the import method selected
     */
    private void performImport()
    {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        String tokenSerialNumber = null;
        if(m_importCTKIP.isChecked()) {
            String ctkipURL = m_importText1.getText().toString().trim();
            String ctkipActivationCode = m_importText2.getText().toString().trim();
            boolean allowUntrustedCert = m_allowUntrustedCertCB.isChecked();
            //import a token using CT-KIP
            tokenSerialNumber = m_libHelper.importTokenFromCTKIP(ctkipURL, ctkipActivationCode, allowUntrustedCert);
        }
        else if(m_importCTF.isChecked()) {
            //import a token using CTF
            String ctf = m_importText1.getText().toString().trim();
            String password = m_importText2.getText().toString().trim();
            tokenSerialNumber = m_libHelper.importTokenFromCTF(ctf, password);
        }
        else {
            //import a token using the provided sample SDTID file
            String sdtidPath = m_importText1.getText().toString().trim();
            String password = m_importText2.getText().toString().trim();
            tokenSerialNumber = m_libHelper.importTokenFromFile(sdtidPath, password);
        }

        //complete and return to the main UI
        Intent resultIntent = new Intent();
        resultIntent.putExtra(MapsActivity.IMPORTED_TOKEN_SERIALNUMBER, tokenSerialNumber);
        ((ImportTokenActivity)m_context).setResult(RESULT_OK, resultIntent);
        ((ImportTokenActivity)m_context).finish();
    }


    /**
     * Event handler that handles switch of token import methods.
     */
    RadioButton.OnClickListener importRadioButtonListener = new RadioButton.OnClickListener() {
        @Override
        public void onClick(View view) {
            updateImportUI();
        }
    };

    /**
     * Update UI based on the import method selected
     */
    private void updateImportUI()
    {
        TextView importLabel1 = (TextView)findViewById(R.id.importLabel1);
        TextView importLabel2 = (TextView)findViewById(R.id.importLabel2);
        m_importText1.setText("");
        m_importText2.setText("");

        if(m_importCTF.isChecked()) {
            importLabel1.setText(R.string.ctfLabel);

            m_importText1.setHint(R.string.ctfHint);
            m_importText1.setEnabled(true);

            importLabel2.setText(R.string.passwordLabel);

            m_importText2.setHint(R.string.ctfPasswrodHint);
            m_importText2.setVisibility(View.VISIBLE);
            m_importText2.setEnabled(true);

            m_allowUntrustedCertCB.setVisibility(View.INVISIBLE);
        }
        else if(m_importCTKIP.isChecked()) {
            importLabel1.setText(R.string.ctkipURL);

            m_importText1.setHint(R.string.ctkipURLHint);
            m_importText1.setEnabled(true);

            importLabel2.setText(R.string.ctkipActivationCode);

            m_importText2.setHint(R.string.ctkipActivationCodeHint);
            m_importText2.setVisibility(View.VISIBLE);
            m_importText2.setEnabled(true);

            m_allowUntrustedCertCB.setVisibility(View.VISIBLE);
        }
        else {
            importLabel1.setText(R.string.fileLabel);

            m_importText1.setHint(R.string.fileHint);
            m_importText1.setText(SDTIDFILE_PATH + SDTIDFILE_NAME);
            m_importText1.setEnabled(false);

            importLabel2.setText(R.string.passwordLabel);

            m_importText2.setHint(R.string.filePasswordHint);
            m_importText2.setVisibility(View.VISIBLE);
            m_importText2.setText(SDTIDFILE_PASSWORD);
            m_importText2.setEnabled(false);

            m_allowUntrustedCertCB.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Copy the sample SDTID file from resource to application folder
     * @throws IOException
     */
    private void copySDTIDFile() throws IOException
    {
        File file = m_context.getFileStreamPath(SDTIDFILE_NAME);

        if(file != null && !file.exists()) {
            InputStream sdtidFileInput = m_context.getAssets().open(SDTIDFILE_NAME);
            OutputStream sdtidFileOutput = new FileOutputStream(SDTIDFILE_PATH + SDTIDFILE_NAME);

            byte[] buffer = new byte[1024];
            int length;
            while((length = sdtidFileInput.read(buffer)) > 0) {
                sdtidFileOutput.write(buffer, 0, length);
            }
            sdtidFileOutput.flush();
            sdtidFileOutput.close();
            sdtidFileInput.close();
        }
    }
}