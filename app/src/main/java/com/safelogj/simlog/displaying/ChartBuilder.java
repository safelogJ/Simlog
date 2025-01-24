package com.safelogj.simlog.displaying;

import android.content.Context;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.safelogj.simlog.helpers.LinearLayoutBuilder;
import com.safelogj.simlog.R;

import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class ChartBuilder {

    public static final Path EMPTY_PATH = Paths.get("empty");
    private static final int CHART_LENGTH = 1500;
    private final int[] mColorsInChart = new int[CHART_LENGTH];
    private static final Pattern LOG_PATTERN = Pattern.compile("^\\d+,\\w+,\\d$");
    private final Context mContext;
    private final BarChart mChart;
    private final LinearLayout mLinearLayoutForLegend;

    private final int[] mPercentsOfSignalLvl = new int[6];
    private final int[] mPercentsOfTypeNetwork = new int[6];
    private final int[] mColors = new int[6];
    private final int[] mLevels = new int[6];
    private String errorText;
    private final int[] mPalette;


    public ChartBuilder(Context context, BarChart chart, LinearLayout linearLayoutForLegend) {
        this.mContext = context;
        this.mChart = chart;
        this.mLinearLayoutForLegend = linearLayoutForLegend;
        mPalette = initColors(context);
        initChart();
    }

    public void showChart(Path filePath) {
        List<BarEntry> entryList = new ArrayList<>();
        if (filePath != EMPTY_PATH) {
            entryList = getEntries(filePath);
        } else {
            errorText = ContextCompat.getString(mContext, R.string.no_data_files);
        }

        if (entryList.isEmpty()) {
            mLinearLayoutForLegend.removeAllViews();
            mChart.setNoDataText(errorText);
            mChart.setNoDataTextColor(ContextCompat.getColor(mContext, R.color.red));
            mChart.setData(null);
            mChart.invalidate();

        } else {

            BarDataSet dataSet = new BarDataSet(entryList, "Graph");
            dataSet.setColors(mColorsInChart);
            dataSet.setDrawValues(false);

            BarData barData = new BarData(dataSet);
            barData.setBarWidth(1f);
            mChart.setData(barData);

            mChart.fitScreen();
            mChart.setVisibleXRangeMinimum(5f);

            setHorizontalAxis();
            setVerticalAxis();
            setCustomLegend();
            mChart.animateXY(200, 1200);
        }
    }

    private List<BarEntry> getEntries(Path filePath) {
        List<BarEntry> emptyEntries = new ArrayList<>();
        ArrayList<LogLine> logBars = getLinkedLogs(filePath);

        if (!logBars.isEmpty()) {
            emptyEntries = getEmptyEntries();
            Arrays.fill(mColorsInChart, mPalette[0]);
            for (LogLine log : logBars) {
                    fillFutureBar(emptyEntries, log);
            }
        }
        return emptyEntries;
    }

    private void fillFutureBar(List<BarEntry> emptyEntries, LogLine log) {
        int time = log.getTime() + 30;
        int color = getColor(log.getType());
        int level = log.getLevel() + 1;
        int futureEnd = time < CHART_LENGTH - 90 ? time + 60 : CHART_LENGTH - 30;
        for (int j = time; j < futureEnd; j++) {
            emptyEntries.set(j, new BarEntry(j, level));
            mColorsInChart[j] = color;
        }
    }

    private int getColor(String type) {
        if (type == null) return mPalette[1];
        return switch (type) {
            case "2G" -> mPalette[2];
            case "3G" -> mPalette[3];
            case "4G" -> mPalette[4];
            case "5G" -> mPalette[5];
            default -> mPalette[1];
        };
    }

    private ArrayList<LogLine> getLinkedLogs(Path filePath) {
        List<String> lines;
        try {
            lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);

        } catch (IOException e) {
            errorText = ContextCompat.getString(mContext, R.string.file_read_error);
            return new ArrayList<>();
        }
        errorText = ContextCompat.getString(mContext, R.string.no_data_to_display);
        return getLogLines(lines);
    }

    private ArrayList<LogLine> getLogLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return new ArrayList<>();

        TreeMap<Integer, LogLine> logTree = new TreeMap<>();
        for (String line : lines) {
            if (LOG_PATTERN.matcher(line).matches()) {
                String[] logLine = line.split(",");
                int time = Integer.parseInt(logLine[0]);
                String type = logLine[1];
                int level = Integer.parseInt(logLine[2]);
                LogLine log = new LogLine(time, type, level);
                logTree.put(time, log);
            }
        }
        return new ArrayList<>(logTree.values());
    }
  
    private void initChart() {
        mChart.getDescription().setEnabled(false);
        mChart.setBackgroundColor(ContextCompat.getColor(mContext, R.color.main_background));
        mChart.setDrawBorders(false);

        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setScaleXEnabled(true);
        mChart.setScaleYEnabled(false);
        mChart.getLegend().setEnabled(false);

    }

    private void setHorizontalAxis() {
        XAxis xAxis = mChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.TOP);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(ContextCompat.getColor(mContext, R.color.black));
        xAxis.setTextSize(11f);
        
    }

    private void setVerticalAxis() {
        YAxis yAxisRight = mChart.getAxisRight();
        yAxisRight.setEnabled(true);
        yAxisRight.setTextSize(12f);

        YAxis yAxisLeft = mChart.getAxisLeft();
        yAxisLeft.setEnabled(true);
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setTextSize(12f);
        yAxisLeft.setTextColor(ContextCompat.getColor(mContext, R.color.black));

        yAxisLeft.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value < 1) return "";
                int y = (int) value;
                return String.format(Locale.US, "%d%%", mPercentsOfSignalLvl[y]);
            }
        });
        yAxisRight.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value < 1 ? "" : "" + (int) value;
            }
        });

    }

    private void setCustomLegend() {
        mLinearLayoutForLegend.removeAllViews();
        for (int i = 1; i < mPalette.length; i++) {
            if (mPercentsOfTypeNetwork[i] > 0) {
                int color = mPalette[i];
                String type = switch (i) {
                    case 2 -> "2G";
                    case 3 -> "3G";
                    case 4 -> "4G";
                    case 5 -> "5G";
                    default -> "xG";
                };
                String text = String.format(Locale.US, "%s - %d%%", type, mPercentsOfTypeNetwork[i]);
                mLinearLayoutForLegend.addView(LinearLayoutBuilder.createConstraintLayoutForLegend(mContext, text, color));
            }
        }

    }

    @NonNull
    private List<BarEntry> getEmptyEntries() {
        List<BarEntry> emptyEntList = new ArrayList<>(CHART_LENGTH);
        for (int x = 0; x < CHART_LENGTH; x++) {
            emptyEntList.add(new BarEntry(x, 0));
        }
        return emptyEntList;
    }

    @NonNull
    @Contract("_ -> new")
    private int[] initColors(Context context) {
        return new int[]{ContextCompat.getColor(context, R.color.main_background),
                ContextCompat.getColor(context, R.color.signal_xG),
                ContextCompat.getColor(context, R.color.signal_2g),
                ContextCompat.getColor(context, R.color.signal_3g),
                ContextCompat.getColor(context, R.color.signal_4g),
                ContextCompat.getColor(context, R.color.signal_5g),
        };

    }
}
