package kr.carecabin.demo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Speech bubble and six user-supplied Soomgil sprites. Does not control the vehicle. */
final class SoomgilCharacterView extends LinearLayout {
    enum Emotion { TALKING, POOR, SEVERE, CLEARED }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ImageView portrait;
    private final TextView messageView;
    private final Button voiceButton;
    private TextToSpeech tts;
    private boolean voiceEnabled = true;
    private boolean voiceReady;
    private Emotion emotion;
    private String message = "";
    private long animateUntil;
    private boolean secondFrame;
    private int previewStep = -1;

    private final Runnable animate = new Runnable() {
        @Override public void run() {
            if (SystemClock.uptimeMillis() >= animateUntil ||
                    (emotion != Emotion.TALKING && emotion != Emotion.CLEARED)) {
                portrait.setImageResource(emotion == Emotion.CLEARED
                        ? R.drawable.soomgil_character_6 : R.drawable.soomgil_character_1);
                return;
            }
            secondFrame = !secondFrame;
            int frame = emotion == Emotion.TALKING
                    ? (secondFrame ? R.drawable.soomgil_character_2 : R.drawable.soomgil_character_1)
                    : (secondFrame ? R.drawable.soomgil_character_6 : R.drawable.soomgil_character_5);
            portrait.setImageResource(frame);
            handler.postDelayed(this, 320);
        }
    };

    SoomgilCharacterView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(12);
        setPadding(pad, pad, pad, pad);
        setBackground(rounded(Color.rgb(232, 241, 255), 18, false));

        portrait = new ImageView(context);
        portrait.setAdjustViewBounds(true);
        portrait.setScaleType(ImageView.ScaleType.FIT_CENTER);
        portrait.setImageResource(R.drawable.soomgil_character_1);
        portrait.setContentDescription("숨길 마스코트");
        LayoutParams portraitParams = new LayoutParams(dp(98), dp(136));
        portraitParams.setMarginEnd(dp(10));
        addView(portrait, portraitParams);

        LinearLayout bubble = new LinearLayout(context);
        bubble.setOrientation(VERTICAL);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setBackground(rounded(Color.WHITE, 14, true));
        addView(bubble, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(context);
        title.setText("숨길이가 알려드릴게요");
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(61, 118, 210));
        bubble.addView(title);

        messageView = new TextView(context);
        messageView.setTextSize(15);
        messageView.setTextColor(Color.rgb(24, 32, 42));
        messageView.setLineSpacing(dp(2), 1f);
        messageView.setPadding(0, dp(6), 0, dp(4));
        messageView.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        bubble.addView(messageView);

        voiceButton = new Button(context);
        voiceButton.setAllCaps(false);
        voiceButton.setTextSize(11);
        voiceButton.setMinHeight(0);
        voiceButton.setMinimumHeight(0);
        voiceButton.setPadding(dp(4), 0, dp(4), 0);
        voiceButton.setTextColor(Color.rgb(61, 118, 210));
        voiceButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.rgb(234, 242, 255)));
        bubble.addView(voiceButton, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(34)));
        updateVoiceButton();
        voiceButton.setOnClickListener(v -> {
            voiceEnabled = !voiceEnabled;
            updateVoiceButton();
            if (!voiceEnabled && tts != null) tts.stop();
            else if (voiceEnabled && voiceReady) speak(message);
        });

        // Demo preview works on an ordinary Android emulator without Pleos HVAC.
        if (BuildConfig.DEBUG) {
            Button preview = new Button(context);
            preview.setText("캐릭터 시연 · 다음 상태");
            preview.setAllCaps(false);
            preview.setTextSize(11);
            preview.setMinHeight(0);
            preview.setMinimumHeight(0);
            preview.setPadding(dp(4), 0, dp(4), 0);
            preview.setTextColor(Color.rgb(88, 100, 117));
            preview.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Color.rgb(242, 245, 248)));
            bubble.addView(preview, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(34)));
            preview.setOnClickListener(v -> {
                previewStep = (previewStep + 1) % 4;
                switch (previewStep) {
                    case 0 -> show(Emotion.TALKING, "안녕하세요! 숨길이가 오늘도 함께할게요!");
                    case 1 -> show(Emotion.POOR, "앗, 공기가 안 좋아요! 주변 환경을 확인해 주세요.");
                    case 2 -> show(Emotion.SEVERE, "주의해 주세요! 지금은 대기 오염이 매우 심해요.");
                    default -> show(Emotion.CLEARED, "오염 구간을 지났어요! 이제 한결 안심이에요.");
                }
            });
        }

        try {
            tts = new TextToSpeech(context.getApplicationContext(), status -> {
                if (status != TextToSpeech.SUCCESS || tts == null) return;
                int languageResult = tts.setLanguage(Locale.KOREAN);
                voiceReady = languageResult != TextToSpeech.LANG_MISSING_DATA
                        && languageResult != TextToSpeech.LANG_NOT_SUPPORTED;
                if (voiceReady) {
                    tts.setSpeechRate(1.0f);
                    if (voiceEnabled && !message.isEmpty()) speak(message);
                }
            });
        } catch (RuntimeException unsupportedTts) {
            voiceReady = false;
        }
    }

    void show(Emotion nextEmotion, String nextMessage) {
        if (emotion == nextEmotion && message.equals(nextMessage)) return;
        emotion = nextEmotion;
        message = nextMessage;
        messageView.setText(nextMessage);
        handler.removeCallbacks(animate);
        secondFrame = false;
        if (emotion == Emotion.POOR) {
            portrait.setImageResource(R.drawable.soomgil_character_3);
        } else if (emotion == Emotion.SEVERE) {
            portrait.setImageResource(R.drawable.soomgil_character_4);
        } else {
            portrait.setImageResource(emotion == Emotion.CLEARED
                    ? R.drawable.soomgil_character_5 : R.drawable.soomgil_character_1);
            animateUntil = SystemClock.uptimeMillis() + Math.min(7000,
                    Math.max(2600, nextMessage.length() * 145L));
            handler.postDelayed(animate, 320);
        }
        if (voiceEnabled && voiceReady) speak(nextMessage);
    }

    private void speak(String words) {
        if (tts == null || words.isEmpty()) return;
        tts.speak(words, TextToSpeech.QUEUE_FLUSH, null, "soomgil-character-guide");
    }

    private void updateVoiceButton() {
        voiceButton.setText(voiceEnabled ? "음성 안내 켜짐 · 누르면 끄기" : "음성 안내 꺼짐 · 누르면 켜기");
    }

    void shutdown() {
        handler.removeCallbacks(animate);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(animate);
        super.onDetachedFromWindow();
    }

    private GradientDrawable rounded(int fill, int radius, boolean border) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(radius));
        if (border) background.setStroke(dp(1), Color.rgb(213, 226, 245));
        return background;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
