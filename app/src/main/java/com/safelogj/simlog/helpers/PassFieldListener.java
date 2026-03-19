package com.safelogj.simlog.helpers;

import android.graphics.drawable.Drawable;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import com.safelogj.simlog.R;

public class PassFieldListener implements View.OnTouchListener {

    private boolean isPasswordVisible = false;


    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!(v instanceof EditText editText)) return false;

        final int DRAWABLE_END = 2;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            Drawable drawableEnd = editText.getCompoundDrawables()[DRAWABLE_END];
            if (drawableEnd != null) {
                float x = event.getX();
                int width = editText.getWidth();
                int paddingEnd = editText.getPaddingEnd();
                int drawableWidth = drawableEnd.getBounds().width();

                if (x >= (width - paddingEnd - drawableWidth)) {
                    // 👇 сообщаем системе, что это клик
                    v.performClick();

                    isPasswordVisible = !isPasswordVisible;

                    if (isPasswordVisible) {
                        editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                0, 0, R.drawable.visibility_20px, 0
                        );
                    } else {
                        editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                0, 0, R.drawable.visibility_off_20px, 0
                        );
                    }

                    editText.setSelection(editText.getText().length());
                    editText.invalidate();
                    return true;
                }
            }
        }
        return false;
    }
}
