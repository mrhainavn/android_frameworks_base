package com.android.keyguard.clock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.internal.util.du.ThemesUtils;

import com.android.systemui.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

public class CustomTextClock extends TextView {

    private String mDescFormat;
    private String[] mHours;
    private final String[] mMinutes;
    private final Resources mResources;
    private final Calendar mTime = Calendar.getInstance(TimeZone.getDefault());
    private TimeZone mTimeZone;

    private boolean h24;
    private int mAccentColor;
    private int hours;

    private SettingsObserver mSettingsObserver;

    private final BroadcastReceiver mTimeZoneChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                onTimeZoneChanged(TimeZone.getTimeZone(tz));
                onTimeChanged();
            }
        }
    };

    public CustomTextClock(Context context) {
        this(context, null);
    }

    public CustomTextClock(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public CustomTextClock(Context context, AttributeSet attributeSet, int defStyleAttr) {
        super(context, attributeSet, defStyleAttr);

        mDescFormat = ((SimpleDateFormat) DateFormat.getTimeFormat(context)).toLocalizedPattern();
        mResources = context.getResources();
        h24 = DateFormat.is24HourFormat(getContext());
        if (!h24) mHours = mResources.getStringArray(R.array.type_clock_hours_12);
            else mHours = mResources.getStringArray(R.array.type_clock_hours_24);
        mMinutes = mResources.getStringArray(R.array.type_clock_minutes);
        mAccentColor = mResources.getColor(R.color.accent_device_default_light);
    }

    public void onTimeChanged() {
        h24 = DateFormat.is24HourFormat(getContext());
        mTime.setTimeInMillis(System.currentTimeMillis());
        setContentDescription(DateFormat.format(mDescFormat, mTime));
        if (!h24) {
             mHours = mResources.getStringArray(R.array.type_clock_hours_12);
             hours = mTime.get(Calendar.HOUR) % 12;
        } else {
             mHours = mResources.getStringArray(R.array.type_clock_hours_24);
             hours = mTime.get(Calendar.HOUR_OF_DAY);
        }
        final int minutes = mTime.get(Calendar.MINUTE) % 60;
        SpannedString rawFormat = (SpannedString) mResources.getQuantityText(R.plurals.type_clock_header, hours);
        Annotation[] annotationArr = (Annotation[]) rawFormat.getSpans(0, rawFormat.length(), Annotation.class);
        SpannableString colored = new SpannableString(rawFormat);
        for (Annotation annotation : annotationArr) {
            if ("color".equals(annotation.getValue())) {
                colored.setSpan(new ForegroundColorSpan(mAccentColor),
                        colored.getSpanStart(annotation),
                        colored.getSpanEnd(annotation),
                        Spanned.SPAN_POINT_POINT);
            }
        }
        setText(TextUtils.expandTemplate(colored, new CharSequence[]{mHours[hours], mMinutes[minutes]}));
    }

    public void onTimeZoneChanged(TimeZone timeZone) {
        mTimeZone = timeZone;
        mTime.setTimeZone(timeZone);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Calendar calendar = mTime;
        TimeZone timeZone = mTimeZone;
        if (timeZone == null) {
            timeZone = TimeZone.getDefault();
        }
        calendar.setTimeZone(timeZone);
        onTimeChanged();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(mTimeZoneChangedReceiver, filter);

        if (mSettingsObserver == null) {
            mSettingsObserver = new SettingsObserver(new Handler());
        }
        mSettingsObserver.observe();
        updateClockSize();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(mTimeZoneChangedReceiver);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        refreshLockFont();
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
               	Settings.System.CUSTOM_TEXT_CLOCK_FONTS, 32);
    }

    private void refreshLockFont() {
        String[][] fontsArray = ThemesUtils.FONTS_STYLE;
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockFont = isPrimary ? getLockClockFont() : 29;
        int fontType = Typeface.NORMAL;
        switch (fontsArray[lockClockFont][1]) {
            case "BOLD":
                fontType = Typeface.BOLD;
                break;
            case "ITALIC":
                fontType = Typeface.ITALIC;
                break;
            case "BOLD_ITALIC":
                fontType = Typeface.BOLD_ITALIC;
                break;
            default:
                break;
        }
        setTypeface(Typeface.create(fontsArray[lockClockFont][0], fontType));
    }

    public void updateClockSize() {
        int clockSize = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LOCKCLOCK_FONT_SIZE, 54, UserHandle.USER_CURRENT);
        setTextSize(clockSize);
        onTimeChanged();
    }

    protected class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.CUSTOM_TEXT_CLOCK_FONT_SIZE),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
	    updateClockSize();
        }
    }
}
