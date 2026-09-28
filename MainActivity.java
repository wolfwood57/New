package fr.mamemoire.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {

    private EditText memoryInput;
    private EditText searchInput;
    private LinearLayout memoryList;
    private TextView status;
    private SpeechRecognizer recognizer;
    private SharedPreferences prefs;
    private final ArrayList<Memory> memories = new ArrayList<>();
    private static final int REQ_AUDIO = 1001;

    static class Memory {
        String text;
        long time;
        Memory(String text, long time) { this.text = text; this.time = time; }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("memories", MODE_PRIVATE);
        loadMemories();
        buildUi();
        refreshList("");
    }

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView title(String text, float size) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(0xFF111827);
        v.setPadding(0, dp(6), 0, dp(6));
        return v;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(12));
        root.setBackgroundColor(0xFFF8FAFC);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView appTitle = title("🧠 Ma Mémoire", 28);
        content.addView(appTitle);

        TextView intro = title("Ton deuxième cerveau personnel", 16);
        intro.setTextColor(0xFF64748B);
        content.addView(intro);

        status = title("Prêt à capturer une mémoire.", 14);
        status.setTextColor(0xFF475569);
        content.addView(status);

        memoryInput = new EditText(this);
        memoryInput.setHint("Écris une idée ou appuie sur 🎙️ pour parler…");
        memoryInput.setGravity(Gravity.TOP);
        memoryInput.setMinLines(5);
        memoryInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        content.addView(memoryInput, new LinearLayout.LayoutParams(-1, dp(150)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        Button mic = button("🎙️ Parler");
        Button save = button("💾 Enregistrer");
        actions.addView(mic, new LinearLayout.LayoutParams(0, dp(54), 1));
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(54), 1));
        content.addView(actions);

        content.addView(title("🔎 Rechercher dans ma mémoire", 20));
        searchInput = new EditText(this);
        searchInput.setHint("Ex. vacances, idée, travail…");
        content.addView(searchInput, new LinearLayout.LayoutParams(-1, dp(58)));

        Button search = button("Rechercher");
        content.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));

        content.addView(title("📚 Mes souvenirs", 20));
        memoryList = new LinearLayout(this);
        memoryList.setOrientation(LinearLayout.VERTICAL);
        content.addView(memoryList);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView footer = title("Accueil   •   Capturer   •   Mémoire   •   Recherche", 12);
        footer.setGravity(Gravity.CENTER);
        footer.setTextColor(0xFF64748B);
        root.addView(footer);

        setContentView(root);

        mic.setOnClickListener(v -> toggleSpeech());
        save.setOnClickListener(v -> saveMemory());
        search.setOnClickListener(v -> refreshList(searchInput.getText().toString().trim()));
    }

    private void toggleSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("La reconnaissance vocale n'est pas disponible sur ce téléphone.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (recognizer != null) {
            recognizer.stopListening();
            recognizer.destroy();
            recognizer = null;
            status.setText("Écoute arrêtée.");
            return;
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p) { status.setText("🎙️ Je t'écoute…"); }
            public void onBeginningOfSpeech() { status.setText("🎙️ Parle maintenant…"); }
            public void onRmsChanged(float r) {}
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() { status.setText("Traitement de la voix…"); }
            public void onError(int e) {
                status.setText("Reconnaissance terminée. Tu peux réessayer.");
                cleanupRecognizer();
            }
            public void onResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    memoryInput.setText(list.get(0));
                    memoryInput.setSelection(memoryInput.length());
                    status.setText("Transcription française reçue. Vérifie puis enregistre.");
                }
                cleanupRecognizer();
            }
            public void onPartialResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    memoryInput.setText(list.get(0));
                    memoryInput.setSelection(memoryInput.length());
                }
            }
            public void onEvent(int type, Bundle params) {}
        });

        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizer.startListening(i);
    }

    private void cleanupRecognizer() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
    }

    private void saveMemory() {
        String text = memoryInput.getText().toString().trim();
        if (text.isEmpty()) {
            status.setText("Écris ou dicte quelque chose avant d'enregistrer.");
            return;
        }
        memories.add(0, new Memory(text, System.currentTimeMillis()));
        persistMemories();
        memoryInput.setText("");
        status.setText("✓ Mémoire enregistrée.");
        refreshList("");
    }

    private void loadMemories() {
        int count = prefs.getInt("count", 0);
        for (int i = 0; i < count; i++) {
            String text = prefs.getString("text_" + i, null);
            long time = prefs.getLong("time_" + i, 0);
            if (text != null) memories.add(new Memory(text, time));
        }
    }

    private void persistMemories() {
        SharedPreferences.Editor e = prefs.edit().clear();
        e.putInt("count", memories.size());
        for (int i = 0; i < memories.size(); i++) {
            e.putString("text_" + i, memories.get(i).text);
            e.putLong("time_" + i, memories.get(i).time);
        }
        e.apply();
    }

    private void refreshList(String query) {
        if (memoryList == null) return;
        memoryList.removeAllViews();
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        int shown = 0;
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE);

        for (Memory m : memories) {
            if (!q.isEmpty() && !m.text.toLowerCase(Locale.ROOT).contains(q)) continue;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackgroundColor(0xFFFFFFFF);

            TextView date = title(df.format(new Date(m.time)), 12);
            date.setTextColor(0xFF64748B);
            TextView text = title(m.text, 16);
            text.setTextColor(0xFF1E293B);
            card.addView(date);
            card.addView(text);

            memoryList.addView(card, new LinearLayout.LayoutParams(-1, -2));
            Space gap = new Space(this);
            memoryList.addView(gap, new LinearLayout.LayoutParams(1, dp(8)));
            shown++;
        }

        if (shown == 0) {
            TextView empty = title(q.isEmpty()
                    ? "Aucune mémoire pour l'instant."
                    : "Aucune mémoire ne correspond à ta recherche.", 14);
            empty.setTextColor(0xFF64748B);
            memoryList.addView(empty);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_AUDIO && results.length > 0 &&
                results[0] == PackageManager.PERMISSION_GRANTED) {
            status.setText("Micro autorisé. Appuie de nouveau sur 🎙️.");
        } else if (requestCode == REQ_AUDIO) {
            status.setText("L'autorisation du micro est nécessaire pour dicter une mémoire.");
        }
    }

    @Override
    protected void onDestroy() {
        cleanupRecognizer();
        super.onDestroy();
    }
}
