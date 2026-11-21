package com.robert.mbhack;

public class MathSolver {

    public static double solveMathTask(String task) {
        if (task == null || task.trim().isEmpty()) {
            throw new IllegalArgumentException("Leere Aufgabe");
        }

        String expr = task.trim()
                .replace(",", ".")
                .replace("×", "*")
                .replace("·", "*")
                .replace("⋅", "*")
                .replace(":", "/");

        if (expr.contains("x") || expr.contains("X")) {
            String normalized = expr.replace("X", "x");
            String[] parts = normalized.split("=");
            String sideExpr = parts.length == 2
                    ? "(" + parts[0].trim() + ") - (" + parts[1].trim() + ")"
                    : normalized;

            for (int x = -1000; x <= 1000; x++) {
                double val = evaluateExpression(sideExpr, x);
                if (Math.abs(val) < 1e-6) {
                    return x;
                }
            }
            throw new IllegalArgumentException("Keine Lösung für x gefunden: " + task);
        }

        return evaluateExpression(expr, 0.0);
    }

    private static double evaluateExpression(String input, double xValue) {
        return new Parser(input, xValue).parse();
    }

    private static class Parser {
        private final String s;
        private final double xVal;
        private int pos = -1;
        private int ch;

        Parser(String s, double xVal) {
            this.s = s;
            this.xVal = xVal;
            nextChar();
        }

        private void nextChar() {
            pos++;
            ch = (pos < s.length()) ? s.charAt(pos) : -1;
        }

        private void skipWhitespace() {
            while (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                nextChar();
            }
        }

        double parse() {
            double x = parseExpression();
            skipWhitespace();
            if (ch != -1) {
                throw new IllegalArgumentException("Unerwartetes Zeichen: " + (char) ch);
            }
            return x;
        }

        private double parseExpression() {
            double x = parseTerm();
            while (true) {
                skipWhitespace();
                if (ch == '+') {
                    nextChar();
                    x += parseTerm();
                } else if (ch == '-') {
                    nextChar();
                    x -= parseTerm();
                } else {
                    return x;
                }
            }
        }

        private double parseTerm() {
            double x = parsePower();
            while (true) {
                skipWhitespace();
                if (ch == '*') {
                    nextChar();
                    x *= parsePower();
                } else if (ch == '/') {
                    nextChar();
                    double div = parsePower();
                    if (Math.abs(div) < 1e-12) {
                        throw new ArithmeticException("Division durch Null");
                    }
                    x /= div;
                } else {
                    return x;
                }
            }
        }

        private double parsePower() {
            double x = parseFactor();
            skipWhitespace();
            if (ch == '^') {
                nextChar();
                x = Math.pow(x, parsePower());
            }
            return x;
        }

        private double parseFactor() {
            skipWhitespace();
            if (ch == '+') {
                nextChar();
                return parseFactor();
            }
            if (ch == '-') {
                nextChar();
                return -parseFactor();
            }

            double x;
            int startPos = pos;

            if (ch == '(') {
                nextChar();
                x = parseExpression();
                skipWhitespace();
                if (ch == ')') {
                    nextChar();
                } else {
                    throw new IllegalArgumentException("Fehlende schließende Klammer ')'");
                }
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') {
                    nextChar();
                }
                x = Double.parseDouble(s.substring(startPos, pos));
            } else if (ch == 'x') {
                nextChar();
                x = xVal;
            } else {
                throw new IllegalArgumentException("Unerwartetes Zeichen an Position " + pos + ": " + (char) ch);
            }

            return x;
        }
    }
}
