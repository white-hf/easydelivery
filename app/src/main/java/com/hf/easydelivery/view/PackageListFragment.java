package com.hf.easydelivery.view;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.hf.easydelivery.R;
import com.hf.easydelivery.view.Adapter.PackageAdapter;
import com.hf.easydelivery.dao.PackageEntity;

import java.util.List;

public class PackageListFragment extends Fragment {
    private PackageViewModel packageViewModel;
    private RecyclerView recyclerView;
    private PackageAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private Spinner statusSpinner;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_packages_list, container, false);

        recyclerView = view.findViewById(R.id.recycler_view);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyView = view.findViewById(R.id.empty_view);
        statusSpinner = view.findViewById(R.id.status_spinner);

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.status_array, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(spinnerAdapter);

        statusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedStatus = (String) parent.getItemAtPosition(position);
                fetchPackagesByStatus(selectedStatus);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });


        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PackageAdapter();
        recyclerView.setAdapter(adapter);

        packageViewModel = new ViewModelProvider(this).get(PackageViewModel.class);

        return view;
    }

    private void fetchPackagesByStatus(String status) {
        progressBar.setVisibility(View.VISIBLE);
        packageViewModel.getPackagesByStatus(status).observe(getViewLifecycleOwner(), new Observer<List<PackageEntity>>() {
            @Override
            public void onChanged(List<PackageEntity> packageEntities) {
                progressBar.setVisibility(View.GONE);
                if (packageEntities != null && !packageEntities.isEmpty()) {
                    adapter.setPackages(packageEntities);
                    recyclerView.setVisibility(View.VISIBLE);
                    emptyView.setVisibility(View.GONE);
                } else {
                    recyclerView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                }
            }
        });
    }
}
