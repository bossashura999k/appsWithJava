package com.ashura.flappybird;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FlappyBirdView extends SurfaceView implements SurfaceHolder.Callback {

    private enum State { WAITING, PLAYING, DEAD }
    private State state = State.WAITING;
    private GameThread gameThread;
    private int W, H;

    private float birdX, birdY, velY;
    private static final float GRAVITY = 0.55f, FLAP_FORCE = -13f, MAX_FALL = 14f;
    private float birdAngle;
    private int wingFrame = 0, wingTick = 0;

    private static final float PIPE_SPEED = 5f, PIPE_WIDTH = 90f, PIPE_GAP = 320f;
    private static final int PIPE_SPACING = 400;
    private List<float[]> pipes = new ArrayList<>();
    private Random rand = new Random();

    private float groundY, groundScroll = 0;
    private static final float GROUND_H = 80f;

    private static final int CLOUD_COUNT = 4;
    private float[] cloudX = new float[CLOUD_COUNT];
    private float[] cloudY = new float[CLOUD_COUNT];
    private float[] cloudW = new float[CLOUD_COUNT];

    private int score = 0, best = 0;
    private SharedPreferences prefs;

    private MediaPlayer mediaPlayer;
    private int[] songList;
    private int currentSongIndex = 0;

    private Paint groundPaint, groundLinePaint;
    private Paint pipeBodyPaint, pipeLipPaint, pipeHighlightPaint, pipeShadowPaint;
    private Paint birdBodyPaint, birdWingPaint, birdEyePaint, birdPupilPaint, birdBeakPaint;
    private Paint cloudPaint;
    private Paint scoreMainPaint, scoreShadowPaint, uiPaint, uiShadowPaint, subtitlePaint;
    private Paint flashPaint;
    private float flashAlpha = 0f;

    private boolean godMode = false;
    private int godModeFlashTick = 0;

    private boolean menuOpen = false;
    private static final String[] SONG_NAMES = {
            "Song 1","Song 2","Song 3","Song 4","Song 5",
            "Song 6","Song 7","Song 8","Song 9"
    };
    private float musicBtnX, musicBtnY, musicBtnR;
    private final RectF restartBtnRect = new RectF(); // set in surfaceChanged, read in onTouchEvent

    public FlappyBirdView(Context context, AttributeSet attrs) { super(context, attrs); init(context); }
    public FlappyBirdView(Context context) { super(context); init(context); }

    private void init(Context context) {
        getHolder().addCallback(this);
        setFocusable(true);
        setClickable(true);
        prefs = context.getSharedPreferences("flappy_prefs", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        initPaints();
    }

    private void initPaints() {
        groundPaint = new Paint(); groundPaint.setColor(Color.rgb(222, 184, 100));
        groundLinePaint = new Paint(); groundLinePaint.setColor(Color.rgb(180, 140, 60)); groundLinePaint.setStrokeWidth(3f);

        pipeBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG); pipeBodyPaint.setColor(Color.rgb(80, 200, 80));
        pipeLipPaint = new Paint(Paint.ANTI_ALIAS_FLAG); pipeLipPaint.setColor(Color.rgb(60, 170, 60));
        pipeHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG); pipeHighlightPaint.setColor(Color.rgb(130, 230, 130)); pipeHighlightPaint.setStrokeWidth(6f); pipeHighlightPaint.setStyle(Paint.Style.STROKE);
        pipeShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG); pipeShadowPaint.setColor(Color.rgb(30, 120, 30)); pipeShadowPaint.setStrokeWidth(6f); pipeShadowPaint.setStyle(Paint.Style.STROKE);

        birdBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG); birdBodyPaint.setColor(Color.rgb(255, 220, 50));
        birdWingPaint = new Paint(Paint.ANTI_ALIAS_FLAG); birdWingPaint.setColor(Color.rgb(230, 180, 30));
        birdEyePaint = new Paint(Paint.ANTI_ALIAS_FLAG); birdEyePaint.setColor(Color.WHITE);
        birdPupilPaint = new Paint(Paint.ANTI_ALIAS_FLAG); birdPupilPaint.setColor(Color.BLACK);
        birdBeakPaint = new Paint(Paint.ANTI_ALIAS_FLAG); birdBeakPaint.setColor(Color.rgb(255, 140, 0));

        cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG); cloudPaint.setColor(Color.argb(220, 255, 255, 255));

        scoreMainPaint = new Paint(Paint.ANTI_ALIAS_FLAG); scoreMainPaint.setColor(Color.WHITE); scoreMainPaint.setTypeface(Typeface.DEFAULT_BOLD); scoreMainPaint.setTextAlign(Paint.Align.CENTER);
        scoreShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG); scoreShadowPaint.setColor(Color.rgb(80, 80, 80)); scoreShadowPaint.setTypeface(Typeface.DEFAULT_BOLD); scoreShadowPaint.setTextAlign(Paint.Align.CENTER);
        uiPaint = new Paint(Paint.ANTI_ALIAS_FLAG); uiPaint.setColor(Color.WHITE); uiPaint.setTypeface(Typeface.DEFAULT_BOLD); uiPaint.setTextAlign(Paint.Align.CENTER);
        uiShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG); uiShadowPaint.setColor(Color.rgb(60, 60, 60)); uiShadowPaint.setTypeface(Typeface.DEFAULT_BOLD); uiShadowPaint.setTextAlign(Paint.Align.CENTER);
        subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG); subtitlePaint.setColor(Color.rgb(255, 240, 150)); subtitlePaint.setTypeface(Typeface.DEFAULT_BOLD); subtitlePaint.setTextAlign(Paint.Align.CENTER);
        flashPaint = new Paint(); flashPaint.setColor(Color.WHITE);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        new Handler(Looper.getMainLooper()).post(this::startMusic);
    }

    private void startMusic() {
        if (mediaPlayer != null) return;
        int[] allSongs = {
                R.raw.background_music, R.raw.song_bigx_largest, R.raw.song_dave_sprinter,
                R.raw.song_euphoria_pt2, R.raw.song_kendrick_humble, R.raw.song_melly_young_black,
                R.raw.song_six_seven, R.raw.song_travis_highest, R.raw.song_type_shit
        };
        java.util.List<Integer> shuffled = new java.util.ArrayList<>();
        for (int id : allSongs) shuffled.add(id);
        java.util.Collections.shuffle(shuffled);
        songList = new int[shuffled.size()];
        for (int i = 0; i < shuffled.size(); i++) songList[i] = shuffled.get(i);
        currentSongIndex = 0;
        playSong(currentSongIndex);
    }

    private void playSong(int index) {
        try {
            if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            android.content.res.AssetFileDescriptor afd =
                    getContext().getResources().openRawResourceFd(songList[index]);
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mediaPlayer.setLooping(false);
            mediaPlayer.setVolume(0.5f, 0.5f);
            mediaPlayer.setOnPreparedListener(mp -> mp.start());
            mediaPlayer.setOnCompletionListener(mp -> {
                currentSongIndex = (currentSongIndex + 1) % songList.length;
                new Handler(Looper.getMainLooper()).post(() -> playSong(currentSongIndex));
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        W = width; H = height;
        groundY = H - GROUND_H;
        musicBtnR = W * 0.07f;
        musicBtnX = musicBtnR + 20;
        musicBtnY = musicBtnR + 20;
        // Set restart button rect here so touch detection is always accurate
        float rBtnW = W * 0.52f, rBtnH = H * 0.072f;
        float rBtnX = (W - rBtnW) / 2f, rBtnY = H * 0.58f;
        restartBtnRect.set(rBtnX, rBtnY, rBtnX + rBtnW, rBtnY + rBtnH);
        // restartBtnRect set via rBtn* locals below
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

    public void pauseGame() {
        if (gameThread != null) gameThread.running = false;
        if (mediaPlayer != null) try { if (mediaPlayer.isPlaying()) mediaPlayer.pause(); } catch (Exception ignored) {}
    }

    public void resumeGame() {
        if (gameThread != null && !gameThread.isAlive()) { gameThread = new GameThread(getHolder()); gameThread.start(); }
        if (mediaPlayer != null) try { if (!mediaPlayer.isPlaying()) mediaPlayer.start(); } catch (Exception ignored) {}
    }

    public void cleanup() {
        pauseGame();
        if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.release(); mediaPlayer = null; }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx = event.getX(), ty = event.getY();

        // Music button
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float dx = tx - musicBtnX, dy = ty - musicBtnY;
            if (dx * dx + dy * dy <= musicBtnR * musicBtnR * 2.5f) {
                menuOpen = !menuOpen;
                return true;
            }
            if (menuOpen) {
                float menuW = W * 0.72f, rowH = H * 0.072f;
                float menuX = (W - menuW) / 2f, menuTop = H * 0.18f;
                if (tx >= menuX && tx <= menuX + menuW) {
                    int tapped = (int)((ty - menuTop) / rowH);
                    if (tapped >= 0 && tapped < SONG_NAMES.length) {
                        currentSongIndex = tapped;
                        new Handler(Looper.getMainLooper()).post(() -> playSong(currentSongIndex));
                        menuOpen = false;
                        return true;
                    }
                }
                menuOpen = false;
                return true;
            }

            // Restart button (only in DEAD state)
            if (state == State.DEAD && restartBtnRect.contains(tx, ty)) {
                resetGame();
                state = State.WAITING;
                return true;
            }
        }

        // God mode: 3 fingers
        if (event.getPointerCount() == 3 && event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            godMode = !godMode;
            godModeFlashTick = 90;
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            switch (state) {
                case WAITING: state = State.PLAYING; flap(); break;
                case PLAYING: flap(); break;
                case DEAD: break; // handled above by restart button only
            }
        }
        return true;
    }

    private void flap() { velY = FLAP_FORCE; flashAlpha = 0f; }

    private void resetGame() {
        birdX = W * 0.28f; birdY = H * 0.45f; velY = 0f; birdAngle = 0f; score = 0;
        pipes.clear(); passedPipes.clear();
        float startX = W + 100;
        for (int i = 0; i < 3; i++) spawnPipe(startX + i * PIPE_SPACING);
        groundScroll = 0; flashAlpha = 0f;
    }

    private void initClouds() {
        for (int i = 0; i < CLOUD_COUNT; i++) {
            cloudX[i] = rand.nextFloat() * W;
            cloudY[i] = 60 + rand.nextFloat() * (H * 0.35f);
            cloudW[i] = 120 + rand.nextFloat() * 160;
        }
    }

    private void spawnPipe(float x) {
        float minGapTop = H * 0.12f, maxGapTop = groundY - PIPE_GAP - H * 0.12f;
        pipes.add(new float[]{x, minGapTop + rand.nextFloat() * (maxGapTop - minGapTop)});
    }

    private void update() {
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
            if (state == State.WAITING) { birdY = H * 0.45f + (float)(Math.sin(System.currentTimeMillis() / 300.0) * 12); birdAngle = 0f; }
            return;
        }
        velY = Math.min(velY + GRAVITY, MAX_FALL);
        birdY += velY;
        float targetAngle = velY < 0 ? -25f : Math.min(velY * 5f, 80f);
        birdAngle += (targetAngle - birdAngle) * 0.2f;
        wingTick++; if (wingTick >= 5) { wingTick = 0; wingFrame = (wingFrame + 1) % 3; }
        if (flashAlpha > 0) flashAlpha = Math.max(0, flashAlpha - 15);

        List<float[]> toRemove = new ArrayList<>();
        for (float[] pipe : pipes) {
            pipe[0] -= PIPE_SPEED;
            if (!isPipePassed(pipe) && pipe[0] + PIPE_WIDTH < birdX) {
                markPipePassed(pipe); score++;
                if (score > best) { best = score; prefs.edit().putInt("best", best).apply(); }
            }
            if (pipe[0] + PIPE_WIDTH < -10) toRemove.add(pipe);
        }
        pipes.removeAll(toRemove);
        if (pipes.isEmpty() || pipes.get(pipes.size()-1)[0] < W - PIPE_SPACING + PIPE_SPEED) spawnPipe(W + 50);

        float birdR = getBirdRadius();
        if (!godMode && (birdY + birdR >= groundY || birdY - birdR <= 0)) { die(); return; }
        if (!godMode) {
            for (float[] pipe : pipes) {
                float px = pipe[0], gapTop = pipe[1];
                if ((birdX + birdR - 4) > px && (birdX - birdR + 4) < px + PIPE_WIDTH) {
                    if ((birdY - birdR + 4) < gapTop || (birdY + birdR - 4) > gapTop + PIPE_GAP) { die(); return; }
                }
            }
        }
    }

    private java.util.Set<float[]> passedPipes = new java.util.HashSet<>();
    private boolean isPipePassed(float[] pipe) { return passedPipes.contains(pipe); }
    private void markPipePassed(float[] pipe) { passedPipes.add(pipe); }
    private void die() { state = State.DEAD; flashAlpha = 255f; velY = FLAP_FORCE * 0.5f; }
    private float getBirdRadius() { return W * 0.055f; }

    private void drawFrame(Canvas canvas) {
        Paint skyTop = new Paint();
        skyTop.setShader(new LinearGradient(0, 0, 0, groundY, Color.rgb(80, 180, 255), Color.rgb(160, 220, 255), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, W, groundY, skyTop);
        for (int i = 0; i < CLOUD_COUNT; i++) drawCloud(canvas, cloudX[i], cloudY[i], cloudW[i]);
        for (float[] pipe : pipes) drawPipe(canvas, pipe[0], pipe[1]);
        canvas.drawRect(0, groundY, W, H, groundPaint);
        canvas.drawLine(0, groundY + 18, W, groundY + 18, groundLinePaint);
        Paint scrollLine = new Paint(); scrollLine.setColor(Color.rgb(200, 160, 80)); scrollLine.setStrokeWidth(2f);
        for (float gx = -groundScroll; gx < W; gx += 60) canvas.drawLine(gx, groundY, gx + 30, groundY + GROUND_H, scrollLine);
        drawBird(canvas);
        drawHUD(canvas);
        drawMusicBtn(canvas);
        if (flashAlpha > 0) { flashPaint.setAlpha((int) flashAlpha); canvas.drawRect(0, 0, W, H, flashPaint); }
    }

    private void drawCloud(Canvas canvas, float x, float y, float w) {
        float h = w * 0.45f;
        canvas.drawOval(x, y, x+w, y+h, cloudPaint);
        canvas.drawOval(x+w*0.15f, y-h*0.4f, x+w*0.65f, y+h*0.5f, cloudPaint);
        canvas.drawOval(x+w*0.45f, y-h*0.2f, x+w*0.9f, y+h*0.6f, cloudPaint);
    }

    private void drawPipe(Canvas canvas, float x, float gapTop) {
        float lipW = PIPE_WIDTH+16f, lipH = 28f, lipX = x-8f;
        canvas.drawRect(x, 0, x+PIPE_WIDTH, gapTop-lipH, pipeBodyPaint);
        canvas.drawRect(lipX, gapTop-lipH, lipX+lipW, gapTop, pipeLipPaint);
        float gapBottom = gapTop + PIPE_GAP;
        canvas.drawRect(x, gapBottom+lipH, x+PIPE_WIDTH, groundY, pipeBodyPaint);
        canvas.drawRect(lipX, gapBottom, lipX+lipW, gapBottom+lipH, pipeLipPaint);
        canvas.drawLine(x+10, 0, x+10, gapTop-lipH, pipeHighlightPaint);
        canvas.drawLine(x+PIPE_WIDTH-10, 0, x+PIPE_WIDTH-10, gapTop-lipH, pipeShadowPaint);
        canvas.drawLine(x+10, gapBottom+lipH, x+10, groundY, pipeHighlightPaint);
        canvas.drawLine(x+PIPE_WIDTH-10, gapBottom+lipH, x+PIPE_WIDTH-10, groundY, pipeShadowPaint);
    }

    private void drawBird(Canvas canvas) {
        float r = getBirdRadius(), cx = birdX, cy = birdY;
        canvas.save(); canvas.rotate(birdAngle, cx, cy);
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG); shadow.setColor(Color.argb(40,0,0,0));
        canvas.drawOval(cx-r*0.8f, cy+r*0.8f, cx+r*0.8f, cy+r*1.1f, shadow);
        float wingOffY = wingFrame==0 ? -r*0.4f : wingFrame==1 ? 0 : r*0.3f;
        canvas.drawOval(cx-r*0.2f, cy+wingOffY-r*0.4f, cx+r*0.6f, cy+wingOffY+r*0.4f, birdWingPaint);
        canvas.drawCircle(cx, cy, r, birdBodyPaint);
        Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG); hl.setColor(Color.rgb(255,240,130));
        canvas.drawCircle(cx-r*0.15f, cy+r*0.1f, r*0.5f, hl);
        canvas.drawCircle(cx+r*0.4f, cy-r*0.2f, r*0.32f, birdEyePaint);
        canvas.drawCircle(cx+r*0.5f, cy-r*0.18f, r*0.16f, birdPupilPaint);
        Path beak = new Path();
        beak.moveTo(cx+r*0.75f, cy+r*0.05f); beak.lineTo(cx+r*1.3f, cy-r*0.05f); beak.lineTo(cx+r*0.75f, cy+r*0.25f); beak.close();
        canvas.drawPath(beak, birdBeakPaint);
        canvas.restore();
    }

    private void drawHUD(Canvas canvas) {
        scoreMainPaint.setTextSize(W * 0.14f);
        scoreShadowPaint.setTextSize(W * 0.14f);
        if (state == State.PLAYING || state == State.WAITING) {
            scoreShadowPaint.setAlpha(180);
            canvas.drawText(String.valueOf(score), W/2f+4, H*0.14f+4, scoreShadowPaint);
            canvas.drawText(String.valueOf(score), W/2f, H*0.14f, scoreMainPaint);
        }
        if (state == State.WAITING) {
            drawCard(canvas, W/2f, H*0.30f, W*0.75f, H*0.18f);
            uiPaint.setTextSize(W*0.11f); uiShadowPaint.setTextSize(W*0.11f);
            canvas.drawText("MR IZZI", W/2f+3, H*0.28f+3, uiShadowPaint);
            canvas.drawText("MR IZZI", W/2f, H*0.28f, uiPaint);
            subtitlePaint.setTextSize(W*0.055f);
            canvas.drawText("Tap anywhere to start!", W/2f, H*0.34f, subtitlePaint);
        }
        if (godMode || godModeFlashTick > 0) {
            if (godModeFlashTick > 0) godModeFlashTick--;
            Paint devPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            devPaint.setTextSize(W*0.035f); devPaint.setTypeface(Typeface.MONOSPACE); devPaint.setTextAlign(Paint.Align.RIGHT);
            devPaint.setColor(godMode ? Color.rgb(0,255,120) : Color.argb((int)(255*((float)godModeFlashTick/90)), 0, 255, 120));
            canvas.drawText(godMode ? "◆ GOD MODE ON" : "◆ GOD MODE OFF", W-18, 52, devPaint);
        }
        if (state == State.DEAD) {
            // Dark overlay
            Paint overlay = new Paint(); overlay.setColor(Color.argb(140, 0, 0, 0));
            canvas.drawRect(0, 0, W, H, overlay);

            // Game over card
            drawCard(canvas, W/2f, H*0.35f, W*0.82f, H*0.42f);

            uiPaint.setTextSize(W*0.12f); uiShadowPaint.setTextSize(W*0.12f);
            canvas.drawText("GAME OVER", W/2f+3, H*0.22f+3, uiShadowPaint);
            canvas.drawText("GAME OVER", W/2f, H*0.22f, uiPaint);

            float labelSize = W*0.052f, valueSize = W*0.085f;
            uiPaint.setTextSize(labelSize);
            canvas.drawText("SCORE", W/2f, H*0.30f, uiPaint);
            uiPaint.setTextSize(valueSize);
            canvas.drawText(String.valueOf(score), W/2f, H*0.37f, uiPaint);
            uiPaint.setTextSize(labelSize);
            canvas.drawText("BEST", W/2f, H*0.44f, uiPaint);
            uiPaint.setTextSize(valueSize);
            canvas.drawText(String.valueOf(best), W/2f, H*0.51f, uiPaint);

            // ── Restart button ────────────────────────────────────────────────
            // Use restartBtnRect set in surfaceChanged — same values
            float btnX = restartBtnRect.left, btnY = restartBtnRect.top;
            float btnW = restartBtnRect.width(), btnH = restartBtnRect.height();

            // Button shadow
            Paint btnShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnShadow.setColor(Color.argb(120, 0, 0, 0));
            canvas.drawRoundRect(new RectF(btnX+4, btnY+6, btnX+btnW+4, btnY+btnH+6), 36, 36, btnShadow);

            // Button fill — green
            Paint btnFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnFill.setColor(Color.rgb(60, 200, 100));
            canvas.drawRoundRect(restartBtnRect, 36, 36, btnFill);

            // Button highlight top edge
            Paint btnHL = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnHL.setColor(Color.argb(80, 255, 255, 255));
            canvas.drawRoundRect(new RectF(btnX+4, btnY+2, btnX+btnW-4, btnY+btnH/2), 36, 36, btnHL);

            // Button border
            Paint btnBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnBorder.setColor(Color.rgb(30, 160, 70));
            btnBorder.setStyle(Paint.Style.STROKE);
            btnBorder.setStrokeWidth(3f);
            canvas.drawRoundRect(restartBtnRect, 36, 36, btnBorder);

            // Button text
            Paint btnText = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnText.setColor(Color.WHITE);
            btnText.setTypeface(Typeface.DEFAULT_BOLD);
            btnText.setTextSize(W * 0.058f);
            btnText.setTextAlign(Paint.Align.CENTER);
            btnText.setShadowLayer(4f, 0, 2f, Color.argb(120, 0, 80, 30));
            canvas.drawText("▶  PLAY AGAIN", W/2f, btnY + btnH*0.65f, btnText);
        }
    }

    private void drawMusicBtn(Canvas canvas) {
        Paint btn = new Paint(Paint.ANTI_ALIAS_FLAG); btn.setColor(Color.argb(180, 20, 20, 20));
        canvas.drawCircle(musicBtnX, musicBtnY, musicBtnR, btn);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(menuOpen ? Color.rgb(255, 220, 50) : Color.WHITE);
        border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(3f);
        canvas.drawCircle(musicBtnX, musicBtnY, musicBtnR, border);
        Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);
        note.setColor(menuOpen ? Color.rgb(255, 220, 50) : Color.WHITE);
        note.setTextSize(musicBtnR * 1.1f); note.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("\u266B", musicBtnX, musicBtnY + musicBtnR * 0.38f, note);
        if (!menuOpen) return;

        Paint backdrop = new Paint(); backdrop.setColor(Color.argb(160, 0, 0, 0));
        canvas.drawRect(0, 0, W, H, backdrop);
        float menuW = W*0.72f, rowH = H*0.072f, menuH = rowH*SONG_NAMES.length+60;
        float menuX = (W-menuW)/2f, menuTop = H*0.18f;
        Paint card = new Paint(Paint.ANTI_ALIAS_FLAG); card.setColor(Color.argb(230, 15, 15, 30));
        canvas.drawRoundRect(new RectF(menuX, menuTop-50, menuX+menuW, menuTop+menuH), 24, 24, card);
        Paint cardBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBorder.setColor(Color.argb(200, 255, 220, 50)); cardBorder.setStyle(Paint.Style.STROKE); cardBorder.setStrokeWidth(2.5f);
        canvas.drawRoundRect(new RectF(menuX, menuTop-50, menuX+menuW, menuTop+menuH), 24, 24, cardBorder);
        Paint titleP = new Paint(Paint.ANTI_ALIAS_FLAG);
        titleP.setColor(Color.rgb(255,220,50)); titleP.setTypeface(Typeface.DEFAULT_BOLD);
        titleP.setTextAlign(Paint.Align.CENTER); titleP.setTextSize(W*0.055f);
        canvas.drawText("SELECT SONG", W/2f, menuTop-14, titleP);
        Paint rowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint rowTextP = new Paint(Paint.ANTI_ALIAS_FLAG);
        rowTextP.setTypeface(Typeface.DEFAULT_BOLD); rowTextP.setTextAlign(Paint.Align.LEFT); rowTextP.setTextSize(W*0.048f);
        for (int i = 0; i < SONG_NAMES.length; i++) {
            float rowY = menuTop + i*rowH;
            boolean active = (i == currentSongIndex);
            if (active) { rowPaint.setColor(Color.argb(120, 255, 220, 50)); canvas.drawRoundRect(new RectF(menuX+8, rowY, menuX+menuW-8, rowY+rowH-4), 12, 12, rowPaint); }
            Paint sep = new Paint(); sep.setColor(Color.argb(60, 255, 255, 255));
            if (i > 0) canvas.drawLine(menuX+16, rowY, menuX+menuW-16, rowY, sep);
            rowTextP.setColor(active ? Color.rgb(255,220,50) : Color.WHITE);
            canvas.drawText(SONG_NAMES[i], menuX+28, rowY+rowH*0.65f, rowTextP);
            if (active) {
                Paint playing = new Paint(Paint.ANTI_ALIAS_FLAG);
                playing.setColor(Color.rgb(80, 255, 140)); playing.setTextSize(W*0.042f); playing.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText("\u25B6 NOW", menuX+menuW-20, rowY+rowH*0.65f, playing);
            }
        }
    }

    private void drawRestartButton(Canvas canvas) {
        float l = restartBtnRect.left, t = restartBtnRect.top;
        float r = restartBtnRect.right, b = restartBtnRect.bottom;
        float h = restartBtnRect.height();

        // Button shadow
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(Color.argb(80, 0, 0, 0));
        canvas.drawRoundRect(new RectF(l+4, t+6, r+4, b+6), h/2, h/2, shadow);

        // Button fill
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.rgb(255, 210, 30));
        canvas.drawRoundRect(restartBtnRect, h/2, h/2, fill);

        // Top highlight
        Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlight.setColor(Color.argb(80, 255, 255, 255));
        canvas.drawRoundRect(new RectF(l+4, t+3, r-4, t+h*0.5f), h/2, h/2, highlight);

        // Border
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.rgb(200, 150, 0));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(3f);
        canvas.drawRoundRect(restartBtnRect, h/2, h/2, border);

        // Label
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(Color.rgb(40, 20, 0));
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(h * 0.48f);
        canvas.drawText("▶  PLAY AGAIN", l + restartBtnRect.width()/2f, t + h*0.67f, label);
    }

    private void drawCard(Canvas canvas, float cx, float cy, float w, float h) {
        Paint card = new Paint(Paint.ANTI_ALIAS_FLAG); card.setColor(Color.argb(180, 30, 80, 30));
        RectF rect = new RectF(cx-w/2, cy-h/2, cx+w/2, cy+h/2);
        canvas.drawRoundRect(rect, 30, 30, card);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.argb(200, 255, 255, 255)); border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(4f);
        canvas.drawRoundRect(rect, 30, 30, border);
    }

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
                while (delta >= 1) { update(); delta--; }
                Canvas canvas = holder.lockCanvas();
                if (canvas != null) {
                    try { drawFrame(canvas); } finally { holder.unlockCanvasAndPost(canvas); }
                }
                try { Thread.sleep(8); } catch (InterruptedException ignored) {}
            }
        }
    }
}