package com.aefyr.sai.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import androidx.preference.PreferenceManager;

import java.util.Locale;

public final class VsiLocaleHelper {

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_DE = "de";
    public static final String MODE_EN = "en";

    private static final String KEY = "vsi_language";

    private VsiLocaleHelper() {}

    public static Context wrap(Context context) {
        Locale locale = resolveLocale(context);
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            config.setLocales(new android.os.LocaleList(locale));

        return context.createConfigurationContext(config);
    }

    public static Locale resolveLocale(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = prefs.getString(KEY, MODE_SYSTEM);

        if (MODE_DE.equals(mode))
            return Locale.GERMAN;
        if (MODE_EN.equals(mode))
            return Locale.ENGLISH;

        Locale systemLocale;
        Configuration configuration = context.getResources().getConfiguration();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            systemLocale = configuration.getLocales().isEmpty()
                    ? Locale.getDefault()
                    : configuration.getLocales().get(0);
        else
            systemLocale = configuration.locale;

        String language = systemLocale.getLanguage();
        String country = systemLocale.getCountry();

        boolean germanRegion = "de".equalsIgnoreCase(language)
                && ("DE".equalsIgnoreCase(country)
                || "AT".equalsIgnoreCase(country)
                || "CH".equalsIgnoreCase(country));

        return germanRegion ? Locale.GERMAN : Locale.ENGLISH;
    }
}
