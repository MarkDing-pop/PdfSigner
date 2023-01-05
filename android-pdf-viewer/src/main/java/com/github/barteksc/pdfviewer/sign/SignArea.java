package com.github.barteksc.pdfviewer.sign;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;

import com.github.barteksc.pdfviewer.util.Util;

import static android.text.TextUtils.TruncateAt.END;
import static android.text.TextUtils.TruncateAt.START;

public class SignArea {
    public static int SIGN_AREA_WIDTH_HEIGHT_RATIO = 3;

    private String DEFAULT_DATE_FORMAT = "yyyy/mm/dd";
    private int DEFAULT_TEXT_SIZE = 16;
    private int DEFAULT_EMAIL_START_OFFSET = 8;
    private int DEFAULT_EMAIL_TOP_OFFSET = 16;
    private int DEFAULT_DATE_START_OFFSET = 88;
    private int DEFAULT_DATE_TOP_OFFSET = 16;
    private int DEFAULT_ELLIPSIZED_END = 40;
    private int left = -1;
    private int top = -1;
    private int right = -1;
    private int bottom = -1;
    private String tag = "";
    private String email = "";
    private String date = DEFAULT_DATE_FORMAT;
    private Rect emailRect = new Rect();
    private Rect dateRect = new Rect();
    private Paint bgPaint = null;
    private Paint outlinePaint = null;
    private Paint emailPaint = null;
    private Paint datePaint = null;
    private TextPaint emailTextPaint = null;
    private TextPaint dateTextPaint = null;
    private ZoomBall zoomBall = new ZoomBall();
    private AddBall addBall = new AddBall();
    private DelBall delBall = new DelBall();

    public SignArea(String tag, String email, int left, int top, int right, int bottom) {
        this.tag = tag;
        this.email = email;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public SignArea setLeft(int left) {
        this.left = left;
        return this;
    }
    public SignArea setTop(int top) {
        this.top = top;
        return this;
    }
    public SignArea setRight(int right) {
        this.right = right;
        return this;
    }
    public SignArea setBottom(int bottom) {
        this.bottom = bottom;
        return this;
    }

    public int getLeft() { return left; }
    public int getTop() { return top; }
    public int getRight() { return right; }
    public int getBottom() { return bottom; }
    public int getWidth() { return right - left; }
    public int getHeight() { return bottom - top; }
    public String getTag() { return tag; }
    public String getEmail() { return email; }
    public String getDate() { return date; }
    public Paint getBackGroundPaint() {
        if(bgPaint == null) {
            bgPaint = new Paint();
            bgPaint.setStyle(Paint.Style.FILL);
            int alphaRed = Color.argb(127, 255, 0, 0);
            bgPaint.setColor(alphaRed);
        }
        return bgPaint;
    }
    public Paint getOutlinePaint() {
        if(outlinePaint == null) {
            outlinePaint = new Paint();
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(8);
            outlinePaint.setAntiAlias(true);
            outlinePaint.setColor(Color.RED);
        }
        return outlinePaint;
    }
    public Paint getEmailPaint() { return emailPaint; }
    public Paint getDatePaint() { return datePaint; }
    public float[] getEmailCoordinate(float zoom) {
        float[] coordinate = { (left + Util.getDp(DEFAULT_EMAIL_START_OFFSET)) * zoom,
                               (top + Util.getDp(DEFAULT_EMAIL_TOP_OFFSET)) * zoom };
        return coordinate;
    }

    public float[] getDateCoordinate(float zoom) {
        float marginEnd = Util.getDp(DEFAULT_DATE_START_OFFSET);
        float stringWidth = dateTextPaint.measureText(date);

        if(stringWidth >= getWidth()) {
            // 日期格式字串的寬度若大於等於簽名框的寬度，則marginEnd的距離就要縮短
            marginEnd -= (stringWidth - getWidth());
        }

        float[] coordinate = { (right - marginEnd) * zoom ,
                               (bottom + Util.getDp(DEFAULT_DATE_TOP_OFFSET)) * zoom };
        return coordinate;
    }
    public String getEllipsizedEmail(float zoom) {
        emailPaint = getDrawTextPaint(email, emailRect, zoom);
        if(emailTextPaint == null) {
            emailTextPaint = new TextPaint(emailPaint);
        } else {
            emailTextPaint.set(emailPaint);
        }
        int width = Math.round(getWidth() * zoom);
        return getEllipsizedText(emailTextPaint, email, width, END, DEFAULT_ELLIPSIZED_END);
    }
    public String getEllipsizedDate(float zoom) {
        datePaint = getDrawTextPaint(date, dateRect, zoom);
        if(dateTextPaint == null) {
            dateTextPaint = new TextPaint(datePaint);
        } else {
            dateTextPaint.set(datePaint);
        }
        int width = Math.round(getWidth() * zoom);
        return getEllipsizedText(dateTextPaint, date, width, START, 0);
    }
    private Paint getDrawTextPaint(String text, Rect textBounds, float zoom) {
        Paint paint = new TextPaint();
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(zoom * DEFAULT_TEXT_SIZE * Resources.getSystem().getDisplayMetrics().density);
        paint.getTextBounds(text, 0, text.length(), textBounds);

        return paint;
    }
    private String getEllipsizedText(TextPaint textPaint, String text, int areaWidth,
                                     TruncateAt at, int ellipsizedMargin) {
        String string = text;
        float stringWidth = textPaint.measureText(text);
        if(areaWidth < stringWidth) {
            float available = areaWidth - ellipsizedMargin;
            string = TextUtils.ellipsize(text, textPaint, available, at).toString();
        }
        return string;
    }
    public ZoomBall getZoomBall() { return zoomBall; }
    public AddBall getAddBall() { return addBall; }
    public DelBall getDelBall() { return delBall; }

    public class ZoomBall extends FunctionBall {}
    public class AddBall extends FunctionBall {}
    public class DelBall extends FunctionBall {}
}
