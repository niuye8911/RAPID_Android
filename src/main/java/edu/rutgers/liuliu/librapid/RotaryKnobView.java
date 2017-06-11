package edu.rutgers.liuliu.librapid;
/* a view for knobs
* */
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
//import android.widget.ImageView;
import android.support.v7.widget.AppCompatImageView;
//import android.widget.ImageView;

/**
 * Created by liuliu on 3/25/16.
 */
public class RotaryKnobView extends android.support.v7.widget.AppCompatImageView {

    private float angle = 0f;
    private float theta_old=0f;
    private String serviceName;

    private RotaryKnobListener listener;

    public interface RotaryKnobListener {
        public void onKnobChanged(int arg);
    }

    public void setKnobListener(RotaryKnobListener l )
    {
        listener = l;
    }

    public RotaryKnobView(Context context, String ServiceName) {
        super(context);
        this.serviceName = ServiceName;
        Log.d("Knob created",serviceName);
        initialize();
    }

    public RotaryKnobView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        initialize();
    }

    public RotaryKnobView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        initialize();
    }

    private float getTheta(float x, float y)
    {
        float sx = x - (getWidth() / 2.0f);
        float sy = y - (getHeight() / 2.0f);

        float length = (float)Math.sqrt( sx*sx + sy*sy);
        float nx = sx / length;
        float ny = sy / length;
        float theta = (float)Math.atan2( ny, nx );

        final float rad2deg = (float)(180.0/Math.PI);
        float thetaDeg = theta*rad2deg;

        return (thetaDeg < 0) ? thetaDeg + 360.0f : thetaDeg;
    }

    public void initialize()
    {
        this.setImageResource(R.drawable.smallknob);
        setOnTouchListener(new OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x = event.getX(0);
                float y = event.getY(0);
                float theta = getTheta(x,y);

                switch(event.getAction() & MotionEvent.ACTION_MASK)
                {
                    case MotionEvent.ACTION_POINTER_DOWN:
                        theta_old = theta;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        invalidate();
                        float delta_theta = theta - theta_old;
                        theta_old = theta;
                        int direction = (delta_theta > 0) ? 1 : -1;
                        angle += 3*direction;
                        notifyListener(direction);
                        break;
                }
                return true;
            }
        });
    }

    private void notifyListener(int arg)
    {
        if (null!=listener)
            listener.onKnobChanged(arg);
    }

    protected void onDraw(Canvas c)
    {
        c.rotate(angle,getWidth()/2,getHeight()/2);
        super.onDraw(c);
    }

    public String getName(){
        return this.serviceName;
    }
}
