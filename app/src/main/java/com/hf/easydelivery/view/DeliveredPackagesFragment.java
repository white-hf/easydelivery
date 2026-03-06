package com.hf.easydelivery.view;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.adapter.DeliveredPackagesAdapter;
import com.hf.easydelivery.core.PendingPackagesMgr;
import com.hf.easydelivery.dao.PackageEntity;
import com.hf.easydelivery.viewmodel.DeliveredPackagesViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DeliveredPackagesFragment extends Fragment implements DeliveredPackagesAdapter.OnItemDoubleClickListener {

    private static final String ARG_DATE = "arg_date";

    private DeliveredPackagesViewModel viewModel;
    private RecyclerView recyclerView;
    private DeliveredPackagesAdapter adapter;
    private TextView dateTextView;
    private Spinner statusFilterSpinner;

    public static DeliveredPackagesFragment newInstance(long date) {
        Bundle args = new Bundle();
        args.putLong(ARG_DATE, date);
        DeliveredPackagesFragment fragment = new DeliveredPackagesFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_delivered_packages, container, false);
        dateTextView = view.findViewById(R.id.date_selector_textview);
        statusFilterSpinner = view.findViewById(R.id.status_filter_spinner);
        recyclerView = view.findViewById(R.id.delivered_packages_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DeliveredPackagesAdapter(this);
        recyclerView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DeliveredPackagesViewModel.class);
        setupStatusFilter();

        if (getArguments() != null) {
            long date = getArguments().getLong(ARG_DATE);
            viewModel.setSelectedDate(date);
            String formattedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(date));
            dateTextView.setText(getString(R.string.delivered_packages_date_format, formattedDate));
        }

        viewModel.getDeliveredPackages().observe(getViewLifecycleOwner(), packages -> {
            adapter.setPackages(packages);
        });
    }

    @Override
    public void onItemDoubleClick(PackageEntity item) {
        if (item.status.equals(PendingPackagesMgr.PackageStatus.FAILED.getStatus())) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.delivered_packages_reupload_title)
                    .setMessage(R.string.delivered_packages_reupload_message)
                    .setPositiveButton(R.string.action_yes, (dialog, which) -> {
                        ResourceMgr.getInstance().getPendingPackagesMgr().update(
                                item.trackingId,
                                PendingPackagesMgr.PackageStatus.Pending.getStatus());
                        ResourceMgr.getInstance().getPendingPackagesMgr().addQueue(item, true);
                        Toast.makeText(requireContext(),
                                R.string.delivered_packages_reupload_queued,
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.action_no, null)
                    .show();
        }
    }

    private void setupStatusFilter() {
        List<FilterOption> options = new ArrayList<>();
        options.add(new FilterOption(getString(R.string.delivered_packages_filter_all), DeliveredPackagesViewModel.FILTER_ALL));
        options.add(new FilterOption(getString(R.string.delivered_packages_filter_pending), PendingPackagesMgr.PackageStatus.Pending.getStatus()));
        options.add(new FilterOption(getString(R.string.delivered_packages_filter_uploaded), PendingPackagesMgr.PackageStatus.UPLOADED.getStatus()));
        options.add(new FilterOption(getString(R.string.delivered_packages_filter_failed), PendingPackagesMgr.PackageStatus.FAILED.getStatus()));

        ArrayAdapter<FilterOption> spinnerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                options
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusFilterSpinner.setAdapter(spinnerAdapter);
        statusFilterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                FilterOption option = (FilterOption) parent.getItemAtPosition(position);
                viewModel.setSelectedStatus(option.value);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private static class FilterOption {
        final String label;
        final String value;

        FilterOption(String label, String value) {
            this.label = label;
            this.value = value;
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
    }
}
