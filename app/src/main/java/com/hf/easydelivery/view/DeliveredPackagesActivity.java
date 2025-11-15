package com.hf.easydelivery.view;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hf.easydelivery.R;

public class DeliveredPackagesActivity extends AppCompatActivity {

    public static final String EXTRA_DATE = "extra_date";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivered_packages);

        if (savedInstanceState == null) {
            long date = getIntent().getLongExtra(EXTRA_DATE, System.currentTimeMillis());
            DeliveredPackagesFragment fragment = DeliveredPackagesFragment.newInstance(date);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
        }
    }
}
