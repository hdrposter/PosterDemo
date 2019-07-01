package com.facedetector.customview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;

public class FocusCircleView extends View {
    private Paint paint;
    private static final String TAG="Focus Circle View: ";
    private float mx=getWidth()/2;
    private float my=getHeight()/2;

    public FocusCircleView(Context context){
        super(context);
        mx = -200;
        my = -200;
    }
    public FocusCircleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mx = -200;
        my = -200;
    }

    public void setPoint(float x, float y){
        this.mx=x;
        this.my=y;
    }

    @Override
    public void onDraw(Canvas canvas){
        super.onDraw(canvas);
        paint=new Paint();
        paint.setColor(Color.parseColor("#D27522"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        int width = 200;
        int line = 16;
        canvas.drawLine(mx-width/2,my,mx-width/2+line,my,paint);
        canvas.drawLine(mx+width/2,my,mx+width/2-line,my,paint);
        canvas.drawLine(mx,my-width/2,mx,my-width/2+line,paint);
        canvas.drawLine(mx,my+width/2,mx,my+width/2 -line,paint);

        canvas.drawRect(mx-width/2,my-width/2,mx+width/2,my+width/2,paint);
        //canvas.drawCircle(mx,my,30,paint);
        //canvas.drawCircle(mx,my,100,paint);
    }

    public void releaseCanvas(){
        paint.reset();
        invalidate();
    }

    public void myViewScaleAnimation(View myView) {
        ScaleAnimation animation = new ScaleAnimation(1.2f, 1f, 1.2f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f);
        animation.setDuration(300);
        animation.setFillAfter(false);
        animation.setRepeatCount(0);
        myView.startAnimation(animation);
    }
}
