package com.robert.mbhack.battles;

import android.content.Context;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

import dalvik.system.DexFile;

public class BattleRegistry {

    private static final String BATTLES_PACKAGE_PREFIX = "com.robert.mbhack.battles.";

    public static List<BattleConfig> discoverBattles(Context context) {
        List<BattleConfig> found = new ArrayList<>();
        try {
            String sourceDir = context.getApplicationInfo().sourceDir;
            DexFile dexFile = new DexFile(sourceDir);
            Enumeration<String> entries = dexFile.entries();
            ClassLoader classLoader = context.getClassLoader();

            while (entries.hasMoreElements()) {
                String className = entries.nextElement();
                if (!className.startsWith(BATTLES_PACKAGE_PREFIX)) {
                    continue;
                }
                if (className.contains("$")) {
                    continue;
                }
                if (className.equals(BattleConfig.class.getName()) || className.equals(BattleRegistry.class.getName())) {
                    continue;
                }

                Class<?> clazz = Class.forName(className, false, classLoader);
                if (BattleConfig.class.isAssignableFrom(clazz)
                        && !Modifier.isAbstract(clazz.getModifiers())
                        && !Modifier.isInterface(clazz.getModifiers())) {
                    BattleConfig instance = (BattleConfig) clazz.getDeclaredConstructor().newInstance();
                    found.add(instance);
                }
            }
        } catch (Exception ignored) {
        }

        Collections.sort(found, new Comparator<BattleConfig>() {
            @Override
            public int compare(BattleConfig a, BattleConfig b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        return found;
    }
}
