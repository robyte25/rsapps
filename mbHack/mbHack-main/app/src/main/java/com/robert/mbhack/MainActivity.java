package com.robert.mbhack;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.robert.mbhack.battles.BattleConfig;
import com.robert.mbhack.battles.BattleRegistry;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends Activity {

    private TextView tvLog, tvBattleSelect;
    private ScrollView scrollView;
    private Button btnStart, btnStop, btnCopyLog;
    private volatile boolean isRunning = false;
    private Thread workerThread;
    private OkHttpClient client;
    private int totalRuns = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<BattleConfig> availableBattles = new ArrayList<>();
    private int selectedBattleIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLog = findViewById(R.id.tvLog);
        scrollView = findViewById(R.id.scrollView);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnCopyLog = findViewById(R.id.btnCopyLog);
        tvBattleSelect = findViewById(R.id.tvBattleSelect);

        availableBattles.clear();
        availableBattles.addAll(BattleRegistry.discoverBattles(this));

        if (!availableBattles.isEmpty()) {
            tvBattleSelect.setText(availableBattles.get(selectedBattleIndex).getName());
            logAction("Battles", availableBattles.size() + " Battle(s) geladen.");
        } else {
            tvBattleSelect.setText("Keine Battles gefunden");
            btnStart.setEnabled(false);
            logAction("Battles", "Keine Battles gefunden.");
        }

        tvBattleSelect.setOnClickListener(v -> showBattleSelectionDialog());

        checkBatteryOptimization();

        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        CookieJar simpleCookieJar = new CookieJar() {
            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                try {
                    URI uri = url.uri();
                    for (Cookie cookie : cookies) {
                        HttpCookie httpCookie = new HttpCookie(cookie.name(), cookie.value());
                        httpCookie.setDomain(cookie.domain());
                        httpCookie.setPath(cookie.path());
                        httpCookie.setSecure(cookie.secure());
                        httpCookie.setHttpOnly(cookie.httpOnly());
                        if (cookie.persistent()) {
                            long maxAgeSec = Math.max(0L, (cookie.expiresAt() - System.currentTimeMillis()) / 1000L);
                            httpCookie.setMaxAge(maxAgeSec);
                        }
                        cookieManager.getCookieStore().add(uri, httpCookie);
                    }
                } catch (Exception ignored) {
                }
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                List<Cookie> result = new ArrayList<>();
                try {
                    List<HttpCookie> stored = cookieManager.getCookieStore().get(url.uri());
                    for (HttpCookie hc : stored) {
                        if (!hc.hasExpired()) {
                            Cookie.Builder builder = new Cookie.Builder()
                                    .name(hc.getName())
                                    .value(hc.getValue())
                                    .path(hc.getPath() != null ? hc.getPath() : "/");
                            String domain = hc.getDomain() != null ? hc.getDomain().replaceFirst("^\\.", "") : url.host();
                            builder.domain(domain);
                            if (hc.getSecure()) builder.secure();
                            if (hc.isHttpOnly()) builder.httpOnly();
                            result.add(builder.build());
                        }
                    }
                } catch (Exception ignored) {
                }
                return result;
            }
        };

        client = new OkHttpClient.Builder()
                .cookieJar(simpleCookieJar)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .build();
                    return chain.proceed(request);
                })
                .build();

        btnStart.setOnClickListener(v -> {
            if (availableBattles.isEmpty()) {
                logAction("Fehler", "Keine Battles registriert.");
                return;
            }
            isRunning = true;
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            tvBattleSelect.setEnabled(false);

            BattleConfig currentBattle = availableBattles.get(selectedBattleIndex);
            logAction("Start", "Bot gestartet für: " + currentBattle.getName());
            workerThread = new Thread(this::runBot);
            workerThread.start();
        });

        btnStop.setOnClickListener(v -> {
            isRunning = false;
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            tvBattleSelect.setEnabled(true);
            logAction("Stop", "Bot wurde angehalten.");
        });

        btnCopyLog.setOnClickListener(v -> copyLogToClipboard());
    }

    private void copyLogToClipboard() {
        if (tvLog == null) return;
        String logText = tvLog.getText() != null ? tvLog.getText().toString() : "";
        if (logText.isEmpty()) {
            Toast.makeText(this, "Log ist leer.", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("MBhack Log", logText);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Log in Zwischenablage kopiert.", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void showBattleSelectionDialog() {
        if (availableBattles.isEmpty()) return;

        String[] names = new String[availableBattles.size()];
        for (int i = 0; i < availableBattles.size(); i++) {
            names[i] = availableBattles.get(i).getName();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Wähle ein Battle aus");
        builder.setSingleChoiceItems(names, selectedBattleIndex, (dialog, which) -> {
            selectedBattleIndex = which;
            tvBattleSelect.setText(names[which]);
            dialog.dismiss();
        });
        builder.create().show();
    }

    private void logAction(String step, String details) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = "[" + time + "] [" + step + "] -> " + details + "\n";
        mainHandler.post(() -> {
            if (tvLog != null) {
                tvLog.append(line);
            }
            if (scrollView != null) {
                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
            }
        });
    }

    private boolean isPauseLimitReached(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("bereits so viele aufgaben vom gleichen typ gelöst")
                || lower.contains("pause verdient");
    }

    private void stopBotWithError(String errorMessage) {
        isRunning = false;
        logAction("FEHLER", errorMessage);
        mainHandler.post(() -> {
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            tvBattleSelect.setEnabled(true);
        });
    }

    private String extractBodyText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        return doc.body() != null ? doc.body().text().replaceAll("\\s+", " ").trim() : html.trim();
    }

    private String extractCurrentPointsFromText(String fullText) {
        if (fullText == null || fullText.isEmpty()) {
            return "unbekannt";
        }

        Matcher m1 = Pattern.compile("(?i)Punkte\\s*:?\\s*(-?\\d+)").matcher(fullText);
        if (m1.find()) {
            return m1.group(1);
        }

        Matcher m2 = Pattern.compile("(?i)Highscore von\\s+\"[^\"]+\"\\s+(-?\\d+)\\s+P\\.").matcher(fullText);
        if (m2.find()) {
            return m2.group(1);
        }

        Matcher m3 = Pattern.compile("(?i)Robert Krissa\\s+(-?\\d+)\\s+P\\.").matcher(fullText);
        if (m3.find()) {
            return m3.group(1);
        }

        Matcher m4 = Pattern.compile("(?i)(-?\\d+)\\s+Punkte").matcher(fullText);
        if (m4.find()) {
            return m4.group(1);
        }

        return "unbekannt";
    }

    private void logPointsResult(String currentHtml, String battleUrl) {
        String postText = extractBodyText(currentHtml);
        if (isPauseLimitReached(postText)) {
            stopBotWithError("MatheBattle-Limit erreicht: Bereits zu viele Aufgaben vom gleichen Typ gelöst (Pause erforderlich).");
            return;
        }

        String points = extractCurrentPointsFromText(postText);

        if (!"unbekannt".equals(points)) {
            logAction("Punkte", "Aktuelle Punktzahl: " + points);
            return;
        }

        String battlePageText = "";
        try {
            Request req = new Request.Builder().url(battleUrl).build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.body() != null) {
                    battlePageText = extractBodyText(resp.body().string());
                    if (isPauseLimitReached(battlePageText)) {
                        stopBotWithError("MatheBattle-Limit erreicht: Bereits zu viele Aufgaben vom gleichen Typ gelöst (Pause erforderlich).");
                        return;
                    }
                    points = extractCurrentPointsFromText(battlePageText);
                }
            }
        } catch (Exception ignored) {
        }

        if (!"unbekannt".equals(points)) {
            logAction("Punkte", "Aktuelle Punktzahl: " + points);
        } else {
            logAction("Punkte", "Aktuelle Punktzahl: unbekannt");
            if (!postText.isEmpty()) {
                logAction("Punkte [POST-TEXT]", postText);
            }
            if (!battlePageText.isEmpty()) {
                logAction("Punkte [BATTLE-TEXT]", battlePageText);
            }
        }
    }

    private void runBot() {
        try {
            while (isRunning) {
                BattleConfig activeBattle = availableBattles.get(selectedBattleIndex);

                logAction("Login", "Hole Login-Seite und Formularfelder...");
                Request getLoginRequest = new Request.Builder()
                        .url("https://mathebattle.de/users/login")
                        .build();

                String loginHtml;
                try (Response response = client.newCall(getLoginRequest).execute()) {
                    if (response.body() == null) throw new Exception("Login-Seite nicht erreichbar.");
                    loginHtml = response.body().string();
                }

                Document loginDoc = Jsoup.parse(loginHtml);
                Element loginForm = loginDoc.selectFirst("form");

                FormBody.Builder loginBodyBuilder = new FormBody.Builder();

                if (loginForm != null) {
                    for (Element input : loginForm.select("input")) {
                        String name = input.attr("name");
                        String val = input.attr("value");
                        String type = input.attr("type").toLowerCase();

                        if (name.isEmpty()) continue;

                        if (type.equals("text") || name.contains("username")) {
                            loginBodyBuilder.add(name, "KrissaR-MGE");
                        } else if (type.equals("password") || name.contains("password")) {
                            loginBodyBuilder.add(name, "11901112pojap");
                        } else {
                            loginBodyBuilder.add(name, val);
                        }
                    }
                } else {
                    loginBodyBuilder.add("data[User][username]", "KrissaR-MGE");
                    loginBodyBuilder.add("data[User][password]", "11901112pojap");
                }

                String loginActionUrl = (loginForm != null && loginForm.hasAttr("action"))
                        ? loginForm.attr("action")
                        : "/users/login";
                if (!loginActionUrl.startsWith("http")) {
                    loginActionUrl = "https://mathebattle.de" + loginActionUrl;
                }

                logAction("Login", "Sende Anmeldedaten...");
                Request postLoginRequest = new Request.Builder()
                        .url(loginActionUrl)
                        .post(loginBodyBuilder.build())
                        .build();

                try (Response response = client.newCall(postLoginRequest).execute()) {
                    if (response.body() != null) {
                        String respStr = response.body().string();
                        if (respStr.contains("Invalid username or password")) {
                            logAction("Login FEHLER", "Ungültiger Benutzername oder Passwort!");
                        } else {
                            logAction("Login", "Login ausgeführt. Status: " + response.code());
                        }
                    }
                }

                if (!isRunning) break;

                String targetBattleUrl = activeBattle.getUrl();
                logAction("Battle Start", "Rufe Spielseite auf: " + targetBattleUrl);
                Request startRequest = new Request.Builder()
                        .url(targetBattleUrl)
                        .build();

                String currentHtml;
                String currentUrl;

                try (Response response = client.newCall(startRequest).execute()) {
                    if (response.body() == null) throw new Exception("Startseite nicht erreichbar.");
                    currentHtml = response.body().string();
                    currentUrl = response.request().url().toString();
                }

                Document startDoc = Jsoup.parse(currentHtml);
                Element playLink = startDoc.selectFirst(".bluebuttons a, a[href*=/edu_battles/play/]");

                if (playLink != null) {
                    String playUrl = playLink.attr("href");
                    String fullPlayUrl = playUrl.startsWith("http") ? playUrl : "https://mathebattle.de" + playUrl;
                    logAction("Battle Start", "Navigiere zu Spielseite: " + fullPlayUrl);

                    Request playRequest = new Request.Builder().url(fullPlayUrl).build();
                    try (Response response = client.newCall(playRequest).execute()) {
                        if (response.body() != null) {
                            currentHtml = response.body().string();
                            currentUrl = response.request().url().toString();
                        }
                    }
                }

                if (!isRunning) break;

                try {
                    logAction("Aufgabe parsen", "Lese Aufgabe aus HTML...");
                    List<String> tasks = activeBattle.parseTasksFromHtml(currentHtml);

                    if (tasks == null || tasks.isEmpty()) {
                        Document errorDoc = Jsoup.parse(currentHtml);
                        String bodyText = errorDoc.body() != null ? errorDoc.body().text() : currentHtml;
                        throw new Exception("Kein Aufgabentext im HTML gefunden. Erkannter Seitentext: \"" + bodyText + "\"");
                    }

                    logAction("Aufgabe", tasks.toString());

                    List<String> solutions = activeBattle.solveTasks(tasks);
                    logAction("Berechnung", "Lösungen: " + solutions);

                    Document doc = Jsoup.parse(currentHtml);
                    Element form = doc.selectFirst("form");
                    String formAction = form != null ? form.attr("action") : currentUrl;
                    String targetPostUrl = formAction.startsWith("http") ? formAction : "https://mathebattle.de" + formAction;

                    FormBody.Builder postData = new FormBody.Builder();

                    if (form != null) {
                        for (Element hiddenInput : form.select("input[type=hidden]")) {
                            String name = hiddenInput.attr("name");
                            String val = hiddenInput.attr("value");
                            if (!name.isEmpty()) {
                                postData.add(name, val);
                            }
                        }
                    }

                    Map<String, String> answerFields = activeBattle.buildAnswerFields(solutions);
                    for (Map.Entry<String, String> entry : answerFields.entrySet()) {
                        postData.add(entry.getKey(), entry.getValue());
                    }

                    logAction("Absenden", "Sende Lösung(en) " + answerFields + "...");

                    Request postRequest = new Request.Builder()
                            .url(targetPostUrl)
                            .post(postData.build())
                            .build();

                    try (Response response = client.newCall(postRequest).execute()) {
                        if (response.body() != null) {
                            currentHtml = response.body().string();
                            currentUrl = response.request().url().toString();
                        }
                    }

                    logPointsResult(currentHtml, targetBattleUrl);
                    if (!isRunning) break;

                    totalRuns++;
                    logAction("Runde beendet", "Durchlauf " + totalRuns + " abgeschlossen!\n");

                } catch (Exception solveErr) {
                    Document rawDoc = Jsoup.parse(currentHtml);
                    String rawText = rawDoc.body() != null ? rawDoc.body().text() : currentHtml;

                    logAction("FEHLER", (solveErr.getMessage() != null ? solveErr.getMessage() : "Fehler beim Berechnen/Auslesen") + " | RawText: \"" + rawText + "\"");
                    logAction("Fallback", "Sende Fallback...");

                    Document doc = Jsoup.parse(currentHtml);
                    Element form = doc.selectFirst("form");
                    String formAction = form != null ? form.attr("action") : currentUrl;
                    String targetPostUrl = formAction.startsWith("http") ? formAction : "https://mathebattle.de" + formAction;

                    FormBody.Builder postData = new FormBody.Builder();
                    if (form != null) {
                        for (Element hiddenInput : form.select("input[type=hidden]")) {
                            String name = hiddenInput.attr("name");
                            String val = hiddenInput.attr("value");
                            if (!name.isEmpty()) {
                                postData.add(name, val);
                            }
                        }
                    }

                    Map<String, String> fallbackFields = activeBattle.buildFallbackFields();
                    for (Map.Entry<String, String> entry : fallbackFields.entrySet()) {
                        postData.add(entry.getKey(), entry.getValue());
                    }

                    Request fallbackRequest = new Request.Builder()
                            .url(targetPostUrl)
                            .post(postData.build())
                            .build();

                    try (Response response = client.newCall(fallbackRequest).execute()) {
                        if (response.body() != null) {
                            currentHtml = response.body().string();
                            currentUrl = response.request().url().toString();
                        }
                    }

                    logPointsResult(currentHtml, targetBattleUrl);
                    if (!isRunning) break;

                    totalRuns++;
                    logAction("Runde beendet", "Fallback abgesendet (Durchlauf " + totalRuns + ")\n");
                }
            }

        } catch (Exception err) {
            logAction("KRITISCHER FEHLER", err.getMessage() != null ? err.getMessage() : err.toString());
            mainHandler.post(() -> {
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
                tvBattleSelect.setEnabled(true);
            });
        }
    }
}
