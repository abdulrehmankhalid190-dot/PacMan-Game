import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;


public class PacManGame2 extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel     mainPanel  = new JPanel(cardLayout);
    private MainMenuPanel    menuPanel;
    private PacGamePanel     gamePanel;
    private final ArrayList<GameScore> highScores;

    public PacManGame2() {
        setTitle("Pac-Man");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        highScores = ScoreManager.load();
        menuPanel  = new MainMenuPanel(this);
        mainPanel.add(menuPanel, "menu");
        add(mainPanel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void startGame() {
        if (gamePanel != null) { gamePanel.stopLoop(); mainPanel.remove(gamePanel); }
        gamePanel = new PacGamePanel(this, highScores);
        mainPanel.add(gamePanel, "game");
        cardLayout.show(mainPanel, "game");
        gamePanel.requestFocusInWindow();
    }

    public void showMenu() {
        cardLayout.show(mainPanel, "menu");
        menuPanel.updateHighScores(highScores);
        menuPanel.requestFocusInWindow();
    }

    public void recordScore(String name, int score, int timeSec) {
        highScores.add(new GameScore(name.trim(), score, timeSec));
        Collections.sort(highScores);
        while (highScores.size() > 5) highScores.remove(highScores.size() - 1);
        ScoreManager.save(highScores);
        menuPanel.updateHighScores(highScores);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PacManGame2::new);
    }
}


class GameScore implements Comparable<GameScore> {
    final String name;
    final int    score;
    final int    timeSec;

    GameScore(String n, int s, int t) { name = n; score = s; timeSec = t; }

    @Override public int compareTo(GameScore o) { return Integer.compare(o.score, this.score); }
}

class ScoreManager {
    private static final String FILE = "highscores.txt";

    static ArrayList<GameScore> load() {
        ArrayList<GameScore> list = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length == 3) {
                    try {
                        list.add(new GameScore(p[0].trim(), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())));
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException ignored) {}
        Collections.sort(list);
        return list;
    }

    static void save(ArrayList<GameScore> scores) {
        try (PrintWriter w = new PrintWriter(new FileWriter(FILE))) {
            for (GameScore s : scores) w.println(s.name + "," + s.score + "," + s.timeSec);
        } catch (IOException e) { e.printStackTrace(); }
    }
}


class AudioManager {
    private static final int SR = 44100;

    enum Sound { EAT_DOT, EAT_PELLET, EAT_GHOST, DEATH, WIN }

    private final EnumMap<Sound, byte[]> cache = new EnumMap<>(Sound.class);

    AudioManager() {
        cache.put(Sound.EAT_DOT,    tone(440,  55));
        cache.put(Sound.EAT_PELLET, tone(280, 220));
        cache.put(Sound.EAT_GHOST,  tone(600, 140));
        cache.put(Sound.DEATH,      tone(160, 280));
        cache.put(Sound.WIN,        tone(800, 500));
    }

    void play(Sound s) {
        byte[] buf = cache.get(s);
        Thread t = new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(SR, 8, 1, true, true);
                Clip clip = AudioSystem.getClip();
                clip.open(new AudioInputStream(new java.io.ByteArrayInputStream(buf), fmt, buf.length));
                clip.start();
            } catch (Exception ignored) {}
        });
        t.setDaemon(true);
        t.start();
    }

    private static byte[] tone(int freq, int ms) {
        byte[] b = new byte[SR * ms / 1000];
        for (int i = 0; i < b.length; i++)
            b[i] = (byte)(Math.sin(2 * Math.PI * freq * i / SR) * 75);
        return b;
    }
}


class
AppColors {
    static final Color BG_TOP       = new Color(4,   4,  18);
    static final Color BG_BOT       = new Color(12,  4,  28);
    static final Color WALL_LIGHT   = new Color(30,  70, 200);
    static final Color WALL_DARK    = new Color(15,  35, 130);
    static final Color WALL_BORDER  = new Color(70, 130, 255, 100);
    static final Color WALL_BEVEL   = new Color(100,160, 255,  40);
    static final Color DOT          = new Color(255, 225, 180);
    static final Color DOT_GLOW     = new Color(255, 255, 220,  80);
    static final Color PELLET_GLOW  = new Color(80,  160, 255,  55);
    static final Color PAC_GLOW     = new Color(255, 220,   0,  55);
    static final Color PAC_LIGHT    = new Color(255, 240,  20);
    static final Color PAC_DARK     = new Color(215, 150,   0);
    static final Color PAC_EYE      = new Color(20,   20,  50);
    static final Color HUD_BG       = new Color(12,   12,  32);
    static final Color HUD_LINE     = new Color(50,   50, 120);
    static final Color HUD_LABEL    = new Color(190, 190, 230);
    static final Color HUD_SCORE    = new Color(80,  230,  80);
    static final Color HUD_TIME     = new Color(255, 200,  80);
    static final Color HUD_DOTS     = new Color(255, 225, 150);
    static final Color OVERLAY      = new Color(0,     0,   0, 165);
    static final Color FRIGHT_COL   = new Color(35,   35, 200);
    static final Color FRIGHT_MOUTH = new Color(160, 100, 255);
    static final Color[] GHOST_BASE = {
            new Color(255,  60,  60),
            new Color(255, 180, 200),
            new Color(60,  210, 255),
            new Color(255, 165,   0)
    };
}


class GameMaze {
    static final int ROWS = 21, COLS = 21;
    static final int SPAWN_R = 1, SPAWN_C = 1;

    final boolean[][] walls   = new boolean[ROWS][COLS];
    final boolean[][] dots    = new boolean[ROWS][COLS];
    final boolean[][] pellets = new boolean[ROWS][COLS];
    int dotsLeft = 0;

    static final int GH_TOP = 8, GH_BOT = 12, GH_LEFT = 8, GH_RIGHT = 12;
    static final int GH_DOOR_ROW = 8;
    static final int GH_DOOR_COL_L = 9, GH_DOOR_COL_R = 11;

    GameMaze() {
        buildWalls();
        placeCollectibles();
    }

    private void buildWalls() {
        for (int i = 0; i < ROWS; i++) walls[i][0] = walls[i][COLS - 1] = true;
        for (int j = 0; j < COLS; j++) walls[0][j] = walls[ROWS - 1][j] = true;

        hWall(2,  2,  5);   hWall(2, 15, 18);   hWall(2,  8, 12);
        hWall(4,  2,  5);   hWall(4, 15, 18);
        hWall(6,  2,  5);   hWall(6, 15, 18);   hWall(6,  8, 12);
        hWall(14,  2,  5);  hWall(14, 15, 18);  hWall(14,  8, 12);
        hWall(16,  2,  5);  hWall(16, 15, 18);
        hWall(18,  8, 12);

        vWall(2, 6, 2);   vWall(2, 6, 5);   vWall(2, 6, 15);  vWall(2, 6, 18);
        vWall(2, 4, 8);   vWall(2, 4, 12);
        vWall(14, 18, 2); vWall(14, 18, 5); vWall(14, 18, 15); vWall(14, 18, 18);

        for (int j = GH_LEFT; j <= GH_RIGHT; j++) { walls[GH_TOP][j] = true; walls[GH_BOT][j] = true; }
        for (int i = GH_TOP; i <= GH_BOT; i++) { walls[i][GH_LEFT] = true; walls[i][GH_RIGHT] = true; }
        walls[GH_DOOR_ROW][GH_DOOR_COL_L] = false;
        walls[GH_DOOR_ROW][GH_DOOR_COL_R] = false;
        walls[GH_DOOR_ROW][10]             = false;
        for (int i = GH_TOP + 1; i < GH_BOT; i++)
            for (int j = GH_LEFT + 1; j < GH_RIGHT; j++)
                walls[i][j] = false;
        walls[SPAWN_R][SPAWN_C] = false;
    }

    private void hWall(int r, int c1, int c2) { for (int j = c1; j <= c2; j++) walls[r][j] = true; }
    private void vWall(int r1, int r2, int c)  { for (int i = r1; i <= r2; i++) walls[i][c] = true; }

    private void placeCollectibles() {
        boolean[][] reachable = bfs(SPAWN_R, SPAWN_C);
        int[][] pelletPos = {{1, COLS - 2}, {ROWS - 2, 1}, {ROWS - 2, COLS - 2}, {1, 1}};
        for (int[] p : pelletPos) {
            if (reachable[p[0]][p[1]] && !walls[p[0]][p[1]]) { pellets[p[0]][p[1]] = true; dotsLeft++; }
        }
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                if (!reachable[i][j]) continue;
                if (walls[i][j]) continue;
                if (i == SPAWN_R && j == SPAWN_C) continue;
                if (pellets[i][j]) continue;
                if (i > GH_TOP && i < GH_BOT && j > GH_LEFT && j < GH_RIGHT) continue;
                dots[i][j] = true; dotsLeft++;
            }
        }
    }

    boolean[][] bfs(int sr, int sc) {
        boolean[][] vis = new boolean[ROWS][COLS];
        if (walls[sr][sc]) return vis;
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{sr, sc}); vis[sr][sc] = true;
        int[][] d = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            for (int[] dir : d) {
                int nr = cur[0] + dir[0], nc = cur[1] + dir[1];
                if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && !vis[nr][nc] && !walls[nr][nc]) {
                    vis[nr][nc] = true; q.add(new int[]{nr, nc});
                }
            }
        }
        return vis;
    }

    boolean isOpen(int r, int c) { return r >= 0 && r < ROWS && c >= 0 && c < COLS && !walls[r][c]; }
}


abstract class GameEntity {
    int row, col;
    int dirRow, dirCol;

    float renderX, renderY;

    GameEntity(int r, int c, int dr, int dc) {
        row = r; col = c; dirRow = dr; dirCol = dc;
        renderX = c * GameRenderer.TILE + GameRenderer.TILE / 2f;
        renderY = r * GameRenderer.TILE + GameRenderer.TILE / 2f;
    }

    final void lerpRender(float alpha) {
        float tx = col * GameRenderer.TILE + GameRenderer.TILE / 2f;
        float ty = row * GameRenderer.TILE + GameRenderer.TILE / 2f;
        renderX += (tx - renderX) * alpha;
        renderY += (ty - renderY) * alpha;
    }

    final void snapRender() {
        renderX = col * GameRenderer.TILE + GameRenderer.TILE / 2f;
        renderY = row * GameRenderer.TILE + GameRenderer.TILE / 2f;
    }
}


class PacPlayer extends GameEntity {
    private int nextDR = 0, nextDC = 0;
    private final int spawnR, spawnC;

    PacPlayer(int r, int c) { super(r, c, 0, 0); spawnR = r; spawnC = c; }

    void setNextDir(int dr, int dc) { nextDR = dr; nextDC = dc; }

    void update(GameMaze maze) {
        if (nextDR != 0 || nextDC != 0) {
            int nr = row + nextDR, nc = col + nextDC;
            if (maze.isOpen(nr, nc)) {
                dirRow = nextDR; dirCol = nextDC; row = nr; col = nc; return;
            }
        }
        if (dirRow != 0 || dirCol != 0) {
            int nr = row + dirRow, nc = col + dirCol;
            if (maze.isOpen(nr, nc)) { row = nr; col = nc; }
        }
    }

    void respawn() {
        row = spawnR; col = spawnC; dirRow = 0; dirCol = 0; nextDR = 0; nextDC = 0;
        snapRender();
    }
}


class GhostEntity extends GameEntity {
    final Color color;
    private final int    spawnR, spawnC;
    private final int    normalSpeed;
    private final double chaseChance;
    private final int    offR, offC;

    private boolean frightened = false;
    private int     tickCount  = 0;
    private boolean exitingBox = true;
    private final Random rng   = new Random();

    GhostEntity(int r, int c, Color col, int speed, double chase, int offR, int offC) {
        super(r, c, 0, -1);
        spawnR = r; spawnC = c; color = col;
        this.normalSpeed = speed; this.chaseChance = chase;
        this.offR = offR; this.offC = offC;
    }

    void setFrightened(boolean f) { frightened = f; }
    boolean isFrightened()        { return frightened; }

    void respawn() {
        row = spawnR; col = spawnC; frightened = false; tickCount = 0; exitingBox = true;
        dirRow = 0; dirCol = -1;
        snapRender();
    }

    void update(GameMaze maze, PacPlayer pac) {
        int speed = frightened ? normalSpeed + 2 : normalSpeed;
        if (++tickCount % speed != 0) return;

        if (exitingBox) { exitGhostHouse(maze); return; }

        if (rng.nextDouble() < (frightened ? 0.10 : chaseChance)) {
            int tr = pac.row + (frightened ? 0 : offR);
            int tc = pac.col + (frightened ? 0 : offC);
            chaseTo(maze, tr, tc, !frightened);
        } else {
            drift(maze);
        }
    }

    private void exitGhostHouse(GameMaze maze) {
        int doorRow = GameMaze.GH_DOOR_ROW, doorCol = 10;
        if (row > doorRow) {
            if (maze.isOpen(row - 1, col)) { row--; dirRow = -1; dirCol = 0; }
            else if (col != doorCol) {
                int dc = Integer.compare(doorCol, col);
                if (maze.isOpen(row, col + dc)) { col += dc; dirRow = 0; dirCol = dc; }
            }
        } else if (col != doorCol) {
            int dc = Integer.compare(doorCol, col);
            if (maze.isOpen(row, col + dc)) { col += dc; dirRow = 0; dirCol = dc; }
        } else {
            if (maze.isOpen(row - 1, col)) { row--; dirRow = -1; dirCol = 0; exitingBox = false; }
            else { exitingBox = false; }
        }
    }

    private void chaseTo(GameMaze maze, int tr, int tc, boolean toward) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        int revR = -dirRow, revC = -dirCol;
        int[] best = null;
        int bestDist = toward ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        for (int[] d : dirs) {
            if (d[0] == revR && d[1] == revC) continue;
            int nr = row + d[0], nc = col + d[1];
            if (!maze.isOpen(nr, nc)) continue;
            int dist = Math.abs(nr - tr) + Math.abs(nc - tc);
            if (toward ? dist < bestDist : dist > bestDist) { bestDist = dist; best = d; }
        }
        if (best == null) {
            for (int[] d : dirs) {
                int nr = row + d[0], nc = col + d[1];
                if (!maze.isOpen(nr, nc)) continue;
                int dist = Math.abs(nr - tr) + Math.abs(nc - tc);
                if (toward ? dist < bestDist : dist > bestDist) { bestDist = dist; best = d; }
            }
        }
        if (best != null) { row += best[0]; col += best[1]; dirRow = best[0]; dirCol = best[1]; }
    }

    private void drift(GameMaze maze) {
        if (rng.nextDouble() < 0.6 && maze.isOpen(row + dirRow, col + dirCol) && (dirRow != 0 || dirCol != 0)) {
            row += dirRow; col += dirCol; return;
        }
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        int revR = -dirRow, revC = -dirCol;
        ArrayList<int[]> valid = new ArrayList<>(4);
        for (int[] d : dirs) {
            if (d[0] == revR && d[1] == revC) continue;
            if (maze.isOpen(row + d[0], col + d[1])) valid.add(d);
        }
        if (valid.isEmpty()) {
            if (maze.isOpen(row + revR, col + revC)) { row += revR; col += revC; dirRow = revR; dirCol = revC; }
            return;
        }
        int[] d = valid.get(rng.nextInt(valid.size()));
        row += d[0]; col += d[1]; dirRow = d[0]; dirCol = d[1];
    }
}


class ScorePopup {
    String text; int x; float y; Color color; float alpha = 1f;
    private int t = 0;
    ScorePopup(String txt, int x, int y, Color c) { text = txt; this.x = x; this.y = y; color = c; }
    void tick() { y -= 1.2f; if (++t > 18) alpha = Math.max(0f, alpha - 0.08f); }
    boolean done() { return alpha <= 0f; }
}


class GameEngine {
    static final int    NUM_GHOSTS  = 4;
    static final int    GHOST_SPEED = 3;
    static final double CHASE_RATE  = 0.65;
    static final long   POWER_MS    = 7000L;

    private static final int[][] OFFSETS = {{0, 0}, {0, 3}, {-3, 0}, {0, -3}};
    private static final int[][] SPAWNS  = {{10, 9}, {10, 10}, {10, 11}, {10, 10}};

    final GameMaze      maze;
    final PacPlayer     pac;
    final GhostEntity[] ghosts;
    final ArrayList<ScorePopup> popups = new ArrayList<>();
    final AudioManager  audio;

    int  score = 0, lives = 3, elapsed = 0;
    boolean gameOver = false, gameWon = false, powerMode = false;
    long powerEnd = 0;

    private final long startTime       = System.currentTimeMillis();
    private int        ghostEatenCombo = 0;
    private int        tickTotal       = 0;

    GameEngine(AudioManager audio) {
        this.audio = audio;
        maze   = new GameMaze();
        pac    = new PacPlayer(GameMaze.SPAWN_R, GameMaze.SPAWN_C);
        ghosts = new GhostEntity[NUM_GHOSTS];
        for (int i = 0; i < NUM_GHOSTS; i++) {
            ghosts[i] = new GhostEntity(SPAWNS[i][0], SPAWNS[i][1], AppColors.GHOST_BASE[i],
                    GHOST_SPEED, CHASE_RATE, OFFSETS[i][0], OFFSETS[i][1]);
        }
    }

    void update() {
        if (gameOver || gameWon) return;
        tickTotal++;
        elapsed = (int)((System.currentTimeMillis() - startTime) / 1000);

        if (powerMode && System.currentTimeMillis() > powerEnd) {
            powerMode = false; ghostEatenCombo = 0;
            for (GhostEntity g : ghosts) g.setFrightened(false);
        }

        pac.update(maze);
        eatCollectible();

        if (maze.dotsLeft <= 0) { gameWon = true; audio.play(AudioManager.Sound.WIN); return; }

        for (GhostEntity g : ghosts) g.update(maze, pac);
        for (GhostEntity g : ghosts) { if (g.row == pac.row && g.col == pac.col) collide(g); }

        Iterator<ScorePopup> it = popups.iterator();
        while (it.hasNext()) { ScorePopup p = it.next(); p.tick(); if (p.done()) it.remove(); }
    }

    private void eatCollectible() {
        if (maze.dots[pac.row][pac.col]) {
            maze.dots[pac.row][pac.col] = false; score += 10; maze.dotsLeft--;
            addPopup("+10", pac.col, pac.row, new Color(255, 240, 100));
            audio.play(AudioManager.Sound.EAT_DOT);
        }
        if (maze.pellets[pac.row][pac.col]) {
            maze.pellets[pac.row][pac.col] = false; score += 50; maze.dotsLeft--;
            powerMode = true; powerEnd = System.currentTimeMillis() + POWER_MS;
            ghostEatenCombo = 0;
            for (GhostEntity g : ghosts) g.setFrightened(true);
            addPopup("+50", pac.col, pac.row, new Color(100, 200, 255));
            audio.play(AudioManager.Sound.EAT_PELLET);
        }
    }

    private void collide(GhostEntity g) {
        if (powerMode && g.isFrightened()) {
            ghostEatenCombo++;
            int bonus = 200 * (int)Math.pow(2, ghostEatenCombo - 1);
            score += bonus;
            addPopup("+" + bonus, g.col, g.row, new Color(255, 100, 255));
            g.respawn(); audio.play(AudioManager.Sound.EAT_GHOST);
        } else {
            lives--; audio.play(AudioManager.Sound.DEATH);
            if (lives <= 0) { gameOver = true; }
            else { pac.respawn(); for (GhostEntity gh : ghosts) gh.respawn(); }
        }
    }

    private void addPopup(String txt, int col, int row, Color c) {
        popups.add(new ScorePopup(txt, col * GameRenderer.TILE, row * GameRenderer.TILE, c));
    }
}


class GameRenderer {
    static final int TILE = 28, W = GameMaze.COLS * TILE, H = GameMaze.ROWS * TILE, HUD = 50;

    private static final float LERP_ALPHA = 0.40f;

    private BufferedImage wallCache;
    private boolean       wallCacheDirty = true;
    private BufferedImage bgCache;

    private final Color[] ghostGlowCache = new Color[AppColors.GHOST_BASE.length];

    private static final Font FONT_BOLD_12   = new Font("Arial", Font.BOLD,  12);
    private static final Font FONT_BOLD_13   = new Font("Arial", Font.BOLD,  13);
    private static final Font FONT_BOLD_15   = new Font("Arial", Font.BOLD,  15);
    private static final Font FONT_BOLD_10   = new Font("Arial", Font.BOLD,  10);
    private static final Font FONT_BOLD_50   = new Font("Arial", Font.BOLD,  50);
    private static final Font FONT_PLAIN_17  = new Font("Arial", Font.PLAIN, 17);
    private static final Font FONT_PLAIN_15  = new Font("Arial", Font.PLAIN, 15);
    private static final Font FONT_ITALIC_13 = new Font("Arial", Font.ITALIC,13);

    private static final GradientPaint BG_GRAD =
            new GradientPaint(0, 0, AppColors.BG_TOP, W, H, AppColors.BG_BOT);

    private final AlphaComposite[] alphaCache = new AlphaComposite[21];

    GameRenderer() {
        for (int i = 0; i < AppColors.GHOST_BASE.length; i++) {
            Color b = AppColors.GHOST_BASE[i];
            ghostGlowCache[i] = new Color(b.getRed(), b.getGreen(), b.getBlue(), 45);
        }
        for (int i = 0; i <= 20; i++) {
            alphaCache[i] = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, i / 20f);
        }
    }

    private AlphaComposite alphaComposite(float a) {
        int idx = Math.max(0, Math.min(20, Math.round(a * 20)));
        return alphaCache[idx];
    }

    private BufferedImage getBgCache() {
        if (bgCache == null) {
            bgCache = new BufferedImage(W, H + HUD, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bgCache.createGraphics();
            g.setPaint(BG_GRAD);
            g.fillRect(0, 0, W, H);
            g.setColor(AppColors.HUD_BG);
            g.fillRect(0, H, W, HUD);
            g.dispose();
        }
        return bgCache;
    }

    private BufferedImage getWallCache(GameMaze maze) {
        if (wallCache == null || wallCacheDirty) {
            wallCache      = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            wallCacheDirty = false;
            Graphics2D g   = wallCache.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, W, H);
            g.setComposite(AlphaComposite.SrcOver);
            for (int i = 0; i < GameMaze.ROWS; i++) {
                for (int j = 0; j < GameMaze.COLS; j++) {
                    if (!maze.walls[i][j]) continue;
                    int wx = j * TILE, wy = i * TILE;
                    g.setPaint(new GradientPaint(wx, wy, AppColors.WALL_LIGHT, wx + TILE, wy + TILE, AppColors.WALL_DARK));
                    g.fillRect(wx, wy, TILE, TILE);
                    g.setColor(AppColors.WALL_BORDER);
                    g.drawRect(wx, wy, TILE - 1, TILE - 1);
                    g.setColor(AppColors.WALL_BEVEL);
                    g.drawLine(wx + 1, wy + 1, wx + TILE - 2, wy + 1);
                    g.drawLine(wx + 1, wy + 1, wx + 1, wy + TILE - 2);
                }
            }
            g.dispose();
        }
        return wallCache;
    }

    void render(Graphics2D g2, GameEngine eng, long now) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_SPEED);

        eng.pac.lerpRender(LERP_ALPHA);
        for (GhostEntity gh : eng.ghosts) gh.lerpRender(LERP_ALPHA);

        g2.drawImage(getBgCache(), 0, 0, null);
        g2.drawImage(getWallCache(eng.maze), 0, 0, null);

        drawCollectibles(g2, eng.maze, now);
        drawPac(g2, eng.pac, now);
        for (GhostEntity gh : eng.ghosts) drawGhost(g2, gh, eng.powerEnd, now);
        drawPopups(g2, eng.popups);
        drawHUD(g2, eng, now);

        if      (eng.gameOver) drawOverlay(g2, false, eng.score, eng.elapsed);
        else if (eng.gameWon)  drawOverlay(g2, true,  eng.score, eng.elapsed);
    }

    private void drawCollectibles(Graphics2D g2, GameMaze maze, long now) {
        for (int i = 0; i < GameMaze.ROWS; i++) {
            for (int j = 0; j < GameMaze.COLS; j++) {
                int cx = j * TILE + TILE / 2, cy = i * TILE + TILE / 2;
                if (maze.dots[i][j]) {
                    g2.setColor(AppColors.DOT_GLOW);
                    g2.fillOval(cx - 5, cy - 5, 10, 10);
                    g2.setColor(AppColors.DOT);
                    g2.fillOval(cx - 3, cy - 3, 6, 6);
                }
                if (maze.pellets[i][j]) {
                    double pulse = Math.abs(Math.sin(now / 200.0));
                    int r = 6 + (int)(pulse * 3);
                    g2.setColor(AppColors.PELLET_GLOW);
                    g2.fillOval(cx - r - 4, cy - r - 4, (r + 4) * 2, (r + 4) * 2);
                    g2.setPaint(new GradientPaint(cx - r, cy - r, new Color(160, 220, 255),
                            cx + r, cy + r, new Color(40, 90, 210)));
                    g2.fillOval(cx - r, cy - r, r * 2, r * 2);
                }
            }
        }
    }


    private void drawPac(Graphics2D g2, PacPlayer pac, long now) {
        int mouth = 15 + (int)(Math.abs(Math.sin(now / 110.0)) * 35);
        int r     = TILE / 2 - 2;

        Graphics2D pg = (Graphics2D) g2.create();
        pg.translate(pac.renderX, pac.renderY);

        if (pac.dirCol == 1) {
            // RIGHT
        } else if (pac.dirCol == -1) {
            // LEFT
            pg.scale(-1, 1);
        } else if (pac.dirRow == -1) {
            // UP
            pg.rotate(Math.toRadians(-90));
        } else if (pac.dirRow == 1) {
            // DOWN
            pg.rotate(Math.toRadians(90));
        }

        pg.setColor(AppColors.PAC_GLOW);
        pg.fillOval(-r - 5, -r - 5, (r + 5) * 2, (r + 5) * 2);
        pg.setPaint(new GradientPaint(-r + 2, -r + 2, AppColors.PAC_LIGHT, r - 2, r - 2, AppColors.PAC_DARK));
        pg.fillArc(-r, -r, r * 2, r * 2, mouth, 360 - mouth * 2);
        pg.setColor(AppColors.PAC_EYE);
        pg.fillOval(-2, -r + 4, 4, 4);
        pg.dispose();
    }

    private void drawGhost(Graphics2D g2, GhostEntity gh, long powerEnd, long now) {
        int cx  = (int) gh.renderX;
        int cy  = (int) gh.renderY;
        int gx  = cx - TILE / 2 + 2;
        int gy  = cy - TILE / 2 + 2;
        int gw  = TILE - 4, gh2 = TILE - 4;

        Color base;
        if (gh.isFrightened()) {
            long rem = powerEnd - now;
            base = (rem < 2000 && (now / 200) % 2 == 0) ? Color.WHITE : AppColors.FRIGHT_COL;
        } else {
            base = gh.color;
        }

        Color glowColor;
        if (gh.isFrightened()) {
            glowColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), 45);
        } else {
            glowColor = ghostGlowCache[ghostColorIndex(gh.color)];
        }
        g2.setColor(glowColor);
        g2.fillRoundRect(gx - 3, gy - 3, gw + 6, gh2 + 6, 14, 14);

        g2.setPaint(new GradientPaint(gx, gy, base.brighter(), gx, gy + gh2, base.darker()));
        g2.fillArc(gx, gy, gw, gw, 0, 180);
        g2.fillRect(gx, gy + gw / 2, gw, gh2 - gw / 2);

        int sw = gw / 3;
        for (int s = 0; s < 3; s++) {
            g2.setPaint(new GradientPaint(gx, gy, base.brighter(), gx, gy + gh2, base.darker()));
            g2.fillArc(gx + s * sw, gy + gh2 - sw, sw, sw, 0, 180);
        }

        if (!gh.isFrightened()) {
            g2.setColor(Color.WHITE);
            g2.fillOval(gx + 2,  gy + 4, 8, 9);
            g2.fillOval(gx + 13, gy + 4, 8, 9);
            g2.setColor(new Color(0, 50, 200));
            g2.fillOval(gx + 4,  gy + 6, 5, 6);
            g2.fillOval(gx + 15, gy + 6, 5, 6);
            g2.setColor(Color.BLACK);
            g2.fillOval(gx + 5,  gy + 7, 2, 3);
            g2.fillOval(gx + 16, gy + 7, 2, 3);
        } else {
            g2.setColor(Color.WHITE);
            g2.fillOval(gx + 3,  gy + 6, 5, 5);
            g2.fillOval(gx + 14, gy + 6, 5, 5);
            g2.setColor(AppColors.FRIGHT_MOUTH);
            g2.setStroke(new BasicStroke(1.5f));
            int[] xp = {gx + 2, gx + 6, gx + 10, gx + 14, gx + 20};
            int[] yp = {gy + 18, gy + 14, gy + 18, gy + 14, gy + 18};
            g2.drawPolyline(xp, yp, 5);
            g2.setStroke(new BasicStroke(1f));
        }
    }

    private int ghostColorIndex(Color c) {
        for (int i = 0; i < AppColors.GHOST_BASE.length; i++)
            if (AppColors.GHOST_BASE[i] == c) return i;
        return 0;
    }

    private void drawPopups(Graphics2D g2, ArrayList<ScorePopup> popups) {
        g2.setFont(FONT_BOLD_13);
        for (ScorePopup p : popups) {
            g2.setComposite(alphaComposite(p.alpha));
            g2.setColor(p.color);
            g2.drawString(p.text, p.x, (int) p.y);
        }
        g2.setComposite(AlphaComposite.SrcOver);
    }

    private void drawHUD(Graphics2D g2, GameEngine eng, long now) {
        int hy = H + 1;
        g2.setColor(AppColors.HUD_LINE);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(0, hy, W, hy);

        g2.setFont(FONT_BOLD_12);
        g2.setColor(AppColors.HUD_LABEL);
        g2.drawString("LIVES:", 10, hy + 30);
        for (int i = 0; i < eng.lives; i++) {
            g2.setColor(Color.YELLOW);
            g2.fillArc(62 + i * 22, hy + 16, 16, 16, 30, 300);
        }

        g2.setColor(AppColors.HUD_LABEL);
        g2.drawString("SCORE:", 175, hy + 30);
        g2.setColor(AppColors.HUD_SCORE);
        g2.setFont(FONT_BOLD_15);
        g2.drawString(String.valueOf(eng.score), 230, hy + 30);

        g2.setFont(FONT_BOLD_12);
        g2.setColor(AppColors.HUD_LABEL);
        g2.drawString("TIME:", 310, hy + 30);
        g2.setColor(AppColors.HUD_TIME);
        g2.drawString(eng.elapsed + "s", 350, hy + 30);

        g2.setColor(AppColors.HUD_LABEL);
        g2.drawString("DOTS:", 410, hy + 30);
        g2.setColor(AppColors.HUD_DOTS);
        g2.drawString(String.valueOf(eng.maze.dotsLeft), 452, hy + 30);

        if (eng.powerMode) {
            long rem   = Math.max(0, eng.powerEnd - now);
            float frac = (float) rem / GameEngine.POWER_MS;
            boolean blink = (rem < 2000 && (now / 250) % 2 == 0);
            int bx = W - 85, by = hy + 10, bh = 28;
            g2.setColor(new Color(30, 30, 80));
            g2.fillRoundRect(bx, by, 80, bh, 8, 8);
            if (!blink && (int)(80 * frac) > 0) {
                g2.setPaint(new GradientPaint(bx, by, new Color(80, 160, 255),
                        bx + (int)(80 * frac), by, new Color(40, 80, 200)));
                g2.fillRoundRect(bx, by, (int)(80 * frac), bh, 8, 8);
            }
            g2.setColor(new Color(120, 190, 255));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(bx, by, 80, bh, 8, 8);
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_BOLD_10);
            g2.drawString("POWER", bx + 18, by + 18);
        }
    }

    void drawOverlay(Graphics2D g2, boolean won, int score, int elapsed) {
        g2.setColor(AppColors.OVERLAY);
        g2.fillRect(0, 0, W, H);
        int px = (W - 430) / 2, py = H / 2 - 120, pw = 430, ph = 230;
        g2.setPaint(won
                ? new GradientPaint(px, py, new Color(0, 55, 0, 225), px, py + ph, new Color(0, 90, 25, 225))
                : new GradientPaint(px, py, new Color(75, 0, 0, 225), px, py + ph, new Color(35, 0, 0, 225)));
        g2.fillRoundRect(px, py, pw, ph, 26, 26);
        g2.setColor(won ? new Color(70, 255, 70) : new Color(255, 70, 70));
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawRoundRect(px, py, pw, ph, 26, 26);

        String title = won ? "YOU WIN!" : "GAME OVER";
        g2.setFont(FONT_BOLD_50);
        g2.setPaint(won
                ? new GradientPaint(0, py + 70, new Color(80, 255, 80), 0, py + 120, new Color(0, 180, 0))
                : new GradientPaint(0, py + 70, new Color(255, 80, 80), 0, py + 120, new Color(180, 0, 0)));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, (W - fm.stringWidth(title)) / 2, py + 80);

        g2.setColor(Color.WHITE);
        g2.setFont(FONT_PLAIN_17);
        String sub = "Score: " + score + "   |   Time: " + elapsed + "s";
        g2.drawString(sub, (W - g2.getFontMetrics().stringWidth(sub)) / 2, py + 115);

        g2.setColor(new Color(180, 180, 255));
        g2.setFont(FONT_PLAIN_15);
        String hint = "[ R ] Retry          [ M ] Main Menu";
        g2.drawString(hint, (W - g2.getFontMetrics().stringWidth(hint)) / 2, py + 150);

        g2.setColor(new Color(140, 140, 160));
        g2.setFont(FONT_ITALIC_13);
        String sub2 = "4 ghosts  \u00B7  Medium difficulty";
        g2.drawString(sub2, (W - g2.getFontMetrics().stringWidth(sub2)) / 2, py + 178);
    }
}


class PacGamePanel extends JPanel implements KeyListener {

    private static final int  LOGIC_UPS = 12;
    private static final long LOGIC_NS  = 1_000_000_000L / LOGIC_UPS;
    private static final long RENDER_NS = 1_000_000_000L / 60;

    private final PacManGame2          frame;
    private final ArrayList<GameScore> highScores;
    private final AudioManager         audio    = new AudioManager();
    private final GameRenderer         renderer = new GameRenderer();
    private final GameEngine           eng;

    private volatile boolean running   = true;
    private          boolean nameAsked = false;

    PacGamePanel(PacManGame2 frame, ArrayList<GameScore> highScores) {
        this.frame      = frame;
        this.highScores = highScores;
        setPreferredSize(new Dimension(GameRenderer.W, GameRenderer.H + GameRenderer.HUD));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        setDoubleBuffered(true);
        eng = new GameEngine(audio);

        Thread logicThread = new Thread(this::logicLoop, "pac-logic");
        logicThread.setDaemon(true);
        logicThread.setPriority(Thread.NORM_PRIORITY + 1);

        Thread renderThread = new Thread(this::renderLoop, "pac-render");
        renderThread.setDaemon(true);
        renderThread.setPriority(Thread.NORM_PRIORITY);

        logicThread.start();
        renderThread.start();
    }

    void stopLoop() { running = false; }

    private void logicLoop() {
        long nextTick = System.nanoTime();
        while (running) {
            eng.update();
            nextTick += LOGIC_NS;

            if ((eng.gameOver || eng.gameWon) && running) {
                running = false;
                SwingUtilities.invokeLater(this::askName);
                return;
            }

            long now       = System.nanoTime();
            long remaining = nextTick - now;
            if (remaining > 2_000_000L) {
                try { Thread.sleep((remaining - 1_000_000L) / 1_000_000L); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
            while (System.nanoTime() < nextTick && running) { Thread.onSpinWait(); }
        }
    }

    private void renderLoop() {
        long nextFrame = System.nanoTime();
        while (running || eng.gameOver || eng.gameWon) {
            repaint();
            nextFrame += RENDER_NS;

            long now       = System.nanoTime();
            long remaining = nextFrame - now;
            if (remaining > 2_000_000L) {
                try { Thread.sleep((remaining - 1_000_000L) / 1_000_000L); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
            while (System.nanoTime() < nextFrame) { Thread.onSpinWait(); }
        }
    }

    private void askName() {
        if (nameAsked) return;
        nameAsked = true;
        String msg  = (eng.gameWon ? "You Win! " : "Game Over! ") + "Score: " + eng.score + "\nEnter your name:";
        String name = JOptionPane.showInputDialog(this, msg, "High Score", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) frame.recordScore(name.trim(), eng.score, eng.elapsed);
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        renderer.render((Graphics2D) g, eng, System.currentTimeMillis());
    }

    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:    case KeyEvent.VK_W: eng.pac.setNextDir(-1,  0); break;
            case KeyEvent.VK_DOWN:  case KeyEvent.VK_S: eng.pac.setNextDir( 1,  0); break;
            case KeyEvent.VK_LEFT:  case KeyEvent.VK_A: eng.pac.setNextDir( 0, -1); break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: eng.pac.setNextDir( 0,  1); break;
            case KeyEvent.VK_R: if (eng.gameOver || eng.gameWon) { stopLoop(); frame.startGame(); } break;
            case KeyEvent.VK_M: if (eng.gameOver || eng.gameWon) { stopLoop(); frame.showMenu();  } break;
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e)    {}
    @Override public Dimension getPreferredSize()  { return new Dimension(GameRenderer.W, GameRenderer.H + GameRenderer.HUD); }
}


class MainMenuPanel extends JPanel {
    private final PacManGame2    frame;
    private ArrayList<GameScore> highScores = new ArrayList<>();
    private float                anim       = 0f;

    private final int BTN_W = 300, BTN_H = 58;
    private int       btnX  = 0;
    private final Rectangle BTN_START = new Rectangle(0, 145, BTN_W, BTN_H);
    private final Rectangle BTN_EXIT  = new Rectangle(0, 220, BTN_W, BTN_H);

    private static final Color[] RANK_COLS = {
            new Color(255, 215,   0), new Color(210, 210, 210),
            new Color(205, 130,  50), new Color(160, 210, 255), new Color(160, 210, 255)
    };

    private static final Font F_TITLE    = new Font("Arial", Font.BOLD,   58);
    private static final Font F_ITALIC13 = new Font("Arial", Font.ITALIC, 13);
    private static final Font F_BOLD20   = new Font("Arial", Font.BOLD,   20);
    private static final Font F_BOLD12   = new Font("Arial", Font.BOLD,   12);
    private static final Font F_BOLD14   = new Font("Arial", Font.BOLD,   14);
    private static final Font F_PLAIN14  = new Font("Arial", Font.PLAIN,  14);
    private static final Font F_BOLD22   = new Font("Arial", Font.BOLD,   22);

    MainMenuPanel(PacManGame2 frame) {
        this.frame = frame;
        int panelW = GameRenderer.W;
        int panelH = GameRenderer.H + GameRenderer.HUD;
        setPreferredSize(new Dimension(panelW, panelH));
        setBackground(new Color(8, 8, 20));
        setFocusable(true);

        btnX        = (panelW - BTN_W) / 2;
        BTN_START.x = btnX;
        BTN_EXIT.x  = btnX;

        javax.swing.Timer ticker = new javax.swing.Timer(40, e -> { anim += 0.04f; repaint(); });
        ticker.start();

        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if      (BTN_START.contains(e.getPoint())) frame.startGame();
                else if (BTN_EXIT .contains(e.getPoint())) System.exit(0);
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) frame.startGame();
            }
        });
    }

    void updateHighScores(ArrayList<GameScore> s) { highScores = new ArrayList<>(s); repaint(); }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int W = getWidth(), H = getHeight();
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setPaint(new GradientPaint(0, 0, new Color(8, 8, 22), 0, H, new Color(18, 0, 35)));
        g2.fillRect(0, 0, W, H);

        for (int i = 0; i < 18; i++) {
            float x = (float)(Math.sin(anim * .7 + i * 1.1) * (W * 0.4) + W / 2.0);
            float y = (float)(Math.cos(anim * .4 + i * .9)  * (H * 0.4) + H / 2.0);
            int   a = Math.max(0, Math.min(255, 18 + (int)(Math.sin(anim + i) * 10)));
            g2.setColor(new Color(120, 80, 255, a));
            g2.fillOval((int) x - 6, (int) y - 6, 12, 12);
        }

        g2.setFont(F_TITLE);
        String title = "PAC-MAN";
        FontMetrics fm = g2.getFontMetrics();
        int tx = (W - fm.stringWidth(title)) / 2;
        for (int i = 8; i > 0; i--) {
            g2.setColor(new Color(255, 200, 0, 8));
            g2.drawString(title, tx - i, 92 + i / 3);
            g2.drawString(title, tx + i, 92 + i / 3);
        }
        g2.setPaint(new GradientPaint(tx, 40, new Color(255, 230, 0), tx, 92, new Color(255, 130, 0)));
        g2.drawString(title, tx, 92);

        int mouth = 25 + (int)(Math.abs(Math.sin(anim * 3)) * 30);
        g2.setColor(new Color(255, 230, 0));
        g2.fillArc(tx - 46, 57, 36, 36, mouth, 360 - mouth * 2);

        g2.setFont(F_ITALIC13);
        g2.setColor(new Color(160, 130, 255));
        String sub = "Eat all dots. Avoid the ghosts. Grab power pellets!";
        g2.drawString(sub, (W - g2.getFontMetrics().stringWidth(sub)) / 2, 115);

        drawBtn(g2, "START GAME", BTN_START, new Color(40, 170, 70),  new Color(20, 110, 40));
        drawBtn(g2, "EXIT",       BTN_EXIT,  new Color(190,  50, 50),  new Color(130, 25, 25));

        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(255, 165, 0, 60));
        g2.drawLine(60, 298, W - 60, 298);

        g2.setFont(F_BOLD20);
        g2.setPaint(new GradientPaint(150, 0, new Color(255, 220, 0), W - 150, 0, new Color(255, 140, 0)));
        String hsTitle = "TOP 5 HIGH SCORES";
        g2.drawString(hsTitle, (W - g2.getFontMetrics().stringWidth(hsTitle)) / 2, 330);

        g2.setFont(F_BOLD12);
        g2.setColor(new Color(150, 150, 220));
        int lx = 80;
        g2.drawString("RANK",  lx,       355);
        g2.drawString("NAME",  lx + 65,  355);
        g2.drawString("SCORE", lx + 220, 355);
        g2.drawString("TIME",  lx + 345, 355);
        g2.setColor(new Color(80, 80, 160, 80));
        g2.drawLine(lx, 360, W - lx, 360);

        int y = 385;
        if (highScores.isEmpty()) {
            g2.setColor(new Color(140, 140, 140));
            g2.setFont(F_ITALIC13);
            String noScore = "No scores yet -- be the first!";
            g2.drawString(noScore, (W - g2.getFontMetrics().stringWidth(noScore)) / 2, y);
        } else {
            for (int i = 0; i < Math.min(5, highScores.size()); i++) {
                GameScore hs = highScores.get(i);
                if (i % 2 == 0) {
                    g2.setColor(new Color(255, 255, 255, 8));
                    g2.fillRoundRect(lx - 4, y - 16, W - lx * 2 + 8, 24, 8, 8);
                }
                g2.setColor(RANK_COLS[i]);
                g2.setFont(F_BOLD14);
                g2.drawString("#" + (i + 1), lx, y);
                g2.setColor(Color.WHITE);
                g2.setFont(F_PLAIN14);
                String displayName = hs.name.length() > 14 ? hs.name.substring(0, 13) + "\u2026" : hs.name;
                g2.drawString(displayName, lx + 65,  y);
                g2.setColor(new Color(90, 220, 90));
                g2.drawString(hs.score + " pts", lx + 220, y);
                g2.setColor(new Color(160, 200, 255));
                g2.drawString(hs.timeSec + "s", lx + 345, y);
                y += 30;
            }
        }

        g2.setFont(F_ITALIC13);
        g2.setColor(new Color(100, 160, 255, 180));
        String hint = "Click START or press ENTER";
        g2.drawString(hint, (W - g2.getFontMetrics().stringWidth(hint)) / 2, H - 20);
    }

    private void drawBtn(Graphics2D g2, String text, Rectangle r, Color top, Color bot) {
        g2.setPaint(new GradientPaint(r.x, r.y, top, r.x, r.y + r.height, bot));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 16, 16);
        g2.setColor(top.brighter());
        g2.setStroke(new BasicStroke(1.8f));
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 16, 16);
        g2.setColor(Color.WHITE);
        g2.setFont(F_BOLD22);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, r.x + (r.width - fm.stringWidth(text)) / 2, r.y + r.height / 2 + 8);
    }

    @Override public void addNotify() { super.addNotify(); requestFocusInWindow(); }
}
