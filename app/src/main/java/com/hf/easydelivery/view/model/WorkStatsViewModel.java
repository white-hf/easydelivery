package com.hf.easydelivery.view.model;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.hf.easydelivery.domain.WorkStatsRepository;
import com.hf.easydelivery.domain.WorkStatsRepository.MonthSummary;

import java.util.Calendar;

public class WorkStatsViewModel extends AndroidViewModel {

    private final WorkStatsRepository repository;
    private final MutableLiveData<MonthSummary> monthSummaryLiveData = new MutableLiveData<>();
    private int currentYear;
    private int currentMonth;

    public WorkStatsViewModel(@NonNull Application application) {
        super(application);
        repository = new WorkStatsRepository(application);
        Calendar calendar = Calendar.getInstance();
        currentYear = calendar.get(Calendar.YEAR);
        currentMonth = calendar.get(Calendar.MONTH) + 1;
        loadMonth(currentYear, currentMonth);
    }

    public LiveData<MonthSummary> getMonthSummary() {
        return monthSummaryLiveData;
    }

    public void loadMonth(int year, int month) {
        this.currentYear = year;
        this.currentMonth = month;
        repository.getMonthlySummary(year, month, monthSummaryLiveData::postValue);
    }

    public int getCurrentYear() {
        return currentYear;
    }

    public int getCurrentMonth() {
        return currentMonth;
    }
}
