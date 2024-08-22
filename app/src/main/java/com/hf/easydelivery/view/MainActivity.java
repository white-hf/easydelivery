package com.hf.easydelivery.view;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;

import android.os.Bundle;

import android.util.Log;


import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import com.hf.easydelivery.R;
import com.hf.easydelivery.map.MapActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    AppCompatButton  btn_order_detail;
    private EditText mEditText;
    private EditText mPickId;
    private Spinner spinner;

    private final ArrayList<String> mSearchOrder = new ArrayList<>();
    
    private ListView mLvSearchOrder;

    private AlertDialog mLoginDialog;
    private AlertDialog.Builder  mSystemOperationDialog;


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_barcode);

        initLoginDialog();


        AppCompatButton btn_Setting = findViewById(R.id.btn_setting);
        btn_Setting.setOnClickListener((view)->{
                Intent intent = new Intent(getApplication(), SettingsActivity.class);
                startActivity(intent);
            });

        AppCompatButton btn_delivery = findViewById(R.id.btn_delivery);
        btn_delivery.setOnClickListener((view)->{
            Intent intent = new Intent(getApplication(), MapActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);
        });


        initSystemOperation();

    }


    private void initSystemOperation()
    {
        AppCompatButton btn_systemOperation = (AppCompatButton)findViewById(R.id.btn_cacheOrders);
        btn_systemOperation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSystemOperationDialog.show();
             }
        });

        mSystemOperationDialog = new AlertDialog.Builder(this);

        mSystemOperationDialog.setTitle(R.string.operation_choose);
        final String[] opertionsArray = new String[] {"已派送包裹查询"};

        final int selectedIndex[] = { 0 };

        mSystemOperationDialog.setSingleChoiceItems(opertionsArray, 0,
                (dialog,which)->{
                        selectedIndex[0] = which;
                    });

        mSystemOperationDialog.setPositiveButton(R.string.str_confirm,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (selectedIndex[0])
                        {
                            case 0:
                                showDeliveredPackages();
                                break;
                            default:
                                break;
                        }

                        dialog.dismiss();
                    }
                });

        mSystemOperationDialog.setNegativeButton(R.string.str_cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
    }

    private void initLoginDialog() {
        AppCompatButton btn_login = (AppCompatButton)findViewById(R.id.btnLogin);
        btn_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mLoginDialog.show();
            }
        });

        mLoginDialog = LoginDialog.init(this);
    }
    private void showDeliveredPackages() {
        PackageListFragment packageListFragment = new PackageListFragment();
        getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, packageListFragment)
                .addToBackStack(null)
                .commit();
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d("debug", "onResume()");
    }
}