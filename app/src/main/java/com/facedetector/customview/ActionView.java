package com.facedetector.customview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;

public class ActionView extends View {
    public ActionView(Context context){
        super(context);
    }
    public ActionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onDraw(Canvas canvas){
        super.onDraw(canvas);
        this.setAlpha(0f);
        this.setBackgroundColor(Color.BLACK);
    }

    public void show(){
        this.setAlpha(0f);
        this.setVisibility(View.VISIBLE);
        final View that = this;
        this.animate().alpha(1f).setDuration(100).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
               that.setVisibility(View.GONE);
            }
        });


    }
}
