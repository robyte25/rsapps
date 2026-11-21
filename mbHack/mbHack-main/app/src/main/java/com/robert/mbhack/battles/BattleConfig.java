package com.robert.mbhack.battles;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BattleConfig {

    public abstract String getName();

    public abstract String getUrl();

    public int getInputFieldCount() {
        return 1;
    }

    public List<String> parseTasksFromHtml(String html) {
        String single = parseSingleTaskFromHtml(html);
        if (single == null || single.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(single);
    }

    public List<String> solveTasks(List<String> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        String firstTask = tasks.get(0);
        double solutionVal = com.robert.mbhack.MathSolver.solveMathTask(firstTask);
        return Collections.singletonList(String.valueOf((long) solutionVal));
    }

    public Map<String, String> buildAnswerFields(List<String> solutions) {
        Map<String, String> fields = new LinkedHashMap<>();
        int count = Math.max(1, getInputFieldCount());

        for (int i = 0; i < count; i++) {
            String sol = (solutions != null && i < solutions.size()) ? solutions.get(i) : "0";
            String fieldName = (i == 0) ? "value" : "value_" + (i + 1);
            fields.put(fieldName, sol);
        }
        fields.put("submit_button", "lösen");
        return fields;
    }

    public Map<String, String> buildFallbackFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        int count = Math.max(1, getInputFieldCount());

        for (int i = 0; i < count; i++) {
            String fieldName = (i == 0) ? "value" : "value_" + (i + 1);
            fields.put(fieldName, "0");
        }
        fields.put("submit_button", "lösen");
        return fields;
    }

    protected String parseSingleTaskFromHtml(String html) {
        Document doc = Jsoup.parse(html);

        for (Element msup : doc.select("msup")) {
            Elements children = msup.children();
            if (children.size() >= 2) {
                String base = children.get(0).text().trim();
                String exp = children.get(children.size() - 1).text().trim();
                msup.text(" (" + base + ")^(" + exp + ") ");
            }
        }

        Element container = doc.selectFirst(".exercise_question");
        if (container == null) container = doc.selectFirst("#aufgabe");

        if (container == null) {
            return "";
        }

        String text = container.text();

        // Alle Kästchen-Zeichen (inkl. ⬜) in x umwandeln
        text = text.replaceAll("[■□▢☐▯⬜⬛◻◼◾◽]", "x");

        text = text.replace("&middot;", "*")
                .replace("&sdot;", "*")
                .replace("&times;", "*")
                .replace("&minus;", "-")
                .replace("⋅", "*")
                .replaceAll("\\s+", " ")
                .trim();

        // Zuerst "Berechne: <Ausdruck> <Ausdruck> =" behandeln, solange ':' noch nicht zu '/' wurde
        Matcher calcMatcher = Pattern.compile("(?i)Berechne\\s*:\\s*(.+?)\\s*=").matcher(text);
        if (calcMatcher.find()) {
            String inside = calcMatcher.group(1).trim().replace(":", "/");
            return deduplicateRepeatedExpression(inside).trim();
        }

        text = text.replace(":", "/");

        // Falls ein x und ein = vorkommen, Gleichung mit rechter Seite extrahieren
        if (text.contains("x") && text.contains("=")) {
            int eqIdx = text.indexOf("=");
            String beforeEq = text.substring(0, eqIdx).trim();
            String afterEq = text.substring(eqIdx + 1).trim();

            Matcher rightMatcher = Pattern.compile("^([+-]?\\s*\\d+(?:[.,]\\d+)?)").matcher(afterEq);
            String rightSide = rightMatcher.find() ? rightMatcher.group(1).replaceAll("\\s+", "") : "";

            String[] tokens = beforeEq.split("\\s+");
            int xTokenIndex = -1;
            for (int i = tokens.length - 1; i >= 0; i--) {
                if (tokens[i].equals("x")) {
                    xTokenIndex = i;
                    break;
                }
            }

            if (xTokenIndex != -1 && !rightSide.isEmpty()) {
                int startIdx = xTokenIndex;
                if (startIdx >= 2 && (tokens[startIdx - 1].equals("+") || tokens[startIdx - 1].equals("-")
                        || tokens[startIdx - 1].equals("*") || tokens[startIdx - 1].equals("/"))
                        && tokens[startIdx - 2].matches("[+-]?\\d+(?:[.,]\\d+)?")) {
                    startIdx -= 2;
                }

                int endIdx = xTokenIndex;
                if (endIdx + 2 < tokens.length && (tokens[endIdx + 1].equals("+") || tokens[endIdx + 1].equals("-")
                        || tokens[endIdx + 1].equals("*") || tokens[endIdx + 1].equals("/"))
                        && tokens[endIdx + 2].matches("[+-]?\\d+(?:[.,]\\d+)?")) {
                    endIdx += 2;
                }

                StringBuilder leftBuilder = new StringBuilder();
                for (int i = startIdx; i <= endIdx; i++) {
                    if (leftBuilder.length() > 0) leftBuilder.append(" ");
                    leftBuilder.append(tokens[i]);
                }

                return (leftBuilder.toString() + " = " + rightSide).trim();
            }
        }

        text = text.replaceAll("(?i)Ich möchte nur.*", "");
        text = text.replaceAll("(?i)Berechne den Quotienten.*?/", "");
        text = text.replaceAll("(?i)Berechne das Produkt.*?/", "");
        text = text.replaceAll("(?i)Berechne die Summe.*?/", "");
        text = text.replaceAll("(?i)Berechne die Differenz.*?/", "");
        text = text.replaceAll("(?i)Berechne.*?/", "");
        text = text.replaceAll("(?i)Aufgabe\\s*/\\s*(\\(\\d+\\s*Punkt[e]?\\))?", "");
        text = text.replaceAll("(?i)Was muss in das Kästchen\\??", "");
        text = text.replaceAll("(?i)Was muss.*?\\?", "");
        text = text.replaceAll("(?i)Welche Zahl.*?\\?", "");

        if (text.contains("=")) {
            text = text.split("=")[0];
        }

        text = text.replaceAll("(?i)Was muss.*\\?", "")
                .replaceAll("(?i)Welche Zahl.*\\?", "")
                .replaceAll("\\s+", " ")
                .trim();

        return text;
    }

    private String deduplicateRepeatedExpression(String expr) {
        String trimmed = expr.trim().replaceAll("\\s+", " ");
        int len = trimmed.length();
        for (int i = 1; i < len; i++) {
            String left = trimmed.substring(0, i).trim();
            String right = trimmed.substring(i).trim();
            if (!left.isEmpty() && left.equals(right)) {
                return left;
            }
        }
        return trimmed;
    }
}
