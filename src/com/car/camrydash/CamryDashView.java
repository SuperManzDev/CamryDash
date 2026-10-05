package com.car.camrydash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

public class CamryDashView extends View {

    public interface OnModeToggleListener {
        void onToggleMode(boolean simulationActive);
    }

    public interface OnOpenSettingsListener {
        void onOpenSettings();
    }

    private CamryData data = new CamryData();
    private OnModeToggleListener modeToggleListener;
    private OnOpenSettingsListener settingsListener;

    // Paints
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcTachPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcSpeedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcRedlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textLargePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textMediumPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textSmallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gearBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gearTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Interactive button rectangles
    private final RectF btnModeRect = new RectF();
    private final RectF btnSettingsRect = new RectF();
    private final RectF tachArcRect = new RectF();
    private final RectF speedArcRect = new RectF();

    // Smoothed values for 60 fps rendering
    private float smoothRpm = 0;
    private float smoothSpeed = 0;
    private float smoothLoad = 0;
    private float smoothThrottle = 0;

    public CamryDashView(Context context) {
        super(context);
        init();
    }

    public CamryDashView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CamryDashView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint.setColor(Color.parseColor("#090C10"));

        cardBgPaint.setColor(Color.parseColor("#121721"));
        cardBorderPaint.setColor(Color.parseColor("#202836"));
        cardBorderPaint.setStyle(Paint.Style.STROKE);
        cardBorderPaint.setStrokeWidth(2.0f);

        arcTrackPaint.setStyle(Paint.Style.STROKE);
        arcTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        arcTrackPaint.setColor(Color.parseColor("#19212D"));

        arcTachPaint.setStyle(Paint.Style.STROKE);
        arcTachPaint.setStrokeCap(Paint.Cap.ROUND);
        arcTachPaint.setColor(Color.parseColor("#00E5FF"));

        arcRedlinePaint.setStyle(Paint.Style.STROKE);
        arcRedlinePaint.setStrokeCap(Paint.Cap.ROUND);
        arcRedlinePaint.setColor(Color.parseColor("#FF3B30"));

        tickPaint.setColor(Color.parseColor("#384457"));
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(2.0f);

        arcSpeedPaint.setStyle(Paint.Style.STROKE);
        arcSpeedPaint.setStrokeCap(Paint.Cap.ROUND);
        arcSpeedPaint.setColor(Color.parseColor("#F0F6FC"));

        textLargePaint.setColor(Color.WHITE);
        textLargePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textLargePaint.setTextAlign(Paint.Align.CENTER);

        textMediumPaint.setColor(Color.WHITE);
        textMediumPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textMediumPaint.setTextAlign(Paint.Align.CENTER);

        textSmallPaint.setColor(Color.parseColor("#8B949E"));
        textSmallPaint.setTextAlign(Paint.Align.CENTER);

        textLabelPaint.setColor(Color.parseColor("#6E7681"));
        textLabelPaint.setTextAlign(Paint.Align.CENTER);

        gearBgPaint.setColor(Color.parseColor("#0D2536"));
        gearBgPaint.setStyle(Paint.Style.FILL);

        gearTextPaint.setColor(Color.parseColor("#00E5FF"));
        gearTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        gearTextPaint.setTextAlign(Paint.Align.CENTER);

        barBgPaint.setColor(Color.parseColor("#18202C"));
        barFillPaint.setColor(Color.parseColor("#00E5FF"));

        btnPaint.setStyle(Paint.Style.FILL);
        setClickable(true);
        setFocusable(true);
    }

    public void setOnModeToggleListener(OnModeToggleListener listener) {
        this.modeToggleListener = listener;
    }

    public void setOnOpenSettingsListener(OnOpenSettingsListener listener) {
        this.settingsListener = listener;
    }

    public void updateData(CamryData newData) {
        this.data = newData;
        postInvalidateOnAnimation();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (insets != null) {
            setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom()
            );
        }
        return insets;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // Check Settings / TranslationBox button
            RectF settingsHitBox = new RectF(btnSettingsRect.left - 15.0f, 0, btnSettingsRect.right + 15.0f, btnSettingsRect.bottom + 20.0f);
            if (settingsHitBox.contains(x, y)) {
                if (settingsListener != null) {
                    settingsListener.onOpenSettings();
                }
                return true;
            }

            // Check Mode toggle button
            RectF modeHitBox = new RectF(btnModeRect.left - 15.0f, 0, btnModeRect.right + 25.0f, btnModeRect.bottom + 20.0f);
            if (modeHitBox.contains(x, y)) {
                boolean newSimMode = !data.isSimulation;
                data.isSimulation = newSimMode;
                data.statusText = newSimMode ? "SIMULATION RUNNING" : "SEARCHING FOR BRIDGE...";
                invalidate();
                if (modeToggleListener != null) {
                    modeToggleListener.onToggleMode(newSimMode);
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        cardBorderPaint.setStrokeWidth(Math.max(1.5f, 1.5f * density));

        // Exponential smoothing filter
        smoothRpm += (data.rpm - smoothRpm) * 0.25f;
        smoothSpeed += (data.speedMph - smoothSpeed) * 0.25f;
        smoothLoad += (data.engineLoad - smoothLoad) * 0.25f;
        smoothThrottle += (data.throttlePos - smoothThrottle) * 0.25f;

        // Background
        canvas.drawRect(0, 0, w, h, bgPaint);

        boolean isSplit = (w < 620.0f * (density > 1.5f ? 1.0f : 1.0f) || ((float) w / h < 1.25f));

        if (isSplit) {
            drawSplitScreen(canvas, w, h, density);
        } else {
            drawWidescreen(canvas, w, h, density);
        }

        if (Math.abs(data.rpm - smoothRpm) > 1.0f || Math.abs(data.speedMph - smoothSpeed) > 0.1f) {
            postInvalidateOnAnimation();
        }
    }

    private void drawWidescreen(Canvas canvas, int w, int h, float density) {
        // Safe padding respecting system navigation gestures and notches
        float safeLeft = Math.max(16.0f * density, (float) getPaddingLeft() + 10.0f * density);
        float safeRight = Math.max(16.0f * density, (float) getPaddingRight() + 10.0f * density);
        float safeTop = Math.max(8.0f * density, (float) getPaddingTop() + 4.0f * density);
        float safeBottom = Math.max(10.0f * density, (float) getPaddingBottom() + 6.0f * density);

        float headerHeight = Math.max(46.0f * density, h * 0.08f);
        drawHeader(canvas, w, headerHeight, false, density, safeLeft, safeRight);

        float bottomCardsHeight = Math.max(85.0f * density, h * 0.22f);
        float mainAreaTop = headerHeight + 8.0f * density;
        float mainAreaHeight = h - mainAreaTop - bottomCardsHeight - safeBottom;

        float availableWidth = w - safeLeft - safeRight;
        float gap = 14.0f * density;
        float halfWidth = (availableWidth - gap) / 2.0f;

        // Left Gauge: Tachometer Arc
        drawTachometerGauge(canvas, safeLeft, mainAreaTop, halfWidth, mainAreaHeight, density);

        // Right Gauge: Speedometer Arc + Gear
        drawSpeedometerGauge(canvas, safeLeft + halfWidth + gap, mainAreaTop, halfWidth, mainAreaHeight, density);

        // Bottom Row: 5 Telemetry Tiles
        float bottomTop = h - bottomCardsHeight - safeBottom;
        float tileGap = 10.0f * density;
        float tileWidth = (availableWidth - (tileGap * 4.0f)) / 5.0f;

        drawTile(canvas, safeLeft + (tileWidth + tileGap) * 0, bottomTop, tileWidth, bottomCardsHeight,
                "COOLANT", String.format("%.0f", data.coolantF), "°F", getCoolantColor(data.coolantF), density);

        drawTile(canvas, safeLeft + (tileWidth + tileGap) * 1, bottomTop, tileWidth, bottomCardsHeight,
                "BATTERY", String.format("%.1f", data.voltage), "V", getVoltageColor(data.voltage), density);

        drawTile(canvas, safeLeft + (tileWidth + tileGap) * 2, bottomTop, tileWidth, bottomCardsHeight,
                "INTAKE", String.format("%.0f", data.intakeTempF), "°F", Color.parseColor("#E0E6ED"), density);

        drawTile(canvas, safeLeft + (tileWidth + tileGap) * 3, bottomTop, tileWidth, bottomCardsHeight,
                "ENG LOAD", String.format("%.0f", smoothLoad), "%", Color.parseColor("#00E5FF"), density);

        drawTile(canvas, safeLeft + (tileWidth + tileGap) * 4, bottomTop, tileWidth, bottomCardsHeight,
                "THROTTLE", String.format("%.0f", smoothThrottle), "%", Color.parseColor("#38EF7D"), density);
    }

    private void drawSplitScreen(Canvas canvas, int w, int h, float density) {
        float safeLeft = Math.max(12.0f * density, (float) getPaddingLeft() + 6.0f * density);
        float safeRight = Math.max(12.0f * density, (float) getPaddingRight() + 6.0f * density);
        float safeBottom = Math.max(10.0f * density, (float) getPaddingBottom() + 6.0f * density);

        float headerHeight = Math.max(42.0f * density, h * 0.07f);
        drawHeader(canvas, w, headerHeight, true, density, safeLeft, safeRight);

        float currentY = headerHeight + 8.0f * density;
        float primaryCardHeight = Math.min(h * 0.36f, 220.0f * density);
        float availableWidth = w - safeLeft - safeRight;

        drawCompactPrimaryCard(canvas, safeLeft, currentY, availableWidth, primaryCardHeight, density);

        currentY += primaryCardHeight + 10.0f * density;
        float remainingHeight = h - currentY - safeBottom;

        boolean showThreeRows = (remainingHeight >= 300.0f * density);
        int rows = showThreeRows ? 3 : 2;
        float rowGap = 10.0f * density;
        float cardH = (remainingHeight - (rowGap * (rows - 1))) / rows;
        float cardW = (availableWidth - rowGap) / 2.0f;

        // Row 1
        drawTile(canvas, safeLeft, currentY, cardW, cardH,
                "COOLANT", String.format("%.0f", data.coolantF), "°F", getCoolantColor(data.coolantF), density);
        drawTile(canvas, safeLeft + cardW + rowGap, currentY, cardW, cardH,
                "BATTERY", String.format("%.1f", data.voltage), "V", getVoltageColor(data.voltage), density);

        // Row 2
        currentY += cardH + rowGap;
        drawTile(canvas, safeLeft, currentY, cardW, cardH,
                "ENG LOAD", String.format("%.0f", smoothLoad), "%", Color.parseColor("#00E5FF"), density);
        drawTile(canvas, safeLeft + cardW + rowGap, currentY, cardW, cardH,
                "THROTTLE", String.format("%.0f", smoothThrottle), "%", Color.parseColor("#38EF7D"), density);

        // Row 3 (if tall vertical aspect ratio)
        if (showThreeRows) {
            currentY += cardH + rowGap;
            drawTile(canvas, safeLeft, currentY, cardW, cardH,
                    "INTAKE", String.format("%.0f", data.intakeTempF), "°F", Color.parseColor("#E0E6ED"), density);
            drawTile(canvas, safeLeft + cardW + rowGap, currentY, cardW, cardH,
                    "AIR FLOW", String.format("%.1f", data.maf), "g/s", Color.parseColor("#00E5FF"), density);
        }
    }

    private void drawHeader(Canvas canvas, int w, float headerHeight, boolean isSplit, float density, float safeLeft, float safeRight) {
        float btnH = headerHeight - 10.0f * density;
        float btnTop = 5.0f * density;

        // Mode button pill
        float btnModeW = isSplit ? 120.0f * density : 145.0f * density;
        float btnModeRight = w - safeRight;
        float btnModeLeft = btnModeRight - btnModeW;
        btnModeRect.set(btnModeLeft, btnTop, btnModeRight, btnTop + btnH);

        // Settings / TranslationBox button pill
        float btnSetW = isSplit ? 100.0f * density : 135.0f * density;
        float btnSetRight = btnModeLeft - (8.0f * density);
        float btnSetLeft = btnSetRight - btnSetW;
        btnSettingsRect.set(btnSetLeft, btnTop, btnSetRight, btnTop + btnH);

        // Vehicle Title
        textSmallPaint.setTextAlign(Paint.Align.LEFT);
        textSmallPaint.setColor(Color.parseColor("#8B949E"));
        textSmallPaint.setTextSize(Math.min(17.0f * density, headerHeight * 0.40f));
        textSmallPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        canvas.drawText(isSplit ? "CAMRY 2.5L" : "2012 TOYOTA CAMRY 2.5L (2AR-FE)", safeLeft, headerHeight * 0.65f, textSmallPaint);

        // TranslationBox / Settings Button
        btnPaint.setColor(Color.parseColor("#15202E"));
        canvas.drawRoundRect(btnSettingsRect, 8.0f * density, 8.0f * density, btnPaint);
        cardBorderPaint.setColor(Color.parseColor("#00B0FF"));
        canvas.drawRoundRect(btnSettingsRect, 8.0f * density, 8.0f * density, cardBorderPaint);

        textMediumPaint.setColor(Color.parseColor("#00B0FF"));
        textMediumPaint.setTextSize(Math.min(13.0f * density, btnH * 0.45f));
        textMediumPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(isSplit ? "BRIDGE" : "TRANSLATIONBOX", btnSettingsRect.centerX(), btnSettingsRect.centerY() + (5.0f * density), textMediumPaint);

        // Mode Button (SIM / LIVE)
        btnPaint.setColor(data.isSimulation ? Color.parseColor("#251D14") : Color.parseColor("#12281D"));
        canvas.drawRoundRect(btnModeRect, 8.0f * density, 8.0f * density, btnPaint);
        cardBorderPaint.setColor(data.isSimulation ? Color.parseColor("#FF9F0A") : Color.parseColor("#30D158"));
        canvas.drawRoundRect(btnModeRect, 8.0f * density, 8.0f * density, cardBorderPaint);

        textMediumPaint.setColor(data.isSimulation ? Color.parseColor("#FF9F0A") : Color.parseColor("#30D158"));
        textMediumPaint.setTextSize(Math.min(13.0f * density, btnH * 0.45f));
        textMediumPaint.setTextAlign(Paint.Align.CENTER);
        String btnText = data.isSimulation ? (isSplit ? "SIM" : "SIMULATOR") : (isSplit ? "LIVE" : "LIVE OBD");
        canvas.drawText(btnText, btnModeRect.centerX(), btnModeRect.centerY() + (5.0f * density), textMediumPaint);
    }

    private void drawTachometerGauge(Canvas canvas, float left, float top, float width, float height, float density) {
        RectF card = new RectF(left, top, left + width, top + height);
        canvas.drawRoundRect(card, 14.0f * density, 14.0f * density, cardBgPaint);
        cardBorderPaint.setColor(Color.parseColor("#202836"));
        canvas.drawRoundRect(card, 14.0f * density, 14.0f * density, cardBorderPaint);

        float size = Math.min(width * 0.85f, height * 0.74f);
        float cx = card.centerX();
        float cy = top + height * 0.44f;
        tachArcRect.set(cx - size / 2.0f, cy - size / 2.0f, cx + size / 2.0f, cy + size / 2.0f);

        float stroke = size * 0.082f;
        arcTrackPaint.setStrokeWidth(stroke);
        arcTachPaint.setStrokeWidth(stroke);
        arcRedlinePaint.setStrokeWidth(stroke);

        float startAngle = 140.0f;
        float totalSweep = 260.0f;

        // Base track
        canvas.drawArc(tachArcRect, startAngle, totalSweep, false, arcTrackPaint);

        // Discrete radial tick marks at each 1000 RPM (0 to 7)
        float rInner = (size / 2.0f) + stroke * 0.65f;
        float rOuter = rInner + (8.0f * density);
        for (int i = 0; i <= 7; i++) {
            float angle = startAngle + (totalSweep * (i / 7.0f));
            float rad = (float) Math.toRadians(angle);
            float x1 = cx + (float) Math.cos(rad) * rInner;
            float y1 = cy + (float) Math.sin(rad) * rInner;
            float x2 = cx + (float) Math.cos(rad) * rOuter;
            float y2 = cy + (float) Math.sin(rad) * rOuter;
            tickPaint.setColor(i >= 6 ? Color.parseColor("#FF3B30") : Color.parseColor("#384457"));
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        // Redline arc (6200 to 7000 RPM)
        float redlineFraction = 6200.0f / 7000.0f;
        float redlineStart = startAngle + (totalSweep * redlineFraction);
        float redlineSweep = totalSweep * (800.0f / 7000.0f);
        canvas.drawArc(tachArcRect, redlineStart, redlineSweep, false, arcRedlinePaint);

        // Active Tach Arc
        float rpmFraction = Math.min(1.0f, Math.max(0.0f, smoothRpm / 7000.0f));
        float currentSweep = totalSweep * rpmFraction;

        if (smoothRpm >= 6200.0f) {
            arcTachPaint.setColor(Color.parseColor("#FF3B30"));
        } else if (smoothRpm >= 5200.0f) {
            arcTachPaint.setColor(Color.parseColor("#FF9F0A"));
        } else {
            arcTachPaint.setColor(Color.parseColor("#00E5FF"));
        }
        canvas.drawArc(tachArcRect, startAngle, currentSweep, false, arcTachPaint);

        // Digital RPM Readout (strictly non-overlapping)
        float rpmTextSize = size * 0.28f;
        textLargePaint.setColor(Color.WHITE);
        textLargePaint.setTextSize(rpmTextSize);
        canvas.drawText(String.valueOf(Math.round(smoothRpm)), cx, cy + (rpmTextSize * 0.32f), textLargePaint);

        textLabelPaint.setColor(Color.parseColor("#6E7681"));
        textLabelPaint.setTextSize(Math.max(12.0f * density, size * 0.09f));
        canvas.drawText("ENGINE RPM", cx, cy + (rpmTextSize * 0.32f) + (18.0f * density), textLabelPaint);

        // Bottom Load Bar
        float barW = width * 0.72f;
        float barH = 8.0f * density;
        float barX = cx - barW / 2.0f;
        float barY = top + height - (22.0f * density);

        textSmallPaint.setTextAlign(Paint.Align.LEFT);
        textSmallPaint.setTextSize(12.0f * density);
        textSmallPaint.setColor(Color.parseColor("#8B949E"));
        canvas.drawText("LOAD", barX, barY - 4.0f * density, textSmallPaint);

        textSmallPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format("%.0f%%", smoothLoad), barX + barW, barY - 4.0f * density, textSmallPaint);

        RectF barBg = new RectF(barX, barY, barX + barW, barY + barH);
        canvas.drawRoundRect(barBg, 4.0f * density, 4.0f * density, barBgPaint);

        float fillW = barW * (Math.min(100.0f, Math.max(0.0f, smoothLoad)) / 100.0f);
        RectF barFill = new RectF(barX, barY, barX + fillW, barY + barH);
        barFillPaint.setColor(Color.parseColor("#00E5FF"));
        canvas.drawRoundRect(barFill, 4.0f * density, 4.0f * density, barFillPaint);
    }

    private void drawSpeedometerGauge(Canvas canvas, float left, float top, float width, float height, float density) {
        RectF card = new RectF(left, top, left + width, top + height);
        canvas.drawRoundRect(card, 14.0f * density, 14.0f * density, cardBgPaint);
        cardBorderPaint.setColor(Color.parseColor("#202836"));
        canvas.drawRoundRect(card, 14.0f * density, 14.0f * density, cardBorderPaint);

        float size = Math.min(width * 0.85f, height * 0.74f);
        float cx = card.centerX();
        float cy = top + height * 0.44f;
        speedArcRect.set(cx - size / 2.0f, cy - size / 2.0f, cx + size / 2.0f, cy + size / 2.0f);

        float stroke = size * 0.082f;
        arcTrackPaint.setStrokeWidth(stroke);
        arcSpeedPaint.setStrokeWidth(stroke);

        float startAngle = 140.0f;
        float totalSweep = 260.0f;

        canvas.drawArc(speedArcRect, startAngle, totalSweep, false, arcTrackPaint);

        // Speed tick marks at 0, 20, 40, 60, 80, 100, 120 MPH
        float rInner = (size / 2.0f) + stroke * 0.65f;
        float rOuter = rInner + (8.0f * density);
        for (int i = 0; i <= 6; i++) {
            float angle = startAngle + (totalSweep * (i / 6.0f));
            float rad = (float) Math.toRadians(angle);
            float x1 = cx + (float) Math.cos(rad) * rInner;
            float y1 = cy + (float) Math.sin(rad) * rInner;
            float x2 = cx + (float) Math.cos(rad) * rOuter;
            float y2 = cy + (float) Math.sin(rad) * rOuter;
            tickPaint.setColor(Color.parseColor("#384457"));
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        // Active Speed Arc
        float speedFraction = Math.min(1.0f, Math.max(0.0f, smoothSpeed / 120.0f));
        float currentSweep = totalSweep * speedFraction;
        arcSpeedPaint.setColor(Color.parseColor("#F0F6FC"));
        canvas.drawArc(speedArcRect, startAngle, currentSweep, false, arcSpeedPaint);

        // Gear Badge Pill: placed higher inside top arch to strictly prevent ANY overlap
        float gearH = size * 0.17f;
        float gearW = size * 0.19f;
        float gearTop = cy - size * 0.38f;
        RectF gearRect = new RectF(cx - gearW / 2.0f, gearTop, cx + gearW / 2.0f, gearTop + gearH);
        canvas.drawRoundRect(gearRect, 8.0f * density, 8.0f * density, gearBgPaint);

        cardBorderPaint.setColor(Color.parseColor("#00E5FF"));
        canvas.drawRoundRect(gearRect, 8.0f * density, 8.0f * density, cardBorderPaint);

        gearTextPaint.setTextSize(gearH * 0.62f);
        canvas.drawText(data.gear != null ? data.gear : "P", gearRect.centerX(), gearRect.centerY() + (gearH * 0.22f), gearTextPaint);

        // Digital Speed Readout: clean vertical gap beneath gear badge
        float speedTextSize = size * 0.30f;
        float speedBaseline = cy + (size * 0.14f);
        textLargePaint.setColor(Color.WHITE);
        textLargePaint.setTextSize(speedTextSize);
        canvas.drawText(String.valueOf(Math.round(smoothSpeed)), cx, speedBaseline, textLargePaint);

        // MPH Unit label cleanly beneath speed numbers
        textLabelPaint.setColor(Color.parseColor("#6E7681"));
        textLabelPaint.setTextSize(Math.max(12.0f * density, size * 0.09f));
        canvas.drawText("MPH", cx, speedBaseline + (18.0f * density), textLabelPaint);

        // Bottom Throttle Bar
        float barW = width * 0.72f;
        float barH = 8.0f * density;
        float barX = cx - barW / 2.0f;
        float barY = top + height - (22.0f * density);

        textSmallPaint.setTextAlign(Paint.Align.LEFT);
        textSmallPaint.setTextSize(12.0f * density);
        textSmallPaint.setColor(Color.parseColor("#8B949E"));
        canvas.drawText("THROTTLE", barX, barY - 4.0f * density, textSmallPaint);

        textSmallPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format("%.0f%%", smoothThrottle), barX + barW, barY - 4.0f * density, textSmallPaint);

        RectF barBg = new RectF(barX, barY, barX + barW, barY + barH);
        canvas.drawRoundRect(barBg, 4.0f * density, 4.0f * density, barBgPaint);

        float fillW = barW * (Math.min(100.0f, Math.max(0.0f, smoothThrottle)) / 100.0f);
        RectF barFill = new RectF(barX, barY, barX + fillW, barY + barH);
        barFillPaint.setColor(Color.parseColor("#38EF7D"));
        canvas.drawRoundRect(barFill, 4.0f * density, 4.0f * density, barFillPaint);
    }

    private void drawCompactPrimaryCard(Canvas canvas, float left, float top, float width, float height, float density) {
        RectF card = new RectF(left, top, left + width, top + height);
        canvas.drawRoundRect(card, 14.0f * density, 14.0f * density, cardBgPaint);
        cardBorderPaint.setColor(Color.parseColor("#202836"));
        canvas.drawRoundRect(card, 14.0f * density, 14.0f * density, cardBorderPaint);

        // Top Tachometer Bar
        float barX = left + 14.0f * density;
        float barY = top + 14.0f * density;
        float barW = width - 28.0f * density;
        float barH = 10.0f * density;

        RectF rpmBg = new RectF(barX, barY, barX + barW, barY + barH);
        canvas.drawRoundRect(rpmBg, 5.0f * density, 5.0f * density, barBgPaint);

        float rpmFrac = Math.min(1.0f, Math.max(0.0f, smoothRpm / 7000.0f));
        RectF rpmFill = new RectF(barX, barY, barX + (barW * rpmFrac), barY + barH);
        if (smoothRpm >= 6200.0f) {
            barFillPaint.setColor(Color.parseColor("#FF3B30"));
        } else if (smoothRpm >= 5200.0f) {
            barFillPaint.setColor(Color.parseColor("#FF9F0A"));
        } else {
            barFillPaint.setColor(Color.parseColor("#00E5FF"));
        }
        canvas.drawRoundRect(rpmFill, 5.0f * density, 5.0f * density, barFillPaint);

        textSmallPaint.setTextAlign(Paint.Align.LEFT);
        textSmallPaint.setColor(Color.parseColor("#8B949E"));
        textSmallPaint.setTextSize(12.0f * density);
        canvas.drawText("TACH", barX, barY + barH + 16.0f * density, textSmallPaint);

        textSmallPaint.setTextAlign(Paint.Align.RIGHT);
        textSmallPaint.setColor(Color.WHITE);
        canvas.drawText(String.format("%.0f RPM", smoothRpm), barX + barW, barY + barH + 16.0f * density, textSmallPaint);

        // Lower zone cleanly divided horizontally: 60% Left for Speed, 40% Right for Gear
        float lowerTop = barY + barH + 20.0f * density;
        float lowerHeight = (top + height) - lowerTop - 12.0f * density;

        float speedZoneCenterX = left + (width * 0.36f);
        float gearZoneCenterX = left + (width * 0.78f);
        float centerContentY = lowerTop + (lowerHeight * 0.55f);

        // Speed in Left Zone
        float speedTextSize = Math.min(width * 0.28f, lowerHeight * 0.75f);
        textLargePaint.setColor(Color.WHITE);
        textLargePaint.setTextSize(speedTextSize);
        textLargePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.valueOf(Math.round(smoothSpeed)), speedZoneCenterX, centerContentY, textLargePaint);

        textLabelPaint.setColor(Color.parseColor("#6E7681"));
        textLabelPaint.setTextSize(Math.max(12.0f * density, speedTextSize * 0.22f));
        textLabelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("MPH", speedZoneCenterX, centerContentY + (speedTextSize * 0.28f), textLabelPaint);

        // Gear Badge in Right Zone (Guaranteed zero overlap)
        float gearSize = Math.min(width * 0.24f, lowerHeight * 0.70f);
        RectF gearRect = new RectF(
                gearZoneCenterX - gearSize / 2.0f,
                centerContentY - gearSize * 0.65f,
                gearZoneCenterX + gearSize / 2.0f,
                centerContentY + gearSize * 0.35f
        );
        canvas.drawRoundRect(gearRect, 10.0f * density, 10.0f * density, gearBgPaint);

        cardBorderPaint.setColor(Color.parseColor("#00E5FF"));
        canvas.drawRoundRect(gearRect, 10.0f * density, 10.0f * density, cardBorderPaint);

        gearTextPaint.setTextSize(gearSize * 0.60f);
        canvas.drawText(data.gear != null ? data.gear : "P", gearRect.centerX(), gearRect.centerY() + (gearSize * 0.20f), gearTextPaint);
    }

    private void drawTile(Canvas canvas, float left, float top, float width, float height,
                          String title, String val, String unit, int valColor, float density) {
        RectF tile = new RectF(left, top, left + width, top + height);
        canvas.drawRoundRect(tile, 12.0f * density, 12.0f * density, cardBgPaint);
        cardBorderPaint.setColor(Color.parseColor("#202836"));
        canvas.drawRoundRect(tile, 12.0f * density, 12.0f * density, cardBorderPaint);

        float cx = tile.centerX();

        // Responsive font scaling
        float titleSize = Math.min(width * 0.16f, height * 0.18f);
        float valSize = Math.min(width * 0.32f, height * 0.40f);
        float unitSize = Math.min(width * 0.14f, height * 0.16f);

        // Title
        textLabelPaint.setColor(Color.parseColor("#6E7681"));
        textLabelPaint.setTextSize(titleSize);
        textLabelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(title, cx, top + height * 0.28f, textLabelPaint);

        // Value
        textMediumPaint.setColor(valColor);
        textMediumPaint.setTextSize(valSize);
        canvas.drawText(val, cx, top + height * 0.68f, textMediumPaint);

        // Unit
        textSmallPaint.setColor(Color.parseColor("#8B949E"));
        textSmallPaint.setTextSize(unitSize);
        textSmallPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(unit, cx, top + height * 0.88f, textSmallPaint);
    }

    private int getCoolantColor(float tempF) {
        if (tempF < 160.0f) return Color.parseColor("#00C2FF");
        if (tempF <= 212.0f) return Color.parseColor("#38EF7D");
        if (tempF <= 225.0f) return Color.parseColor("#FF9F0A");
        return Color.parseColor("#FF3B30");
    }

    private int getVoltageColor(float volt) {
        if (volt < 12.0f) return Color.parseColor("#FF3B30");
        if (volt < 13.2f) return Color.parseColor("#FF9F0A");
        if (volt <= 14.8f) return Color.parseColor("#38EF7D");
        return Color.parseColor("#FF3B30");
    }
}
