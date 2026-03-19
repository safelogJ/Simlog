package com.safelogj.simlog.displaying;

import android.content.Context;
import android.view.MotionEvent;
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
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
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
import java.util.TreeMap;
import java.util.regex.Pattern;

public class ChartBuilder {

    public static final Path EMPTY_PATH = Paths.get("empty");
    private static final int CHART_LENGTH = 1500;
    private static final Pattern LOG_PATTERN = Pattern.compile("^\\d+,\\w+,\\d$");
    private static final String DATASET_PATTERN = "Graph";
    private static final String EMPTY_STRING = "";
    private static final String TYPE_2G = "2G";
    private static final String TYPE_3G = "3G";
    private static final String TYPE_4G = "4G";
    private static final String TYPE_5G = "5G";
    private static final String TYPE_XG = "xG";
    private static final String TIME_LINE_PATTERN = "%02d:%02d";
    private static final String LEFT_AXIS_PATTERN = "%d%%";
    private static final String LEGEND_PATTERN = "%s - %d%%";
    private static final String LOGLINE_SPLITTER = ",";
    private final int[] mColorsInChart = new int[CHART_LENGTH];
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
            setMLevelPercents(entryList);
            setTypePercents();

            BarDataSet dataSet = new BarDataSet(entryList, DATASET_PATTERN);
            dataSet.setColors(mColorsInChart);
            dataSet.setDrawValues(false);

            BarData barData = new BarData(dataSet);
            barData.setBarWidth(1f);
            mChart.setData(barData);

            mChart.fitScreen();
            mChart.setVisibleXRangeMinimum(5f);

            setHorizontalAxis();
            setVerticalAxis();
            setCustomLegend(mPercentsOfTypeNetwork);
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

        emptyEntries.set(time, new BarEntry(time, level));
        mColorsInChart[time] = color;

//        int futureEnd = time < CHART_LENGTH - 90 ? time + 60 : CHART_LENGTH - 30;
//        for (int j = time; j < futureEnd; j++) {
//            emptyEntries.set(j, new BarEntry(j, level));
//            mColorsInChart[j] = color;
//        }

    }

    private int getColor(String type) {
        if (type == null) return mPalette[1];
        return switch (type) {
            case TYPE_2G -> mPalette[2];
            case TYPE_3G -> mPalette[3];
            case TYPE_4G -> mPalette[4];
            case TYPE_5G -> mPalette[5];
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
                String[] logLine = line.split(LOGLINE_SPLITTER);
                int time = Integer.parseInt(logLine[0]);
                String type = logLine[1];
                int level = Integer.parseInt(logLine[2]);
                LogLine log = new LogLine(time, type, level);
                logTree.put(time, log);
            }
        }
        return new ArrayList<>(logTree.values());
    }

    private void setMLevelPercents(List<BarEntry> entryList) {
        Arrays.fill(mPercentsOfSignalLvl, 0);
        Arrays.fill(mLevels, 0);
        for (int i = 5; i < CHART_LENGTH; i++) {
            BarEntry entry = entryList.get(i);
            int lvl = (int) entry.getY();
            mLevels[lvl]++;
        }
        countPercents(mPercentsOfSignalLvl, mLevels);
    }


    private void countPercents(int[] result, int[] counter) {
        int sum = 0;
        int counterLength = counter.length;
        for (int i = 1; i < counterLength; i++) {
            sum += counter[i];
        }

        if (sum == 0) {
            return;
        }
        int totalPercentage = 0;

        for (int i = 1; i < counterLength; i++) {
            result[i] = (int) Math.ceil((counter[i] * 100.0) / sum);
            totalPercentage += result[i];
        }
        adjustPercents(result, totalPercentage);
    }

    private void adjustPercents(int[] result, int totalPercentage) {
        int diff = 100 - totalPercentage;
        int resultLength = result.length;
        while (diff < 0) {
            for (int i = 1; i < resultLength; i++) {
                if (result[i] > 1 && diff < 0) {
                    result[i]--;
                    diff++;
                }
            }
        }

    }

    private void setTypePercents() {
        Arrays.fill(mPercentsOfTypeNetwork, 0);
        Arrays.fill(mColors, 0);
        int colorLength = mColorsInChart.length;
        int paletteLength = mPalette.length;

        for (int i = 0; i < colorLength; i++) {
            for (int j = 1; j < paletteLength; j++) {
                if (mColorsInChart[i] == mPalette[j]) {
                    mColors[j]++;
                    break;
                }
            }
        }

        countPercents(mPercentsOfTypeNetwork, mColors);
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
        mChart.setDragDecelerationEnabled(false);
        initChartTouchListener();
    }

    private void setHorizontalAxis() {
        XAxis xAxis = mChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.TOP);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(ContextCompat.getColor(mContext, R.color.black));
        xAxis.setTextSize(11f);

        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value < CHART_LENGTH - 30 && value >= 30) {
                    int totalMinutes = (int) value - 30;
                    int hours = totalMinutes / 60;
                    int minutes = totalMinutes % 60;
                    return String.format(Locale.US, TIME_LINE_PATTERN, hours, minutes);
                } else {
                    return EMPTY_STRING;
                }
            }
        });

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
                if (value < 1) return EMPTY_STRING;
                int y = (int) value;
                return String.format(Locale.US, LEFT_AXIS_PATTERN, mPercentsOfSignalLvl[y]);
            }
        });
        yAxisRight.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value < 1 ? EMPTY_STRING : EMPTY_STRING + (int) value;
            }
        });

    }

    private void setCustomLegend(int[] massivePercents) {
        mLinearLayoutForLegend.removeAllViews();
        for (int i = 1; i < mPalette.length; i++) {
            if (massivePercents[i] > 0) {
                int color = mPalette[i];
                String type = switch (i) {
                    case 2 -> TYPE_2G;
                    case 3 -> TYPE_3G;
                    case 4 -> TYPE_4G;
                    case 5 -> TYPE_5G;
                    default -> TYPE_XG;
                };
                String text = String.format(Locale.US, LEGEND_PATTERN, type, massivePercents[i]);
                mLinearLayoutForLegend.addView(LinearLayoutBuilder.createConstraintLayoutForLegend(mContext, text, color));
            }
        }

    }

    @NonNull
    private List<BarEntry> getEmptyEntries() {
        List<BarEntry> emptyEntList = new ArrayList<>(CHART_LENGTH);
        for (int x = 0; x < 5; x++) {
            int y = x + 1;
            emptyEntList.add(new BarEntry(x, y));
        }
        for (int x = 5; x < CHART_LENGTH; x++) {
            emptyEntList.add(new BarEntry(x, 0));
        }
        return emptyEntList;
    }

    @NonNull
    @Contract("_ -> new")
    private int[] initColors(Context context) {
        return new int[]{ContextCompat.getColor(context, R.color.chart_transparent_5),
                ContextCompat.getColor(context, R.color.signal_xG),
                ContextCompat.getColor(context, R.color.signal_2g),
                ContextCompat.getColor(context, R.color.signal_3g),
                ContextCompat.getColor(context, R.color.signal_4g),
                ContextCompat.getColor(context, R.color.signal_5g),
        };

    }

    private void initChartTouchListener() {
        mChart.setOnChartGestureListener(new OnChartGestureListener() {
            @Override
            public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {    //

            }

            @Override
            public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
                updateLegendForVisibleEntries();
                updateYAxisForVisibleEntries();
            }

            @Override
            public void onChartLongPressed(MotionEvent me) {      //

            }

            @Override
            public void onChartDoubleTapped(MotionEvent me) {     //

            }

            @Override
            public void onChartSingleTapped(MotionEvent me) {     //

            }

            @Override
            public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {      //

            }

            @Override
            public void onChartScale(MotionEvent me, float scaleX, float scaleY) {     //
            }

            @Override
            public void onChartTranslate(MotionEvent me, float dX, float dY) {      //
            }
        });
    }

    private void updateLegendForVisibleEntries() {
        int visibleXMin = (int) Math.max(5, mChart.getLowestVisibleX());
        int visibleXMax = (int) Math.min(mChart.getHighestVisibleX(), CHART_LENGTH - 1f);
        Arrays.fill(mPercentsOfTypeNetwork, 0);
        Arrays.fill(mColors, 0);
        int paletteLength = mPalette.length;
        for (int i = visibleXMin; i <= visibleXMax; i++) {
            for (int j = 1; j < paletteLength; j++) {
                if (mColorsInChart[i] == mPalette[j]) {
                    mColors[j]++;
                    break;
                }
            }
        }
        countPercents(mPercentsOfTypeNetwork, mColors);
        setCustomLegend(mPercentsOfTypeNetwork);
    }

    private void updateYAxisForVisibleEntries() {
        int visibleXMin = (int) Math.max(5, mChart.getLowestVisibleX());
        int visibleXMax = (int) Math.min(mChart.getHighestVisibleX(), CHART_LENGTH - 1f);
        List<BarEntry> visibleEntries = new ArrayList<>(Math.max(5, visibleXMax - visibleXMin));
        for (IBarDataSet set : mChart.getBarData().getDataSets()) {
            for (int i = visibleXMin; i <= visibleXMax; i++) {
                visibleEntries.add(set.getEntryForIndex(i));
            }
        }
        Arrays.fill(mPercentsOfSignalLvl, 0);
        Arrays.fill(mLevels, 0);
        for (BarEntry entry : visibleEntries) {
            int lvl = (int) entry.getY();
            mLevels[lvl]++;
        }
        countPercents(mPercentsOfSignalLvl, mLevels);
    }
}
