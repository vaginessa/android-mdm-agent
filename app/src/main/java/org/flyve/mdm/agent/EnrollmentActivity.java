/*
 *   Copyright © 2017 Teclib. All rights reserved.
 *
 * this file is part of flyve-mdm-android-agent
 *
 * flyve-mdm-android-agent is a subproject of Flyve MDM. Flyve MDM is a mobile
 * device management software.
 *
 * Flyve MDM is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * Flyve MDM is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * ------------------------------------------------------------------------------
 * @author    Rafael Hernandez
 * @date      02/06/2017
 * @copyright Copyright © 2017 Teclib. All rights reserved.
 * @license   GPLv3 https://www.gnu.org/licenses/gpl-3.0.html
 * @link      https://github.com/flyve-mdm/flyve-mdm-android-agent
 * @link      https://flyve-mdm.com
 * ------------------------------------------------------------------------------
 */

package org.flyve.mdm.agent;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.flyvemdm.inventory.categories.Hardware;

import org.flyve.mdm.agent.core.supervisor.SupervisorController;
import org.flyve.mdm.agent.core.supervisor.SupervisorModel;
import org.flyve.mdm.agent.core.user.UserController;
import org.flyve.mdm.agent.core.user.UserModel;
import org.flyve.mdm.agent.data.DataStorage;
import org.flyve.mdm.agent.security.AndroidCryptoProvider;
import org.flyve.mdm.agent.utils.EnrollmentHelper;
import org.flyve.mdm.agent.utils.FlyveLog;
import org.flyve.mdm.agent.utils.Helpers;
import org.flyve.mdm.agent.utils.InputValidatorHelper;
import org.flyve.mdm.agent.utils.MultipleEditText;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import static org.flyve.mdm.agent.R.string.email;


/**
 * Register the agent to the platform
 */
public class EnrollmentActivity extends AppCompatActivity {

    private ProgressBar pbx509;
    private DataStorage cache;
    private EnrollmentHelper enroll;

    private TextView txtMessage;
    private EditText editName;
    private EditText editLastName;
    private EditText editAdministrative;
    private UserModel user;
    private MultipleEditText editEmail;
    private MultipleEditText editPhone;
    private Spinner spinnerLanguage;
    private ImageView btnRegister;
    private boolean sendEnrollment = false;

    private ProgressDialog pd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_form);

        user = new UserController(EnrollmentActivity.this).getCache();

        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
        }

        pbx509 = (ProgressBar) findViewById(R.id.progressBarX509);

        enroll = new EnrollmentHelper(EnrollmentActivity.this);
        cache = new DataStorage(EnrollmentActivity.this);

        txtMessage = (TextView) findViewById(R.id.txtMessage);

        editName = (EditText) findViewById(R.id.editName);
        editLastName = (EditText) findViewById(R.id.editLastName);

        // Multiples Emails
        LinearLayout lnEmails = (LinearLayout) findViewById(R.id.lnEmails);
        editEmail = new MultipleEditText(this, lnEmails, getResources().getString(email));
        editEmail.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        editEmail.setSpinnerArray(R.array.email_array);
        lnEmails.addView( editEmail.createEditText() );

        // 3 Phones
        LinearLayout lnPhones = (LinearLayout) findViewById(R.id.lnPhones);
        editPhone = new MultipleEditText(this, lnPhones, getResources().getString(R.string.phone));
        editPhone.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_CLASS_PHONE);
        editPhone.setLimit(3);
        editPhone.setSpinnerArray(R.array.phone_array);
        lnPhones.addView( editPhone.createEditText() );

        // Language
        spinnerLanguage = (Spinner) findViewById(R.id.spinnerLanguage);

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.language_array, android.R.layout.simple_spinner_item);

        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the spinner
        spinnerLanguage.setAdapter(adapter);

        editAdministrative = (EditText) findViewById(R.id.editAdministrative);
        editAdministrative.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editAdministrative.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    validateForm();
                    return true;
                }
                return false;
            }
        });

        btnRegister = (ImageView) findViewById(R.id.btnSave);
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateForm();
            }
        });

        // start creating a certificated
        pbx509.setVisibility(View.VISIBLE);
        enroll.createX509cert(new EnrollmentHelper.enrollCallBack() {
            @Override
            public void onSuccess(String data) {
                pbx509.setVisibility(View.GONE);
                if(sendEnrollment) {
                    pd.dismiss();
                    validateForm();
                }
            }

            @Override
            public void onError(String error) {
                pbx509.setVisibility(View.GONE);
                showError("Error creating certificate X509");
            }
        });
    }

    /**
     * Send information to validateForm
     */
    private void validateForm() {
        StringBuilder errMsg = new StringBuilder("Please fix the following errors and try again.\n\n");
        txtMessage.setText("");

        // Hide keyboard
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

        // waiting for cert x509
        if(pbx509.getVisibility() == View.VISIBLE) {
            sendEnrollment = true;
            pd = ProgressDialog.show(EnrollmentActivity.this, "", getResources().getString(R.string.creating_certified_x509));
            return;
        }

        //Validate and Save
        boolean allowSave = true;

        String name = editName.getText().toString().trim();
        String lastName = editLastName.getText().toString().trim();

        // First name
        if (InputValidatorHelper.isNullOrEmpty(name)) {
            errMsg.append("- First name should not be empty.\n");
            allowSave = false;
        }

        // Last name
        if (InputValidatorHelper.isNullOrEmpty(lastName)) {
            errMsg.append("- Last name should not be empty.\n");
            allowSave = false;
        }

        // Email
        if (editEmail.getEditList().isEmpty()) {
            errMsg.append("- Please add at least one email.\n");
            allowSave = false;
        }

        if(allowSave){
            sendEnroll();
        } else {
            txtMessage.setText(errMsg);
        }
    }

    /**
     * Send information to validateForm the device
     */
    private void sendEnroll() {
        try {
            pd = ProgressDialog.show(EnrollmentActivity.this, "", getResources().getString(R.string.loading));

            AndroidCryptoProvider csr = new AndroidCryptoProvider(EnrollmentActivity.this.getBaseContext());
            String requestCSR = "";
            if( csr.getlCsr() != null ) {
                requestCSR = URLEncoder.encode(csr.getlCsr(), "UTF-8");
            }

            JSONObject payload = new JSONObject();

            payload.put("_email", editEmail.getEditList().get(0).getText().toString());
            payload.put("_invitation_token", cache.getInvitationToken());
            payload.put("_serial", Helpers.getDeviceSerial());
            payload.put("_uuid", new Hardware(EnrollmentActivity.this).getUUID());
            payload.put("csr", requestCSR);
            payload.put("firstname", editName.getText().toString());
            payload.put("lastname", editLastName.getText().toString());
            if(!editPhone.getEditList().isEmpty()) {
                payload.put("phone", editPhone.getEditList().get(0).getText().toString());
            }
            payload.put("version", BuildConfig.VERSION_NAME);

            enroll.enrollment(payload, new EnrollmentHelper.enrollCallBack() {
                @Override
                public void onSuccess(String data) {
                    pd.dismiss();

                    ArrayList<UserModel.EmailsData> arrEmails = new ArrayList<>();
                    UserModel.EmailsData emails = new UserModel().new EmailsData();

                    List<EditText> emailEdit = editEmail.getEditList();
                    List<Spinner> emailTypeEdit = editEmail.getSpinnList();

                    for (int i=0; i<emailEdit.size(); i++) {
                        EditText editText = emailEdit.get(i);
                        Spinner spinner = emailTypeEdit.get(i);

                        if(!editText.getText().toString().equals("")) {
                            emails.setEmail(editText.getText().toString());
                            emails.setType(spinner.getSelectedItem().toString());
                        }

                        arrEmails.add(emails);
                    }

                    // -------------------------------
                    // Store user information
                    // -------------------------------
                    UserModel userModel = new UserModel();
                    userModel.setFirstName(editName.getText().toString());
                    userModel.setLastName(editLastName.getText().toString());
                    userModel.setEmails(arrEmails);

                    // Mobile Phone
                    if(!editPhone.getEditList().isEmpty()) {
                        String mobilePhone = editPhone.getEditList().get(0).getText().toString();
                        if (!mobilePhone.equals("")) {
                            user.setMobilePhone(mobilePhone);
                        }
                    }

                    // Phone
                    if(editPhone.getEditList().size() > 1) {
                        String phone = editPhone.getEditList().get(1).getText().toString();
                        if (!phone.equals("")) {
                            user.setPhone(phone);
                        }
                    }

                    // Phone 2
                    if(editPhone.getEditList().size() > 2) {
                        String phone2 = editPhone.getEditList().get(2).getText().toString();
                        if (!phone2.equals("")) {
                            user.setPhone(phone2);
                        }
                    }

                    user.setLanguage( spinnerLanguage.getSelectedItem().toString() );
                    user.setAdministrativeNumber( editAdministrative.getText().toString() );

                    new UserController(EnrollmentActivity.this).save(userModel);

                    // -------------------------------
                    // Store supervisor information
                    // -------------------------------
                    SupervisorModel supervisorModel = new SupervisorModel();

                    supervisorModel.setName("Teclib Spain SL");
                    supervisorModel.setEmail("sales@teclib.com");

                    new SupervisorController(EnrollmentActivity.this).save(supervisorModel);

                    openMain();
                }

                @Override
                public void onError(String error) {
                    pd.dismiss();
                    showError(error);
                }
            });
        } catch (Exception ex) {
            pd.dismiss();
            showError( ex.getMessage() );
            FlyveLog.e( ex.getMessage() );
        }
    }

    private void showError(String message) {
        Helpers.snack(this, message, this.getResources().getString(R.string.snackbar_close), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
    }

    /**
     * Open the main activity
     */
    private void openMain() {
        Intent intent = new Intent(EnrollmentActivity.this, MainActivity.class);
        EnrollmentActivity.this.startActivity(intent);
        setResult(RESULT_OK, null);
        EnrollmentActivity.this.finish();
    }
}
