package com.basir.ai;

import java.util.HashMap;
import java.util.Map;

/**
 * v3.0 — deterministic LaTeX-to-spoken-text converter.
 *
 * Why this exists
 * ───────────────
 *   The v2.9 math-extraction flow asked Gemini to produce both spoken
 *   form AND LaTeX in a single response. That doubled the output
 *   length, exhausted maxOutputTokens on dense pages, and depended on
 *   the model remembering an exact format rule that it often dropped.
 *   v3.0 splits the work: Gemini only emits LaTeX (small, well-defined
 *   output it is highly trained on); this class converts each LaTeX
 *   expression into spoken Arabic or English on-device. The spoken
 *   conversion is deterministic — never fails, never truncates,
 *   identical output every run.
 *
 * Coverage
 * ────────
 *   Common K-12 / university math: arithmetic, algebra, calculus,
 *   trigonometry, basic set theory, common Greek letters, sub/super
 *   scripts, fractions, roots, integrals, sums, products, limits,
 *   simple matrices. Unknown LaTeX commands are emitted as their
 *   literal name (e.g. \foo → "foo") rather than crashing.
 *
 * What this class deliberately does NOT do
 * ────────────────────────────────────────
 *   - Solve math. It converts notation to speech only.
 *   - Re-typeset matrices visually. It announces rows by content.
 *   - Pronounce numbers as words ("five" vs "5"). The TTS engine
 *     does that natively — keeping digits as digits avoids losing
 *     precision on decimals and large numbers.
 *
 * Vocabulary alignment with v2.9
 * ──────────────────────────────
 *   The Arabic mappings here match the v2.9 mathExtractionInstruction
 *   vocabulary table (تكامل, مجموع, مشتقة, الجذر التربيعي لـ, تربيع,
 *   تكعيب, باي, ألفا, جا/جتا/ظا, نها, etc.) so a user familiar with
 *   one screen recognises the other.
 */
public final class LatexToSpeech {

    private LatexToSpeech() {}

    /** Convert a LaTeX expression to a spoken-form string. */
    public static String convert(String latex, boolean arabic) {
        if (latex == null) return "";
        String s = latex.trim();
        if (s.isEmpty()) return "";
        // Strip outer $...$ or $$...$$ if present.
        if (s.startsWith("$$") && s.endsWith("$$")) s = s.substring(2, s.length() - 2);
        else if (s.startsWith("$") && s.endsWith("$")) s = s.substring(1, s.length() - 1);
        Scanner sc = new Scanner(s);
        StringBuilder out = new StringBuilder();
        renderUntil(sc, out, arabic, '\0');
        return clean(out.toString());
    }

    // ─────────────────────────────────────────────────────────────────
    // Scanner: a tiny cursor over the LaTeX string
    // ─────────────────────────────────────────────────────────────────

    private static final class Scanner {
        final String s;
        int p;
        Scanner(String s) { this.s = s; this.p = 0; }
        boolean more() { return p < s.length(); }
        char peek() { return more() ? s.charAt(p) : '\0'; }
        char next() { return s.charAt(p++); }
        void skipWs() { while (more() && Character.isWhitespace(peek())) p++; }

        /** Read a \command starting at the current backslash. Returns
         *  the full "\name" including the backslash. */
        String readCommand() {
            if (peek() != '\\') return "";
            p++;
            if (!more()) return "\\";
            char c = peek();
            if (!Character.isLetter(c)) { p++; return "\\" + c; }
            int start = p;
            while (more() && Character.isLetter(peek())) p++;
            return "\\" + s.substring(start, p);
        }

        /** Read a {...} group (balanced) and return its content. */
        String readGroup() {
            if (peek() != '{') return "";
            p++;
            int depth = 1;
            int start = p;
            while (more() && depth > 0) {
                char c = next();
                if (c == '\\' && more()) { p++; continue; }   // escaped char
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return s.substring(start, p - 1);
                }
            }
            return s.substring(start);
        }

        /** Read an optional [...] argument (used by \sqrt[n]{}). */
        String readBracketGroup() {
            if (peek() != '[') return "";
            p++;
            int start = p;
            while (more() && peek() != ']') p++;
            String r = s.substring(start, p);
            if (more()) p++;
            return r;
        }

        /** Read the next argument: either a {group} or a single token. */
        String readArg() {
            skipWs();
            if (peek() == '{') return readGroup();
            if (peek() == '\\') return readCommand();
            if (more()) return String.valueOf(next());
            return "";
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Main render loop
    // ─────────────────────────────────────────────────────────────────

    private static void renderUntil(Scanner sc, StringBuilder out, boolean ar, char stop) {
        while (sc.more()) {
            char c = sc.peek();
            if (c == stop) return;

            if (c == '\\') {
                handleCommand(sc.readCommand(), sc, out, ar);
            } else if (c == '{') {
                String inner = sc.readGroup();
                out.append(' ');
                renderUntil(new Scanner(inner), out, ar, '\0');
                out.append(' ');
            } else if (c == '^') {
                sc.next();
                String arg = sc.readArg();
                out.append(' ').append(ar ? superKey(arg, true) : superKey(arg, false));
                if (!isSimplePower(arg)) {
                    out.append(' ');
                    renderUntil(new Scanner(arg), out, ar, '\0');
                }
                out.append(' ');
            } else if (c == '_') {
                sc.next();
                String arg = sc.readArg();
                out.append(ar ? " تحت " : " sub ");
                renderUntil(new Scanner(arg), out, ar, '\0');
                out.append(' ');
            } else if (c == '+') { sc.next(); out.append(ar ? " زائد " : " plus "); }
              else if (c == '-') { sc.next(); out.append(ar ? " ناقص " : " minus "); }
              else if (c == '=') { sc.next(); out.append(ar ? " يساوي " : " equals "); }
              else if (c == '/') { sc.next(); out.append(ar ? " على " : " over "); }
              else if (c == '*') { sc.next(); out.append(ar ? " ضرب " : " times "); }
              else if (c == '<') { sc.next(); out.append(ar ? " أصغر من " : " less than "); }
              else if (c == '>') { sc.next(); out.append(ar ? " أكبر من " : " greater than "); }
              else if (c == ',') { sc.next(); out.append(ar ? "، " : ", "); }
              else if (c == ';') { sc.next(); out.append(ar ? "؛ " : "; "); }
              else if (c == '.') { sc.next(); out.append("."); }
              else if (c == '(') { sc.next(); out.append(ar ? " مفتوح قوس " : " open paren "); }
              else if (c == ')') { sc.next(); out.append(ar ? " مغلق قوس " : " close paren "); }
              else if (c == '[') { sc.next(); out.append(ar ? " مفتوح قوس مربع " : " open bracket "); }
              else if (c == ']') { sc.next(); out.append(ar ? " مغلق قوس مربع " : " close bracket "); }
              else if (c == '|') { sc.next(); out.append(ar ? " القيمة المطلقة لـ " : " absolute value of "); }
              else if (c == '!') { sc.next(); out.append(ar ? " مضروب " : " factorial "); }
              else if (c == '\'') { sc.next(); out.append(ar ? " مشتقة " : " prime "); }
              else if (Character.isDigit(c)) {
                StringBuilder num = new StringBuilder();
                while (sc.more() && (Character.isDigit(sc.peek()) || sc.peek() == '.')) {
                    num.append(sc.next());
                }
                out.append(' ').append(num).append(' ');
            } else if (Character.isLetter(c) || isArabicLetter(c)) {
                sc.next();
                out.append(' ').append(letterName(c, ar)).append(' ');
            } else if (Character.isWhitespace(c)) {
                sc.next();
            } else {
                // Unknown character: skip silently
                sc.next();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Command handlers
    // ─────────────────────────────────────────────────────────────────

    private static void handleCommand(String cmd, Scanner sc, StringBuilder out, boolean ar) {
        // 1) Greek letters
        String greek = greekLetter(cmd, ar);
        if (greek != null) { out.append(' ').append(greek).append(' '); return; }

        // 2) Trig / log / common functions
        String fn = functionName(cmd, ar);
        if (fn != null) { out.append(' ').append(fn).append(' '); return; }

        // 3) Operators and relation symbols
        String op = operatorName(cmd, ar);
        if (op != null) { out.append(' ').append(op).append(' '); return; }

        // 4) Spacing / formatting commands: silently consume
        switch (cmd) {
            case "\\,": case "\\;": case "\\:": case "\\!":
            case "\\quad": case "\\qquad": case "\\,\\":
            case "\\left": case "\\right":
            case "\\bigl": case "\\bigr": case "\\Bigl": case "\\Bigr":
                return;
            case "\\\\":
                out.append(ar ? "، الصف التالي " : ", next row ");
                return;
        }

        // 5) Composite commands with arguments
        switch (cmd) {
            case "\\frac":
            case "\\dfrac":
            case "\\tfrac":
                renderFraction(sc, out, ar); return;
            case "\\sqrt":
                renderRoot(sc, out, ar); return;
            case "\\int":
                out.append(ar ? " تكامل " : " integral ");
                renderSubSup(sc, out, ar, /*sumLike*/ true); return;
            case "\\iint":
                out.append(ar ? " تكامل مزدوج " : " double integral ");
                renderSubSup(sc, out, ar, true); return;
            case "\\iiint":
                out.append(ar ? " تكامل ثلاثي " : " triple integral ");
                renderSubSup(sc, out, ar, true); return;
            case "\\oint":
                out.append(ar ? " تكامل خطّي " : " contour integral ");
                renderSubSup(sc, out, ar, true); return;
            case "\\sum":
                out.append(ar ? " مجموع " : " sum ");
                renderSubSup(sc, out, ar, true); return;
            case "\\prod":
                out.append(ar ? " حاصل ضرب " : " product ");
                renderSubSup(sc, out, ar, true); return;
            case "\\lim":
                out.append(ar ? " نهاية عندما " : " limit as ");
                renderSubSup(sc, out, ar, false); return;
            case "\\limsup":
                out.append(ar ? " نهاية عليا " : " limit superior ");
                renderSubSup(sc, out, ar, false); return;
            case "\\liminf":
                out.append(ar ? " نهاية دنيا " : " limit inferior ");
                renderSubSup(sc, out, ar, false); return;
            case "\\bar":
            case "\\overline": {
                String arg = sc.readArg();
                renderUntil(new Scanner(arg), out, ar, '\0');
                out.append(ar ? " شرطة علوية " : " bar "); return;
            }
            case "\\hat":
            case "\\widehat": {
                String arg = sc.readArg();
                renderUntil(new Scanner(arg), out, ar, '\0');
                out.append(ar ? " قبعة " : " hat "); return;
            }
            case "\\vec": {
                String arg = sc.readArg();
                out.append(ar ? " متجه " : " vector ");
                renderUntil(new Scanner(arg), out, ar, '\0');
                out.append(' '); return;
            }
            case "\\begin": {
                String env = sc.readArg();
                if (env.contains("matrix") || env.contains("array")) {
                    out.append(ar ? " مصفوفة: " : " matrix: ");
                    String body = readUntilEnd(sc, env);
                    // Replace & with ", " and \\ with " ; "
                    body = body.replace("&", ar ? "، " : ", ")
                                .replace("\\\\", ar ? " ؛ " : " ; ");
                    renderUntil(new Scanner(body), out, ar, '\0');
                }
                return;
            }
            case "\\end":
                sc.readArg();
                return;
            case "\\text":
            case "\\textrm":
            case "\\mathrm":
            case "\\textit":
            case "\\mathbf":
            case "\\boldsymbol":
            case "\\mathit": {
                String arg = sc.readArg();
                out.append(' ').append(arg).append(' '); return;
            }
            case "\\dots": case "\\ldots": case "\\cdots":
                out.append(ar ? " ... " : " dot dot dot "); return;
        }

        // 6) Unknown command — emit its tail as-is so the user sees
        //    what was missed instead of silent dropping.
        if (cmd.length() > 1) {
            out.append(' ').append(cmd.substring(1)).append(' ');
        }
    }

    private static void renderFraction(Scanner sc, StringBuilder out, boolean ar) {
        String num = sc.readArg();
        String den = sc.readArg();
        if (ar) {
            renderUntil(new Scanner(num), out, true, '\0');
            out.append(" على ");
            renderUntil(new Scanner(den), out, true, '\0');
        } else {
            renderUntil(new Scanner(num), out, false, '\0');
            out.append(" over ");
            renderUntil(new Scanner(den), out, false, '\0');
        }
        out.append(' ');
    }

    private static void renderRoot(Scanner sc, StringBuilder out, boolean ar) {
        sc.skipWs();
        String index = "";
        if (sc.peek() == '[') index = sc.readBracketGroup().trim();
        String arg = sc.readArg();
        if (index.isEmpty()) {
            out.append(ar ? " الجذر التربيعي لـ " : " the square root of ");
        } else if ("3".equals(index)) {
            out.append(ar ? " الجذر التكعيبي لـ " : " the cube root of ");
        } else {
            if (ar) out.append(" الجذر النوني ");
            else    out.append(" the ").append(index).append("th root of ");
        }
        renderUntil(new Scanner(arg), out, ar, '\0');
        out.append(' ');
    }

    /** Pick up an optional sub/superscript that immediately follows
     *  a sum/integral/lim and verbalise it. */
    private static void renderSubSup(Scanner sc, StringBuilder out, boolean ar, boolean sumLike) {
        sc.skipWs();
        String lo = null, hi = null;
        if (sc.peek() == '_') { sc.next(); lo = sc.readArg(); sc.skipWs(); }
        if (sc.peek() == '^') { sc.next(); hi = sc.readArg(); }
        else if (sc.peek() == '_') { sc.next(); lo = sc.readArg(); sc.skipWs(); }
        // Sub/super can come in either order; do another sweep in case.
        if (lo != null) {
            out.append(ar ? (sumLike ? " من " : " ") : (sumLike ? " from " : " "));
            renderUntil(new Scanner(lo), out, ar, '\0');
            out.append(' ');
        }
        if (hi != null) {
            out.append(ar ? " إلى " : " to ");
            renderUntil(new Scanner(hi), out, ar, '\0');
            out.append(' ');
        }
        if (sumLike) out.append(ar ? "للقيمة " : "of ");
    }

    private static String readUntilEnd(Scanner sc, String envName) {
        String marker = "\\end{" + envName + "}";
        int idx = sc.s.indexOf(marker, sc.p);
        if (idx < 0) {
            String r = sc.s.substring(sc.p);
            sc.p = sc.s.length();
            return r;
        }
        String r = sc.s.substring(sc.p, idx);
        sc.p = idx + marker.length();
        return r;
    }

    // ─────────────────────────────────────────────────────────────────
    // Lookup tables
    // ─────────────────────────────────────────────────────────────────

    private static String greekLetter(String cmd, boolean ar) {
        Map<String, String[]> m = GREEK;
        String[] pair = m.get(cmd);
        if (pair == null) return null;
        return ar ? pair[0] : pair[1];
    }

    private static final Map<String, String[]> GREEK = new HashMap<>();
    static {
        // [arabic, english]
        GREEK.put("\\alpha",   new String[]{"ألفا", "alpha"});
        GREEK.put("\\beta",    new String[]{"بيتا", "beta"});
        GREEK.put("\\gamma",   new String[]{"غاما", "gamma"});
        GREEK.put("\\delta",   new String[]{"دلتا", "delta"});
        GREEK.put("\\epsilon", new String[]{"إبسلون", "epsilon"});
        GREEK.put("\\varepsilon", new String[]{"إبسلون", "epsilon"});
        GREEK.put("\\zeta",    new String[]{"زيتا", "zeta"});
        GREEK.put("\\eta",     new String[]{"إيتا", "eta"});
        GREEK.put("\\theta",   new String[]{"ثيتا", "theta"});
        GREEK.put("\\vartheta",new String[]{"ثيتا", "theta"});
        GREEK.put("\\iota",    new String[]{"أيوتا", "iota"});
        GREEK.put("\\kappa",   new String[]{"كابا", "kappa"});
        GREEK.put("\\lambda",  new String[]{"لامبدا", "lambda"});
        GREEK.put("\\mu",      new String[]{"ميو", "mu"});
        GREEK.put("\\nu",      new String[]{"نيو", "nu"});
        GREEK.put("\\xi",      new String[]{"كساي", "xi"});
        GREEK.put("\\pi",      new String[]{"باي", "pi"});
        GREEK.put("\\rho",     new String[]{"رو", "rho"});
        GREEK.put("\\sigma",   new String[]{"سيغما", "sigma"});
        GREEK.put("\\tau",     new String[]{"تاو", "tau"});
        GREEK.put("\\upsilon", new String[]{"أبسلون", "upsilon"});
        GREEK.put("\\phi",     new String[]{"فاي", "phi"});
        GREEK.put("\\varphi",  new String[]{"فاي", "phi"});
        GREEK.put("\\chi",     new String[]{"خاي", "chi"});
        GREEK.put("\\psi",     new String[]{"بساي", "psi"});
        GREEK.put("\\omega",   new String[]{"أوميغا", "omega"});
        GREEK.put("\\Gamma",   new String[]{"غاما الكبيرة", "capital gamma"});
        GREEK.put("\\Delta",   new String[]{"دلتا الكبيرة", "capital delta"});
        GREEK.put("\\Theta",   new String[]{"ثيتا الكبيرة", "capital theta"});
        GREEK.put("\\Lambda",  new String[]{"لامبدا الكبيرة", "capital lambda"});
        GREEK.put("\\Xi",      new String[]{"كساي الكبيرة", "capital xi"});
        GREEK.put("\\Pi",      new String[]{"باي الكبيرة", "capital pi"});
        GREEK.put("\\Sigma",   new String[]{"سيغما الكبيرة", "capital sigma"});
        GREEK.put("\\Phi",     new String[]{"فاي الكبيرة", "capital phi"});
        GREEK.put("\\Psi",     new String[]{"بساي الكبيرة", "capital psi"});
        GREEK.put("\\Omega",   new String[]{"أوميغا الكبيرة", "capital omega"});
    }

    private static String functionName(String cmd, boolean ar) {
        switch (cmd) {
            case "\\sin":    return ar ? "جا" : "sine";
            case "\\cos":    return ar ? "جتا" : "cosine";
            case "\\tan":    return ar ? "ظا" : "tangent";
            case "\\cot":    return ar ? "ظتا" : "cotangent";
            case "\\sec":    return ar ? "قاطع" : "secant";
            case "\\csc":    return ar ? "قاطع تمام" : "cosecant";
            case "\\arcsin": return ar ? "قوس جا" : "arcsine";
            case "\\arccos": return ar ? "قوس جتا" : "arccosine";
            case "\\arctan": return ar ? "قوس ظا" : "arctangent";
            case "\\sinh":   return ar ? "جا زائدية" : "hyperbolic sine";
            case "\\cosh":   return ar ? "جتا زائدية" : "hyperbolic cosine";
            case "\\tanh":   return ar ? "ظا زائدية" : "hyperbolic tangent";
            case "\\log":    return ar ? "لوغاريتم" : "log";
            case "\\ln":     return ar ? "لوغاريتم طبيعي" : "natural log";
            case "\\exp":    return ar ? "أُسّي" : "exponential";
            case "\\max":    return ar ? "أكبر قيمة" : "max";
            case "\\min":    return ar ? "أصغر قيمة" : "min";
            case "\\det":    return ar ? "محدّد" : "determinant";
            case "\\dim":    return ar ? "بعد" : "dimension";
            case "\\deg":    return ar ? "درجة" : "degree";
            case "\\arg":    return ar ? "سعة" : "argument";
            case "\\mod":    return ar ? "باقي" : "mod";
        }
        return null;
    }

    private static String operatorName(String cmd, boolean ar) {
        switch (cmd) {
            case "\\leq":    case "\\le":   return ar ? " أصغر من أو يساوي " : " less than or equal to ";
            case "\\geq":    case "\\ge":   return ar ? " أكبر من أو يساوي " : " greater than or equal to ";
            case "\\neq":    case "\\ne":   return ar ? " لا يساوي " : " not equal to ";
            case "\\approx":                return ar ? " يقارب " : " approximately equal to ";
            case "\\equiv":                 return ar ? " يكافئ " : " equivalent to ";
            case "\\times":                 return ar ? " ضرب " : " times ";
            case "\\cdot":                  return ar ? " ضرب " : " dot ";
            case "\\div":                   return ar ? " قسمة " : " divided by ";
            case "\\pm":                    return ar ? " زائد أو ناقص " : " plus or minus ";
            case "\\mp":                    return ar ? " ناقص أو زائد " : " minus or plus ";
            case "\\infty":                 return ar ? " ما لا نهاية " : " infinity ";
            case "\\partial":               return ar ? " مشتقة جزئية " : " partial ";
            case "\\nabla":                 return ar ? " نابلا " : " nabla ";
            case "\\in":                    return ar ? " ينتمي إلى " : " in ";
            case "\\notin":                 return ar ? " لا ينتمي إلى " : " not in ";
            case "\\subset":                return ar ? " مجموعة جزئية من " : " subset of ";
            case "\\subseteq":              return ar ? " مجموعة جزئية أو يساوي " : " subset or equal ";
            case "\\supset":                return ar ? " مجموعة فوقية " : " superset of ";
            case "\\cup":                   return ar ? " اتحاد " : " union ";
            case "\\cap":                   return ar ? " تقاطع " : " intersection ";
            case "\\emptyset":              return ar ? " مجموعة فارغة " : " empty set ";
            case "\\forall":                return ar ? " لكل " : " for all ";
            case "\\exists":                return ar ? " يوجد " : " there exists ";
            case "\\neg":                   return ar ? " ليس " : " not ";
            case "\\land":                  return ar ? " و " : " and ";
            case "\\lor":                   return ar ? " أو " : " or ";
            case "\\to":                    return ar ? " يؤول إلى " : " approaches ";
            case "\\rightarrow":            return ar ? " يستلزم " : " implies ";
            case "\\Rightarrow":            return ar ? " يستلزم " : " implies ";
            case "\\leftarrow":             return ar ? " مستلزَم من " : " implied by ";
            case "\\leftrightarrow":        return ar ? " إذا وفقط إذا " : " if and only if ";
            case "\\iff":                   return ar ? " إذا وفقط إذا " : " if and only if ";
            case "\\mapsto":                return ar ? " يُرسَل إلى " : " maps to ";
            case "\\bot":                   return ar ? " متعامد " : " perpendicular ";
            case "\\angle":                 return ar ? " زاوية " : " angle ";
            case "\\triangle":              return ar ? " مثلّث " : " triangle ";
            case "\\circ":                  return ar ? " درجة " : " degree ";
            case "\\prime":                 return ar ? " شَرطة " : " prime ";
            case "\\Re":                    return ar ? " الجزء الحقيقي " : " real part ";
            case "\\Im":                    return ar ? " الجزء التخيُّلي " : " imaginary part ";
            case "\\sqrt":                  return null;  // handled separately
        }
        return null;
    }

    /** Common power names: ^2 → "squared", ^3 → "cubed". Returns the
     *  full phrase ("squared" / "cubed" / "to the power of") and the
     *  caller appends the actual exponent if not a simple recognized one. */
    private static String superKey(String arg, boolean ar) {
        String trimmed = arg.trim();
        if ("2".equals(trimmed)) return ar ? "تربيع" : "squared";
        if ("3".equals(trimmed)) return ar ? "تكعيب" : "cubed";
        if ("-1".equals(trimmed) || "{-1}".equals(trimmed))
            return ar ? "معكوس" : "inverse";
        return ar ? "أُسّ" : "to the power of";
    }

    private static boolean isSimplePower(String arg) {
        String t = arg.trim();
        return "2".equals(t) || "3".equals(t) || "-1".equals(t) || "{-1}".equals(t);
    }

    /** Map a single math letter to a spoken name. In Arabic mode the
     *  canonical variable letters (x→س, y→ص, z→ع, n→ن, etc.) get the
     *  expected Arabic equivalent; the rest stay as Latin letters
     *  pronounced by the TTS engine. */
    private static String letterName(char c, boolean ar) {
        if (!ar) return String.valueOf(c);
        switch (c) {
            case 'x': return "س";
            case 'y': return "ص";
            case 'z': return "ع";
            case 'n': return "ن";
            case 'k': return "ك";
            case 'm': return "م";
            case 'i': return "i";   // imaginary unit: keep Latin
            case 'e': return "e";   // Euler's number: keep Latin
            default:  return String.valueOf(c);
        }
    }

    private static boolean isArabicLetter(char c) {
        return c >= 0x0600 && c <= 0x06FF;
    }

    private static String clean(String s) {
        String out = s.replaceAll("[\\s\\u00A0]+", " ").trim();
        // Tidy doubled punctuation
        out = out.replaceAll("\\s+([,.;؛،])", "$1");
        out = out.replaceAll("\\(\\s+", "(");
        out = out.replaceAll("\\s+\\)", ")");
        return out;
    }
}
