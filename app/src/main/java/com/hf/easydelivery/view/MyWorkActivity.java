package com.hf.easydelivery.view;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.hf.easydelivery.R;
import com.hf.easydelivery.domain.WorkStatsRepository;
import com.hf.easydelivery.domain.WorkStatsRepository.MonthSummary;
import com.hf.easydelivery.view.Adapter.WorkStatsAdapter;
import com.hf.easydelivery.view.model.WorkStatsViewModel;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MyWorkActivity extends AppCompatActivity implements WorkStatsAdapter.OnItemClickListener {

    private WorkStatsViewModel viewModel;
    private TextView tvSelectedMonth;
    private TextView tvSummary;
    private TextView tvEmptyState;
    private WorkStatsAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_work);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSelectedMonth = findViewById(R.id.tv_selected_month);
        tvSummary = findViewById(R.id.tv_month_summary);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        Button pickMonthBtn = findViewById(R.id.btn_pick_month);
        RecyclerView recyclerView = findViewById(R.id.rv_work_stats);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WorkStatsAdapter(this);
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(WorkStatsViewModel.class);
        viewModel.getMonthSummary().observe(this, this::renderSummary);

        pickMonthBtn.setOnClickListener(v -> showMonthPicker());
    }

    private void renderSummary(MonthSummary summary) {
        if (summary == null) return;
        tvSelectedMonth.setText(formatMonth(summary.year, summary.month));
        adapter.submitList(summary.dayStats);
        tvEmptyState.setVisibility(summary.dayStats.isEmpty() ? View.VISIBLE : View.GONE);
        tvSummary.setText(getString(R.string.my_work_summary_template,
                summary.workingDays,
                summary.totalParcels,
                summary.totalDistanceKilometers()));
    }

    private void showMonthPicker() {
        int year = viewModel.getCurrentYear();
        int month = viewModel.getCurrentMonth() - 1;
        DatePickerDialog dialog = new DatePickerDialog(this, (DatePicker view, int y, int m, int dayOfMonth) -> {
            viewModel.loadMonth(y, m + 1);
        }, year, month, 1);
        DatePicker picker = dialog.getDatePicker();
        int dayId = getResources().getIdentifier("day", "id", "android");
        if (dayId != 0) {
            View dayView = picker.findViewById(dayId);
            if (dayView != null) {
                dayView.setVisibility(View.GONE);
            }
        }
        dialog.show();
    }

    private String formatMonth(int year, int month) {
        return String.format(Locale.getDefault(), "%04d-%02d", year, month);
    }

    @Override
    public void onItemClick(WorkStatsRepository.WorkDayStat item) {
        Intent intent = new Intent(this, DeliveredPackagesActivity.class);
        intent.putExtra(DeliveredPackagesActivity.EXTRA_DATE, dateFromKey(item.dateKey).getTime());
        startActivity(intent);
    }

    private Date dateFromKey(long dateKey) {
        int year = (int) (dateKey / 10000);
        int month = (int) ((dateKey % 10000) / 100) - 1;
        int day = (int) (dateKey % 100);
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, 0, 0, 0);
        return calendar.getTime();
    }
}
