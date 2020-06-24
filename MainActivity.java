package com.example.creditcard;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.exceptions.BraintreeError;
import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.interfaces.BraintreeCancelListener;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.BraintreeListener;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCreatedListener;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.models.PaymentMethodNonce;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements BraintreeListener, View.OnClickListener {

    private BraintreeFragment mBraintreeFragment;

    private EditText etCvv, etExpiry, etName, etCardNo;
    private Button payNow;

    private String token, stringFy, mLastInput = "";
    private Boolean saveCard = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getToken();

        inits();
    }

    private void inits() {

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        setTitle("Credit/Debit Card");

        etName = findViewById(R.id.etName);
        etCardNo = findViewById(R.id.etCardNo);
        etCvv = findViewById(R.id.etCvv);
        etExpiry = findViewById(R.id.etExpiry);

        payNow = findViewById(R.id.payNow);
        payNow.setOnClickListener(this);

        etExpiry.addTextChangedListener(textWatcher);

        Switch switchSaveCard = findViewById(R.id.switchSaveCard);
        switchSaveCard.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                saveCard = isChecked;
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.payNow) {
            payNow.setText("Processing...");
            startCardReading();
        }
    }

    TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence p0, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            String input = s.toString();
            SimpleDateFormat formatter = new SimpleDateFormat("MM/yy", Locale.getDefault());
            Calendar expiryDateDate = Calendar.getInstance();
            try {
                expiryDateDate.setTime(Objects.requireNonNull(formatter.parse(input)));
            } catch (ParseException e) {

                if (s.length() == 2 && !mLastInput.endsWith("/")) {
                    int month = Integer.parseInt(input);
                    if (month <= 12) {
                        etExpiry.setText(etExpiry.getText().toString() + "/");
                        etExpiry.setSelection(etExpiry.getText().toString().length());
                    }
                } else if (s.length() == 2 && mLastInput.endsWith("/")) {
                    int month = Integer.parseInt(input);
                    if (month <= 12) {
                        etExpiry.setText(etExpiry.getText().toString().substring(0, 1));
                        etExpiry.setSelection(etExpiry.getText().toString().length());
                    } else {
                        etExpiry.setText("");
                        etExpiry.setSelection(etExpiry.getText().toString().length());
                        Toast.makeText(getApplicationContext(), "Enter a valid month", Toast.LENGTH_LONG).show();
                    }
                } else if (s.length() == 1) {
                    int month = Integer.parseInt(input);
                    if (month > 1) {
                        etExpiry.setText("0" + etExpiry.getText().toString() + "/");
                        etExpiry.setSelection(etExpiry.getText().toString().length());
                    }
                } else {
                    Log.d("beant", etExpiry.getText().toString());
                }
                mLastInput = etExpiry.getText().toString();
            }
        }
    };


    private void getToken() {

        RequestCache stringLoad = new RequestCache(Request.Method.GET, Utils.API_FETCH_BRAIN_TREE_TOKEN
                + "?amount=10&user_id=abcde",
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            Log.e("response", response);
                            JSONObject res = new JSONObject(response);

                            if (res.getInt("status") == 200) {

                                token = res.getString("AuthToken");
                                stringFy = response;
                            } else {
                                Log.e("response", res.getInt("status") + "");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("response", Objects.requireNonNull(error.getMessage()));
                    }
                });
        AppController.getInstance(this).addToRequestQueue(stringLoad);
    }

    private void startCardReading() {

        String cardNo = etCardNo.getText().toString().trim();
        String name = etName.getText().toString().trim();
        String expiry = etExpiry.getText().toString().trim();
        String cvv = etCvv.getText().toString().trim();

        if (cardNo.isEmpty()) {
            Toast.makeText(this, "Please enter card number", Toast.LENGTH_SHORT).show();
        } else if (cardNo.length() < 12) {
            Toast.makeText(this, "Please enter valid card number", Toast.LENGTH_SHORT).show();
        } else if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
        } else if (expiry.isEmpty() || !expiry.contains("/")) {
            Toast.makeText(this, "Please enter expiry date", Toast.LENGTH_SHORT).show();
        } else if (cvv.isEmpty() || cvv.length() < 3) {
            Toast.makeText(this, "Please enter your cvv code", Toast.LENGTH_SHORT).show();
        } else {
            try {
                mBraintreeFragment = BraintreeFragment.newInstance(MainActivity.this, token);

                CardBuilder cardBuilder = new CardBuilder()
                        .cardNumber(cardNo)
                        .expirationDate(expiry)
                        .cardholderName(name)
                        .cvv(cvv);
                cardBuilder.validate(true);

                Card.tokenize(mBraintreeFragment, cardBuilder);

                mBraintreeFragment.addListener(createdListener);
                mBraintreeFragment.addListener(cancelListener);
                mBraintreeFragment.addListener(errorListener);

                JSONObject jsonObject = new JSONObject(stringFy);
                if (saveCard) {
                    jsonObject.put("nameOnCard", name);
                    jsonObject.put("canSaveCard", "yes");
                } else {
                    jsonObject.put("canSaveCard", "no");
                }
                stringFy = jsonObject.toString();
            } catch (InvalidArgumentException | JSONException e) {
                e.printStackTrace();
                onSaveError();
            }
        }
    }

    private void uploadNonceId(final String nonceId) {

        RequestCache stringLoad = new RequestCache(Request.Method.POST, Utils.API_BRAIN_TREE_CONFIRM_PAYMENT,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        try {
                            payNow.setText("Pay Now");
                            Log.e("response", response);
                            JSONObject res = new JSONObject(response);

                            if (res.getInt("status") == 200) {
                                Log.e("response", "code " + res.getInt("status"));

                                Toast.makeText(MainActivity.this, res.getString("message"), Toast.LENGTH_SHORT).show();
                            } else {
                                Log.e("response", "code " + res.getInt("status"));
                                Toast.makeText(MainActivity.this, res.getString("message"), Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            onSaveError();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("response", Objects.requireNonNull(error.getMessage()));
                        onSaveError();
                    }
                }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("payload", stringFy);
                params.put("nonce", nonceId);
                return params;
            }
        };

        AppController.getInstance(this).addToRequestQueue(stringLoad);
    }

    PaymentMethodNonceCreatedListener createdListener = new PaymentMethodNonceCreatedListener() {
        @Override
        public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {

            String nonce = paymentMethodNonce.getNonce();

            Log.d("beant", "nonce id  " + nonce);

            uploadNonceId(nonce);
        }
    };

    BraintreeCancelListener cancelListener = new BraintreeCancelListener() {
        @Override
        public void onCancel(int requestCode) {
            Log.d("beant", "Braintree Error Code  " + requestCode);
            onSaveError();
        }
    };

    BraintreeErrorListener errorListener = new BraintreeErrorListener() {
        @Override
        public void onError(Exception error) {
            if (error instanceof ErrorWithResponse) {
                ErrorWithResponse errorWithResponse = (ErrorWithResponse) error;
                BraintreeError cardErrors = errorWithResponse.errorFor("creditCard");
                if (cardErrors != null) {

                    List<BraintreeError> errors = cardErrors.getFieldErrors();

                    String err = Objects.requireNonNull(errors.get(0).getMessage());

                    Log.d("beant", errors.toString());

                    Toast.makeText(MainActivity.this, err, Toast.LENGTH_SHORT).show();

                    payNow.setText("Pay Now");
                }
            }
        }
    };

    @Override
    protected void onStop() {
        super.onStop();

        if (mBraintreeFragment != null)
            mBraintreeFragment.removeListener(createdListener);
    }

    private void onSaveError() {

        Toast.makeText(MainActivity.this, "Please try again later", Toast.LENGTH_SHORT).show();
        payNow.setText("Pay Now");
    }
}