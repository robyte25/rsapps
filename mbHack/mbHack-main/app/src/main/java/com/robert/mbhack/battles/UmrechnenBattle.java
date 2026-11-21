package com.robert.mbhack.battles;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UmrechnenBattle extends BattleConfig {

    @Override
    public String getName() {
        return "Umrechnen";
    }

    @Override
    public String getUrl() {
        return "https://mathebattle.de/edu_battles/play/135002";
    }

    @Override
    public List<String> parseTasksFromHtml(String html) {
        Document doc = Jsoup.parse(html);
        Element container = doc.selectFirst(".exercise_question");
        if (container == null) container = doc.selectFirst("#aufgabe");

        String sourceText = (container != null)
                ? container.text()
                : (doc.body() != null ? doc.body().text() : html);

        String cleaned = sourceText
                .replace("²", "^2")
                .replaceAll("\\s+", " ")
                .trim();

        // Sucht z.B. "12 ha = ..... ^2" oder "105 dm = ..... mm"
        Matcher m = Pattern.compile("([+-]?\\d+(?:[.,]\\d+)?)\\s*(km\\^2|ha|a|m\\^2|dm\\^2|cm\\^2|mm\\^2|km|m|dm|cm|mm)\\s*=\\s*[.…_\\s]*\\s*(km\\^2|ha|a|m\\^2|dm\\^2|cm\\^2|mm\\^2|km|m|dm|cm|mm)", Pattern.CASE_INSENSITIVE)
                .matcher(cleaned);

        if (m.find()) {
            String valStr = m.group(1).replace(',', '.');
            String fromUnit = normalizeUnit(m.group(2));
            String toUnit = normalizeUnit(m.group(3));
            return Collections.singletonList(valStr + "|" + fromUnit + "|" + toUnit);
        }

        return Collections.emptyList();
    }

    @Override
    public List<String> solveTasks(List<String> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }

        String[] parts = tasks.get(0).split("\\|");
        if (parts.length != 3) {
            return Collections.emptyList();
        }

        double value = Double.parseDouble(parts[0]);
        String fromUnit = parts[1];
        String toUnit = parts[2];

        double result = convertUnit(value, fromUnit, toUnit);
        return Collections.singletonList(formatNumber(result));
    }

    private String normalizeUnit(String unit) {
        return unit.toLowerCase(Locale.ROOT).trim();
    }

    private double convertUnit(double value, String from, String to) {
        Double fromFactor = getFactorInBase(from);
        Double toFactor = getFactorInBase(to);

        if (fromFactor == null || toFactor == null) {
            throw new IllegalArgumentException("Unbekannte Einheit: " + from + " -> " + to);
        }

        // In Basiseinheit (m bzw. m^2) umrechnen und dann in Zieleinheit teilen
        double baseValue = value * fromFactor;
        return baseValue / toFactor;
    }

    private Double getFactorInBase(String unit) {
        switch (unit) {
            // Längen (Basis: m)
            case "km":
                return 1000.0;
            case "m":
                return 1.0;
            case "dm":
                return 0.1;
            case "cm":
                return 0.01;
            case "mm":
                return 0.001;

            // Flächen (Basis: m^2)
            case "km^2":
                return 1_000_000.0;
            case "ha":
                return 10_000.0;
            case "a":
                return 100.0;
            case "m^2":
                return 1.0;
            case "dm^2":
                return 0.01;
            case "cm^2":
                return 0.0001;
            case "mm^2":
                return 0.000001;

            default:
                return null;
        }
    }

    private String formatNumber(double val) {
        double rounded = Math.rint(val);
        if (Math.abs(val - rounded) < 1e-9) {
            return String.valueOf((long) rounded);
        }
        String formatted = String.format(Locale.US, "%.6f", val)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
        return formatted.replace('.', ',');
    }
}
