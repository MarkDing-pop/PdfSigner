/**
 * Copyright 2016 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.pdfviewer;

import android.graphics.PointF;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.github.barteksc.pdfviewer.model.LinkTapEvent;
import com.github.barteksc.pdfviewer.scroll.ScrollHandle;
import com.github.barteksc.pdfviewer.sign.FunctionBall;
import com.github.barteksc.pdfviewer.sign.SignArea;
import com.github.barteksc.pdfviewer.sign.WatermarkArea;
import com.github.barteksc.pdfviewer.util.SnapEdge;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.util.SizeF;

import java.util.HashMap;
import java.util.Iterator;

import static com.github.barteksc.pdfviewer.sign.SignArea.SIGN_AREA_WIDTH_HEIGHT_RATIO;
import static com.github.barteksc.pdfviewer.util.Constants.Pinch.MAXIMUM_ZOOM;
import static com.github.barteksc.pdfviewer.util.Constants.Pinch.MINIMUM_ZOOM;

/**
 * This Manager takes care of moving the PDFView,
 * set its zoom track user actions.
 */
class DragPinchManager implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, ScaleGestureDetector.OnScaleGestureListener, View.OnTouchListener {

    private PDFView pdfView;
    private AnimationManager animationManager;

    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;

    private boolean scrolling = false;
    private boolean scaling = false;
    private boolean enabled = false;

    DragPinchManager(PDFView pdfView, AnimationManager animationManager) {
        this.pdfView = pdfView;
        this.animationManager = animationManager;
        gestureDetector = new GestureDetector(pdfView.getContext(), this);
        scaleGestureDetector = new ScaleGestureDetector(pdfView.getContext(), this);
        pdfView.setOnTouchListener(this);
    }

    void enable() {
        enabled = true;
    }

    void disable() {
        enabled = false;
    }

    void disableLongpress(){
        gestureDetector.setIsLongpressEnabled(false);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        boolean onTapHandled = pdfView.callbacks.callOnTap(e);
        boolean linkTapped = checkLinkTapped(e.getX(), e.getY());
        if (!onTapHandled && !linkTapped) {
            ScrollHandle ps = pdfView.getScrollHandle();
            if (ps != null && !pdfView.documentFitsView()) {
                if (!ps.shown()) {
                    ps.show();
                } else {
                    ps.hide();
                }
            }
        }
        pdfView.performClick();
        return true;
    }

    private boolean checkLinkTapped(float x, float y) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return false;
        }
        float mappedX = -pdfView.getCurrentXOffset() + x;
        float mappedY = -pdfView.getCurrentYOffset() + y;
        int page = pdfFile.getPageAtOffset(pdfView.isSwipeVertical() ? mappedY : mappedX, pdfView.getZoom());
        SizeF pageSize = pdfFile.getScaledPageSize(page, pdfView.getZoom());
        int pageX, pageY;
        if (pdfView.isSwipeVertical()) {
            pageX = (int) pdfFile.getSecondaryPageOffset(page, pdfView.getZoom());
            pageY = (int) pdfFile.getPageOffset(page, pdfView.getZoom());
        } else {
            pageY = (int) pdfFile.getSecondaryPageOffset(page, pdfView.getZoom());
            pageX = (int) pdfFile.getPageOffset(page, pdfView.getZoom());
        }
        for (PdfDocument.Link link : pdfFile.getPageLinks(page)) {
            RectF mapped = pdfFile.mapRectToDevice(page, pageX, pageY, (int) pageSize.getWidth(),
                    (int) pageSize.getHeight(), link.getBounds());
            mapped.sort();
            if (mapped.contains(mappedX, mappedY)) {
                pdfView.callbacks.callLinkHandler(new LinkTapEvent(x, y, mappedX, mappedY, mapped, link));
                return true;
            }
        }
        return false;
    }

    private void startPageFling(MotionEvent downEvent, MotionEvent ev, float velocityX, float velocityY) {
        if (!checkDoPageFling(velocityX, velocityY)) {
            return;
        }

        int direction;
        if (pdfView.isSwipeVertical()) {
            direction = velocityY > 0 ? -1 : 1;
        } else {
            direction = velocityX > 0 ? -1 : 1;
        }
        // get the focused page during the down event to ensure only a single page is changed
        float delta = pdfView.isSwipeVertical() ? ev.getY() - downEvent.getY() : ev.getX() - downEvent.getX();
        float offsetX = pdfView.getCurrentXOffset() - delta * pdfView.getZoom();
        float offsetY = pdfView.getCurrentYOffset() - delta * pdfView.getZoom();
        int startingPage = pdfView.findFocusPage(offsetX, offsetY);
        int targetPage = Math.max(0, Math.min(pdfView.getPageCount() - 1, startingPage + direction));

        SnapEdge edge = pdfView.findSnapEdge(targetPage);
        float offset = pdfView.snapOffsetForPage(targetPage, edge);
        animationManager.startPageFlingAnimation(-offset);
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        // 20210105 JLin Added
        // 雙擊範圍在取得焦點的View中，就取消雙擊放大PDF的功能
        if(isAreaInFocus()) { return false; }
        /////
        if (!pdfView.isDoubletapEnabled()) {
            return false;
        }

        if (pdfView.getZoom() < pdfView.getMidZoom()) {
            pdfView.zoomWithAnimation(e.getX(), e.getY(), pdfView.getMidZoom());
        } else if (pdfView.getZoom() < pdfView.getMaxZoom()) {
            pdfView.zoomWithAnimation(e.getX(), e.getY(), pdfView.getMaxZoom());
        } else {
            pdfView.resetZoomWithAnimation();
        }
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        animationManager.stopFling();
        if(!chkTouchInSignArea(e) && !chkTouchInWatermarkArea(e)) {
            cleanAreaInFocus();
        }
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        // JLin Added
        if(mIsTouchInWatermarkDelBall) {
            deleteWatermark();
            return true;
        } else if(mIsTouchInWatermark) {
            setWatermarkInFocus();
            return true;
        } else if(mIsTouchInSignAreaDelBall) {
            deleteSignArea();
            return true;
        } else if(mIsTouchInSignAreaAddBall) {
            addSignArea();
            return true;
        } else if(mIsTouchInSignArea) {
            setSignAreaInFocus();
            return true;
        } else {
            cleanAreaInFocus();
        }
        //////
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // 20201204 Jin Added
        if(mIsTouchInWatermarkZoomBall) {
            if(distanceX != 0 && distanceY != 0) {
                zoomWatermark(distanceX, distanceY);
            }
            return true;
        } else if(mIsTouchInSignAreaZoomBall) {
            if(distanceX != 0 && distanceY != 0) {
                zoomSignArea(distanceX, distanceY);
            }
            return true;
        } else if(mIsTouchInSignArea) {
            moveSignArea(distanceX, distanceY);
            return true;
        }
        //////
        scrolling = true;
        if (pdfView.isZooming() || pdfView.isSwipeEnabled()) {
            pdfView.moveRelativeTo(-distanceX, -distanceY);
        }
        if (!scaling || pdfView.doRenderDuringScale()) {
            pdfView.loadPageByOffset();
        }
        return true;
    }

    private void onScrollEnd(MotionEvent event) {
        pdfView.loadPages();
        hideHandle();
        if (!animationManager.isFlinging()) {
            pdfView.performPageSnap();
        }
    }

    @Override
    public void onLongPress(MotionEvent e) {
        pdfView.callbacks.callOnLongPress(e);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (!pdfView.isSwipeEnabled()) {
            return false;
        }
        if (pdfView.isPageFlingEnabled()) {
            if (pdfView.pageFillsScreen()) {
                onBoundedFling(velocityX, velocityY);
            } else {
                startPageFling(e1, e2, velocityX, velocityY);
            }
            return true;
        }

        int xOffset = (int) pdfView.getCurrentXOffset();
        int yOffset = (int) pdfView.getCurrentYOffset();

        float minX, minY;
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfView.isSwipeVertical()) {
            minX = -(pdfView.toCurrentScale(pdfFile.getMaxPageWidth()) - pdfView.getWidth());
            minY = -(pdfFile.getDocLen(pdfView.getZoom()) - pdfView.getHeight());
        } else {
            minX = -(pdfFile.getDocLen(pdfView.getZoom()) - pdfView.getWidth());
            minY = -(pdfView.toCurrentScale(pdfFile.getMaxPageHeight()) - pdfView.getHeight());
        }

        animationManager.startFlingAnimation(xOffset, yOffset, (int) (velocityX), (int) (velocityY),
                (int) minX, 0, (int) minY, 0);
        return true;
    }

    private void onBoundedFling(float velocityX, float velocityY) {
        int xOffset = (int) pdfView.getCurrentXOffset();
        int yOffset = (int) pdfView.getCurrentYOffset();

        PdfFile pdfFile = pdfView.pdfFile;

        float pageStart = -pdfFile.getPageOffset(pdfView.getCurrentPage(), pdfView.getZoom());
        float pageEnd = pageStart - pdfFile.getPageLength(pdfView.getCurrentPage(), pdfView.getZoom());
        float minX, minY, maxX, maxY;
        if (pdfView.isSwipeVertical()) {
            minX = -(pdfView.toCurrentScale(pdfFile.getMaxPageWidth()) - pdfView.getWidth());
            minY = pageEnd + pdfView.getHeight();
            maxX = 0;
            maxY = pageStart;
        } else {
            minX = pageEnd + pdfView.getWidth();
            minY = -(pdfView.toCurrentScale(pdfFile.getMaxPageHeight()) - pdfView.getHeight());
            maxX = pageStart;
            maxY = 0;
        }

        animationManager.startFlingAnimation(xOffset, yOffset, (int) (velocityX), (int) (velocityY),
                (int) minX, (int) maxX, (int) minY, (int) maxY);
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float dr = detector.getScaleFactor();
        float wantedZoom = pdfView.getZoom() * dr;
        float minZoom = Math.min(MINIMUM_ZOOM, pdfView.getMinZoom());
        float maxZoom = Math.min(MAXIMUM_ZOOM, pdfView.getMaxZoom());
        if (wantedZoom < minZoom) {
            dr = minZoom / pdfView.getZoom();
        } else if (wantedZoom > maxZoom) {
            dr = maxZoom / pdfView.getZoom();
        }
        pdfView.zoomCenteredRelativeTo(dr, new PointF(detector.getFocusX(), detector.getFocusY()));
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        scaling = true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        pdfView.loadPages();
        hideHandle();
        scaling = false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!enabled) {
            return false;
        }

        boolean retVal = scaleGestureDetector.onTouchEvent(event);
        retVal = gestureDetector.onTouchEvent(event) || retVal;

        if (event.getAction() == MotionEvent.ACTION_UP) {
            // 20201222 JLin Added
            if (mIsTouchInWatermarkZoomBall) {
                mIsTouchInWatermarkZoomBall = false;
            } else if(mIsTouchInSignAreaZoomBall) {
                mIsTouchInSignAreaZoomBall = false;
                //////
            } else if (scrolling) {
                scrolling = false;
                onScrollEnd(event);
            }
        }
        return retVal;
    }

    private void hideHandle() {
        ScrollHandle scrollHandle = pdfView.getScrollHandle();
        if (scrollHandle != null && scrollHandle.shown()) {
            scrollHandle.hideDelayed();
        }
    }

    private boolean checkDoPageFling(float velocityX, float velocityY) {
        float absX = Math.abs(velocityX);
        float absY = Math.abs(velocityY);
        return pdfView.isSwipeVertical() ? absY > absX : absX > absY;
    }

    // 20201203 JLin add
    private int RATIO_SIGN_AREA_WIDTH_HEIGHT = 2;
    private int MIN_WIDTH_SIGN_AREA = 100;
    private int MIN_HEIGHT_SIGN_AREA = MIN_WIDTH_SIGN_AREA / RATIO_SIGN_AREA_WIDTH_HEIGHT;
    private HashMap<String, SignArea> mMapSignAreas = new HashMap<>();
    private String mTagCurrentTouchArea = "";       // 用以表示目前觸碰的區域是簽名框/浮水印

    // 浮水印
    private boolean mIsTouchInWatermark = false;
    private boolean mIsTouchInWatermarkZoomBall = false;
    private boolean mIsTouchInWatermarkDelBall = false;
    // 簽名框
    private boolean mIsTouchInSignArea = false;
    private boolean mIsTouchInSignAreaZoomBall = false;
    private boolean mIsTouchInSignAreaAddBall = false;
    private boolean mIsTouchInSignAreaDelBall = false;

    // 共用函式
    private void cleanAreaInFocus() {
        mTagCurrentTouchArea = "";
        mIsTouchInWatermark = false;
        mIsTouchInWatermarkDelBall = false;
        mIsTouchInWatermarkZoomBall = false;
        mIsTouchInSignAreaDelBall = false;
        mIsTouchInSignAreaAddBall = false;
        mIsTouchInSignAreaZoomBall = false;
        mIsTouchInSignArea = false;
        pdfView.invalidate();
    }
    private boolean isAreaInFocus() {
        return mIsTouchInWatermark || mIsTouchInWatermarkDelBall || mIsTouchInWatermarkZoomBall ||
                mIsTouchInSignArea || mIsTouchInSignAreaAddBall || mIsTouchInSignAreaDelBall ||
                mIsTouchInSignAreaZoomBall;
    }
    private boolean isInFunctionBall(FunctionBall ball, float eventX, float eventY) {
        float ballLeft = ball.getLeft();
        float ballRight = ball.getRight();
        float ballTop = ball.getTop();
        float ballBottom = ball.getBottom();

        return eventX > ballLeft && eventX < ballRight && eventY > ballTop && eventY < ballBottom;
    }

    // 浮水印
    public boolean isTouchInWatermark() {
        return mIsTouchInWatermark;
    }
    public boolean isTouchInWatermarkZoomBall() {
        return mIsTouchInWatermarkZoomBall;
    }
    private boolean chkTouchInWatermarkArea(MotionEvent event) {
        WatermarkArea mWatermarkArea = pdfView.getWatermarkArea();

        if(mWatermarkArea == null) { return false; }
        mIsTouchInWatermarkDelBall = false;
        mIsTouchInWatermarkZoomBall = false;
        if(isTouchInWatermark(event)) {
            return true;
        }

        return false;
    }
    private boolean isTouchInWatermark(MotionEvent event) {
        WatermarkArea mWatermarkArea = pdfView.getWatermarkArea();
        if(mWatermarkArea == null) { return false; }

        float xOffset = pdfView.getCurrentXOffset();
        float yOffset = pdfView.getCurrentYOffset();
        float eventXOffset = event.getX() - xOffset;
        float eventYOffset = event.getY() - yOffset;

        if(isInFunctionBall(mWatermarkArea.getDelBall(), eventXOffset, eventYOffset)) {
            mIsTouchInWatermarkDelBall = true;
            return true;
        } else if(isInFunctionBall(mWatermarkArea.getZoomBall(), eventXOffset, eventYOffset)) {
            mIsTouchInWatermarkZoomBall = true;
            return true;
        } else if (isInWatermarkArea(mWatermarkArea, eventXOffset, eventYOffset)) {
            mIsTouchInWatermark = true;
            mTagCurrentTouchArea = mWatermarkArea.getTag();
            return true;
        }

        return false;
    }
    private boolean isInWatermarkArea(WatermarkArea area, float eventX, float eventY) {
        float areaLeft = area.getLeft() * pdfView.getZoom();
        float areaRight = area.getRight() * pdfView.getZoom();
        float areaTop = area.getTop() * pdfView.getZoom();
        float areaBottom = area.getBottom() * pdfView.getZoom();

        return eventX > areaLeft && eventX < areaRight && eventY > areaTop && eventY < areaBottom;
    }
    private void setWatermarkInFocus() {
        mIsTouchInWatermark = true;
        pdfView.invalidate();
    }
    private void deleteWatermark() {
        pdfView.setWatermarkArea(null);
        pdfView.invalidate();
        mIsTouchInWatermarkDelBall = false;
        mIsTouchInWatermark = false;
    }
    private void zoomWatermark(float distanceX, float distanceY) {
        if(distanceX * distanceY > 0) {                 // 表示手勢滑動是往左上或右下
            WatermarkArea area = pdfView.getWatermarkArea();

            if(area == null) { return; }
            if(area.getTag().equals(mTagCurrentTouchArea)) {
                float areaWidth = area.getRight() - area.getLeft();
                float areaHeight = area.getBottom() - area.getTop();
                double areaDiagonal = Math.sqrt(Math.pow(areaWidth, 2) + Math.pow(areaHeight, 2));
                double fingerMove = Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));
                double newDiagonal = areaDiagonal;
                if(distanceX < 0 && distanceX < 0) {     // 負數表示往右下滑動，正數表示往左上滑動
                    newDiagonal += fingerMove;
                } else {
                    newDiagonal -= fingerMove;
                }
                float ratio = (float) (newDiagonal / areaDiagonal);

                pdfView.setWatermarkRatio(ratio);
                pdfView.invalidate();
            }
        }
    }

    // 簽名框
    public String getCurrentTouchAreaTag() {
        return mTagCurrentTouchArea;
    }
    private boolean chkTouchInSignArea(MotionEvent event) {
        // 20201208 JLin added
        if(mIsTouchInWatermark || mIsTouchInWatermarkDelBall ||mIsTouchInWatermarkZoomBall) {
            return false;
        }

        mMapSignAreas = pdfView.getCurrentPageMapSignAreas();

        if(mMapSignAreas == null) return false;

        if(mMapSignAreas.size() != 0) {
            // 取消三個功能圓球的touch flag
            mIsTouchInSignAreaZoomBall = false;
            mIsTouchInSignAreaAddBall = false;
            mIsTouchInSignAreaDelBall = false;

            if (isTouchInFocusSignArea(event)) {
                return true;
            } else if (isTouchInAllOtherSignArea(event)) {
                return true;
            }
        }

        return false;
    }
    private boolean isTouchInAllOtherSignArea(MotionEvent event) {
        Iterator<String> mapIterator = mMapSignAreas.keySet().iterator();
        while (mapIterator.hasNext()) {
            String key = mapIterator.next();

            SignArea area = mMapSignAreas.get(key);

            if(area == null) { continue; }
            if(area.getTag().equals(key)) {
                int[] pagesOffset = pdfView.getPreviousPagesOffset();

                float xOffset = pdfView.getCurrentXOffset();
                float yOffset = pdfView.getCurrentYOffset();
                float eventXOffset = event.getX() - xOffset;
                float eventYOffset = event.getY() - yOffset;

                if (isInAnSignArea(area, pagesOffset, eventXOffset, eventYOffset)) {
                    mIsTouchInSignArea = true;
                    mTagCurrentTouchArea = key;
                    return true;
                }
            }
        }

        return false;
    }
    private boolean isTouchInFocusSignArea(MotionEvent event) {
        SignArea area = mMapSignAreas.get(mTagCurrentTouchArea);

        if(area == null) { return false; }
        if(area.getTag().equals(mTagCurrentTouchArea)) {
            int[] pagesOffset = pdfView.getPreviousPagesOffset();

            float xOffset = pdfView.getCurrentXOffset();
            float yOffset = pdfView.getCurrentYOffset();
            float eventXOffset = event.getX() - xOffset;
            float eventYOffset = event.getY() - yOffset;

            if(isInFunctionBall(area.getDelBall(), eventXOffset, eventYOffset)) {
                mIsTouchInSignAreaDelBall = true;
                return true;
            } else if(isInFunctionBall(area.getAddBall(), eventXOffset, eventYOffset)) {
                mIsTouchInSignAreaAddBall = true;
                return true;
            }  else if(isInFunctionBall(area.getZoomBall(), eventXOffset, eventYOffset)) {
                mIsTouchInSignAreaZoomBall = true;
                return true;
            } else if (isInAnSignArea(area, pagesOffset, eventXOffset, eventYOffset)) {
                mIsTouchInSignArea = true;
                return true;
            }
        }

        return false;
    }
    private boolean isInAnSignArea(SignArea area, int[] pagesOffset, float eventX, float eventY) {
        float areaLeft = pagesOffset[0] + area.getLeft() * pdfView.getZoom();
        float areaRight = pagesOffset[0] + area.getRight() * pdfView.getZoom();
        float areaTop = pagesOffset[1] + area.getTop() * pdfView.getZoom();
        float areaBottom = pagesOffset[1] + area.getBottom() * pdfView.getZoom();

        return eventX > areaLeft && eventX < areaRight && eventY > areaTop && eventY < areaBottom;
    }
    private void setSignAreaInFocus() {
        mIsTouchInSignArea = true;
        pdfView.invalidate();
    }
    private void moveSignArea(float distanceX, float distanceY) {
        SignArea area = mMapSignAreas.get(mTagCurrentTouchArea);

        if(area == null) { return; }
        if(area.getTag().equals(mTagCurrentTouchArea)) {
            int newLeft = area.getLeft() - Math.round(distanceX / pdfView.getZoom());
            int newTop = area.getTop() - Math.round(distanceY / pdfView.getZoom());
            int newRight = area.getRight() - Math.round(distanceX / pdfView.getZoom());
            int newBottom = area.getBottom() - Math.round(distanceY / pdfView.getZoom());

            int sizeAreaWidth = area.getRight() - area.getLeft();
            int sizeAreaHeight = area.getBottom() - area.getTop();

            SizeF pageSize = pdfView.getPageSize(pdfView.getCurrentPage());
            if (newTop < 0) {
                newTop = 0;
                newBottom = sizeAreaHeight;
            }
            if (newBottom > pageSize.getHeight()) {
                newTop = Math.round(pageSize.getHeight()) - sizeAreaHeight;
                newBottom = Math.round(pageSize.getHeight());
            }
            if (newLeft < 0) {
                newLeft = 0;
                newRight = sizeAreaWidth;
            }
            if (newRight > pageSize.getWidth()) {
                newLeft = Math.round(pageSize.getWidth()) - sizeAreaWidth;
                newRight = Math.round(pageSize.getWidth());
            }

            area.setLeft(newLeft).setTop(newTop).setRight(newRight).setBottom(newBottom);
            pdfView.invalidate();
        }
    }
    private void addSignArea() {
        HashMap<String, SignArea> currentPageSignAreas = pdfView.getMapSignAreas();
        SignArea area = currentPageSignAreas.get(mTagCurrentTouchArea);

        if(area == null) { return; }
        if(area.getTag().equals(mTagCurrentTouchArea)) {
            String tag = String.valueOf(System.currentTimeMillis());
            int left = area.getLeft() + 50;
            int top = area.getTop() + 50;
            int right = area.getRight() + 50;
            int bottom = area.getBottom() + 50;
            currentPageSignAreas.put(tag, new SignArea(tag, "yaerse@yahoo.com.tw",
                    left, top, right, bottom));
            pdfView.invalidate();
            mIsTouchInSignAreaAddBall = false;
        }
    }
    private void deleteSignArea() {
        pdfView.getMapSignAreas().remove(mTagCurrentTouchArea);
        pdfView.invalidate();
        mIsTouchInSignAreaDelBall = false;
        mIsTouchInSignArea = false;
    }
    private void zoomSignArea(float distanceX, float distanceY) {
        if(distanceX * distanceY > 0) {                 // 表示手勢滑動是往左上或右下
            SignArea area = pdfView.getMapSignAreas().get(mTagCurrentTouchArea);

            if(area == null) { return; }
            if(area.getTag().equals(mTagCurrentTouchArea)) {
                int areaWidth = area.getRight() - area.getLeft();
                int areaHeight = area.getBottom() - area.getTop();
                double areaDiagonal = Math.sqrt(Math.pow(areaWidth, 2) + Math.pow(areaHeight, 2));
                double fingerMove = Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));
                double newDiagonal = areaDiagonal;
                if(distanceX < 0 && distanceX < 0) {     // 負數表示往右下滑動，正數表示往左上滑動
                    newDiagonal += fingerMove;
                } else {
                    newDiagonal -= fingerMove;
                }
                double ratio = newDiagonal / areaDiagonal;

                int newWidth = (int) Math.max(Math.round(areaWidth * ratio), MIN_WIDTH_SIGN_AREA);
                int newHeight = (int) Math.max(Math.round(areaHeight * ratio), MIN_HEIGHT_SIGN_AREA);
                SizeF pageSize = pdfView.getPageSize(pdfView.getCurrentPage());
                if(newWidth > pageSize.getWidth() - area.getLeft()) {
                    newWidth = (int) Math.floor(pageSize.getWidth() - area.getLeft());
                    newHeight = newWidth / SIGN_AREA_WIDTH_HEIGHT_RATIO;
                }

                area.setLeft(area.getLeft()).
                        setTop(area.getTop()).
                        setRight(area.getLeft() + newWidth).
                        setBottom(area.getTop() + newHeight);
                pdfView.invalidate();
            }
        }
    }
}
