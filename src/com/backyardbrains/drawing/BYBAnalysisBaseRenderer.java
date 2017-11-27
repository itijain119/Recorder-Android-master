package com.backyardbrains.drawing;

import android.support.annotation.NonNull;
import android.view.MotionEvent;
import com.backyardbrains.BaseFragment;
import com.backyardbrains.events.RedrawAudioAnalysisEvent;
import com.backyardbrains.utils.BYBGlUtils;
import com.backyardbrains.view.ofRectangle;
import java.util.List;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import org.greenrobot.eventbus.EventBus;

public class BYBAnalysisBaseRenderer extends BaseRenderer {

    private static final String TAG = BYBAnalysisBaseRenderer.class.getCanonicalName();

    protected int height;
    protected int width;

    ofRectangle mainRect;
    ofRectangle[] thumbRects;
    int selected = 0;

    private int touchDownRect = -1;

    // ----------------------------------------------------------------------------------------
    BYBAnalysisBaseRenderer(@NonNull BaseFragment fragment) {
        super(fragment);
    }

    // ----------------------------------------------------------------------------------------
    public void close() {
    }

    // -----------------------------------------------------------------------------------------------------------------------------
    // ----------------------------------------- TOUCH
    // -----------------------------------------------------------------------------------------------------------------------------
    private int checkInsideAllThumbRects(float x, float y) {
        if (thumbRects != null) {
            for (int i = 0; i < thumbRects.length; i++) {
                if (thumbRects[i] != null) {
                    if (thumbRects[i].inside(x, y)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    // ----------------------------------------------------------------------------------------
    boolean onTouchEvent(MotionEvent event) {
        if (event.getActionIndex() == 0) {
            int insideRect = checkInsideAllThumbRects(event.getX(), event.getY());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownRect = insideRect;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (insideRect == -1 || insideRect != touchDownRect) touchDownRect = -1;
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_OUTSIDE:
                    touchDownRect = -1;
                    break;
                case MotionEvent.ACTION_UP:
                    //valid click!!!
                    if (insideRect == touchDownRect) thumbRectClicked(insideRect);
                    break;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------------------------------
    protected void thumbRectClicked(int i) {
        setSelected(i);
    }

    // ----------------------------------------------------------------------------------------
    @Override public void onDrawFrame(GL10 gl) {
        preDrawingHandler();
        BYBGlUtils.glClear(gl);
        drawingHandler(gl);
        postDrawingHandler(gl);
    }

    // ----------------------------------------------------------------------------------------
    private void preDrawingHandler() {
    }

    // ----------------------------------------------------------------------------------------
    protected void postDrawingHandler(GL10 gl) {
    }

    // ----------------------------------------------------------------------------------------
    protected void drawingHandler(GL10 gl) {
    }

    // ----------------------------------------------------------------------------------------
    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        this.width = width;
        this.height = height;
    }

    // ----------------------------------------------------------------------------------------
    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        gl.glDisable(GL10.GL_DITHER);
        gl.glHint(GL10.GL_LINE_SMOOTH_HINT, GL10.GL_NICEST);
    }

    // ----------------------------------------------------------------------------------------
    void initGL(GL10 gl) {
        // set viewport
        gl.glViewport(0, 0, width, height);

        BYBGlUtils.glClear(gl);
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glOrthof(0f, width, height, 0f, -1f, 1f);
        gl.glRotatef(0f, 0f, 0f, 1f);

        gl.glClearColor(0f, 0f, 0f, 1.0f);
        gl.glEnable(GL10.GL_BLEND); // Enable Alpha Blend
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA); // Set Alpha Blend Function
        gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);
        gl.glDisable(GL10.GL_DEPTH_TEST);
    }

    // ----------------------------------------------------------------------------------------
    void makeThumbAndMainRectangles() {
        int margin = 20;
        int maxSpikeTrains = 3;
        float d = (Math.min(width, height) / (float) (maxSpikeTrains + 1)) * 0.2f;
        if (d < margin) {
            margin = (int) d;
        }
        float s = (Math.min(width, height) - margin * (maxSpikeTrains + 1)) / (float) maxSpikeTrains;

        boolean bVert = width > height;

        thumbRects = new ofRectangle[maxSpikeTrains];
        for (int i = 0; i < maxSpikeTrains; i++) {

            float x = (float) margin;
            float y = (float) margin;

            if (bVert) {
                y += (s + margin) * i;
            } else {
                y = height - s - margin;
                x += (s + margin) * i;
            }

            thumbRects[i] = new ofRectangle(x, y, s, s);
        }

        float x = (float) margin;
        float y = (float) margin;
        float w = width;
        float h = height;

        if (bVert) {
            x += margin + s;
            h -= margin * 2;
            w = width - (margin * 3) - s;
        } else {
            w -= x - margin;
            h -= (margin * 3) - s;
        }
        mainRect = new ofRectangle(x, y, w, h);
    }

    // ----------------------------------------------------------------------------------------
    void graphIntegerList(GL10 gl, List<Integer> ac, ofRectangle r, float[] color, boolean bDrawBox) {
        graphIntegerList(gl, ac, r.x, r.y, r.width, r.height, color, bDrawBox);
    }

    // ----------------------------------------------------------------------------------------
    private void graphIntegerList(GL10 gl, List<Integer> ac, float px, float py, float w, float h, float[] color,
        boolean bDrawBox) {
        if (ac != null) {
            if (ac.size() > 0) {
                int s = ac.size();
                float[] values = new float[s];
                int mx = Integer.MIN_VALUE;
                for (int i = 0; i < s; i++) {
                    int y = ac.get(i);
                    if (mx < y) mx = y;
                }
                if (mx == 0) mx = 1;// avoid division by zero
                for (int i = 0; i < s; i++) {
                    values[i] = ((float) ac.get(i)) / (float) mx;
                }
                BYBBarGraph graph = new BYBBarGraph(values, px, py, w, h, color);
                if (bDrawBox) {
                    graph.makeBox(BYBColors.getColorAsGlById(BYBColors.white));
                }
                graph.setVerticalAxis(0, mx, 5);
                graph.setHorizontalAxis(0, ac.size(), 6);
                graph.draw(gl);
            }
        }
    }

    private void setSelected(int s) {
        selected = s;

        EventBus.getDefault().post(new RedrawAudioAnalysisEvent());
    }
}
