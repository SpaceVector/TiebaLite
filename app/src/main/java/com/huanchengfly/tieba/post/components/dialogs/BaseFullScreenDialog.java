package com.huanchengfly.tieba.post.components.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.huanchengfly.tieba.post.R;

import java.util.Objects;

public abstract class BaseFullScreenDialog extends Dialog {
    private final View mContentView;

    BaseFullScreenDialog(@NonNull Context context) {
        super(context, R.style.Dialog_FullScreen);
        mContentView = View.inflate(getContext(), getLayoutId(), null);
    }

    public View getContentView() {
        return mContentView;
    }

    protected abstract int getLayoutId();

    protected abstract void initView(View contentView);

    @Override
    public void show() {
        super.show();
        WindowManager.LayoutParams layoutParams = Objects.requireNonNull(getWindow()).getAttributes();
        layoutParams.gravity = Gravity.BOTTOM;
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        getWindow().getDecorView().setPadding(0, 0, 0, 0);
        getWindow().setAttributes(layoutParams);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(mContentView);
        Window window = Objects.requireNonNull(getWindow());
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        int initialLeft = mContentView.getPaddingLeft();
        int initialTop = mContentView.getPaddingTop();
        int initialRight = mContentView.getPaddingRight();
        int initialBottom = mContentView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(mContentView, (view, insets) -> {
            Insets systemBars =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    initialLeft + systemBars.left,
                    initialTop + systemBars.top,
                    initialRight + systemBars.right,
                    initialBottom + systemBars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(mContentView);
        initView(mContentView);
    }
}
