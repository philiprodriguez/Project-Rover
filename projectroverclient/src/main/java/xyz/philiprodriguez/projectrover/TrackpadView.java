package xyz.philiprodriguez.projectrover;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

// TODO: support an absolute mode and a relative mode which refuses to "jump" to far away positions
// TODO: consider a "movement speed limit"
// TODO: consider see-through or very-thin-line background option(s)
public class TrackpadView extends View {

    private final int dimensions;
    private final int toolRadius;
    private final int toolOutlineColor;
    private final int toolInsideColor;
    private final int trackpadColor;
    private final float minimumChange;

    private final Paint trackpadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toolOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toolInsidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float lastX = 0;
    private float lastY = 0;
    private float lastEventX = 0;
    private float lastEventY = 0;

    private final Object listenerLock = new Object();
    private OnTrackpadPositionChangedListener onTrackpadPositionChangedListener;

    public TrackpadView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        TypedArray typedArray = context.getTheme().obtainStyledAttributes(attributeSet, R.styleable.TrackpadView, 0, 0);
        try {
            dimensions = typedArray.getInteger(R.styleable.TrackpadView_dimensions, 2);
            toolRadius = typedArray.getDimensionPixelSize(R.styleable.TrackpadView_toolRadius, 8);
            trackpadColor = typedArray.getColor(R.styleable.TrackpadView_trackpadColor, 0xffcccccc);
            toolOutlineColor = typedArray.getColor(R.styleable.TrackpadView_toolOutlineColor, 0xff000000);
            toolInsideColor = typedArray.getColor(R.styleable.TrackpadView_toolInsideColor, 0xff777777);
            minimumChange = typedArray.getFloat(R.styleable.TrackpadView_minimumChange, 0.0f);
        } finally {
            typedArray.recycle();
        }

        trackpadPaint.setColor(trackpadColor);
        trackpadPaint.setStyle(Paint.Style.FILL);
        toolInsidePaint.setColor(toolInsideColor);
        toolInsidePaint.setStyle(Paint.Style.FILL);
        toolOutlinePaint.setColor(toolOutlineColor);
        toolOutlinePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), trackpadPaint);
        if (dimensions == 0) {
            // Only X, horizontal slider
            canvas.drawCircle(lastX, getHeight()/2.0f, toolRadius, toolOutlinePaint);
            canvas.drawCircle(lastX, getHeight()/2.0f, toolRadius - (toolRadius * 0.10f), toolInsidePaint);
        }
        if (dimensions == 1) {
            // Only Y, vertical slider
            canvas.drawCircle(getWidth()/2.0f, lastY, toolRadius, toolOutlinePaint);
            canvas.drawCircle(getWidth()/2.0f, lastY, toolRadius - (toolRadius * 0.10f), toolInsidePaint);
        }
        if (dimensions == 2) {
            // X and Y
            canvas.drawCircle(lastX, lastY, toolRadius, toolOutlinePaint);
            canvas.drawCircle(lastX, lastY, toolRadius - (toolRadius * 0.10f), toolInsidePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);

        handlePositionChange(event.getX(), event.getY());

        return true;
    }

    private void handlePositionChange(float newX, float newY) {
        // Does this warrant a new event?
        if (Math.sqrt((newX-lastEventX)*(newX-lastEventX)+(newY-lastEventY)*(newY-lastEventY)) >= minimumChange) {
            // Yes, fire new event and update!
            synchronized (listenerLock) {
                if (onTrackpadPositionChangedListener != null)
                    onTrackpadPositionChangedListener.onTrackpadPositionChanged(newX, newY);
            }

            lastEventX = newX;
            lastEventY = newY;
        }

        // Update for drawing purposes
        lastX = newX;
        lastY = newY;

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int chosenWidth;
        int chosenHeight;

        if (widthMode == MeasureSpec.EXACTLY) {
            chosenWidth = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            chosenWidth = (int)(0.75*widthSize);
        } else if (widthMode == MeasureSpec.UNSPECIFIED) {
            chosenWidth = (int)(0.75*widthSize);
        } else {
            throw new IllegalArgumentException("Unknown width mode supplied to TrackpadView!");
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            chosenHeight = heightSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            chosenHeight = (int)(0.75*heightSize);
        } else if (widthMode == MeasureSpec.UNSPECIFIED) {
            chosenHeight = (int)(0.75*heightSize);
        } else {
            throw new IllegalArgumentException("Unknown height mode supplied to TrackpadView!");
        }

        setMeasuredDimension(chosenWidth, chosenHeight);
    }

    public void setOnTrackpadPositionChangedListener(OnTrackpadPositionChangedListener onTrackpadPositionChangedListener) {
        synchronized (listenerLock) {
            this.onTrackpadPositionChangedListener = onTrackpadPositionChangedListener;
        }
    }

    public void setX(float x) {
        handlePositionChange(x, lastY);
        invalidate();
    }

    public void setY(float y) {
        handlePositionChange(lastX, y);
        invalidate();
    }
}
