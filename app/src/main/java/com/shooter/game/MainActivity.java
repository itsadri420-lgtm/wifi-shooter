package com.shooter.game;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainActivity extends Activity {
    static final int PORT = 8080;
    static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    SharedPreferences sp;
    WebView web;
    ServerSocket server;
    volatile boolean running;
    final CopyOnWriteArrayList<Conn> conns = new CopyOnWriteArrayList<>();
    float dp;

    class Conn {
        final Socket s;
        final OutputStream out;

        Conn(Socket s, OutputStream out) {
            this.s = s;
            this.out = out;
        }

        synchronized void send(int op, byte[] d) {
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                b.write(0x80 | op);
                if (d.length < 126) {
                    b.write(d.length);
                } else {
                    b.write(126);
                    b.write(d.length >> 8);
                    b.write(d.length & 255);
                }
                b.write(d);
                out.write(b.toByteArray());
                out.flush();
            } catch (Exception e) {
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        dp = getResources().getDisplayMetrics().density;
        showMenu("");
    }

    int d(int v) {
        return (int) (v * dp);
    }

    GradientDrawable box(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(d(12));
        g.setStroke(d(1), stroke);
        return g;
    }

    EditText field(String hint, String val) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF8A97B8);
        e.setTextColor(0xFFE8F0FF);
        e.setText(val);
        e.setSingleLine(true);
        e.setBackground(box(0x22FFFFFF, 0x3300E5FF));
        e.setPadding(d(14), d(10), d(14), d(10));
        return e;
    }

    Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setTextColor(primary ? 0xFFFFFFFF : 0xFF00E5FF);
        b.setBackground(box(primary ? 0xFF7C4DFF : 0x2200E5FF, 0xFF00E5FF));
        return b;
    }

    TextView label(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(d(420), -2);
        l.topMargin = d(top);
        return l;
    }

    String clean(String n) {
        n = n.trim().replaceAll("[^A-Za-z0-9_\\-]", "");
        if (n.length() > 12) n = n.substring(0, 12);
        return n.isEmpty() ? "Spieler" : n;
    }

    String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    void showMenu(String msg) {
        stopServer();
        if (web != null) {
            web.destroy();
            web = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(0xFF0B1020);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(d(24), d(20), d(24), d(20));
        col.addView(label("WIFI SHOOTER", 28, 0xFF00E5FF, true), lp(0));
        col.addView(label("Einer hostet, der andere tritt bei (selbes WLAN)", 13, 0xFF8A97B8, false), lp(2));
        EditText nameIn = field("Dein Name", sp.getString("name", ""));
        col.addView(nameIn, lp(14));
        Button host = button("Spiel hosten", true);
        col.addView(host, lp(10));
        EditText ipIn = field("IP des Hosts, z. B. 192.168.1.23", sp.getString("ip", ""));
        col.addView(ipIn, lp(14));
        Button join = button("Beitreten", false);
        col.addView(join, lp(8));
        TextView st = label(msg, 13, 0xFFFF4DA6, false);
        col.addView(st, lp(10));
        sv.addView(col);
        setContentView(sv);

        host.setOnClickListener(v -> {
            String n = clean(nameIn.getText().toString());
            sp.edit().putString("name", n).apply();
            try {
                startServer();
            } catch (Exception e) {
                st.setText("Server-Fehler: " + e.getMessage());
                return;
            }
            openGame("http://127.0.0.1:" + PORT + "/?ip=" + localIp() + "&n=" + enc(n));
        });
        join.setOnClickListener(v -> {
            String n = clean(nameIn.getText().toString());
            String ip = ipIn.getText().toString().trim();
            if (ip.isEmpty()) {
                st.setText("Bitte die IP des Hosts eintragen.");
                return;
            }
            sp.edit().putString("name", n).putString("ip", ip).apply();
            openGame("http://" + ip + ":" + PORT + "/?n=" + enc(n));
        });
    }

    void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) hideBars();
    }

    void openGame(String url) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setSupportZoom(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setLongClickable(false);
        web.setHapticFeedbackEnabled(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError err) {
                if (r.isForMainFrame()) {
                    v.post(() -> showMenu("Verbindung fehlgeschlagen. Sind beide im selben WLAN und läuft das Spiel beim Host?"));
                }
            }
        });
        setContentView(web);
        hideBars();
        web.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (web != null) showMenu("");
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    String localIp() {
        String fallback = "?";
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements(); ) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                String nm = ni.getName().toLowerCase(Locale.US);
                for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                    InetAddress ia = a.nextElement();
                    if (ia.isLoopbackAddress() || !(ia instanceof Inet4Address)) continue;
                    if (nm.startsWith("wlan") || nm.startsWith("ap") || nm.startsWith("eth")) {
                        return ia.getHostAddress();
                    }
                    fallback = ia.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    // ---------- Mini-Server: Spielseite + WebSocket-Relay ----------
    void startServer() throws Exception {
        stopServer();
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(PORT));
        server = ss;
        running = true;
        new Thread(() -> {
            while (running && !ss.isClosed()) {
                try {
                    Socket s = ss.accept();
                    new Thread(() -> handle(s)).start();
                } catch (Exception e) {
                    break;
                }
            }
        }).start();
    }

    void stopServer() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        server = null;
        for (Conn c : conns) {
            try {
                c.s.close();
            } catch (Exception ignored) {
            }
        }
        conns.clear();
    }

    String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') return sb.toString();
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) break;
        }
        return sb.length() == 0 && c < 0 ? null : sb.toString();
    }

    void readFully(InputStream in, byte[] b) throws Exception {
        int o = 0;
        while (o < b.length) {
            int n = in.read(b, o, b.length - o);
            if (n < 0) throw new java.io.IOException("EOF");
            o += n;
        }
    }

    void handle(Socket s) {
        Conn c = null;
        try {
            s.setTcpNoDelay(true);
            InputStream in = new java.io.BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            String first = readLine(in);
            if (first == null) return;
            String key = null;
            boolean ws = false;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                String low = line.toLowerCase(Locale.US);
                if (low.startsWith("upgrade:") && low.contains("websocket")) ws = true;
                if (low.startsWith("sec-websocket-key:")) key = line.substring(18).trim();
            }
            if (ws && key != null) {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                String acc = Base64.encodeToString(
                        md.digest((key + GUID).getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n"
                        + "Connection: Upgrade\r\nSec-WebSocket-Accept: " + acc + "\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                c = new Conn(s, out);
                conns.add(c);
                wsLoop(in, c);
            } else if (first.startsWith("GET /") && !first.contains("favicon")) {
                byte[] page;
                try (InputStream is = getAssets().open("game.html")) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                    page = bo.toByteArray();
                }
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                        + "Cache-Control: no-store\r\nContent-Length: " + page.length
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(page);
                out.flush();
            } else {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) conns.remove(c);
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    void wsLoop(InputStream in, Conn me) throws Exception {
        while (true) {
            int b0 = in.read();
            int b1 = in.read();
            if (b0 < 0 || b1 < 0) return;
            int op = b0 & 15;
            long len = b1 & 127;
            boolean masked = (b1 & 128) != 0;
            if (len == 126) {
                len = (in.read() << 8) | in.read();
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
            }
            byte[] mask = new byte[4];
            if (masked) readFully(in, mask);
            if (len > 60000) return;
            byte[] data = new byte[(int) len];
            readFully(in, data);
            if (masked) for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
            if (op == 8) return;
            if (op == 9) {
                me.send(10, data);
            } else if (op == 1) {
                for (Conn o : conns) if (o != me) o.send(1, data);
            }
        }
    }
}

