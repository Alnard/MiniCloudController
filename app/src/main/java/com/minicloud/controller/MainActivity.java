package com.minicloud.controller;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.UUID;

public class MainActivity extends Activity {
    TextView status;
    String token;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        token = getSharedPreferences("pairing", MODE_PRIVATE).getString("token", null);
        if (token == null) {
            token = UUID.randomUUID().toString().replace("-", "");
            getSharedPreferences("pairing", MODE_PRIVATE).edit().putString("token", token).apply();
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 36, 28, 28);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("MiniCloud Controller");
        title.setTextSize(25);
        layout.addView(title);

        TextView info = new TextView(this);
        info.setText("\nPhone IP: " + getIpAddress() +
                "\nPort: 8765\nPairing token: " + token +
                "\n\nKeep your phone and laptop on the same hotspot. " +
                "Only share this token with your own laptop.");
        info.setTextSize(16);
        layout.addView(info);

        Button accessibility = new Button(this);
        accessibility.setText("1. Open Accessibility settings");
        accessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        layout.addView(accessibility);

        Button start = new Button(this);
        start.setText("2. Start control server");
        start.setOnClickListener(v -> {
            if (ControlAccessibilityService.instance == null) {
                Toast.makeText(this, "Enable MiniCloud Controller in Accessibility first.", Toast.LENGTH_LONG).show();
                return;
            }
            Intent i = new Intent(this, ControlServerService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
            Toast.makeText(this, "Control server started on port 8765", Toast.LENGTH_LONG).show();
        });
        layout.addView(start);

        Button stop = new Button(this);
        stop.setText("Stop control server");
        stop.setOnClickListener(v -> stopService(new Intent(this, ControlServerService.class)));
        layout.addView(stop);

        status = new TextView(this);
        status.setText("\nSecurity: requests require your pairing token. Stop the server when finished.");
        status.setTextSize(14);
        layout.addView(status);
        setContentView(layout);
    }

    private String getIpAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress a : Collections.list(ni.getInetAddresses())) {
                    String ip = a.getHostAddress();
                    if (ip != null && ip.indexOf(':') < 0 && !ip.startsWith("127.")) return ip;
                }
            }
        } catch (Exception ignored) {}
        return "Check phone Wi-Fi details";
    }
}
