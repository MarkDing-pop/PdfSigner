package com.github.barteksc.pdfviewer.sign;

public class FunctionBall {
    private float left;
    private float right;
    private float top;
    private float bottom;

    public float getLeft() { return left; }
    public float getRight() { return right; }
    public float getTop() { return top; }
    public float getBottom() { return bottom; }

    public FunctionBall setLeft(float left) {
        this.left = left;
        return this;
    }
    public FunctionBall setRight(float right) {
        this.right = right;
        return this;
    }
    public FunctionBall setTop(float top) {
        this.top = top;
        return this;
    }
    public FunctionBall setBottom(float bottom) {
        this.bottom = bottom;
        return this;
    }
}
