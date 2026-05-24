package com.example.lilapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FlappyBirdView extends SurfaceView implements SurfaceHolder.Callback {

    // ── Game states ──────────────────────────────────────────────────────────
    private enum State { WAITING, PLAYING, DEAD }
    private State state = State.WAITING;

    // ── Thread ───────────────────────────────────────────────────────────────
    private GameThread gameThread;

    // ── Screen ───────────────────────────────────────────────────────────────
    private int W, H;

    // ── Bird ─────────────────────────────────────────────────────────────────
    private float birdX, birdY;
    private float velY;
    private static final float GRAVITY      = 0.55f;
    private static final float FLAP_FORCE   = -13f;
    private static final float MAX_FALL     = 14f;
    private float birdAngle;                       // visual tilt
    private int   wingFrame  = 0;
    private int   wingTick   = 0;

    // ── Pipes ────────────────────────────────────────────────────────────────
    private static final float PIPE_SPEED   = 5f;
    private static final float PIPE_WIDTH   = 90f;
    private static final float PIPE_GAP     = 320f;  // gap between top & bottom pipe
    private static final int   PIPE_SPACING = 400;   // horizontal distance between pipes
    private List<float[]> pipes = new ArrayList<>();  // each: [x, gapTop]
    private Random rand = new Random();

    // ── Ground ───────────────────────────────────────────────────────────────
    private float groundY;
    private float groundScroll = 0;
    private static final float GROUND_H = 80f;

    // ── Clouds ───────────────────────────────────────────────────────────────
    private static final int CLOUD_COUNT = 4;
    private float[] cloudX = new float[CLOUD_COUNT];
    private float[] cloudY = new float[CLOUD_COUNT];
    private float[] cloudW = new float[CLOUD_COUNT];

    // ── Score ────────────────────────────────────────────────────────────────
    private int score    = 0;
    private int best     = 0;
    private SharedPreferences prefs;

    // ── Music ────────────────────────────────────────────────────────────────
    private MediaPlayer mediaPlayer;

    // ── Paints ───────────────────────────────────────────────────────────────
    private Paint skyPaint, groundPaint, groundLinePaint;
    private Paint pipeBodyPaint, pipeLipPaint, pipeHighlightPaint, pipeShadowPaint;
    private Paint birdBodyPaint, birdWingPaint, birdEyePaint, birdPupilPaint, birdBeakPaint;
    private Paint cloudPaint;
    private Paint scoreMainPaint, scoreShadowPaint, uiPaint, uiShadowPaint, subtitlePaint;
    private Paint flashPaint;
    private float flashAlpha = 0f;

    // ─────────────────────────────────────────────────────────────────────────
    public FlappyBirdView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FlappyBirdView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        getHolder().addCallback(this);
        setFocusable(true);
        setClickable(true);
        prefs = context.getSharedPreferences("flappy_prefs", Context.MODE_PRIVATE);
        best  = prefs.getInt("best", 0);
        initPaints();
    }

    private void initPaints() {
        // Sky gradient drawn manually per frame

        // Ground
        groundPaint = new Paint();
        groundPaint.setColor(Color.rgb(222, 184, 100));
        groundLinePaint = new Paint();
        groundLinePaint.setColor(Color.rgb(180, 140, 60));
        groundLinePaint.setStrokeWidth(3f);

        // Pipe
        pipeBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipeBodyPaint.setColor(Color.rgb(80, 200, 80));
        pipeLipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipeLipPaint.setColor(Color.rgb(60, 170, 60));
        pipeHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipeHighlightPaint.setColor(Color.rgb(130, 230, 130));
        pipeHighlightPaint.setStrokeWidth(6f);
        pipeHighlightPaint.setStyle(Paint.Style.STROKE);
        pipeShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipeShadowPaint.setColor(Color.rgb(30, 120, 30));
        pipeShadowPaint.setStrokeWidth(6f);
        pipeShadowPaint.setStyle(Paint.Style.STROKE);

        // Bird
        birdBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        birdBodyPaint.setColor(Color.rgb(255, 220, 50));
        birdWingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        birdWingPaint.setColor(Color.rgb(230, 180, 30));
        birdEyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        birdEyePaint.setColor(Color.WHITE);
        birdPupilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        birdPupilPaint.setColor(Color.BLACK);
        birdBeakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        birdBeakPaint.setColor(Color.rgb(255, 140, 0));

        // Cloud
        cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cloudPaint.setColor(Color.argb(220, 255, 255, 255));

        // Score
        scoreMainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scoreMainPaint.setColor(Color.WHITE);
        scoreMainPaint.setTypeface(Typeface.DEFAULT_BOLD);
        scoreMainPaint.setTextAlign(Paint.Align.CENTER);

        scoreShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scoreShadowPaint.setColor(Color.rgb(80, 80, 80));
        scoreShadowPaint.setTypeface(Typeface.DEFAULT_BOLD);
        scoreShadowPaint.setTextAlign(Paint.Align.CENTER);

        uiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        uiPaint.setColor(Color.WHITE);
        uiPaint.setTypeface(Typeface.DEFAULT_BOLD);
        uiPaint.setTextAlign(Paint.Align.CENTER);

        uiShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        uiShadowPaint.setColor(Color.rgb(60, 60, 60));
        uiShadowPaint.setTypeface(Typeface.DEFAULT_BOLD);
        uiShadowPaint.setTextAlign(Paint.Align.CENTER);

        subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subtitlePaint.setColor(Color.rgb(255, 240, 150));
        subtitlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        subtitlePaint.setTextAlign(Paint.Align.CENTER);

        flashPaint = new Paint();
        flashPaint.setColor(Color.WHITE);
    }

    // ── Surface callbacks ────────────────────────────────────────────────────
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mediaPlayer == null) {
            try {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                );
                android.content.res.AssetFileDescriptor afd =
                        getContext().getResources().openRawResourceFd(R.raw.background_music);
                mediaPlayer.setDataSource(
                        afd.getFileDescriptor(),
                        afd.getStartOffset(),
                        afd.getLength()
                );
                afd.close();
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(0.5f, 0.5f);
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (Exception e) {
                // Release and null out so resumeGame won't try to call isPlaying() on a broken instance
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        W = width;
        H = height;
        groundY = H - GROUND_H;
        resetGame();
        initClouds();
        if (gameThread == null || !gameThread.isAlive()) {
            gameThread = new GameThread(holder);
            gameThread.start();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (gameThread != null) {
            gameThread.running = false;
            try { gameThread.join(500); } catch (InterruptedException ignored) {}
        }
    }

    // ── Public controls ──────────────────────────────────────────────────────
    public void pauseGame() {
        if (gameThread != null) gameThread.running = false;
        if (mediaPlayer != null) {
            try { if (mediaPlayer.isPlaying()) mediaPlayer.pause(); } catch (Exception ignored) {}
        }
    }

    public void resumeGame() {
        if (gameThread != null && !gameThread.isAlive()) {
            gameThread = new GameThread(getHolder());
            gameThread.start();
        }
        if (mediaPlayer != null) {
            try { if (!mediaPlayer.isPlaying()) mediaPlayer.start(); } catch (Exception ignored) {}
        }
    }

    public void cleanup() {
        pauseGame();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // ── Touch ────────────────────────────────────────────────────────────────
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            switch (state) {
                case WAITING:
                    state = State.PLAYING;
                    flap();
                    break;
                case PLAYING:
                    flap();
                    break;
                case DEAD:
                    resetGame();
                    state = State.WAITING;
                    break;
            }
        }
        return true;
    }

    private void flap() {
        velY = FLAP_FORCE;
        flashAlpha = 0f; // stop any flash
    }

    // ── Game logic ───────────────────────────────────────────────────────────
    private void resetGame() {
        birdX = W * 0.28f;
        birdY = H * 0.45f;
        velY  = 0f;
        birdAngle = 0f;
        score = 0;
        pipes.clear();
        passedPipes.clear();
        // Spawn initial pipes off-screen to the right
        float startX = W + 100;
        for (int i = 0; i < 3; i++) {
            spawnPipe(startX + i * PIPE_SPACING);
        }
        groundScroll = 0;
        flashAlpha   = 0f;
    }

    private void initClouds() {
        for (int i = 0; i < CLOUD_COUNT; i++) {
            cloudX[i] = rand.nextFloat() * W;
            cloudY[i] = 60 + rand.nextFloat() * (H * 0.35f);
            cloudW[i] = 120 + rand.nextFloat() * 160;
        }
    }

    private void spawnPipe(float x) {
        float minGapTop = H * 0.12f;
        float maxGapTop = groundY - PIPE_GAP - H * 0.12f;
        float gapTop = minGapTop + rand.nextFloat() * (maxGapTop - minGapTop);
        pipes.add(new float[]{x, gapTop});
    }

    private void update() {
        // Always scroll ground and clouds
        groundScroll = (groundScroll + PIPE_SPEED) % 60f;
        for (int i = 0; i < CLOUD_COUNT; i++) {
            cloudX[i] -= 1.2f;
            if (cloudX[i] + cloudW[i] < 0) {
                cloudX[i] = W + 50;
                cloudY[i] = 60 + rand.nextFloat() * (H * 0.35f);
                cloudW[i] = 120 + rand.nextFloat() * 160;
            }
        }

        if (state != State.PLAYING) {
            // Idle bob in WAITING state
            if (state == State.WAITING) {
                birdY = H * 0.45f + (float)(Math.sin(System.currentTimeMillis() / 300.0) * 12);
                birdAngle = 0f;
            }
            return;
        }

        // Bird physics
        velY = Math.min(velY + GRAVITY, MAX_FALL);
        birdY += velY;

        // Bird tilt: nose up on flap, nose down on fall
        float targetAngle = velY < 0 ? -25f : Math.min(velY * 5f, 80f);
        birdAngle += (targetAngle - birdAngle) * 0.2f;

        // Wing animation
        wingTick++;
        if (wingTick >= 5) { wingTick = 0; wingFrame = (wingFrame + 1) % 3; }

        // Flash decay
        if (flashAlpha > 0) flashAlpha = Math.max(0, flashAlpha - 15);

        // Move pipes
        List<float[]> toRemove = new ArrayList<>();
        for (float[] pipe : pipes) {
            pipe[0] -= PIPE_SPEED;
            // Score: bird passed the pipe
            if (!isPipePassed(pipe) && pipe[0] + PIPE_WIDTH < birdX) {
                markPipePassed(pipe);
                score++;
                if (score > best) {
                    best = score;
                    prefs.edit().putInt("best", best).apply();
                }
            }
            if (pipe[0] + PIPE_WIDTH < -10) toRemove.add(pipe);
        }
        pipes.removeAll(toRemove);

        // Spawn new pipes to keep 3 ahead
        if (pipes.isEmpty() || pipes.get(pipes.size() - 1)[0] < W - PIPE_SPACING + PIPE_SPEED) {
            spawnPipe(W + 50);
        }

        // Collision: ground / ceiling
        float birdR = getBirdRadius();
        if (birdY + birdR >= groundY || birdY - birdR <= 0) {
            die();
            return;
        }

        // Collision: pipes
        for (float[] pipe : pipes) {
            float px = pipe[0], gapTop = pipe[1];
            float pRight = px + PIPE_WIDTH;
            float bLeft  = birdX - birdR + 4;
            float bRight = birdX + birdR - 4;
            float bTop   = birdY - birdR + 4;
            float bBottom= birdY + birdR - 4;

            if (bRight > px && bLeft < pRight) {
                if (bTop < gapTop || bBottom > gapTop + PIPE_GAP) {
                    die();
                    return;
                }
            }
        }
    }

    private java.util.Set<float[]> passedPipes = new java.util.HashSet<>();

    private boolean isPipePassed(float[] pipe) { return passedPipes.contains(pipe); }
    private void markPipePassed(float[] pipe) { passedPipes.add(pipe); }

    private void die() {
        state = State.DEAD;
        flashAlpha = 255f;
        velY = FLAP_FORCE * 0.5f; // small upward bump on death
    }

    private float getBirdRadius() { return W * 0.055f; }

    // ── Draw ─────────────────────────────────────────────────────────────────
    private void drawFrame(Canvas canvas) {
        // Sky gradient
        Paint skyTop = new Paint();
        skyTop.setShader(new LinearGradient(0, 0, 0, groundY,
                Color.rgb(80, 180, 255), Color.rgb(160, 220, 255), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, W, groundY, skyTop);

        // Clouds
        for (int i = 0; i < CLOUD_COUNT; i++) {
            drawCloud(canvas, cloudX[i], cloudY[i], cloudW[i]);
        }

        // Pipes
        for (float[] pipe : pipes) {
            drawPipe(canvas, pipe[0], pipe[1]);
        }

        // Ground
        canvas.drawRect(0, groundY, W, H, groundPaint);
        // Ground stripe
        canvas.drawLine(0, groundY + 18, W, groundY + 18, groundLinePaint);
        // Ground scroll lines
        Paint scrollLine = new Paint();
        scrollLine.setColor(Color.rgb(200, 160, 80));
        scrollLine.setStrokeWidth(2f);
        for (float gx = -groundScroll; gx < W; gx += 60) {
            canvas.drawLine(gx, groundY, gx + 30, groundY + GROUND_H, scrollLine);
        }

        // Bird
        drawBird(canvas);

        // Score HUD
        drawHUD(canvas);

        // White flash on death
        if (flashAlpha > 0) {
            flashPaint.setAlpha((int) flashAlpha);
            canvas.drawRect(0, 0, W, H, flashPaint);
        }
    }

    private void drawCloud(Canvas canvas, float x, float y, float w) {
        float h = w * 0.45f;
        canvas.drawOval(x, y, x + w, y + h, cloudPaint);
        canvas.drawOval(x + w * 0.15f, y - h * 0.4f, x + w * 0.65f, y + h * 0.5f, cloudPaint);
        canvas.drawOval(x + w * 0.45f, y - h * 0.2f, x + w * 0.9f, y + h * 0.6f, cloudPaint);
    }

    private void drawPipe(Canvas canvas, float x, float gapTop) {
        float lipW   = PIPE_WIDTH + 16f;
        float lipH   = 28f;
        float lipX   = x - 8f;

        // Top pipe body
        canvas.drawRect(x, 0, x + PIPE_WIDTH, gapTop - lipH, pipeBodyPaint);
        // Top pipe lip
        canvas.drawRect(lipX, gapTop - lipH, lipX + lipW, gapTop, pipeLipPaint);

        // Bottom pipe body
        float gapBottom = gapTop + PIPE_GAP;
        canvas.drawRect(x, gapBottom + lipH, x + PIPE_WIDTH, groundY, pipeBodyPaint);
        // Bottom pipe lip
        canvas.drawRect(lipX, gapBottom, lipX + lipW, gapBottom + lipH, pipeLipPaint);

        // Highlight & shadow on bodies
        canvas.drawLine(x + 10, 0, x + 10, gapTop - lipH, pipeHighlightPaint);
        canvas.drawLine(x + PIPE_WIDTH - 10, 0, x + PIPE_WIDTH - 10, gapTop - lipH, pipeShadowPaint);
        canvas.drawLine(x + 10, gapBottom + lipH, x + 10, groundY, pipeHighlightPaint);
        canvas.drawLine(x + PIPE_WIDTH - 10, gapBottom + lipH, x + PIPE_WIDTH - 10, groundY, pipeShadowPaint);
    }

    private void drawBird(Canvas canvas) {
        float r  = getBirdRadius();
        float cx = birdX;
        float cy = birdY;

        canvas.save();
        canvas.rotate(birdAngle, cx, cy);

        // Shadow
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(Color.argb(40, 0, 0, 0));
        canvas.drawOval(cx - r * 0.8f, cy + r * 0.8f, cx + r * 0.8f, cy + r * 1.1f, shadow);

        // Wing (animates up/middle/down)
        float wingOffY = wingFrame == 0 ? -r * 0.4f : wingFrame == 1 ? 0 : r * 0.3f;
        canvas.drawOval(cx - r * 0.2f, cy + wingOffY - r * 0.4f,
                cx + r * 0.6f, cy + wingOffY + r * 0.4f, birdWingPaint);

        // Body
        canvas.drawCircle(cx, cy, r, birdBodyPaint);

        // Chest highlight
        Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlight.setColor(Color.rgb(255, 240, 130));
        canvas.drawCircle(cx - r * 0.15f, cy + r * 0.1f, r * 0.5f, highlight);

        // Eye white
        canvas.drawCircle(cx + r * 0.4f, cy - r * 0.2f, r * 0.32f, birdEyePaint);
        // Pupil
        canvas.drawCircle(cx + r * 0.5f, cy - r * 0.18f, r * 0.16f, birdPupilPaint);

        // Beak
        Path beak = new Path();
        beak.moveTo(cx + r * 0.75f, cy + r * 0.05f);
        beak.lineTo(cx + r * 1.3f,  cy - r * 0.05f);
        beak.lineTo(cx + r * 0.75f, cy + r * 0.25f);
        beak.close();
        canvas.drawPath(beak, birdBeakPaint);

        canvas.restore();
    }

    private void drawHUD(Canvas canvas) {
        float textSize = W * 0.14f;
        scoreMainPaint.setTextSize(textSize);
        scoreShadowPaint.setTextSize(textSize);

        if (state == State.PLAYING || state == State.WAITING) {
            // Score shadow then score
            scoreShadowPaint.setAlpha(180);
            canvas.drawText(String.valueOf(score), W / 2f + 4, H * 0.14f + 4, scoreShadowPaint);
            canvas.drawText(String.valueOf(score), W / 2f, H * 0.14f, scoreMainPaint);
        }

        if (state == State.WAITING) {
            // Title card
            drawCard(canvas, W / 2f, H * 0.30f, W * 0.75f, H * 0.18f);
            uiPaint.setTextSize(W * 0.11f);
            uiShadowPaint.setTextSize(W * 0.11f);
            canvas.drawText("FLAPPY BIRD", W / 2f + 3, H * 0.28f + 3, uiShadowPaint);
            canvas.drawText("FLAPPY BIRD", W / 2f, H * 0.28f, uiPaint);

            subtitlePaint.setTextSize(W * 0.055f);
            canvas.drawText("Tap anywhere to start!", W / 2f, H * 0.34f, subtitlePaint);
        }

        if (state == State.DEAD) {
            // Game over card
            drawCard(canvas, W / 2f, H * 0.38f, W * 0.80f, H * 0.36f);

            uiPaint.setTextSize(W * 0.12f);
            uiShadowPaint.setTextSize(W * 0.12f);
            canvas.drawText("GAME OVER", W / 2f + 3, H * 0.28f + 3, uiShadowPaint);
            canvas.drawText("GAME OVER", W / 2f, H * 0.28f, uiPaint);

            float labelSize = W * 0.055f;
            float valueSize = W * 0.085f;
            uiPaint.setTextSize(labelSize);
            canvas.drawText("SCORE", W / 2f, H * 0.35f, uiPaint);
            uiPaint.setTextSize(valueSize);
            canvas.drawText(String.valueOf(score), W / 2f, H * 0.42f, uiPaint);

            uiPaint.setTextSize(labelSize);
            canvas.drawText("BEST", W / 2f, H * 0.49f, uiPaint);
            uiPaint.setTextSize(valueSize);
            canvas.drawText(String.valueOf(best), W / 2f, H * 0.56f, uiPaint);

            // Tap to retry
            subtitlePaint.setTextSize(W * 0.055f);
            canvas.drawText("Tap to play again", W / 2f, H * 0.65f, subtitlePaint);
        }
    }

    private void drawCard(Canvas canvas, float cx, float cy, float w, float h) {
        Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
        card.setColor(Color.argb(180, 30, 80, 30));
        RectF rect = new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        canvas.drawRoundRect(rect, 30, 30, card);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.argb(200, 255, 255, 255));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(4f);
        canvas.drawRoundRect(rect, 30, 30, border);
    }

    // ── Game Thread ───────────────────────────────────────────────────────────
    class GameThread extends Thread {
        SurfaceHolder holder;
        volatile boolean running = true;

        GameThread(SurfaceHolder holder) { this.holder = holder; }

        @Override
        public void run() {
            long lastTime = System.nanoTime();
            final double NS_PER_TICK = 1_000_000_000.0 / 60.0;
            double delta = 0;

            while (running) {
                long now = System.nanoTime();
                delta += (now - lastTime) / NS_PER_TICK;
                lastTime = now;

                while (delta >= 1) {
                    update();
                    delta--;
                }

                Canvas canvas = holder.lockCanvas();
                if (canvas != null) {
                    try {
                        drawFrame(canvas);
                    } finally {
                        holder.unlockCanvasAndPost(canvas);
                    }
                }

                try { Thread.sleep(8); } catch (InterruptedException ignored) {}
            }
        }
    }
}