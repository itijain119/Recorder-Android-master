package com.backyardbrains;

import android.annotation.SuppressLint;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import com.backyardbrains.analysis.BYBAnalysisType;
import com.backyardbrains.drawing.AutoCorrelationRenderer;
import com.backyardbrains.drawing.AverageSpikeRenderer;
import com.backyardbrains.drawing.BYBAnalysisBaseRenderer;
import com.backyardbrains.drawing.CrossCorrelationRenderer;
import com.backyardbrains.drawing.ISIRenderer;
import com.backyardbrains.drawing.TouchGLSurfaceView;
import com.backyardbrains.drawing.WaitRenderer;
import com.backyardbrains.events.AudioAnalysisDoneEvent;
import com.backyardbrains.events.OpenRecordingsEvent;
import com.backyardbrains.events.RedrawAudioAnalysisEvent;
import com.backyardbrains.utils.ApacheCommonsLang3Utils;
import com.backyardbrains.utils.ViewUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import static com.backyardbrains.utils.LogUtils.LOGD;
import static com.backyardbrains.utils.LogUtils.makeLogTag;

public class BackyardBrainsAnalysisFragment extends BaseFragment {

    private static final String TAG = makeLogTag(BackyardBrainsAnalysisFragment.class);

    private static final String ARG_FILE_PATH = "bb_file_path";
    private static final String ARG_ANALYSIS_TYPE = "bb_analysis_type";

    @BindView(R.id.fl_container) FrameLayout flGL;
    @BindView(R.id.tv_analysis_title) TextView tvTitle;
    @BindView(R.id.ibtn_back) ImageButton ibtnBack;
    @BindView(R.id.pb_waiting) ProgressBar pbWaiting;
    @BindView(R.id.tv_waiting) TextView tvWaiting;

    private Unbinder unbinder;
    private TouchGLSurfaceView glSurface;
    private BYBAnalysisBaseRenderer currentRenderer;

    private String filePath;
    private int analysisType = BYBAnalysisType.NONE;

    /**
     * Factory for creating a new instance of the fragment.
     *
     * @return A new instance of fragment {@link BackyardBrainsAnalysisFragment}.
     */
    public static BackyardBrainsAnalysisFragment newInstance(@Nullable String filePath,
        @BYBAnalysisType int analysisType) {
        final BackyardBrainsAnalysisFragment fragment = new BackyardBrainsAnalysisFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_FILE_PATH, filePath);
        args.putInt(ARG_ANALYSIS_TYPE, analysisType);
        fragment.setArguments(args);
        return fragment;
    }

    //==============================================
    //  LIFECYCLE IMPLEMENTATIONS
    //==============================================

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            filePath = getArguments().getString(ARG_FILE_PATH);
            analysisType = getArguments().getInt(ARG_ANALYSIS_TYPE);
        }
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LOGD(TAG, "onCreateView()");
        final View root = inflater.inflate(R.layout.fragment_analysis, container, false);
        unbinder = ButterKnife.bind(this, root);

        setupUI();

        return root;
    }

    @Override public void onStart() {
        LOGD(TAG, "onStart()");
        super.onStart();

        if (glSurface != null) glSurface.onResume();

        if (ApacheCommonsLang3Utils.isBlank(filePath)) {
            ViewUtils.toast(getContext(), getString(R.string.error_message_files_no_file));
            return;
        }

        if (getAnalysisManager() != null) {
            // if file hasn't already been analyzed show waiting screen
            if (getAnalysisManager().analyzeFile(filePath, analysisType)) showWaiting(true);
        }
    }

    @Override public void onStop() {
        super.onStop();
        LOGD(TAG, "onStop()");
        if (glSurface != null) glSurface.onPause();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override public void onDestroy() {
        LOGD(TAG, "onDestroy()");
        destroyRenderer();
        super.onDestroy();
    }

    //=================================================
    //  EVENT BUS
    //=================================================

    @SuppressWarnings("unused") @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAudioAnalysisDoneEvent(AudioAnalysisDoneEvent event) {
        LOGD(TAG, "Analysis of audio file finished. Success - " + event.isSuccess());
        // if everything is OK set render and request GL surface render
        if (event.isSuccess()) setRenderer(event.getType());
    }

    @SuppressWarnings("unused") @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRedrawAudioAnalysisEvent(RedrawAudioAnalysisEvent event) {
        redraw();
    }

    //=================================================
    //  PUBLIC METHODS
    //=================================================

    public void onBackPressed() {
        // if we are currently rendering cross correlation analysis specific train
        // just redraw to show thumbs view
        if (currentRenderer instanceof CrossCorrelationRenderer
            && !((CrossCorrelationRenderer) currentRenderer).isThumbsView()) {
            ((CrossCorrelationRenderer) currentRenderer).setThumbsView(true);
            redraw();

            return;
        }

        // we need to open recordings screen
        EventBus.getDefault().post(new OpenRecordingsEvent());
    }

    //=================================================
    //  RENDERING OF ANALYSIS GRAPHS
    //=================================================

    // Sets new renderer for the GL surface view
    private void setRenderer(@BYBAnalysisType int type) {
        LOGD(TAG, "setRenderer()");
        if (type >= BYBAnalysisType.FIND_SPIKES && type <= BYBAnalysisType.AVERAGE_SPIKE) {
            analysisType = type;
            reassignSurfaceView(analysisType);
        }
    }

    // Redraws the GL surface view
    private void redraw() {
        LOGD(TAG, "redraw()");

        if (glSurface != null) glSurface.requestRender();
    }

    // Sets visibility of "Waiting" views.
    private void showWaiting(final boolean show) {
        pbWaiting.setVisibility(show ? View.VISIBLE : View.GONE);
        tvWaiting.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // Initializes GL surface view with the renderer of specified type
    @SuppressLint("SwitchIntDef") private void reassignSurfaceView(@BYBAnalysisType int rendererType) {
        LOGD(TAG, "reassignSurfaceView  renderer: " + getTitle(rendererType) + "  " + rendererType);
        // hide waiting screen
        showWaiting(false);

        currentRenderer = new WaitRenderer(this);
        switch (rendererType) {
            case BYBAnalysisType.AUTOCORRELATION:
                currentRenderer = new AutoCorrelationRenderer(this);
                break;
            case BYBAnalysisType.AVERAGE_SPIKE:
                currentRenderer = new AverageSpikeRenderer(this);
                break;
            case BYBAnalysisType.CROSS_CORRELATION:
                currentRenderer = new CrossCorrelationRenderer(this);
                break;
            case BYBAnalysisType.ISI:
                currentRenderer = new ISIRenderer(this);
                break;
        }

        if (flGL != null) {
            flGL.removeAllViews();
            // create new GL surface
            if (glSurface != null) glSurface = null;
            glSurface = new TouchGLSurfaceView(getContext());
            glSurface.setRenderer(currentRenderer);
            glSurface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            // and add GL surface to UI
            flGL.addView(glSurface);
        }

        if (tvTitle != null) tvTitle.setText(getTitle(rendererType));

        LOGD(TAG, "Analysis GLSurfaceView reassigned");
    }

    //=================================================
    //  PRIVATE METHODS
    //=================================================

    // Initializes user interface
    private void setupUI() {
        reassignSurfaceView(analysisType);

        ibtnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    // Returns analysis title depending on the analyisis type
    private String getTitle(@BYBAnalysisType int analysisType) {
        switch (analysisType) {
            case BYBAnalysisType.AUTOCORRELATION:
                return getString(R.string.analysis_autocorrelation);
            case BYBAnalysisType.AVERAGE_SPIKE:
                return getString(R.string.analysis_average_spike);
            case BYBAnalysisType.CROSS_CORRELATION:
                return getString(R.string.analysis_cross_correlation);
            case BYBAnalysisType.ISI:
                return getString(R.string.analysis_isi);
            case BYBAnalysisType.NONE:
                return getString(R.string.analysis_please_wait);
            case BYBAnalysisType.FIND_SPIKES:
            default:
                return "";
        }
    }

    // Destroys renderer
    private void destroyRenderer() {
        if (currentRenderer != null) {
            currentRenderer.close();
            currentRenderer = null;
        }
    }
}
