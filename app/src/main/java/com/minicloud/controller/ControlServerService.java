package com.minicloud.controller;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class ControlServerService extends Service {
    private volatile boolean running = false;
    private ServerSocket server;
    private Thread worker;
    private String token;

    @Override public void onCreate() {
        super.onCreate();
        token = getSharedPreferences("pairing", MODE_PRIVATE).getString("token", null);
        // MainActivity stores the token in its private preferences; retrieve it through the same named file below.
        if (token == null) token = getSharedPreferences("MiniCloudController", MODE_PRIVATE).getString("token", null);
        createNotification();
    }

    private void createNotification() {
        String channel = "minicloud_control";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel nc = new NotificationChannel(channel, "MiniCloud control server", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(nc);
        }
        Notification n = new Notification.Builder(this, channel)
                .setContentTitle("MiniCloud Controller is running")
                .setContentText("Local control server on port 8765")
                .setSmallIcon(android.R.drawable.presence_online)
                .build();
        startForeground(7, n);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            running = true;
            worker = new Thread(this::serve, "MiniCloudServer");
            worker.start();
        }
        return START_STICKY;
    }

    private void serve() {
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(8765));
            while (running) {
                Socket socket = server.accept();
                new Thread(() -> handle(socket)).start();
            }
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = s.getOutputStream()) {
            s.setSoTimeout(3000);
            String first = in.readLine();
            if (first == null) return;
            String line;
            String suppliedToken = "";
            int length = 0;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                String lower = line.toLowerCase();
                if (lower.startsWith("x-minicloud-token:")) suppliedToken = line.substring(line.indexOf(':') + 1).trim();
                if (lower.startsWith("content-length:")) length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
            if (token == null || !constantTimeEquals(token, suppliedToken)) {
                respond(out, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if (length < 0 || length > 2048) {
                respond(out, 400, "{\"error\":\"bad request\"}");
                return;
            }
            char[] body = new char[length];
            int read = 0;
            while (read < length) {
                int n = in.read(body, read, length - read);
                if (n < 0) break;
                read += n;
            }
            String path = first.split(" ")[1];
            JSONObject data = new JSONObject(new String(body, 0, read));
            ControlAccessibilityService a = ControlAccessibilityService.instance;
            if (a == null) {
                respond(out, 503, "{\"error\":\"accessibility service is not enabled\"}");
                return;
            }
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            boolean ok = false;
            if ("/tap".equals(path)) {
                ok = a.tap(clamp(data.getDouble("x")) * w, clamp(data.getDouble("y")) * h);
            } else if ("/swipe".equals(path)) {
                ok = a.swipe(clamp(data.getDouble("x1")) * w, clamp(data.getDouble("y1")) * h,
                        clamp(data.getDouble("x2")) * w, clamp(data.getDouble("y2")) * h,
                        data.optLong("duration", 300));
            } else {
                respond(out, 404, "{\"error\":\"unknown endpoint\"}");
                return;
            }
            respond(out, ok ? 200 : 500, ok ? "{\"ok\":true}" : "{\"error\":\"gesture rejected\"}");
        } catch (Exception e) {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }
    private void respond(OutputStream out, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "OK" : code == 401 ? "Unauthorized" : code == 404 ? "Not Found" : code == 503 ? "Service Unavailable" : "Bad Request";
        String header = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    @Override public void onDestroy() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        stopForeground(true);
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
