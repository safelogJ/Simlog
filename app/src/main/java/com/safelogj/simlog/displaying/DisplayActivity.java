package com.safelogj.simlog.displaying;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.safelogj.simlog.databinding.ActivityDisplayBinding;
import com.safelogj.simlog.AppController;
import com.safelogj.simlog.R;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DisplayActivity extends AppCompatActivity {
    private static final String SPINNER_1_KEY = "spinner1Key";
    private static final String SPINNER_2_KEY = "spinner2Key";
    private ActivityDisplayBinding mBinding;
    private Map<String, Path> mFilesPaths;
    private ArrayAdapter<String> mAdapter;
    private ChartBuilder mChartBuilder1;
    private ChartBuilder mChartBuilder2;
    private int spinner1Pos;
    private int spinner2Pos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityDisplayBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mFilesPaths = ((AppController) getApplication()).getFilesPaths();
        mChartBuilder1 = new ChartBuilder(this, mBinding.chart1, mBinding.linearLegend1);
        mChartBuilder2 = new ChartBuilder(this, mBinding.chart2, mBinding.linearLegend2);
        mAdapter = getSpinnerAdapter();
        mBinding.spinner1.setAdapter(mAdapter);
        mBinding.spinner2.setAdapter(mAdapter);


        mBinding.spinner1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedItem = parent.getItemAtPosition(position).toString();
                mChartBuilder1.showChart(mFilesPaths.get(selectedItem));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Действие, если ничего не выбрано
            }
        });

        mBinding.spinner2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedItem = parent.getItemAtPosition(position).toString();
                mChartBuilder2.showChart(mFilesPaths.get(selectedItem));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Действие, если ничего не выбрано
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        ((AppController) getApplication()).updateFilesPaths();
        mAdapter = getSpinnerAdapter();
        mBinding.spinner1.setAdapter(mAdapter);
        mBinding.spinner1.setSelection(spinner1Pos < mAdapter.getCount() ? spinner1Pos: 0);
        mBinding.spinner2.setAdapter(mAdapter);
        mBinding.spinner2.setSelection(spinner2Pos < mAdapter.getCount() ? spinner2Pos: 0);
        mBinding.chart1.setNoDataText("");
        mBinding.chart2.setNoDataText("");

        if (mFilesPaths.isEmpty()) {
            mChartBuilder1.showChart(ChartBuilder.EMPTY_PATH);
            mChartBuilder2.showChart(ChartBuilder.EMPTY_PATH);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SPINNER_1_KEY, mBinding.spinner1.getSelectedItemPosition());
        outState.putInt(SPINNER_2_KEY, mBinding.spinner2.getSelectedItemPosition());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mBinding.spinner1.setSelection(savedInstanceState.getInt(SPINNER_1_KEY));
        mBinding.spinner2.setSelection(savedInstanceState.getInt(SPINNER_2_KEY));
    }

    @Override
    protected void onStop() {
        super.onStop();
        spinner1Pos = mBinding.spinner1.getSelectedItemPosition();
        spinner2Pos = mBinding.spinner2.getSelectedItemPosition();
    }

    private ArrayAdapter<String> getSpinnerAdapter() {
        List<Map.Entry<String, Path>> paths = new ArrayList<>(mFilesPaths.entrySet());
        paths.sort((path1, path2) -> {
            try {
                long lastModified1 = Files.getLastModifiedTime(path1.getValue()).toMillis();
                long lastModified2 = Files.getLastModifiedTime(path2.getValue()).toMillis();
                return Long.compare(lastModified2, lastModified1); // Обратная сортировка
            } catch (IOException e) {
                return 1;
            }
        });
        List<String> sortedFileNames = new ArrayList<>();
        for (Map.Entry<String, Path> entry : paths) {
            sortedFileNames.add(entry.getKey());
        }


        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, sortedFileNames);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        return adapter;
    }
}
