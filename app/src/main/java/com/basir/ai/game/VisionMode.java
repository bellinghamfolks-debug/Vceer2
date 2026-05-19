package com.basir.ai.game;

/**
 * Vision impairment modes selectable at the start of "حياة كفيف" (Blind Life).
 * Each value changes how WorldView renders the scene and how strongly the game
 * relies on audio/haptic cues. The gameplay mechanics themselves stay the
 * same — only the *visual layer* changes, so a sighted player can switch
 * modes and feel the difference in real time.
 */
public enum VisionMode {
    /** Totally blind. The screen stays nearly black; the player navigates by
     *  TTS, sound and vibration only. A faint compass HUD remains for
     *  sighted observers / TalkBack focus. */
    BLIND_TOTAL,
    /** Severe low vision. Heavy blur, very low contrast, large shapes only. */
    LOW_VISION_SEVERE,
    /** Blurry vision. Moderate blur, colors approximate, faces unreadable. */
    BLURRY,
    /** Central vision only — like macular preservation lost in periphery. A
     *  circular keyhole in the middle is sharp; everything else is black. */
    CENTRAL_ONLY,
    /** Peripheral vision only — like advanced glaucoma. The center is black;
     *  shapes are only visible around the edges. */
    PERIPHERAL_ONLY,
    /** Fully sighted observer mode. Used by sighted players who want to
     *  experience the rest of the game's mechanics without a vision filter. */
    SIGHTED;

    public String arabicName() {
        switch (this) {
            case BLIND_TOTAL:        return "كفيف كلي";
            case LOW_VISION_SEVERE:  return "ضعيف بصر شديد";
            case BLURRY:             return "رؤية ضبابية";
            case CENTRAL_ONLY:       return "رؤية مركزية فقط";
            case PERIPHERAL_ONLY:    return "رؤية طرفية فقط";
            case SIGHTED:            return "وضع المبصر";
        }
        return name();
    }

    public String arabicDescription() {
        switch (this) {
            case BLIND_TOTAL:
                return "تعتمد على الصوت والاهتزاز والوصف فقط. الشاشة شبه مظلمة.";
            case LOW_VISION_SEVERE:
                return "أشكال ضخمة وغير واضحة، ألوان شاحبة، تباين منخفض.";
            case BLURRY:
                return "كل شيء ضبابي. الوجوه غير قابلة للتمييز.";
            case CENTRAL_ONLY:
                return "ترى دائرة صغيرة في المنتصف فقط. الأطراف مظلمة.";
            case PERIPHERAL_ONLY:
                return "المنتصف مظلم. ترى الحركة من زوايا عينيك فقط.";
            case SIGHTED:
                return "رؤية كاملة. مناسب للمبصرين الذين يريدون تجربة المحتوى.";
        }
        return "";
    }
}
