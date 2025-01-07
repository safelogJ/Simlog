package com.safelogj.simlog;

import android.content.Context;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.safelogj.simlog.collecting.SimCard;

import java.util.Locale;

public class LinearLayoutBuilder {

    private LinearLayoutBuilder() {}

    public static ConstraintLayout createConstraintLayoutForSimCard(Context context, SimCard simCard) {

        ConstraintLayout holderOut = new ConstraintLayout(context);
        ConstraintLayout holderIn = new ConstraintLayout(context);

        ConstraintLayout.LayoutParams holderOutParams = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                160
        );
        ConstraintLayout.LayoutParams holderInParams = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        holderOutParams.setMargins(10, 10, 10, 20);
        holderOut.setPadding(50, 10, 50, 10);
        holderOut.setLayoutParams(holderOutParams);
        holderOut.setBackground(ContextCompat.getDrawable(context,android.R.drawable.btn_default));

        holderIn.setLayoutParams(holderInParams);
        holderIn.setOnClickListener(view -> simCard.setChecked(!simCard.isChecked())
        );

        ConstraintLayout.LayoutParams paramsSimCard = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsSimCard.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        paramsSimCard.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        paramsSimCard.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        paramsSimCard.width = 0;
        paramsSimCard.horizontalWeight = 1f;
        simCard.setLayoutParams(paramsSimCard);

        TextView textView = new TextView(context);
        String text = context.getText(R.string.sim_slot).toString() + simCard.getSlot() + " " + simCard.getOperator();
        textView.setText(text);
        ConstraintLayout.LayoutParams textParams = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textView.setTextSize(16f);
        textView.setTypeface(Typeface.SERIF);
        textView.setTextColor(ContextCompat.getColor(context, R.color.black2));
        textParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        textParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        textParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        textParams.width = 0;
        textParams.horizontalWeight = 1f;
        textView.setLayoutParams(textParams);

        holderIn.addView(textView);
        holderIn.addView(simCard);
        holderOut.addView(holderIn);
        return holderOut;
    }
    public static ConstraintLayout createConstraintLayoutForLegend(Context context, String text, int color) {
        ConstraintLayout holder = new ConstraintLayout(context);
        ConstraintLayout.LayoutParams holderParams = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        holder.setLayoutParams(holderParams);

        TextView legendTextView = new TextView(context);
        legendTextView.setText(text);
        ConstraintLayout.LayoutParams legendParams = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        legendTextView.setTextSize(14f);
        legendTextView.setTypeface(Typeface.SERIF, Typeface.BOLD);
        legendTextView.setTextColor(color);
        legendTextView.setTextLocale(Locale.US);
        legendTextView.setPadding(10, 0, 10, 0);
        legendParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        legendParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        legendParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        legendParams.width = 0;
        legendParams.horizontalWeight = 1f;
        legendTextView.setLayoutParams(legendParams);

        holder.addView(legendTextView);

        return holder;
    }
}
