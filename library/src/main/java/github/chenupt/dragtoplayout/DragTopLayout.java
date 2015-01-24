
/*
 * Copyright 2015 chenupt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * imitations under the License.
 */

package github.chenupt.dragtoplayout;

import android.content.Context;
import android.os.Handler;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;


/**
 * Created by chenupt@gmail.com on 2015/1/18.
 * Description : Drag down to show a menu panel on the top.
 */
public class DragTopLayout extends FrameLayout {

    private SetupWizard wizard;
    private ViewDragHelper dragHelper;
    private int dragRange;
    private View dragContentView;
    private View menuView;

    private int contentTop;
    private int menuHeight;
    private boolean isRefreshing;

    public static enum PanelState {
        EXPANDED,
        COLLAPSED
    }

    private PanelState panelState = PanelState.COLLAPSED;
    private boolean shouldIntercept = true;

    public DragTopLayout(Context context) {
        this(context, null);
    }

    public DragTopLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DragTopLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        dragHelper = ViewDragHelper.create(this, 1.0f, callback);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() < 2) {
            throw new RuntimeException("Content view must contains two child view at least.");
        }
        menuView = getChildAt(0);
        dragContentView = getChildAt(1);
    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        dragRange = getHeight();

        int contentTopTemp = contentTop;
        resetMenuHeight();

        // Clear the drag content top value so that menu could be collapsed.
        if (!wizard.initOpen) {
            wizard.initOpen = true;
            contentTop = getPaddingTop();
            contentTopTemp = getPaddingTop();
        }

        menuView.layout(left, Math.min(0, contentTop - menuHeight), right, contentTop);
        dragContentView.layout(
                left,
                contentTopTemp,
                right,
                contentTopTemp + dragContentView.getHeight());
    }

    private void resetMenuHeight() {
        if (menuHeight != menuView.getHeight()) {
            if (contentTop == menuHeight) {
                contentTop = menuView.getHeight();
                handleSlide(menuView.getHeight());
            }
            menuHeight = menuView.getHeight();
        }
    }

    private void handleSlide(final int top) {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                dragHelper.smoothSlideViewTo(dragContentView, getPaddingLeft(), top);
                postInvalidate();
            }
        });
    }

    public void openMenu(boolean anim) {
        resetDragContent(anim, menuHeight);
    }

    public void closeMenu(boolean anim) {
        resetDragContent(anim, 0);
    }

    public void toggleMenu() {
        switch (panelState) {
            case COLLAPSED:
                openMenu(true);
                break;
            case EXPANDED:
                closeMenu(true);
                break;
        }
    }

    private void resetDragContent(boolean anim, int top) {
        contentTop = top;
        if (anim) {
            dragHelper.smoothSlideViewTo(dragContentView, getPaddingLeft(), contentTop);
            postInvalidate();
        } else {
            requestLayout();
        }
    }

    /**
     * Get refresh state
     * @return
     */
    public boolean isRefreshing() {
        return isRefreshing;
    }

    /**
     * Complete refresh and reset the refresh state.
     */
    public void onRefreshComplete() {
        isRefreshing = false;
    }

    private void calculateRadio(float top){
        if (wizard.panelListener != null) {
            // Calculate the radio while dragging.
            float radio = top / menuHeight;
            wizard.panelListener.onSliding(radio);
            if (radio > wizard.refreshRadio) {
                wizard.panelListener.onRefresh();
                isRefreshing = true;
            }
        }
    }

    private ViewDragHelper.Callback callback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return child == dragContentView;
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            contentTop = top;
            requestLayout();
            calculateRadio(contentTop);
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return dragRange;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
//            return Math.min(menuHeight, Math.max(top, getPaddingTop()));
            // Drag over the menu height.
            return Math.max(top, getPaddingTop());
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            super.onViewReleased(releasedChild, xvel, yvel);
            // yvel > 0 Fling down || yvel < 0 Fling up
            int top;
            if (yvel > 0 || contentTop > menuHeight) {
                top = menuHeight + getPaddingTop();
            } else {
                top = getPaddingTop();
            }
            dragHelper.settleCapturedViewAt(releasedChild.getLeft(), top);
            postInvalidate();
        }

        @Override
        public void onViewDragStateChanged(int state) {
            // 1 -> 2 -> 0
            if (state == ViewDragHelper.STATE_IDLE) {
                // Change the panel state while the drag content view is idle.
                if (contentTop > getPaddingTop()) {
                    panelState = PanelState.EXPANDED;
                } else {
                    panelState = PanelState.COLLAPSED;
                }
                if (wizard.panelListener != null) {
                    wizard.panelListener.onPanelStateChanged(panelState);
                }
            }
            super.onViewDragStateChanged(state);
        }
    };


    @Override
    public void computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return shouldIntercept && dragHelper.shouldInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        dragHelper.processTouchEvent(event);
        return true;
    }


    public void setTouchMode(boolean shouldIntercept) {
        this.shouldIntercept = shouldIntercept;
    }

    private void setWizard(SetupWizard setupWizard) {
        this.wizard = setupWizard;

        if (wizard.panelListener != null){
            if (wizard.initOpen) {
                wizard.panelListener.onSliding(1.0f);
            }else{
                wizard.panelListener.onSliding(0f);
            }
        }
    }

    public interface PanelListener {
        /**
         * Called while the panel state is changed.
         * @param panelState
         */
        public void onPanelStateChanged(PanelState panelState);

        /**
         * Called while dragging.
         * radio >= 0.
         * @param radio
         */
        public void onSliding(float radio);

        /**
         * Called while the radio over refreshRadio.
         */
        public void onRefresh();
    }

    public static class SimplePanelListener implements PanelListener {

        @Override
        public void onPanelStateChanged(PanelState panelState) {

        }

        @Override
        public void onSliding(float radio) {

        }

        @Override
        public void onRefresh() {

        }
    }


    // -----------------

    public static SetupWizard from(Context context) {
        return new SetupWizard(context);
    }

    public static final class SetupWizard {
        private Context context;
        private PanelListener panelListener;
        private boolean initOpen;
        private float refreshRadio = 1.5f;

        public SetupWizard(Context context) {
            this.context = context;
        }

        /**
         * Setup the drag listener.
         * @return SetupWizard
         */
        public SetupWizard listener(PanelListener panelListener) {
            this.panelListener = panelListener;
            return this;
        }

        /**
         * Open the menu after the drag layout is created.
         * The default value is false.
         * @return SetupWizard
         */
        public SetupWizard open() {
            initOpen = true;
            return this;
        }

        /**
         * Set the refresh position while dragging you want.
         * The default value is 1.5f.
         * @return SetupWizard
         */
        public SetupWizard setRefreshRadio(float radio) {
            this.refreshRadio = radio;
            return this;
        }

        public void setup(DragTopLayout dragTopLayout) {
            dragTopLayout.setWizard(this);
        }
    }


}