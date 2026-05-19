package com.basir.ai.game;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Game-side controller. Owns the {@link GameState}, the current {@link Scene},
 * and the auxiliary {@link Haptics} / {@link SpatialAudio} / TTS instances.
 *
 * Activities call into the engine for every gameplay action
 * (stepForward, turnLeft, interact, describe, …). The engine never touches
 * Views directly — instead it pushes Arabic strings + haptic/audio cues
 * to a {@link Listener}, so the activity can render or speak them however
 * it likes.
 */
public class GameEngine {

    public interface Listener {
        void onSpeak(String arabic);
        void onState(GameState state, Scene scene);
        void onDialogue(Dialogue.Node node);
        void onMessage(String arabic);
        void onGameOver(String arabic);
    }

    private final Context ctx;
    private final GameState state;
    private final Haptics haptics;
    private final SpatialAudio audio;
    private final TextToSpeech tts;
    private Scene scene;
    private Listener listener;

    public GameEngine(Context ctx, GameState state, TextToSpeech tts) {
        this.ctx = ctx.getApplicationContext();
        this.state = state;
        this.tts = tts;
        this.haptics = new Haptics(this.ctx);
        this.audio = new SpatialAudio();
        this.scene = SceneLibrary.get(state.currentScene);
        if (this.scene == null) {
            this.scene = SceneLibrary.get("bedroom");
            state.currentScene = "bedroom";
        }
        if (tts != null) {
            try {
                tts.setLanguage(new Locale("ar"));
                tts.setSpeechRate(0.95f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    public void onStart(String s) {}
                    public void onDone(String s) {}
                    public void onError(String s) {}
                });
            } catch (Throwable ignored) {}
        }
    }

    public void setListener(Listener l) { this.listener = l; }
    public GameState state() { return state; }
    public Scene scene() { return scene; }

    public void pushState() {
        if (listener != null) listener.onState(state, scene);
    }

    /** Initial announcement on entering a scene. */
    public void announceScene() {
        speak(scene.arabicName + ". " + scene.ambientDescription);
        playAmbient();
        pushState();
    }

    // ----- Movement -----

    public void stepForward() {
        int nx = state.playerX + state.facing.dx();
        int ny = state.playerY + state.facing.dy();
        if (!scene.walkable(nx, ny)) {
            SceneObject o = scene.objectAt(nx, ny);
            Tile t = scene.tileAt(nx, ny);
            if (o != null) {
                haptics.pulseObstacle();
                speak("أمامك " + o.label + ". انقر مرتين للتفاعل.");
            } else if (t == Tile.WALL) {
                haptics.pulseObstacle();
                speak("جدار. لا يمكن المرور.");
            } else {
                haptics.pulseObstacle();
                speak("لا يمكن التقدم هنا.");
            }
            return;
        }
        state.playerX = nx;
        state.playerY = ny;
        state.energy = Math.max(0, state.energy - 1);
        haptics.tap();
        // Detect if the new tile is risky
        Tile t = scene.tileAt(nx, ny);
        if (t == Tile.ROAD) {
            haptics.pulseDanger();
            speak("انتبه! أنت تقف على طريق سيارات.");
        } else if (t == Tile.STAIRS) {
            haptics.pulseStairs();
            speak("درج. خطوة بخطوة.");
        } else if (t == Tile.PUDDLE) {
            speak("بركة ماء. حذارِ من الانزلاق.");
        }
        SceneObject onObj = scene.objectAt(nx, ny);
        if (onObj != null) {
            speak("تقف على " + onObj.label + ". انقر مرتين للتفاعل.");
        } else {
            scanNeighbors(); // light scan without long descriptions
        }
        pushState();
    }

    public void turnLeft() {
        state.facing = state.facing.turnLeft();
        haptics.tap();
        speak("استدرت يسارًا. تواجه " + state.facing.arabicName() + ".");
        scanNeighbors();
        pushState();
    }

    public void turnRight() {
        state.facing = state.facing.turnRight();
        haptics.tap();
        speak("استدرت يمينًا. تواجه " + state.facing.arabicName() + ".");
        scanNeighbors();
        pushState();
    }

    public void stop() {
        haptics.tap();
        speak("توقفت. " + (scene.ambientDescription));
    }

    public void reorient() {
        state.facing = scene.startFacing;
        haptics.pulseStairs();
        speak("أعدت تحديد الاتجاه. تواجه " + state.facing.arabicName() + " الآن.");
        pushState();
    }

    /** Long-press: a full sweep of the player's surroundings. */
    public void describeEnvironment() {
        StringBuilder sb = new StringBuilder();
        sb.append("أنت في ").append(scene.arabicName).append(". ");
        Tile here = scene.tileAt(state.playerX, state.playerY);
        sb.append("تحت قدميك ").append(here.arabicSurface()).append(". ");
        sb.append("تواجه ").append(state.facing.arabicName()).append(". ");
        // List nearby objects (within radius 3)
        List<String> near = new ArrayList<>();
        for (SceneObject o : scene.objects) {
            int dx = o.x - state.playerX;
            int dy = o.y - state.playerY;
            int dist = Math.abs(dx) + Math.abs(dy);
            if (dist == 0) continue;
            if (dist <= 4) {
                String rel = state.facing.relativeLabelArabic(dx, dy);
                near.add(rel + " " + o.label + " على بُعد " + dist + " خطوات");
            }
        }
        if (near.isEmpty()) {
            sb.append("لا يوجد شيء قريب منك في حدود أربع خطوات.");
        } else {
            sb.append("حولك: ");
            for (int i = 0; i < near.size(); i++) {
                sb.append(near.get(i));
                sb.append(i == near.size() - 1 ? "." : "، ");
            }
        }
        speak(sb.toString());
        playAmbient();
        pushState();
    }

    /** Quick scan for the spoken "what's around me right now". */
    private void scanNeighbors() {
        // Check the cell directly ahead.
        int fx = state.playerX + state.facing.dx();
        int fy = state.playerY + state.facing.dy();
        SceneObject ahead = scene.objectAt(fx, fy);
        Tile t = scene.tileAt(fx, fy);
        if (ahead != null) {
            haptics.pulseNear();
            if (ahead.soundFreq > 0) {
                audio.playPanned(ahead.soundFreq, 0f, 0.3f, 200);
            }
        } else if (t == Tile.WALL) {
            haptics.pulseNear();
        }
    }

    private void playAmbient() {
        if (scene.ambientFreq > 0) {
            audio.playPanned(scene.ambientFreq, 0f, 0.20f, 320);
        }
        for (SceneObject o : scene.objects) {
            if (o.soundFreq <= 0) continue;
            int dx = o.x - state.playerX;
            int dy = o.y - state.playerY;
            float rx;
            switch (state.facing) {
                case NORTH: rx = dx;  break;
                case EAST:  rx = dy;  break;
                case SOUTH: rx = -dx; break;
                case WEST:  rx = -dy; break;
                default:    rx = dx;
            }
            float pan = clampF(rx / 3f, -1f, 1f);
            int dist = Math.abs(dx) + Math.abs(dy);
            float vol = Math.max(0.05f, 0.4f - dist * 0.05f);
            audio.playPanned(o.soundFreq, pan, vol, 250);
        }
    }

    private float clampF(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ----- Interaction -----

    public void interact() {
        SceneObject target = findInteractTarget();
        if (target == null) {
            speak("لا يوجد شيء للتفاعل معه هنا.");
            return;
        }
        if ("exit".equals(target.interact) && target.exitsTo != null) {
            travelTo(target.exitsTo, target.label);
            return;
        }
        if (target.interact != null && target.interact.startsWith("dlg_")) {
            Dialogue.Node n = Dialogue.get(target.interact);
            if (n != null && listener != null) {
                listener.onDialogue(n);
                return;
            }
        }
        runItemInteraction(target);
    }

    private SceneObject findInteractTarget() {
        // Prefer the cell directly ahead, then the player's cell.
        int fx = state.playerX + state.facing.dx();
        int fy = state.playerY + state.facing.dy();
        SceneObject ahead = scene.objectAt(fx, fy);
        if (ahead != null) return ahead;
        return scene.objectAt(state.playerX, state.playerY);
    }

    private void runItemInteraction(SceneObject o) {
        switch (o.interact == null ? "" : o.interact) {
            case "rest":
                state.energy = Math.min(100, state.energy + 20);
                speak("ترتاح قليلًا على السرير. تستعيد طاقتك.");
                haptics.reward();
                break;
            case "alarm":
                speak("المنبّه يقول: الساعة الثامنة وعشر دقائق. الحافلة بعد عشرين دقيقة.");
                break;
            case "phone":
                speak("الهاتف يقرأ آخر إشعار: 'مذكّرة: محاضرة الرياضيات اليوم.'");
                state.technology = Math.min(100, state.technology + 1);
                break;
            case "dress":
                speak("ترتب ملابسك. تشعر باستعداد أفضل لليوم.");
                state.confidence = Math.min(100, state.confidence + 1);
                break;
            case "take_cane":
                if (state.devices.add("cane")) {
                    speak("التقطت العصا. سترشدك في الشارع.");
                    state.mobility = Math.min(100, state.mobility + 2);
                    haptics.reward();
                } else {
                    speak("معك العصا أصلًا.");
                }
                break;
            case "eat":
                state.energy = Math.min(100, state.energy + 15);
                speak("تأكل بهدوء. طاقتك ترتفع.");
                break;
            case "ride_bus":
                state.energy = Math.max(0, state.energy - 3);
                speak("ركبت الحافلة. تنطلق نحو الجامعة.");
                travelTo("university", "الجامعة");
                break;
            case "sit":
                state.energy = Math.min(100, state.energy + 5);
                speak("جلست في الصف الأول. سهل عليك سماع الأستاذ.");
                break;
            case "record":
                state.technology = Math.min(100, state.technology + 2);
                speak("شغّلت المسجّل الصوتي. ستراجع المحاضرة لاحقًا.");
                break;
            case "study":
                state.technology = Math.min(100, state.technology + 3);
                state.energy = Math.max(0, state.energy - 5);
                speak("تستمع للكتاب الصوتي. تتعلم بهدوء.");
                break;
            case "study_braille":
                state.communication = Math.min(100, state.communication + 2);
                state.energy = Math.max(0, state.energy - 4);
                speak("تمرّن أصابعك على بريل. القراءة تتحسّن.");
                break;
            case "buy_smart_cane":
                buyDevice("smart_cane", "العصا الذكية", 200, +5, 0, +2);
                break;
            case "buy_glasses":
                buyDevice("smart_glasses", "النظارات الإلكترونية", 500, 0, +3, +1);
                break;
            case "buy_ai":
                buyDevice("ai_assistant", "مساعد الذكاء الاصطناعي", 150, 0, +5, +2);
                break;
            default:
                speak("تفاعلت مع " + o.label + ".");
        }
        haptics.tap();
        pushState();
    }

    private void buyDevice(String id, String name, int price,
                           int dMobility, int dTech, int dConfidence) {
        if (state.devices.contains(id)) {
            speak("لديك " + name + " بالفعل.");
            return;
        }
        if (state.money < price) {
            speak("سعر " + name + " هو " + price
                    + ". لا تملك مالًا كافيًا. لديك " + state.money + ".");
            return;
        }
        state.money -= price;
        state.devices.add(id);
        state.mobility += dMobility;
        state.technology += dTech;
        state.confidence += dConfidence;
        state.clamp();
        haptics.reward();
        speak("اشتريت " + name + ". تجربتك للحياة ستتغير.");
    }

    public void travelTo(String sceneId, String spokenLabel) {
        Scene next = SceneLibrary.get(sceneId);
        if (next == null) {
            speak("لا يمكن الذهاب إلى " + spokenLabel + " الآن.");
            return;
        }
        scene = next;
        state.currentScene = sceneId;
        state.playerX = next.startX;
        state.playerY = next.startY;
        state.facing = next.startFacing;
        haptics.reward();
        speak("انتقلت إلى " + next.arabicName + ". " + next.ambientDescription);
        playAmbient();
        pushState();
    }

    public void applyChoice(Dialogue.Choice c) {
        state.communication += c.dCommunication;
        state.confidence    += c.dConfidence;
        state.mobility      += c.dMobility;
        state.technology    += c.dTech;
        state.energy        += c.dEnergy;
        state.money         += c.dMoney;
        state.friendship    += c.dFriendship;
        state.reputation    += c.dReputation;
        if (c.addDevice != null) state.devices.add(c.addDevice);
        state.clamp();
        haptics.reward();
        speak(c.outcome);
        if (c.next != null && listener != null) {
            Dialogue.Node n = Dialogue.get(c.next);
            if (n != null) listener.onDialogue(n);
        }
        pushState();
    }

    public void endDay() {
        state.day += 1;
        state.energy = Math.min(100, state.energy + 30);
        haptics.reward();
        speak("انتهى اليوم " + (state.day - 1) + ". أنت الآن في اليوم " + state.day + ".");
        pushState();
    }

    private void speak(String text) {
        if (listener != null) listener.onSpeak(text);
        if (tts != null) {
            try {
                tts.speak(text, TextToSpeech.QUEUE_ADD, null, "blind-life-" + System.nanoTime());
            } catch (Throwable ignored) {}
        }
    }

    /** Short HUD line for sighted observers / TalkBack focus. */
    public String hudLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("اليوم ").append(state.day);
        sb.append("  •  طاقة ").append(state.energy);
        sb.append("  •  مال ").append(state.money);
        sb.append("\n").append(scene.arabicName);
        sb.append("  •  ").append(state.facing.arabicName());
        return sb.toString();
    }
}
