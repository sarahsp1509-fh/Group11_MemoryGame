import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * MEMORY · Tag & Nacht
 *
 * Tagphase:   klassisches Memory, 2 Spieler abwechselnd (Hot-Seat oder gegen Computer).
 * Nachtphase: jeder Spieler wählt geheim: Bei sich bleiben / Kartenhaus / Stehlen.
 *
 * Start:  java MemoryGame.java                         (ohne DB -> Daten nur im Arbeitsspeicher)
 *         java -cp sqlite-jdbc.jar MemoryGame.java     (mit SQLite -> Datei memory.db)
 * Test:   java MemoryGame.java --test                  (prüft die Nacht-Ergebnismatrix in der Konsole)
 */
public class MemoryGame extends JFrame {

    // ------------------------------------------------------------------ Spielzustand
    private final Store store;
    private final List<CardDef> allCards;              // alle Karten-Zeilen aus der DB (2 pro Paar)
    private final Map<Integer, CardDef> pairCatalog;    // PAIR_ID -> Repräsentant
    private final Random rnd = new Random();

    private final Player[] players = new Player[2];
    private Difficulty difficulty;
    private int roundsPerDay, maxCycles;
    private StealRule stealRule;

    private int cycle, round, current, totalRounds;
    private int gameToken = 0;                          // verhindert, dass alte Timer nach Neustart feuern
    private List<Tile> tiles = new ArrayList<>();
    private List<CardView> views = new ArrayList<>();
    private int firstIdx = -1;
    private boolean busy = false, dayActive = false;
    private int elapsedSec = 0;
    private Timer clock;
    private final Map<Integer, Integer> botMemory = new HashMap<>();
    private final NightAction[] nightChoices = new NightAction[2];

    // ------------------------------------------------------------------ UI
    private final CardLayout screens = new CardLayout();
    private final JPanel root = new JPanel(screens);
    private final JPanel nightPanel = new JPanel(new GridBagLayout());
    private final JPanel endPanel = new JPanel(new GridBagLayout());
    private final JPanel gridWrap = new JPanel(new GridBagLayout());
    private final JLabel phaseLabel = new JLabel(), cycleLabel = new JLabel(), timerLabel = new JLabel("00:00"), msgLabel = new JLabel(" ");
    private final PlayerBadge[] badges = { new PlayerBadge(Theme.P1), new PlayerBadge(Theme.P2) };

    private JTextField p1Field, p2Field;
    private JCheckBox botBox;
    private JComboBox<Difficulty> diffBox;
    private JComboBox<StealRule> ruleBox;
    private JSpinner roundsSpin, cyclesSpin;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--test")) { NightRules.selfTest(); return; }
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeLater(() -> new MemoryGame().setVisible(true));
    }

    public MemoryGame() {
        super("Memory · Tag & Nacht");
        store = Store.open();
        allCards = store.cards();
        pairCatalog = new LinkedHashMap<>();
        for (CardDef c : allCards) pairCatalog.putIfAbsent(c.pairId(), c);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 760));
        setLocationRelativeTo(null);

        root.add(buildStart(), "start");
        root.add(buildGame(), "game");
        nightPanel.setBackground(Theme.NIGHT);
        endPanel.setBackground(Theme.CREAM);
        root.add(nightPanel, "night");
        root.add(endPanel, "end");
        setContentPane(root);
        pack();
        setSize(1100, 820);
        setLocationRelativeTo(null);
        screens.show(root, "start");
    }

    // ================================================================== STARTSEITE
    private JPanel buildStart() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.CREAM);
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        col.add(label("MEMORY", Theme.serif(64f), Theme.INK));
        col.add(label("TAG  &  NACHT", Theme.sans(13f).deriveFont(Font.BOLD), Theme.GOLD));
        col.add(Box.createVerticalStrut(8));
        col.add(label("Finde Paare bei Tag. Verteidige, sammle oder stiehl sie bei Nacht.", Theme.sans(14f), Theme.MUTED));
        col.add(Box.createVerticalStrut(34));

        JPanel form = new JPanel(new GridLayout(0, 2, 16, 12));
        form.setOpaque(false);
        p1Field = new JTextField("Spieler 1");
        p2Field = new JTextField("Spieler 2");
        botBox = new JCheckBox("Spieler 2 ist der Computer");
        botBox.setOpaque(false);
        botBox.setFont(Theme.sans(13f));
        botBox.addActionListener(e -> {
            p2Field.setEnabled(!botBox.isSelected());
            if (botBox.isSelected()) p2Field.setText("Computer");
        });
        diffBox = new JComboBox<>(store.difficulties().toArray(new Difficulty[0]));
        ruleBox = new JComboBox<>(StealRule.values());
        roundsSpin = new JSpinner(new SpinnerNumberModel(3, 1, 5, 1));
        cyclesSpin = new JSpinner(new SpinnerNumberModel(10, 1, 10, 1));

        addRow(form, "Name Spieler 1", p1Field);
        addRow(form, "Name Spieler 2", p2Field);
        addRow(form, "", botBox);
        addRow(form, "Schwierigkeit", diffBox);
        addRow(form, "Memory-Runden pro Tag", roundsSpin);
        addRow(form, "Max. Tag-Nacht-Zyklen", cyclesSpin);
        addRow(form, "Beim Stehlen nimmt man", ruleBox);
        form.setMaximumSize(new Dimension(520, 320));
        form.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(form);
        col.add(Box.createVerticalStrut(30));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btns.setOpaque(false);
        FlatButton start = new FlatButton("Spiel starten", Theme.INK, Theme.CREAM);
        start.addActionListener(e -> startGame());
        FlatButton hs = new FlatButton("Highscores", Theme.LINE, Theme.INK);
        hs.addActionListener(e -> showHighscores());
        btns.add(start);
        btns.add(hs);
        btns.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(btns);
        col.add(Box.createVerticalStrut(24));
        col.add(label("Speicher: " + store.mode(), Theme.sans(11f), Theme.MUTED));

        p.add(col);
        return p;
    }

    private void addRow(JPanel form, String text, JComponent c) {
        form.add(label(text, Theme.sans(13f), Theme.INK));
        c.setFont(Theme.sans(13f));
        form.add(c);
    }

    // ================================================================== SPIELFLÄCHE (TAG)
    private JPanel buildGame() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.CREAM);
        p.setBorder(new EmptyBorder(20, 28, 18, 28));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        phaseLabel.setFont(Theme.serif(34f));
        phaseLabel.setForeground(Theme.INK);
        cycleLabel.setFont(Theme.sans(12f).deriveFont(Font.BOLD));
        cycleLabel.setForeground(Theme.GOLD);
        titles.add(cycleLabel);
        titles.add(phaseLabel);
        top.add(titles, BorderLayout.WEST);
        timerLabel.setFont(Theme.serif(28f));
        timerLabel.setForeground(Theme.MUTED);
        top.add(timerLabel, BorderLayout.EAST);

        JPanel scores = new JPanel(new GridLayout(1, 2, 20, 0));
        scores.setOpaque(false);
        scores.setBorder(new EmptyBorder(16, 0, 10, 0));
        scores.add(badges[0]);
        scores.add(badges[1]);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(top, BorderLayout.NORTH);
        north.add(scores, BorderLayout.SOUTH);
        p.add(north, BorderLayout.NORTH);

        gridWrap.setOpaque(false);
        p.add(gridWrap, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        msgLabel.setFont(Theme.sans(14f));
        msgLabel.setForeground(Theme.INK);
        bottom.add(msgLabel, BorderLayout.WEST);
        FlatButton restart = new FlatButton("Neu starten", Theme.LINE, Theme.INK);
        restart.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(this, "Aktuelles Spiel abbrechen und neu starten?", "Neu starten", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) backToStart();
        });
        bottom.add(restart, BorderLayout.EAST);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private void startGame() {
        gameToken++;
        boolean bot = botBox.isSelected();
        String n1 = p1Field.getText().isBlank() ? "Spieler 1" : p1Field.getText().trim();
        String n2 = bot ? "Computer" : (p2Field.getText().isBlank() ? "Spieler 2" : p2Field.getText().trim());
        if (n1.equalsIgnoreCase(n2)) n2 = n2 + " (2)";
        players[0] = new Player(n1, store.playerId(n1), false);
        players[1] = new Player(n2, store.playerId(n2), bot);
        difficulty = (Difficulty) diffBox.getSelectedItem();
        stealRule = (StealRule) ruleBox.getSelectedItem();
        roundsPerDay = (Integer) roundsSpin.getValue();
        maxCycles = (Integer) cyclesSpin.getValue();
        cycle = 1; round = 1; totalRounds = 0; current = 0; elapsedSec = 0;
        timerLabel.setText("00:00");
        if (clock != null) clock.stop();
        clock = new Timer(1000, e -> { elapsedSec++; timerLabel.setText(fmtTime(elapsedSec)); });
        screens.show(root, "game");
        startRound();
    }

    private void startRound() {
        // Funktionale Anforderung 1: Karten zufällig mischen und anordnen
        List<Integer> ids = new ArrayList<>(pairCatalog.keySet());
        Collections.shuffle(ids, rnd);
        Set<Integer> chosen = new HashSet<>(ids.subList(0, Math.min(difficulty.pairs(), ids.size())));
        tiles = new ArrayList<>();
        for (CardDef c : allCards) if (chosen.contains(c.pairId())) tiles.add(new Tile(c));
        Collections.shuffle(tiles, rnd);

        int n = tiles.size();
        int cols = n <= 12 ? 4 : n <= 16 ? 4 : 6;
        int rows = (int) Math.ceil(n / (double) cols);
        JPanel grid = new JPanel(new GridLayout(rows, cols, 12, 12));
        grid.setOpaque(false);
        views = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            CardView v = new CardView(tiles.get(i), () -> onCardClick(idx));
            views.add(v);
            grid.add(v);
        }
        gridWrap.removeAll();
        gridWrap.add(grid);
        gridWrap.revalidate();
        gridWrap.repaint();

        firstIdx = -1; busy = false; dayActive = true;
        botMemory.clear();
        current = totalRounds % 2;       // Startspieler wechselt jede Runde
        totalRounds++;
        phaseLabel.setText("Tag " + cycle + "  ·  Runde " + round + " / " + roundsPerDay);
        cycleLabel.setText("ZYKLUS " + cycle + " VON " + maxCycles + "   ·   " + difficulty.name().toUpperCase());
        setMsg(players[current].name + " beginnt.");
        refreshBadges();
        clock.start();
        maybeBot();
    }

    private void onCardClick(int i) {
        if (!dayActive || busy || players[current].bot) return;
        reveal(i);
    }

    // Zentraler Algorithmus (Tagphase)
    private void reveal(int i) {
        Tile t = tiles.get(i);
        if (t.up || t.matched || i == firstIdx) return;
        t.up = true;                                             // 1./2. Karte aufdecken
        views.get(i).repaint();
        if (rnd.nextDouble() < 0.75) botMemory.put(i, t.def.pairId());

        if (firstIdx < 0) { firstIdx = i; return; }

        int a = firstIdx, b = i;
        firstIdx = -1;
        Player p = players[current];
        p.attempts++;
        Tile ta = tiles.get(a), tb = tiles.get(b);

        if (ta.def.pairId() == tb.def.pairId()) {                // 3./4. PAIR_ID vergleichen -> Paar
            ta.matched = tb.matched = true;
            ta.owner = tb.owner = current;
            p.pairs.add(pairCatalog.get(ta.def.pairId()));
            botMemory.remove(a); botMemory.remove(b);
            views.get(a).repaint(); views.get(b).repaint();
            setMsg("Paar gefunden: " + ta.def.motif().label + " (Wert " + ta.def.value() + ")  –  " + p.name + " ist nochmal dran.");
            refreshBadges();
            if (tiles.stream().allMatch(x -> x.matched)) {       // 6./7. alle Paare gefunden -> Runde endet
                endRound();
            } else maybeBot();
        } else {                                                 // 5. kein Paar -> wieder verdecken
            busy = true;
            setMsg("Kein Paar.");
            later(900, () -> {
                ta.up = tb.up = false;
                views.get(a).repaint(); views.get(b).repaint();
                busy = false;
                current = 1 - current;
                setMsg(players[current].name + " ist dran.");
                refreshBadges();
                maybeBot();
            });
        }
    }

    private void endRound() {
        dayActive = false;
        clock.stop();
        setMsg("Runde beendet.");
        later(1300, () -> {
            if (round < roundsPerDay) { round++; startRound(); }
            else startNight();
        });
    }

    // ------------------------------------------------------------------ Computer-Gegner
    private void maybeBot() {
        if (!dayActive || !players[current].bot) return;
        later(750, this::botStep);
    }

    private void botStep() {
        if (!dayActive || busy || !players[current].bot) return;
        if (firstIdx < 0) {
            int a = knownPairFirst();
            if (a < 0) a = randomHidden(-1);
            reveal(a);
            if (dayActive) later(650, this::botStep);
        } else {
            int pid = tiles.get(firstIdx).def.pairId();
            int b = -1;
            for (var e : botMemory.entrySet())
                if (e.getKey() != firstIdx && e.getValue() == pid && !tiles.get(e.getKey()).matched) b = e.getKey();
            if (b < 0) b = randomHidden(firstIdx);
            reveal(b);
        }
    }

    private int knownPairFirst() {
        Map<Integer, Integer> seen = new HashMap<>();
        for (var e : botMemory.entrySet()) {
            if (tiles.get(e.getKey()).matched) continue;
            Integer other = seen.put(e.getValue(), e.getKey());
            if (other != null) return other;
        }
        return -1;
    }

    private int randomHidden(int exclude) {
        List<Integer> unknown = new ArrayList<>(), hidden = new ArrayList<>();
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            if (t.matched || t.up || i == exclude) continue;
            hidden.add(i);
            if (!botMemory.containsKey(i)) unknown.add(i);
        }
        List<Integer> pool = unknown.isEmpty() ? hidden : unknown;
        return pool.get(rnd.nextInt(pool.size()));
    }

    // ================================================================== NACHT
    private void startNight() {
        nightChoices[0] = nightChoices[1] = null;
        screens.show(root, "night");
        askNight(0);
    }

    private void askNight(int who) {
        Player p = players[who];
        if (p.bot) { nightChoices[who] = botNightChoice(who); afterChoice(who); return; }

        JPanel col = nightColumn();
        col.add(label("NACHT " + cycle, Theme.sans(13f).deriveFont(Font.BOLD), Theme.GOLD));
        col.add(label(p.name + ", du wählst geheim.", Theme.serif(40f), Theme.CREAM));
        col.add(label(players[1 - who].bot ? "Deine Wahl bleibt verborgen." : players[1 - who].name + ", bitte jetzt wegschauen.", Theme.sans(14f), Theme.NIGHT_MUTED));
        col.add(Box.createVerticalStrut(10));
        col.add(label("Deine Paare: " + p.pairs.size() + "   ·   Punkte: " + p.score(), Theme.sans(14f), Theme.CREAM));
        col.add(Box.createVerticalStrut(34));

        JPanel choices = new JPanel(new GridLayout(1, 3, 18, 0));
        choices.setOpaque(false);
        for (NightAction a : NightAction.values()) {
            FlatButton b = new FlatButton("<html><div style='text-align:center;width:180px'><b style='font-size:15px'>" + a.label
                    + "</b><br><br><span style='font-size:11px'>" + a.desc + "</span></div></html>", Theme.NIGHT_2, Theme.CREAM);
            b.hover = new Color(0x2A3560);
            b.setBorder(new EmptyBorder(26, 18, 26, 18));
            b.addActionListener(e -> { nightChoices[who] = a; afterChoice(who); });
            choices.add(b);
        }
        choices.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(choices);
        showNight(col);
    }

    private void afterChoice(int who) {
        if (who == 0) {
            if (players[1].bot) { askNight(1); return; }
            JPanel col = nightColumn();
            col.add(label("Wahl gespeichert.", Theme.serif(40f), Theme.CREAM));
            col.add(label("Gerät jetzt an " + players[1].name + " übergeben.", Theme.sans(15f), Theme.NIGHT_MUTED));
            col.add(Box.createVerticalStrut(30));
            FlatButton go = new FlatButton(players[1].name + " ist bereit", Theme.GOLD, Theme.NIGHT);
            go.setAlignmentX(Component.CENTER_ALIGNMENT);
            go.addActionListener(e -> askNight(1));
            col.add(go);
            showNight(col);
        } else resolveNight();
    }

    private NightAction botNightChoice(int who) {
        Player me = players[who], other = players[1 - who];
        double r = rnd.nextDouble();
        if (me.pairs.isEmpty()) return r < 0.5 ? NightAction.HOUSE : NightAction.STEAL;
        if (me.score() > other.score() + 5) return r < 0.6 ? NightAction.DEFEND : NightAction.HOUSE;
        return r < 0.34 ? NightAction.DEFEND : r < 0.67 ? NightAction.HOUSE : NightAction.STEAL;
    }

    private void resolveNight() {
        int[] before = { players[0].score(), players[1].score() };
        List<String> log = NightRules.resolve(players, nightChoices, stealRule, new ArrayList<>(pairCatalog.values()), rnd);

        JPanel col = nightColumn();
        col.add(label("AUFLÖSUNG · NACHT " + cycle, Theme.sans(13f).deriveFont(Font.BOLD), Theme.GOLD));
        col.add(label("Die Nacht ist vorbei.", Theme.serif(40f), Theme.CREAM));
        col.add(Box.createVerticalStrut(22));
        JPanel pick = new JPanel(new GridLayout(1, 2, 30, 0));
        pick.setOpaque(false);
        for (int i = 0; i < 2; i++) {
            JPanel c = new JPanel();
            c.setOpaque(false);
            c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
            c.add(label(players[i].name, Theme.serif(22f), Theme.CREAM));
            c.add(label(nightChoices[i].label, Theme.sans(14f).deriveFont(Font.BOLD), Theme.GOLD));
            int d = players[i].score() - before[i];
            c.add(label(before[i] + "  →  " + players[i].score() + " Punkte  (" + (d >= 0 ? "+" : "") + d + ")", Theme.sans(14f), Theme.CREAM));
            pick.add(c);
        }
        pick.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(pick);
        col.add(Box.createVerticalStrut(22));
        for (String s : log) col.add(label("·  " + s, Theme.sans(14f), Theme.NIGHT_MUTED));
        col.add(Box.createVerticalStrut(30));

        boolean someoneHasAll = (players[0].pairs.isEmpty() && !players[1].pairs.isEmpty())
                             || (players[1].pairs.isEmpty() && !players[0].pairs.isEmpty());
        boolean over = cycle >= maxCycles || someoneHasAll;
        FlatButton next = new FlatButton(over ? "Zum Endergebnis" : "Es wird Tag", Theme.GOLD, Theme.NIGHT);
        next.setAlignmentX(Component.CENTER_ALIGNMENT);
        next.addActionListener(e -> {
            if (over) endGame(someoneHasAll);
            else { cycle++; round = 1; screens.show(root, "game"); startRound(); }
        });
        col.add(next);
        showNight(col);
    }

    private JPanel nightColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        return col;
    }

    private void showNight(JPanel col) {
        nightPanel.removeAll();
        nightPanel.add(col);
        nightPanel.revalidate();
        nightPanel.repaint();
    }

    // ================================================================== ENDE
    private void endGame(boolean byAllCards) {
        gameToken++;
        if (clock != null) clock.stop();
        for (Player p : players) store.saveResult(p.dbId, p.score(), elapsedSec, p.attempts);

        Player a = players[0], b = players[1];
        String headline = a.score() == b.score() ? "Unentschieden." : (a.score() > b.score() ? a.name : b.name) + " gewinnt.";

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(label(byAllCards ? "EIN SPIELER HAT ALLE KARTEN" : "SPIELENDE NACH " + cycle + (cycle == 1 ? " ZYKLUS" : " ZYKLEN"), Theme.sans(13f).deriveFont(Font.BOLD), Theme.GOLD));
        col.add(label(headline, Theme.serif(52f), Theme.INK));
        col.add(Box.createVerticalStrut(26));

        String[] cols = { "Spieler", "Paare", "Punkte (Wert)", "Versuche", "Zeit" };
        Object[][] data = new Object[2][];
        for (int i = 0; i < 2; i++)
            data[i] = new Object[]{ players[i].name, players[i].pairs.size(), players[i].score(), players[i].attempts, fmtTime(elapsedSec) };
        col.add(styledTable(new JTable(new DefaultTableModel(data, cols) { public boolean isCellEditable(int r, int c) { return false; } }), 560, 90));
        col.add(Box.createVerticalStrut(30));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btns.setOpaque(false);
        FlatButton again = new FlatButton("Neues Spiel", Theme.INK, Theme.CREAM);
        again.addActionListener(e -> backToStart());
        FlatButton hs = new FlatButton("Highscores", Theme.LINE, Theme.INK);
        hs.addActionListener(e -> showHighscores());
        btns.add(again);
        btns.add(hs);
        btns.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(btns);

        endPanel.removeAll();
        endPanel.add(col);
        endPanel.revalidate();
        endPanel.repaint();
        screens.show(root, "end");
    }

    private void backToStart() {
        gameToken++;
        dayActive = false;
        if (clock != null) clock.stop();
        screens.show(root, "start");
    }

    private void showHighscores() {
        List<String[]> rows = store.highscores(10);
        String[] cols = { "#", "Spieler", "Punkte", "Zeit", "Versuche", "Datum" };
        Object[][] data = new Object[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            data[i] = new Object[]{ i + 1, r[0], r[1], fmtTime(Integer.parseInt(r[2])), r[3], r[4] };
        }
        JComponent table = rows.isEmpty() ? label("Noch keine Ergebnisse gespeichert.", Theme.sans(14f), Theme.MUTED)
                : styledTable(new JTable(new DefaultTableModel(data, cols) { public boolean isCellEditable(int r, int c) { return false; } }), 620, 280);
        JOptionPane.showMessageDialog(this, table, "Highscores", JOptionPane.PLAIN_MESSAGE);
    }

    // ================================================================== Helfer
    private JScrollPane styledTable(JTable t, int w, int h) {
        t.setFont(Theme.sans(13f));
        t.setRowHeight(30);
        t.getTableHeader().setFont(Theme.sans(12f).deriveFont(Font.BOLD));
        t.setShowVerticalLines(false);
        t.setGridColor(Theme.LINE);
        JScrollPane sp = new JScrollPane(t);
        sp.setPreferredSize(new Dimension(w, h));
        sp.setMaximumSize(new Dimension(w, h));
        sp.setAlignmentX(Component.CENTER_ALIGNMENT);
        return sp;
    }

    private void refreshBadges() {
        for (int i = 0; i < 2; i++) badges[i].update(players[i], i == current && dayActive);
    }

    private void setMsg(String s) { msgLabel.setText(s); }

    private void later(int ms, Runnable r) {
        int tok = gameToken;
        Timer t = new Timer(ms, e -> { if (tok == gameToken) r.run(); });
        t.setRepeats(false);
        t.start();
    }

    static String fmtTime(int s) { return String.format("%02d:%02d", s / 60, s % 60); }

    static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        l.setForeground(c);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }
}

// ====================================================================== DATENMODELL
enum Motif {
    CIRCLE("Kreis", "Geometrie"), SQUARE("Quadrat", "Geometrie"), TRIANGLE("Dreieck", "Geometrie"),
    DIAMOND("Raute", "Geometrie"), HEXAGON("Sechseck", "Geometrie"), CROSS("Kreuz", "Geometrie"),
    STAR("Stern", "Himmel"), MOON("Mond", "Himmel"), RING("Ring", "Schmuck"),
    HEART("Herz", "Symbol"), ARCH("Bogen", "Architektur"), DROP("Tropfen", "Natur");
    final String label, category;
    Motif(String l, String c) { label = l; category = c; }
}

record CardDef(int id, int pairId, Motif motif, String category, Color color, int value) {}

record Difficulty(int id, String name, int pairs) {
    @Override public String toString() { return name + " (" + pairs + " Paare)"; }
}

class Tile {
    final CardDef def;
    boolean up, matched;
    int owner = -1;
    Tile(CardDef d) { def = d; }
}

class Player {
    final String name;
    final int dbId;
    final boolean bot;
    final List<CardDef> pairs = new ArrayList<>();
    int attempts;
    Player(String n, int id, boolean bot) { name = n; dbId = id; this.bot = bot; }
    int score() { return pairs.stream().mapToInt(CardDef::value).sum(); }
}

enum NightAction {
    DEFEND("Bei sich bleiben", "Niemand kann bei dir stehlen."),
    HOUSE("Ins Kartenhaus", "Gratis 1 Kartenpaar – aber ein Dieb kann dir bis zu 2 Paare stehlen."),
    STEAL("Stehlen", "Bis zu 2 Paare vom anderen – aber du bist selbst angreifbar.");
    final String label, desc;
    NightAction(String l, String d) { label = l; desc = d; }
}

enum StealRule {
    HIGHEST("die wertvollsten Paare"), LOWEST("die billigsten Paare"), RANDOM("zufällige Paare");
    final String label;
    StealRule(String l) { label = l; }
    @Override public String toString() { return label; }
}

// ====================================================================== NACHT-REGELN (Ergebnismatrix)
class NightRules {
    static final int STEAL_MAX = 2;

    static List<String> resolve(Player[] p, NightAction[] a, StealRule rule, List<CardDef> catalog, Random rnd) {
        List<String> log = new ArrayList<>();
        // 1) Kartenhaus: zuerst gutschreiben (das neue Paar kann in derselben Nacht gestohlen werden)
        for (int i = 0; i < 2; i++) {
            if (a[i] == NightAction.HOUSE) {
                CardDef g = catalog.get(rnd.nextInt(catalog.size()));
                p[i].pairs.add(g);
                log.add(p[i].name + " holt im Kartenhaus 1 Paar: " + g.motif().label + " (Wert " + g.value() + ").");
            }
        }
        // 2) Diebstahl gleichzeitig berechnen (beide sehen den Stand VOR dem Stehlen)
        List<List<CardDef>> stolen = List.of(new ArrayList<>(), new ArrayList<>());
        for (int i = 0; i < 2; i++) {
            if (a[i] != NightAction.STEAL) continue;
            int v = 1 - i;
            if (a[v] == NightAction.DEFEND) log.add(p[i].name + " will stehlen – aber " + p[v].name + " ist geschützt.");
            else stolen.get(i).addAll(pick(p[v].pairs, STEAL_MAX, rule, rnd));
        }
        // 3) übertragen
        for (int i = 0; i < 2; i++) for (CardDef c : stolen.get(i)) p[1 - i].pairs.remove(c);
        for (int i = 0; i < 2; i++) {
            List<CardDef> s = stolen.get(i);
            p[i].pairs.addAll(s);
            if (!s.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (CardDef c : s) sb.append(sb.length() > 0 ? ", " : "").append(c.motif().label).append(" (").append(c.value()).append(")");
                log.add(p[i].name + " stiehlt " + s.size() + " Paar" + (s.size() > 1 ? "e" : "") + " von " + p[1 - i].name + ": " + sb + ".");
            } else if (a[i] == NightAction.STEAL && a[1 - i] != NightAction.DEFEND) {
                log.add(p[i].name + " wollte stehlen, aber bei " + p[1 - i].name + " war nichts zu holen.");
            }
        }
        if (log.isEmpty()) log.add("Ruhige Nacht – nichts passiert.");
        return log;
    }

    static List<CardDef> pick(List<CardDef> from, int n, StealRule rule, Random rnd) {
        List<CardDef> c = new ArrayList<>(from);
        switch (rule) {
            case HIGHEST -> c.sort(Comparator.comparingInt(CardDef::value).reversed());
            case LOWEST -> c.sort(Comparator.comparingInt(CardDef::value));
            case RANDOM -> Collections.shuffle(c, rnd);
        }
        return new ArrayList<>(c.subList(0, Math.min(n, c.size())));
    }

    /** Spielt alle 9 Kombinationen der Matrix durch und gibt das Ergebnis aus. */
    static void selfTest() {
        List<CardDef> cat = Seeds.pairCatalog();
        for (NightAction a1 : NightAction.values()) for (NightAction a2 : NightAction.values()) {
            Player[] p = { new Player("S1", 0, false), new Player("S2", 0, false) };
            p[0].pairs.addAll(cat.subList(0, 3));
            p[1].pairs.addAll(cat.subList(3, 6));
            int n1 = p[0].pairs.size(), n2 = p[1].pairs.size();
            List<String> log = resolve(p, new NightAction[]{ a1, a2 }, StealRule.HIGHEST, cat, new Random(1));
            System.out.printf("S1 %-17s | S2 %-17s ->  S1 Paare %d->%d  S2 Paare %d->%d%n",
                    a1.label, a2.label, n1, p[0].pairs.size(), n2, p[1].pairs.size());
            for (String s : log) System.out.println("        " + s);
        }
    }
}

// ====================================================================== DATENBANK
interface Store {
    List<Difficulty> difficulties();
    List<CardDef> cards();
    int playerId(String name);
    void saveResult(int playerId, int points, int seconds, int attempts);
    List<String[]> highscores(int limit);   // name, punkte, zeit, versuche, datum
    String mode();

    static Store open() {
        try { return new SqliteStore("jdbc:sqlite:memory.db"); }
        catch (Throwable t) {
            System.out.println("SQLite nicht verfügbar (" + t.getClass().getSimpleName() + ") – nutze Arbeitsspeicher.");
            return new MemoryStore();
        }
    }
}

class Seeds {
    // Motiv, Farbe, Wert
    static final Object[][] CARDS = {
        { Motif.CIRCLE, 0xC2553A, 1 }, { Motif.SQUARE, 0x7A8450, 1 }, { Motif.TRIANGLE, 0x243B5E, 2 },
        { Motif.DIAMOND, 0xD9A441, 3 }, { Motif.HEXAGON, 0x2F5D50, 2 }, { Motif.CROSS, 0xA0522D, 1 },
        { Motif.STAR, 0xB8975A, 5 }, { Motif.MOON, 0x6F9FC0, 4 }, { Motif.RING, 0x6A3E5A, 4 },
        { Motif.HEART, 0xD98C8C, 3 }, { Motif.ARCH, 0x9CAF88, 2 }, { Motif.DROP, 0x2B2B2B, 3 },
    };
    static final Object[][] DIFFICULTIES = { { "Leicht", 6 }, { "Mittel", 8 }, { "Schwer", 12 } };

    static List<CardDef> allCards() {        // 2 Karten pro Paar, gleiche PAIR_ID
        List<CardDef> out = new ArrayList<>();
        int id = 1;
        for (int i = 0; i < CARDS.length; i++) {
            Motif m = (Motif) CARDS[i][0];
            for (int k = 0; k < 2; k++)
                out.add(new CardDef(id++, i + 1, m, m.category, new Color((Integer) CARDS[i][1]), (Integer) CARDS[i][2]));
        }
        return out;
    }

    static List<CardDef> pairCatalog() {
        List<CardDef> out = new ArrayList<>();
        for (CardDef c : allCards()) if (c.id() % 2 == 1) out.add(c);
        return out;
    }
}

class SqliteStore implements Store {
    private final Connection con;

    SqliteStore(String url) throws Exception {
        Class.forName("org.sqlite.JDBC");
        con = DriverManager.getConnection(url);
        try (Statement s = con.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("CREATE TABLE IF NOT EXISTS schwierigkeitsstufe (id INTEGER PRIMARY KEY AUTOINCREMENT, bezeichnung TEXT NOT NULL, anzahl_kartenpaare INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS karte (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, bild BLOB, kategorie TEXT, pair_id INTEGER NOT NULL, wert INTEGER NOT NULL, farbe INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS spieler (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, bisherige_punkte INTEGER NOT NULL DEFAULT 0, anzahl_spiele INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS spielergebnis (id INTEGER PRIMARY KEY AUTOINCREMENT, spieler_id INTEGER NOT NULL REFERENCES spieler(id), punkte INTEGER NOT NULL, benoetigte_zeit_sek INTEGER NOT NULL, anzahl_versuche INTEGER NOT NULL, datum TEXT NOT NULL)");
        }
        seed();
    }

    private int count(String table) throws SQLException {
        try (Statement s = con.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) { return r.next() ? r.getInt(1) : 0; }
    }

    private void seed() throws Exception {
        if (count("schwierigkeitsstufe") == 0) {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO schwierigkeitsstufe (bezeichnung, anzahl_kartenpaare) VALUES (?, ?)")) {
                for (Object[] d : Seeds.DIFFICULTIES) { ps.setString(1, (String) d[0]); ps.setInt(2, (Integer) d[1]); ps.executeUpdate(); }
            }
        }
        if (count("karte") == 0) {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO karte (symbol, bild, kategorie, pair_id, wert, farbe) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (CardDef c : Seeds.allCards()) {
                    ps.setString(1, c.motif().name());
                    ps.setBytes(2, CardPainter.png(c));               // Bilddaten als PNG-BLOB
                    ps.setString(3, c.category());
                    ps.setInt(4, c.pairId());
                    ps.setInt(5, c.value());
                    ps.setInt(6, c.color().getRGB() & 0xFFFFFF);
                    ps.executeUpdate();
                }
            }
        }
    }

    public List<Difficulty> difficulties() {
        List<Difficulty> out = new ArrayList<>();
        try (Statement s = con.createStatement(); ResultSet r = s.executeQuery("SELECT id, bezeichnung, anzahl_kartenpaare FROM schwierigkeitsstufe ORDER BY anzahl_kartenpaare")) {
            while (r.next()) out.add(new Difficulty(r.getInt(1), r.getString(2), r.getInt(3)));
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public List<CardDef> cards() {
        List<CardDef> out = new ArrayList<>();
        try (Statement s = con.createStatement(); ResultSet r = s.executeQuery("SELECT id, pair_id, symbol, kategorie, farbe, wert FROM karte ORDER BY id")) {
            while (r.next()) out.add(new CardDef(r.getInt(1), r.getInt(2), Motif.valueOf(r.getString(3)), r.getString(4), new Color(r.getInt(5)), r.getInt(6)));
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public int playerId(String name) {
        try {
            try (PreparedStatement ps = con.prepareStatement("INSERT OR IGNORE INTO spieler (name) VALUES (?)")) { ps.setString(1, name); ps.executeUpdate(); }
            try (PreparedStatement ps = con.prepareStatement("SELECT id FROM spieler WHERE name = ?")) {
                ps.setString(1, name);
                try (ResultSet r = ps.executeQuery()) { return r.next() ? r.getInt(1) : -1; }
            }
        } catch (SQLException e) { e.printStackTrace(); return -1; }
    }

    public void saveResult(int playerId, int points, int seconds, int attempts) {
        try {
            con.setAutoCommit(false);                                   // beides oder nichts
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO spielergebnis (spieler_id, punkte, benoetigte_zeit_sek, anzahl_versuche, datum) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, playerId); ps.setInt(2, points); ps.setInt(3, seconds); ps.setInt(4, attempts);
                ps.setString(5, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE spieler SET bisherige_punkte = bisherige_punkte + ?, anzahl_spiele = anzahl_spiele + 1 WHERE id = ?")) {
                ps.setInt(1, points); ps.setInt(2, playerId); ps.executeUpdate();
            }
            con.commit();
        } catch (SQLException e) {
            try { con.rollback(); } catch (SQLException ignored) {}
            e.printStackTrace();
        } finally {
            try { con.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public List<String[]> highscores(int limit) {
        List<String[]> out = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT s.name, e.punkte, e.benoetigte_zeit_sek, e.anzahl_versuche, e.datum FROM spielergebnis e JOIN spieler s ON s.id = e.spieler_id " +
                "ORDER BY e.punkte DESC, e.benoetigte_zeit_sek ASC, e.anzahl_versuche ASC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) out.add(new String[]{ r.getString(1), "" + r.getInt(2), "" + r.getInt(3), "" + r.getInt(4), r.getString(5) });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public String mode() { return "SQLite (memory.db)"; }
}

class MemoryStore implements Store {
    private final Map<String, Integer> ids = new HashMap<>();
    private final List<String[]> results = new ArrayList<>();
    private final Map<Integer, String> names = new HashMap<>();

    public List<Difficulty> difficulties() {
        List<Difficulty> out = new ArrayList<>();
        int i = 1;
        for (Object[] d : Seeds.DIFFICULTIES) out.add(new Difficulty(i++, (String) d[0], (Integer) d[1]));
        return out;
    }
    public List<CardDef> cards() { return Seeds.allCards(); }
    public int playerId(String name) {
        return ids.computeIfAbsent(name, n -> { int id = ids.size() + 1; names.put(id, n); return id; });
    }
    public void saveResult(int playerId, int points, int seconds, int attempts) {
        results.add(new String[]{ names.get(playerId), "" + points, "" + seconds, "" + attempts,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) });
    }
    public List<String[]> highscores(int limit) {
        List<String[]> s = new ArrayList<>(results);
        s.sort(Comparator.<String[]>comparingInt(r -> -Integer.parseInt(r[1])).thenComparingInt(r -> Integer.parseInt(r[2])));
        return s.subList(0, Math.min(limit, s.size()));
    }
    public String mode() { return "Arbeitsspeicher (ohne SQLite – Ergebnisse gehen beim Schließen verloren)"; }
}

// ====================================================================== DESIGN
class Theme {
    static final Color CREAM = new Color(0xF4EFE6), PAPER = new Color(0xFBF8F2), INK = new Color(0x1C1C1C),
            MUTED = new Color(0x8A8378), LINE = new Color(0xE2DACB), GOLD = new Color(0xB8975A),
            NIGHT = new Color(0x10162B), NIGHT_2 = new Color(0x1C2546), NIGHT_MUTED = new Color(0x9AA3BF),
            P1 = new Color(0xC2553A), P2 = new Color(0x3C5A8A);
    static final String SERIF = pick("Didot", "Bodoni 72", "Playfair Display", "Georgia", "DejaVu Serif", "Serif");
    static final String SANS = pick("Helvetica Neue", "Inter", "Segoe UI", "Arial", "DejaVu Sans", "SansSerif");

    static String pick(String... names) {
        Set<String> av = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : names) if (av.contains(n)) return n;
        return names[names.length - 1];
    }
    static Font serif(float s) { return new Font(SERIF, Font.PLAIN, 12).deriveFont(s); }
    static Font sans(float s) { return new Font(SANS, Font.PLAIN, 12).deriveFont(s); }
}

class FlatButton extends JButton {
    Color bg, hover;
    FlatButton(String text, Color bg, Color fg) {
        super(text);
        this.bg = bg;
        this.hover = bg.darker();
        setForeground(fg);
        setFont(Theme.sans(14f).deriveFont(Font.BOLD));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setBorder(new EmptyBorder(12, 26, 12, 26));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getModel().isRollover() ? hover : bg);
        g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 14, 14));
        g.dispose();
        super.paintComponent(g0);
    }
}

class PlayerBadge extends JPanel {
    private final Color accent;
    private final JLabel name = new JLabel(), stats = new JLabel();
    private boolean active;
    PlayerBadge(Color accent) {
        this.accent = accent;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 14, 12, 14));
        name.setFont(Theme.serif(22f));
        name.setForeground(Theme.INK);
        stats.setFont(Theme.sans(13f));
        stats.setForeground(Theme.MUTED);
        add(name);
        add(stats);
    }
    void update(Player p, boolean active) {
        this.active = active;
        name.setText(p.name + (p.bot ? "  ·  KI" : "") + (active ? "   ◂ am Zug" : ""));
        stats.setText("Paare " + p.pairs.size() + "   ·   Punkte " + p.score() + "   ·   Versuche " + p.attempts);
        repaint();
    }
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(active ? Theme.PAPER : new Color(0, 0, 0, 0));
        g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 14, 14));
        g.setColor(active ? accent : Theme.LINE);
        g.fillRect(14, getHeight() - 4, active ? getWidth() - 28 : 60, active ? 3 : 1);
        g.dispose();
    }
}

class CardView extends JComponent {
    private final Tile tile;
    private boolean hover;
    CardView(Tile t, Runnable onClick) {
        tile = t;
        setPreferredSize(new Dimension(100, 132));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            public void mousePressed(MouseEvent e) { onClick.run(); }
        });
    }
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        double lift = hover && !tile.up ? 3 : 0;
        g.translate(0, -lift);
        Color border = tile.matched ? (tile.owner == 0 ? Theme.P1 : Theme.P2) : null;
        CardPainter.paint(g, tile.def, getWidth(), getHeight(), tile.up, border);
        g.dispose();
    }
}

class CardPainter {
    static void paint(Graphics2D g, CardDef d, int w, int h, boolean faceUp, Color matchBorder) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double pad = 4, cw = w - 2 * pad, ch = h - 2 * pad;
        g.setColor(new Color(0, 0, 0, 28));
        g.fill(new RoundRectangle2D.Double(pad + 1, pad + 3, cw, ch, 14, 14));
        RoundRectangle2D card = new RoundRectangle2D.Double(pad, pad, cw, ch, 14, 14);

        if (!faceUp) {
            g.setColor(Theme.INK);
            g.fill(card);
            g.setColor(Theme.GOLD);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(pad + 6, pad + 6, cw - 12, ch - 12, 9, 9));
            g.setFont(Theme.serif((float) (Math.min(cw, ch) * 0.34)));
            FontMetrics fm = g.getFontMetrics();
            String m = "M";
            g.drawString(m, (float) (w / 2.0 - fm.stringWidth(m) / 2.0), (float) (h / 2.0 + fm.getAscent() * 0.35));
            return;
        }
        g.setColor(Theme.PAPER);
        g.fill(card);
        if (matchBorder != null) {
            g.setColor(matchBorder);
            g.setStroke(new BasicStroke(3f));
        } else {
            g.setColor(Theme.LINE);
            g.setStroke(new BasicStroke(1f));
        }
        g.draw(card);

        double r = Math.min(cw, ch) * 0.26;
        g.setColor(d.color());
        g.fill(motif(d.motif(), w / 2.0, pad + ch * 0.42, r));

        // Wert als Punkte
        double dot = 5, gap = 4, total = d.value() * dot + (d.value() - 1) * gap;
        double x = w / 2.0 - total / 2.0, y = pad + ch * 0.80;
        g.setColor(Theme.GOLD);
        for (int i = 0; i < d.value(); i++) g.fill(new Ellipse2D.Double(x + i * (dot + gap), y, dot, dot));
        g.setColor(Theme.MUTED);
        g.setFont(Theme.sans(9.5f));
        FontMetrics fm = g.getFontMetrics();
        String lbl = d.motif().label.toUpperCase();
        g.drawString(lbl, (float) (w / 2.0 - fm.stringWidth(lbl) / 2.0), (float) (y + 20));
    }

    static java.awt.Shape motif(Motif m, double cx, double cy, double r) {
        switch (m) {
            case CIRCLE: return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
            case SQUARE: return new Rectangle2D.Double(cx - r * 0.85, cy - r * 0.85, r * 1.7, r * 1.7);
            case TRIANGLE: return poly(cx, cy, r * 1.1, 3, -Math.PI / 2);
            case DIAMOND: return poly(cx, cy, r * 1.1, 4, -Math.PI / 2);
            case HEXAGON: return poly(cx, cy, r, 6, 0);
            case CROSS: {
                Area a = new Area(new Rectangle2D.Double(cx - r * 0.3, cy - r, r * 0.6, r * 2));
                a.add(new Area(new Rectangle2D.Double(cx - r, cy - r * 0.3, r * 2, r * 0.6)));
                return a;
            }
            case STAR: {
                Path2D p = new Path2D.Double();
                for (int i = 0; i < 10; i++) {
                    double rr = i % 2 == 0 ? r * 1.1 : r * 0.45, ang = -Math.PI / 2 + i * Math.PI / 5;
                    if (i == 0) p.moveTo(cx + rr * Math.cos(ang), cy + rr * Math.sin(ang));
                    else p.lineTo(cx + rr * Math.cos(ang), cy + rr * Math.sin(ang));
                }
                p.closePath();
                return p;
            }
            case MOON: {
                Area a = new Area(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                a.subtract(new Area(new Ellipse2D.Double(cx - r * 0.45, cy - r * 1.15, 2 * r, 2 * r)));
                return a;
            }
            case RING: {
                Area a = new Area(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                a.subtract(new Area(new Ellipse2D.Double(cx - r * 0.55, cy - r * 0.55, r * 1.1, r * 1.1)));
                return a;
            }
            case HEART: {
                Path2D p = new Path2D.Double();
                p.moveTo(cx, cy + r * 0.95);
                p.curveTo(cx - r * 1.5, cy - r * 0.05, cx - r * 0.7, cy - r * 1.2, cx, cy - r * 0.4);
                p.curveTo(cx + r * 0.7, cy - r * 1.2, cx + r * 1.5, cy - r * 0.05, cx, cy + r * 0.95);
                p.closePath();
                return p;
            }
            case ARCH: {
                Area a = new Area(new Rectangle2D.Double(cx - r * 0.75, cy - r * 0.2, r * 1.5, r * 1.2));
                a.add(new Area(new Ellipse2D.Double(cx - r * 0.75, cy - r * 0.95, r * 1.5, r * 1.5)));
                return a;
            }
            case DROP: {
                Path2D p = new Path2D.Double();
                p.moveTo(cx, cy - r * 1.1);
                p.curveTo(cx + r * 0.2, cy - r * 0.6, cx + r * 0.85, cy - r * 0.1, cx + r * 0.8, cy + r * 0.35);
                p.curveTo(cx + r * 0.75, cy + r * 1.1, cx - r * 0.75, cy + r * 1.1, cx - r * 0.8, cy + r * 0.35);
                p.curveTo(cx - r * 0.85, cy - r * 0.1, cx - r * 0.2, cy - r * 0.6, cx, cy - r * 1.1);
                p.closePath();
                return p;
            }
        }
        return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
    }

    static Path2D poly(double cx, double cy, double r, int n, double start) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double a = start + i * 2 * Math.PI / n;
            if (i == 0) p.moveTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
            else p.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
        }
        p.closePath();
        return p;
    }

    /** Rendert eine Karte als PNG – wird als Bilddaten (BLOB) in der Tabelle "karte" gespeichert. */
    static byte[] png(CardDef d) {
        try {
            BufferedImage img = new BufferedImage(120, 160, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            paint(g, d, 120, 160, true, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) { return null; }
    }
}
