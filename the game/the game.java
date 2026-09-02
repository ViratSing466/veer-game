import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import javax.sound.sampled.*;

public class VeerWinterHunt extends JPanel implements Runnable, KeyListener, MouseListener, MouseMotionListener {

    // =========================================================
    // WINDOW / WORLD
    // =========================================================

    static final int SCREEN_W = 1200;
    static final int SCREEN_H = 720;

    static final int WORLD_W = 5000;
    static final int WORLD_H = 3500;

    Thread gameThread;
    boolean running = false;

    double camX = 0;
    double camY = 0;

    Random random = new Random();

    // =========================================================
    // PLAYER
    // =========================================================

    Player player = new Player(WORLD_W / 2, WORLD_H / 2);

    int kills = 0;
    int money = 0;

    String weapon = "SWORD";

    boolean hasBow = false;
    int arrows = 0;

    boolean inventoryOpen = false;
    boolean shopOpen = false;

    // =========================================================
    // OBJECTS
    // =========================================================

    ArrayList<Enemy> enemies = new ArrayList<>();
    ArrayList<Arrow> arrowList = new ArrayList<>();
    ArrayList<Particle> particles = new ArrayList<>();
    ArrayList<Coin> coins = new ArrayList<>();
    ArrayList<Snow> snow = new ArrayList<>();

    Yeti yeti = null;
    boolean yetiActive = false;

    int attackCooldown = 0;
    int dashCooldown = 0;

    // =========================================================
    // INPUT
    // =========================================================

    boolean up, down, left, right;
    boolean mouseDown;

    int mouseX;
    int mouseY;

    // =========================================================
    // MUSIC
    // =========================================================

    javax.swing.Timer musicTimer;
    int musicNote = 0;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public VeerWinterHunt() {

        setPreferredSize(new Dimension(SCREEN_W, SCREEN_H));
        setFocusable(true);

        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        // Snow
        for (int i = 0; i < 500; i++) {
            snow.add(new Snow(
                    random.nextInt(WORLD_W),
                    random.nextInt(WORLD_H),
                    1 + random.nextDouble() * 2
            ));
        }

        // Starting enemies
        spawnWolfPack();
        spawnBear();

        gameThread = new Thread(this);
        gameThread.start();

        startMusic();
    }

    // =========================================================
    // MAIN LOOP
    // =========================================================

    @Override
    public void run() {

        running = true;

        long last = System.nanoTime();
        double nsPerUpdate = 1_000_000_000.0 / 60.0;
        double accumulator = 0;

        while (running) {

            long now = System.nanoTime();

            accumulator += (now - last) / nsPerUpdate;
            last = now;

            while (accumulator >= 1) {
                updateGame();
                accumulator--;
            }

            repaint();

            try {
                Thread.sleep(2);
            } catch (Exception ignored) {}
        }
    }

    // =========================================================
    // UPDATE
    // =========================================================

    void updateGame() {

        if (player.dead) return;

        player.update();

        if (attackCooldown > 0)
            attackCooldown--;

        if (dashCooldown > 0)
            dashCooldown--;

        updateEnemies();
        updateArrows();
        updateParticles();
        updateCoins();
        updateSnow();

        if (yetiActive && yeti != null)
            yeti.update();

        // Camera
        camX = player.x - SCREEN_W / 2.0;
        camY = player.y - SCREEN_H / 2.0;

        camX = Math.max(0, Math.min(WORLD_W - SCREEN_W, camX));
        camY = Math.max(0, Math.min(WORLD_H - SCREEN_H, camY));

        // Keep spawning
        if (!yetiActive && enemies.size() < 7) {

            if (random.nextBoolean())
                spawnWolfPack();
            else
                spawnBear();
        }

        // Mouse attack
        if (mouseDown) {
            attack();
        }

        if (player.hp <= 0) {
            player.dead = true;
        }
    }

    // =========================================================
    // PLAYER
    // =========================================================

    class Player {

        double x, y;

        int hp = 100;
        int maxHp = 100;

        double speed = 4.2;

        int invincible = 0;

        boolean dead = false;

        double lastDX = 1;
        double lastDY = 0;

        Player(double x, double y) {
            this.x = x;
            this.y = y;
        }

        void update() {

            double dx = 0;
            double dy = 0;

            if (up) dy--;
            if (down) dy++;
            if (left) dx--;
            if (right) dx++;

            if (dx != 0 || dy != 0) {

                double len = Math.sqrt(dx * dx + dy * dy);

                dx /= len;
                dy /= len;

                x += dx * speed;
                y += dy * speed;

                lastDX = dx;
                lastDY = dy;
            }

            x = Math.max(80, Math.min(WORLD_W - 80, x));
            y = Math.max(80, Math.min(WORLD_H - 80, y));

            if (invincible > 0)
                invincible--;
        }

        void draw(Graphics2D g) {

            double sx = x - camX;
            double sy = y - camY;

            // Shadow
            g.setColor(new Color(0, 0, 0, 70));

            g.fillOval(
                    (int)sx - 28,
                    (int)sy + 35,
                    56,
                    18
            );

            // Cape
            Polygon cape = new Polygon();

            cape.addPoint((int)sx - 27, (int)sy - 15);
            cape.addPoint((int)sx + 27, (int)sy - 15);
            cape.addPoint((int)sx + 40, (int)sy + 58);
            cape.addPoint((int)sx - 40, (int)sy + 58);

            g.setColor(new Color(25, 43, 65));
            g.fillPolygon(cape);

            // Boots
            g.setColor(new Color(25, 29, 35));

            g.fillRoundRect(
                    (int)sx - 23,
                    (int)sy + 25,
                    18,
                    38,
                    7,
                    7
            );

            g.fillRoundRect(
                    (int)sx + 5,
                    (int)sy + 25,
                    18,
                    38,
                    7,
                    7
            );

            // Body armor
            g.setColor(new Color(55, 86, 115));

            g.fillRoundRect(
                    (int)sx - 29,
                    (int)sy - 20,
                    58,
                    60,
                    12,
                    12
            );

            // Armor chest
            g.setColor(new Color(110, 145, 170));

            g.fillRoundRect(
                    (int)sx - 23,
                    (int)sy - 10,
                    46,
                    32,
                    8,
                    8
            );

            // Shoulder pads
            g.setColor(new Color(80, 110, 135));

            g.fillOval(
                    (int)sx - 38,
                    (int)sy - 17,
                    22,
                    22
            );

            g.fillOval(
                    (int)sx + 16,
                    (int)sy - 17,
                    22,
                    22
            );

            // Neck
            g.setColor(new Color(190, 130, 105));

            g.fillRect(
                    (int)sx - 9,
                    (int)sy - 37,
                    18,
                    17
            );

            // Head
            g.setColor(new Color(205, 150, 125));

            g.fillOval(
                    (int)sx - 27,
                    (int)sy - 76,
                    54,
                    54
            );

            // Hair
            g.setColor(new Color(28, 24, 27));

            g.fillArc(
                    (int)sx - 30,
                    (int)sy - 82,
                    60,
                    48,
                    0,
                    180
            );

            // Hair spikes
            for (int i = -2; i <= 2; i++) {

                Polygon spike = new Polygon();

                spike.addPoint(
                        (int)sx + i * 10 - 7,
                        (int)sy - 66
                );

                spike.addPoint(
                        (int)sx + i * 10 + 7,
                        (int)sy - 66
                );

                spike.addPoint(
                        (int)sx + i * 10,
                        (int)sy - 82
                );

                g.fillPolygon(spike);
            }

            // Eyes
            g.setColor(Color.WHITE);

            g.fillOval(
                    (int)sx - 17,
                    (int)sy - 52,
                    12,
                    8
            );

            g.fillOval(
                    (int)sx + 5,
                    (int)sy - 52,
                    12,
                    8
            );

            g.setColor(Color.BLACK);

            g.fillOval(
                    (int)sx - 13,
                    (int)sy - 50,
                    5,
                    5
            );

            g.fillOval(
                    (int)sx + 9,
                    (int)sy - 50,
                    5,
                    5
            );

            // Scarf
            g.setColor(new Color(150, 45, 55));

            g.fillRect(
                    (int)sx - 25,
                    (int)sy - 30,
                    50,
                    10
            );

            // Weapon
            drawPlayerWeapon(g, sx, sy);

            // Damage flash
            if (invincible > 0) {

                g.setColor(new Color(255, 255, 255, 80));

                g.fillOval(
                        (int)sx - 45,
                        (int)sy - 90,
                        90,
                        150
                );
            }
        }
    }

    // =========================================================
    // PLAYER WEAPONS
    // =========================================================

    void drawPlayerWeapon(Graphics2D g, double sx, double sy) {

        if (weapon.equals("SWORD")) {

            g.setStroke(new BasicStroke(7));

            g.setColor(new Color(220, 235, 245));

            g.drawLine(
                    (int)sx + 24,
                    (int)sy,
                    (int)sx + 68,
                    (int)sy - 58
            );

            g.setColor(new Color(220, 180, 70));

            g.setStroke(new BasicStroke(5));

            g.drawLine(
                    (int)sx + 15,
                    (int)sy - 5,
                    (int)sx + 42,
                    (int)sy + 14
            );

            g.setColor(new Color(80, 45, 30));

            g.setStroke(new BasicStroke(8));

            g.drawLine(
                    (int)sx + 17,
                    (int)sy + 2,
                    (int)sx + 29,
                    (int)sy + 17
            );
        }

        else if (weapon.equals("STICK")) {

            g.setColor(new Color(95, 58, 32));

            g.setStroke(new BasicStroke(10));

            g.drawLine(
                    (int)sx + 20,
                    (int)sy + 12,
                    (int)sx + 70,
                    (int)sy - 55
            );
        }

        else if (weapon.equals("BOW")) {

            g.setColor(new Color(105, 65, 35));

            g.setStroke(new BasicStroke(7));

            Arc2D bow = new Arc2D.Double(
                    sx + 10,
                    sy - 55,
                    65,
                    90,
                    -75,
                    150,
                    Arc2D.OPEN
            );

            g.draw(bow);

            g.setColor(Color.WHITE);

            g.setStroke(new BasicStroke(2));

            g.drawLine(
                    (int)sx + 44,
                    (int)sy - 48,
                    (int)sx + 44,
                    (int)sy + 38
            );
        }
    }

    // =========================================================
    // ENEMY
    // =========================================================

    class Enemy {

        double x, y;

        String type;

        int hp;
        int maxHp;

        double speed;

        int attackTimer = 0;

        Enemy(double x, double y, String type) {

            this.x = x;
            this.y = y;
            this.type = type;

            if (type.equals("WOLF")) {

                hp = 3;
                maxHp = 3;
                speed = 2.0 + random.nextDouble() * .5;

            } else {

                hp = 9;
                maxHp = 9;
                speed = .8;
            }
        }

        void update() {

            double dx = player.x - x;
            double dy = player.y - y;

            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist > 70) {

                x += dx / dist * speed;
                y += dy / dist * speed;

            } else {

                attackTimer--;

                if (attackTimer <= 0) {

                    damagePlayer(type.equals("BEAR") ? 14 : 7);

                    attackTimer =
                            type.equals("BEAR") ? 80 : 55;
                }
            }
        }

        void draw(Graphics2D g) {

            double sx = x - camX;
            double sy = y - camY;

            if (type.equals("WOLF"))
                drawWolf(g, sx, sy);
            else
                drawBear(g, sx, sy);
        }
    }

    // =========================================================
    // WOLF DRAW
    // =========================================================

    void drawWolf(Graphics2D g, double x, double y) {

        g.setColor(new Color(0, 0, 0, 70));

        g.fillOval(
                (int)x - 32,
                (int)y + 22,
                64,
                14
        );

        // Body
        g.setColor(new Color(75, 90, 100));

        g.fillOval(
                (int)x - 38,
                (int)y - 5,
                65,
                42
        );

        // Head
        g.fillOval(
                (int)x + 12,
                (int)y - 27,
                45,
                45
        );

        // Ears
        Polygon ear1 = new Polygon();

        ear1.addPoint((int)x + 17, (int)y - 17);
        ear1.addPoint((int)x + 18, (int)y - 47);
        ear1.addPoint((int)x + 34, (int)y - 25);

        g.fillPolygon(ear1);

        Polygon ear2 = new Polygon();

        ear2.addPoint((int)x + 40, (int)y - 21);
        ear2.addPoint((int)x + 53, (int)y - 45);
        ear2.addPoint((int)x + 58, (int)y - 10);

        g.fillPolygon(ear2);

        // Snout
        g.setColor(new Color(45, 52, 58));

        g.fillOval(
                (int)x + 43,
                (int)y - 7,
                27,
                17
        );

        // Eyes
        g.setColor(new Color(255, 65, 75));

        g.fillOval(
                (int)x + 25,
                (int)y - 12,
                7,
                6
        );

        g.fillOval(
                (int)x + 40,
                (int)y - 12,
                7,
                6
        );

        // Legs
        g.setColor(new Color(55, 66, 73));

        g.fillRoundRect(
                (int)x - 28,
                (int)y + 20,
                10,
                27,
                5,
                5
        );

        g.fillRoundRect(
                (int)x - 5,
                (int)y + 20,
                10,
                27,
                5,
                5
        );

        g.fillRoundRect(
                (int)x + 22,
                (int)y + 18,
                10,
                27,
                5,
                5
        );

        // Tail
        g.setStroke(new BasicStroke(10));

        g.drawLine(
                (int)x - 33,
                (int)y,
                (int)x - 60,
                (int)y - 25
        );
    }

    // =========================================================
    // BEAR DRAW
    // =========================================================

    void drawBear(Graphics2D g, double x, double y) {

        // Shadow
        g.setColor(new Color(0, 0, 0, 80));

        g.fillOval(
                (int)x - 55,
                (int)y + 55,
                110,
                20
        );

        // Body
        g.setColor(new Color(83, 57, 43));

        g.fillOval(
                (int)x - 55,
                (int)y - 10,
                110,
                110
        );

        // Arms
        g.fillOval(
                (int)x - 78,
                (int)y,
                35,
                80
        );

        g.fillOval(
                (int)x + 43,
                (int)y,
                35,
                80
        );

        // Head
        g.fillOval(
                (int)x - 47,
                (int)y - 75,
                94,
                85
        );

        // Ears
        g.fillOval(
                (int)x - 55,
                (int)y - 86,
                30,
                30
        );

        g.fillOval(
                (int)x + 25,
                (int)y - 86,
                30,
                30
        );

        // Face
        g.setColor(new Color(125, 88, 65));

        g.fillOval(
                (int)x - 24,
                (int)y - 42,
                48,
                38
        );

        // Eyes
        g.setColor(new Color(255, 65, 65));

        g.fillOval(
                (int)x - 23,
                (int)y - 50,
                10,
                7
        );

        g.fillOval(
                (int)x + 13,
                (int)y - 50,
                10,
                7
        );

        // Nose
        g.setColor(Color.BLACK);

        g.fillOval(
                (int)x - 7,
                (int)y - 32,
                14,
                10
        );

        // Legs
        g.setColor(new Color(70, 48, 38));

        g.fillRoundRect(
                (int)x - 35,
                (int)y + 75,
                25,
                35,
                10,
                10
        );

        g.fillRoundRect(
                (int)x + 10,
                (int)y + 75,
                25,
                35,
                10,
                10
        );

        // Health
        drawHealthBar(
                g,
                x - 45,
                y - 105,
                90,
                7,
                .9
        );
    }

    // =========================================================
    // WOLF PACK
    // =========================================================

    void spawnWolfPack() {

        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 600 + random.nextInt(500);

        double centerX =
                player.x + Math.cos(angle) * distance;

        double centerY =
                player.y + Math.sin(angle) * distance;

        int count = 3 + random.nextInt(3);

        for (int i = 0; i < count; i++) {

            double a = random.nextDouble() * Math.PI * 2;
            double r = 50 + random.nextInt(90);

            double x = centerX + Math.cos(a) * r;
            double y = centerY + Math.sin(a) * r;

            x = Math.max(100, Math.min(WORLD_W - 100, x));
            y = Math.max(100, Math.min(WORLD_H - 100, y));

            enemies.add(new Enemy(x, y, "WOLF"));
        }
    }

    // =========================================================
    // SINGLE BEAR
    // =========================================================

    void spawnBear() {

        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 900 + random.nextInt(500);

        double x =
                player.x + Math.cos(angle) * distance;

        double y =
                player.y + Math.sin(angle) * distance;

        x = Math.max(100, Math.min(WORLD_W - 100, x));
        y = Math.max(100, Math.min(WORLD_H - 100, y));

        enemies.add(new Enemy(x, y, "BEAR"));
    }

    // =========================================================
    // ENEMY UPDATE
    // =========================================================

    void updateEnemies() {

        for (int i = enemies.size() - 1; i >= 0; i--) {

            Enemy e = enemies.get(i);

            e.update();

            if (e.hp <= 0) {

                createExplosion(e.x, e.y, 20);

                kills++;

                if (random.nextDouble() < .55) {

                    int amount =
                            random.nextBoolean() ? 5 : 10;

                    coins.add(
                            new Coin(e.x, e.y, amount)
                    );
                }

                enemies.remove(i);

                soundHit();

                // Yeti after 20 kills
                if (kills >= 20 && !yetiActive) {
                    activateYeti();
                }
            }
        }
    }

    // =========================================================
    // PLAYER DAMAGE
    // =========================================================

    void damagePlayer(int amount) {

        if (player.invincible > 0)
            return;

        player.hp -= amount;
        player.invincible = 35;

        createExplosion(
                player.x,
                player.y,
                12
        );

        soundDamage();
    }

    // =========================================================
    // ATTACK
    // =========================================================

    void attack() {

        if (attackCooldown > 0)
            return;

        if (inventoryOpen || shopOpen)
            return;

        if (weapon.equals("BOW")) {

            if (!hasBow || arrows <= 0) {

                soundError();
                return;
            }

            shootArrow();

            arrows--;

            attackCooldown = 16;

            return;
        }

        attackCooldown =
                weapon.equals("SWORD") ? 22 : 28;

        double attackRange =
                weapon.equals("SWORD") ? 125 : 105;

        int damage =
                weapon.equals("SWORD") ? 3 : 2;

        createSlash();

        if (weapon.equals("SWORD"))
            soundSword();
        else
            soundStick();

        for (Enemy e : enemies) {

            double dx = e.x - player.x;
            double dy = e.y - player.y;

            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist < attackRange) {

                e.hp -= damage;

                createExplosion(
                        e.x,
                        e.y,
                        8
                );
            }
        }

        if (yetiActive && yeti != null) {

            double dx = yeti.x - player.x;
            double dy = yeti.y - player.y;

            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist < 160) {

                yeti.hp -=
                        weapon.equals("SWORD")
                        ? 12
                        : 7;

                createExplosion(
                        yeti.x,
                        yeti.y,
                        12
                );
            }
        }
    }

    // =========================================================
    // BOW
    // =========================================================

    void shootArrow() {

        double dx =
                mouseX + camX - player.x;

        double dy =
                mouseY + camY - player.y;

        double len =
                Math.sqrt(dx * dx + dy * dy);

        if (len == 0)
            return;

        dx /= len;
        dy /= len;

        arrowList.add(
                new Arrow(
                        player.x,
                        player.y,
                        dx * 12,
                        dy * 12
                )
        );

        soundBow();
    }

    // =========================================================
    // ARROWS
    // =========================================================

    class Arrow {

        double x, y;
        double vx, vy;

        int life = 100;

        Arrow(double x, double y, double vx, double vy) {

            this.x = x;
            this.y = y;

            this.vx = vx;
            this.vy = vy;
        }

        void update() {

            x += vx;
            y += vy;

            life--;
        }

        void draw(Graphics2D g) {

            double sx = x - camX;
            double sy = y - camY;

            double angle = Math.atan2(vy, vx);

            g.saveTransform(angle, sx, sy);

            g.setColor(new Color(100, 65, 40));
            g.setStroke(new BasicStroke(4));

            g.drawLine(-20, 0, 20, 0);

            Polygon tip = new Polygon();

            tip.addPoint(25, 0);
            tip.addPoint(12, -6);
            tip.addPoint(12, 6);

            g.setColor(Color.WHITE);

            g.fillPolygon(tip);

            g.restoreTransform();
        }
    }

    // =========================================================
    // ARROW UPDATE
    // =========================================================

    void updateArrows() {

        for (int i = arrowList.size() - 1; i >= 0; i--) {

            Arrow a = arrowList.get(i);

            a.update();

            boolean remove = false;

            for (Enemy e : enemies) {

                double dx = e.x - a.x;
                double dy = e.y - a.y;

                double d = Math.sqrt(dx * dx + dy * dy);

                if (d < 40) {

                    e.hp -= 3;

                    createExplosion(
                            e.x,
                            e.y,
                            8
                    );

                    remove = true;
                    break;
                }
            }

            if (yetiActive && yeti != null && !remove) {

                double dx = yeti.x - a.x;
                double dy = yeti.y - a.y;

                double d = Math.sqrt(dx * dx + dy * dy);

                if (d < 65) {

                    yeti.hp -= 10;

                    createExplosion(
                            yeti.x,
                            yeti.y,
                            10
                    );

                    remove = true;
                }
            }

            if (a.life <= 0)
                remove = true;

            if (remove)
                arrowList.remove(i);
        }
    }

    // =========================================================
    // YETI
    // =========================================================

    void activateYeti() {

        yetiActive = true;

        enemies.clear();

        yeti = new Yeti(
                player.x + 700,
                player.y
        );

        soundYeti();

        JOptionPane.showMessageDialog(
                this,
                "THE YETI HAS AWAKENED!\n\nPrepare yourself."
        );
    }

    class Yeti {

        double x, y;

        int hp = 500;
        int maxHp = 500;

        int attackTimer = 80;

        double speed = 1.1;

        Yeti(double x, double y) {

            this.x = x;
            this.y = y;
        }

        void update() {

            double dx = player.x - x;
            double dy = player.y - y;

            double dist =
                    Math.sqrt(dx * dx + dy * dy);

            if (dist > 130) {

                x += dx / dist * speed;
                y += dy / dist * speed;

            } else {

                attackTimer--;

                if (attackTimer <= 0) {

                    damagePlayer(20);

                    createExplosion(
                            player.x,
                            player.y,
                            25
                    );

                    attackTimer = 90;
                }
            }

            if (hp <= 0) {

                running = false;

                JOptionPane.showMessageDialog(
                        thisPanel(),
                        "🐻‍❄️ YETI DEFEATED!\n\n" +
                        "YOU WIN!\n\n" +
                        "Kills: " + kills +
                        "\nMoney: " + money
                );

                System.exit(0);
            }
        }

        void draw(Graphics2D g) {

            double sx = x - camX;
            double sy = y - camY;

            // Shadow
            g.setColor(new Color(0, 0, 0, 80));

            g.fillOval(
                    (int)sx - 90,
                    (int)sy + 80,
                    180,
                    30
            );

            // Body
            g.setColor(new Color(225, 239, 243));

            g.fillOval(
                    (int)sx - 90,
                    (int)sy - 20,
                    180,
                    180
            );

            // Arms
            g.fillOval(
                    (int)sx - 125,
                    (int)sy,
                    55,
                    120
            );

            g.fillOval(
                    (int)sx + 70,
                    (int)sy,
                    55,
                    120
            );

            // Head
            g.fillOval(
                    (int)sx - 75,
                    (int)sy - 115,
                    150,
                    130
            );

            // Ears
            g.fillOval(
                    (int)sx - 85,
                    (int)sy - 130,
                    50,
                    50
            );

            g.fillOval(
                    (int)sx + 35,
                    (int)sy - 130,
                    50,
                    50
            );

            // Eyes
            g.setColor(new Color(255, 55, 65));

            g.fillOval(
                    (int)sx - 38,
                    (int)sy - 65,
                    20,
                    14
            );

            g.fillOval(
                    (int)sx + 18,
                    (int)sy - 65,
                    20,
                    14
            );

            // Mouth
            g.setColor(new Color(40, 40, 45));

            g.fillRoundRect(
                    (int)sx - 38,
                    (int)sy - 35,
                    76,
                    20,
                    10,
                    10
            );

            // Boss health
            drawHealthBar(
                    g,
                    sx - 120,
                    sy - 170,
                    240,
                    15,
                    (double)hp / maxHp
            );
        }
    }

    // =========================================================
    // COINS
    // =========================================================

    class Coin {

        double x, y;

        int amount;

        Coin(double x, double y, int amount) {

            this.x = x;
            this.y = y;
            this.amount = amount;
        }

        void draw(Graphics2D g) {

            double sx = x - camX;
            double sy = y - camY;

            g.setColor(new Color(235, 195, 65));

            g.fillOval(
                    (int)sx - 10,
                    (int)sy - 10,
                    20,
                    20
            );

            g.setColor(new Color(100, 70, 15));

            g.setFont(new Font("Arial", Font.BOLD, 11));

            g.drawString(
                    "$",
                    (int)sx - 4,
                    (int)sy + 4
            );
        }
    }

    void updateCoins() {

        for (int i = coins.size() - 1; i >= 0; i--) {

            Coin c = coins.get(i);

            double dx = c.x - player.x;
            double dy = c.y - player.y;

            double d = Math.sqrt(dx * dx + dy * dy);

            if (d < 55) {

                money += c.amount;

                coins.remove(i);

                soundCoin();
            }
        }
    }

    // =========================================================
    // PARTICLES
    // =========================================================

    class Particle {

        double x, y;
        double vx, vy;

        int life;

        Particle(double x, double y) {

            this.x = x;
            this.y = y;

            vx = random.nextDouble() * 6 - 3;
            vy = random.nextDouble() * 6 - 3;

            life = 30 + random.nextInt(30);
        }

        void update() {

            x += vx;
            y += vy;

            vx *= .96;
            vy *= .96;

            life--;
        }

        void draw(Graphics2D g) {

            double sx = x - camX;
            double sy = y - camY;

            int alpha =
                    Math.max(
                            0,
                            Math.min(255, life * 8)
                    );

            g.setColor(
                    new Color(
                            230,
                            245,
                            255,
                            alpha
                    )
            );

            g.fillOval(
                    (int)sx - 3,
                    (int)sy - 3,
                    6,
                    6
            );
        }
    }

    void createExplosion(double x, double y, int amount) {

        for (int i = 0; i < amount; i++) {

            particles.add(
                    new Particle(x, y)
            );
        }
    }

    void createSlash() {

        double x = player.x + player.lastDX * 65;
        double y = player.y + player.lastDY * 65;

        for (int i = 0; i < 15; i++) {

            Particle p =
                    new Particle(x, y);

            p.vx += player.lastDX * 4;
            p.vy += player.lastDY * 4;

            particles.add(p);
        }
    }

    void updateParticles() {

        for (int i = particles.size() - 1; i >= 0; i--) {

            Particle p = particles.get(i);

            p.update();

            if (p.life <= 0)
                particles.remove(i);
        }
    }

    // =========================================================
    // SNOW
    // =========================================================

    class Snow {

        double x, y;
        double speed;

        Snow(double x, double y, double speed) {

            this.x = x;
            this.y = y;
            this.speed = speed;
        }

        void update() {

            y += speed;

            x += Math.sin(y * .01) * .25;

            if (y > WORLD_H)
                y = 0;

            if (x > WORLD_W)
                x = 0;

            if (x < 0)
                x = WORLD_W;
        }

        void draw(Graphics2D g) {

            double sx = x - camX;
            double sy = y - camY;

            if (
                    sx >= 0 &&
                    sx <= SCREEN_W &&
                    sy >= 0 &&
                    sy <= SCREEN_H
            ) {

                g.setColor(
                        new Color(
                                255,
                                255,
                                255,
                                180
                        )
                );

                int size =
                        (int)(1 + speed * 1.5);

                g.fillOval(
                        (int)sx,
                        (int)sy,
                        size,
                        size
                );
            }
        }
    }

    void updateSnow() {

        for (Snow s : snow)
            s.update();
    }

    // =========================================================
    // DRAW
    // =========================================================

    @Override
    protected void paintComponent(Graphics graphics) {

        super.paintComponent(graphics);

        Graphics2D g =
                (Graphics2D) graphics.create();

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        drawWorld(g);

        drawObjects(g);

        drawHUD(g);

        if (inventoryOpen)
            drawInventory(g);

        if (shopOpen)
            drawShop(g);

        if (player.dead)
            drawGameOver(g);

        g.dispose();
    }

    // =========================================================
    // WORLD
    // =========================================================

    void drawWorld(Graphics2D g) {

        g.setColor(new Color(205, 227, 235));

        g.fillRect(
                0,
                0,
                SCREEN_W,
                SCREEN_H
        );

        g.saveTransform(-camX, -camY);

        // Ground
        g.setColor(new Color(218, 237, 242));

        g.fillRect(
                0,
                0,
                WORLD_W,
                WORLD_H
        );

        // Large ice lake
        g.setColor(new Color(145, 198, 215));

        g.fillRoundRect(
                500,
                2100,
                1400,
                750,
                120,
                120
        );

        // Ice lines
        g.setColor(new Color(205, 242, 250));

        g.setStroke(new BasicStroke(4));

        for (int i = 0; i < 10; i++) {

            int x = 550 + i * 130;

            g.drawLine(
                    x,
                    2150,
                    x + 100,
                    2750
            );
        }

        // Mountains
        drawMountains(g);

        // Roads
        g.setColor(new Color(170, 192, 200));

        g.fillRect(
                0,
                1450,
                WORLD_W,
                150
        );

        g.fillRect(
                2400,
                0,
                150,
                WORLD_H
        );

        // Trees
        for (int x = 100; x < WORLD_W; x += 260) {

            for (
                    int y = 100 + (x % 180);
                    y < WORLD_H;
                    y += 350
            ) {

                drawTree(g, x, y);
            }
        }

        // Ruins
        drawRuins(g, 900, 750);
        drawRuins(g, 3500, 750);

        // Shop
        drawShopBuilding(g, 4200, 1550);

        g.restoreTransform(0, 0);
    }

    // =========================================================
    // MOUNTAINS
    // =========================================================

    void drawMountains(Graphics2D g) {

        for (int i = 0; i < 16; i++) {

            int x = i * 350 - 150;

            Polygon mountain = new Polygon();

            mountain.addPoint(x, 700);
            mountain.addPoint(x + 170, 160);
            mountain.addPoint(x + 360, 700);

            g.setColor(
                    i % 2 == 0
                    ? new Color(125, 153, 165)
                    : new Color(105, 137, 150)
            );

            g.fillPolygon(mountain);

            Polygon snowCap = new Polygon();

            snowCap.addPoint(x + 170, 160);
            snowCap.addPoint(x + 115, 330);
            snowCap.addPoint(x + 170, 295);
            snowCap.addPoint(x + 225, 330);

            g.setColor(Color.WHITE);

            g.fillPolygon(snowCap);
        }
    }

    // =========================================================
    // TREE
    // =========================================================

    void drawTree(Graphics2D g, int x, int y) {

        g.setColor(new Color(95, 67, 45));

        g.fillRect(
                x - 9,
                y + 25,
                18,
                80
        );

        g.setColor(new Color(30, 67, 65));

        Polygon tree = new Polygon();

        tree.addPoint(x, y - 60);
        tree.addPoint(x - 55, y + 20);
        tree.addPoint(x - 25, y + 20);
        tree.addPoint(x - 65, y + 70);
        tree.addPoint(x + 65, y + 70);
        tree.addPoint(x + 25, y + 20);
        tree.addPoint(x + 55, y + 20);

        g.fillPolygon(tree);

        g.setColor(Color.WHITE);

        Polygon snowCap = new Polygon();

        snowCap.addPoint(x, y - 60);
        snowCap.addPoint(x - 30, y);
        snowCap.addPoint(x - 10, y - 2);
        snowCap.addPoint(x + 10, y - 2);
        snowCap.addPoint(x + 30, y);

        g.fillPolygon(snowCap);
    }

    // =========================================================
    // RUINS
    // =========================================================

    void drawRuins(Graphics2D g, int x, int y) {

        g.setColor(new Color(105, 120, 127));

        g.fillRect(
                x,
                y,
                360,
                45
        );

        g.fillRect(
                x,
                y + 220,
                360,
                45
        );

        g.fillRect(
                x,
                y,
                45,
                265
        );

        g.fillRect(
                x + 315,
                y,
                45,
                265
        );

        g.setColor(Color.WHITE);

        g.fillRect(
                x - 5,
                y - 12,
                370,
                18
        );
    }

    // =========================================================
    // SHOP BUILDING
    // =========================================================

    void drawShopBuilding(Graphics2D g, int x, int y) {

        g.setColor(new Color(100, 70, 55));

        g.fillRect(
                x - 110,
                y - 80,
                220,
                180
        );

        Polygon roof = new Polygon();

        roof.addPoint(x - 140, y - 80);
        roof.addPoint(x, y - 180);
        roof.addPoint(x + 140, y - 80);

        g.setColor(new Color(130, 45, 55));

        g.fillPolygon(roof);

        g.setColor(Color.WHITE);

        g.fillRect(
                x - 140,
                y - 90,
                280,
                20
        );

        g.setColor(new Color(250, 210, 100));

        g.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        28
                )
        );

        g.drawString(
                "SHOP",
                x - 42,
                y - 105
        );

        g.setColor(new Color(45, 32, 30));

        g.fillRect(
                x - 35,
                y + 10,
                70,
                90
        );
    }

    // =========================================================
    // OBJECT DRAW
    // =========================================================

    void drawObjects(Graphics2D g) {

        for (Coin c : coins)
            c.draw(g);

        for (Enemy e : enemies)
            e.draw(g);

        for (Arrow a : arrowList)
            a.draw(g);

        if (yetiActive && yeti != null)
            yeti.draw(g);

        player.draw(g);

        for (Particle p : particles)
            p.draw(g);

        for (Snow s : snow)
            s.draw(g);
    }

    // =========================================================
    // HUD
    // =========================================================

    void drawHUD(Graphics2D g) {

        // Panel
        g.setColor(new Color(5, 14, 22, 220));

        g.fillRoundRect(
                15,
                15,
                300,
                150,
                18,
                18
        );

        g.setColor(Color.WHITE);

        g.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        25
                )
        );

        g.drawString(
                "⚔ VEER",
                30,
                45
        );

        g.setFont(
                new Font(
                        "Arial",
                        Font.PLAIN,
                        13
                )
        );

        g.drawString(
                "WINTER HUNT",
                32,
                63
        );

        // HP
        g.setColor(new Color(50, 55, 60));

        g.fillRoundRect(
                30,
                75,
                260,
                15,
                10,
                10
        );

        g.setColor(new Color(225, 65, 75));

        g.fillRoundRect(
                30,
                75,
                (int)(260 * ((double)player.hp / player.maxHp)),
                15,
                10,
                10
        );

        g.setColor(Color.WHITE);

        g.drawString(
                "HP: " + Math.max(0, player.hp),
                35,
                87
        );

        g.drawString(
                "☠ Kills: " + kills,
                30,
                115
        );

        g.drawString(
                "💰 Money: " + money,
                145,
                115
        );

        g.drawString(
                "Weapon: " + weapon,
                30,
                140
        );

        if (weapon.equals("BOW")) {

            g.drawString(
                    "Arrows: " + arrows,
                    145,
                    140
            );
        }

        // Yeti warning
        if (yetiActive) {

            g.setColor(new Color(5, 10, 15, 220));

            g.fillRoundRect(
                    SCREEN_W / 2 - 220,
                    15,
                    440,
                    70,
                    15,
                    15
            );

            g.setColor(new Color(255, 80, 90));

            g.setFont(
                    new Font(
                            "Arial",
                            Font.BOLD,
                            24
                    )
            );

            g.drawString(
                    "🐻‍❄️ YETI",
                    SCREEN_W / 2 - 60,
                    43
            );

            drawHealthBar(
                    g,
                    SCREEN_W / 2 - 190,
                    55,
                    380,
                    12,
                    (double)yeti.hp / yeti.maxHp
            );
        }

        // Controls
        g.setColor(new Color(5, 14, 22, 210));

        g.fillRoundRect(
                15,
                SCREEN_H - 95,
                420,
                75,
                14,
                14
        );

        g.setColor(new Color(210, 225, 230));

        g.setFont(
                new Font(
                        "Arial",
                        Font.PLAIN,
                        12
                )
        );

        g.drawString(
                "WASD / ARROWS  MOVE",
                28,
                SCREEN_H - 70
        );

        g.drawString(
                "SPACE / MOUSE  ATTACK",
                28,
                SCREEN_H - 50
        );

        g.drawString(
                "SHIFT  DASH     1 SWORD   2 STICK   3 BOW",
                28,
                SCREEN_H - 30
        );

        // Minimap
        drawMinimap(g);
    }

    // =========================================================
    // MINIMAP
    // =========================================================

    void drawMinimap(Graphics2D g) {

        int mw = 180;
        int mh = 125;

        int x = SCREEN_W - mw - 20;
        int y = SCREEN_H - mh - 20;

        g.setColor(new Color(5, 14, 22, 220));

        g.fillRoundRect(
                x,
                y,
                mw,
                mh,
                12,
                12
        );

        g.setColor(new Color(100, 130, 145));

        g.drawRoundRect(
                x,
                y,
                mw,
                mh,
                12,
                12
        );

        // Player
        int px =
                x + (int)(player.x / WORLD_W * mw);

        int py =
                y + (int)(player.y / WORLD_H * mh);

        g.setColor(Color.WHITE);

        g.fillOval(
                px - 4,
                py - 4,
                8,
                8
        );

        // Enemies
        g.setColor(new Color(230, 65, 75));

        for (Enemy e : enemies) {

            int ex =
                    x + (int)(e.x / WORLD_W * mw);

            int ey =
                    y + (int)(e.y / WORLD_H * mh);

            g.fillRect(
                    ex - 2,
                    ey - 2,
                    4,
                    4
            );
        }

        // Yeti
        if (yetiActive && yeti != null) {

            g.setColor(Color.YELLOW);

            int bx =
                    x + (int)(yeti.x / WORLD_W * mw);

            int by =
                    y + (int)(yeti.y / WORLD_H * mh);

            g.fillOval(
                    bx - 5,
                    by - 5,
                    10,
                    10
            );
        }
    }

    // =========================================================
    // HEALTH BAR
    // =========================================================

    void drawHealthBar(
            Graphics2D g,
            double x,
            double y,
            double w,
            double h,
            double percentage
    ) {

        percentage =
                Math.max(
                        0,
                        Math.min(1, percentage)
                );

        g.setColor(new Color(30, 30, 35));

        g.fillRoundRect(
                (int)x,
                (int)y,
                (int)w,
                (int)h,
                8,
                8
        );

        g.setColor(new Color(220, 55, 65));

        g.fillRoundRect(
                (int)x,
                (int)y,
                (int)(w * percentage),
                (int)h,
                8,
                8
        );
    }

    // =========================================================
    // INVENTORY
    // =========================================================

    void drawInventory(Graphics2D g) {

        g.setColor(new Color(3, 8, 14, 245));

        g.fillRoundRect(
                330,
                170,
                540,
                380,
                25,
                25
        );

        g.setColor(Color.WHITE);

        g.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        30
                )
        );

        g.drawString(
                "🎒 INVENTORY",
                500,
                220
        );

        drawInventorySlot(
                g,
                390,
                270,
                "⚔ SWORD",
                "1",
                true
        );

        drawInventorySlot(
                g,
                555,
                270,
                "🪵 STICK",
                "2",
                true
        );

        drawInventorySlot(
                g,
                720,
                270,
                "🏹 BOW",
                "3",
                hasBow
        );

        g.setFont(
                new Font(
                        "Arial",
                        Font.PLAIN,
                        14
                )
        );

        g.drawString(
                "Press 1 / 2 / 3 to equip",
                495,
                455
        );

        g.drawString(
                "Press I to close",
                525,
                485
        );
    }

    void drawInventorySlot(
            Graphics2D g,
            int x,
            int y,
            String name,
            String key,
            boolean unlocked
    ) {

        g.setColor(
                unlocked
                ? new Color(30, 52, 65)
                : new Color(40, 40, 45)
        );

        g.fillRoundRect(
                x,
                y,
                130,
                120,
                15,
                15
        );

        g.setColor(Color.WHITE);

        g.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        15
                )
        );

        g.drawString(
                name,
                x + 15,
                y + 50
        );

        g.drawString(
                "KEY " + key,
                x + 35,
                y + 85
        );

        if (!unlocked) {

            g.setColor(new Color(230, 80, 80));

            g.drawString(
                    "LOCKED",
                    x + 32,
                    y + 108
            );
        }
    }

    // =========================================================
    // SHOP
    // =========================================================

    void drawShop(Graphics2D g) {

        g.setColor(new Color(3, 8, 14, 245));

        g.fillRoundRect(
                340,
                150,
                520,
                420,
                25,
                25
        );

        g.setColor(Color.WHITE);

        g.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        30
                )
        );

        g.drawString(
                "🏪 WINTER SHOP",
                480,
                205
        );

        g.setFont(
                new Font(
                        "Arial",
                        Font.PLAIN,
                        16
                )
        );

        g.drawString(
                "Money: " + money,
                560,
                240
        );

        drawShopButton(
                g,
                410,
                275,
                "BUY BOW",
                "50"
        );

        drawShopButton(
                g,
                410,
                350,
                "10 ARROWS",
                "20"
        );

        drawShopButton(
                g,
                410,
                425,
                "25 ARROWS",
                "40"
        );

        g.drawString(
                "Press E to close",
                515,
                525
        );
    }

    void drawShopButton(
            Graphics2D g,
            int x,
            int y,
            String item,
            String price
    ) {

        g.setColor(new Color(30, 52, 65));

        g.fillRoundRect(
                x,
                y,
                350,
                55,
                12,
                12
        );

        g.setColor(Color.WHITE);

        g.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        16
                )
        );

        g.drawString(
                item,
                x + 25,
                y + 34
        );

        g.drawString(
                "💰 " + price,
                x + 270,
                y + 34
        );
    }

    // =========================================================
    // GAME OVER
    // =========================================================

    void drawGameOver(Graphics2D g) {

        g.setColor(new Color(0, 0, 0, 190));

        g.fillRect(
                0,
                0,
                SCREEN_W,
                SCREEN_H
        );

        g.setColor(Color.WHITE);

        g.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        65
                )
        );

        g.drawString(
                "YOU FELL",
                SCREEN_W / 2 - 170,
                SCREEN_H / 2
        );

        g.setFont(
                new Font(
                        "Arial",
                        Font.PLAIN,
                        20
                )
        );

        g.drawString(
                "Kills: " + kills +
                "   Money: " + money,
                SCREEN_W / 2 - 100,
                SCREEN_H / 2 + 50
        );
    }

    // =========================================================
    // DASH
    // =========================================================

    void dash() {

        if (dashCooldown > 0)
            return;

        double dx = 0;
        double dy = 0;

        if (up) dy--;
        if (down) dy++;
        if (left) dx--;
        if (right) dx++;

        if (dx == 0 && dy == 0) {

            dx = player.lastDX;
            dy = player.lastDY;
        }

        double len =
                Math.sqrt(dx * dx + dy * dy);

        dx /= len;
        dy /= len;

        player.x += dx * 180;
        player.y += dy * 180;

        player.x =
                Math.max(
                        80,
                        Math.min(
                                WORLD_W - 80,
                                player.x
                        )
                );

        player.y =
                Math.max(
                        80,
                        Math.min(
                                WORLD_H - 80,
                                player.y
                        )
                );

        player.invincible = 30;

        dashCooldown = 70;

        createExplosion(
                player.x,
                player.y,
                25
        );

        soundDash();
    }

    // =========================================================
    // SHOP / INVENTORY
    // =========================================================

    void toggleInventory() {

        inventoryOpen = !inventoryOpen;

        if (inventoryOpen)
            shopOpen = false;
    }

    void tryShop() {

        double dx =
                player.x - 4200;

        double dy =
                player.y - 1550;

        double distance =
                Math.sqrt(dx * dx + dy * dy);

        if (distance < 250) {

            shopOpen = !shopOpen;

            if (shopOpen)
                inventoryOpen = false;
        }
    }

    void buyBow() {

        if (money >= 50 && !hasBow) {

            money -= 50;

            hasBow = true;
            arrows += 5;

            soundCoin();
        }
    }

    void buyArrows(int amount, int price) {

        if (!hasBow)
            return;

        if (money >= price) {

            money -= price;

            arrows += amount;

            soundCoin();
        }
    }

    // =========================================================
    // KEYBOARD
    // =========================================================

    @Override
    public void keyPressed(KeyEvent e) {

        int k = e.getKeyCode();

        if (k == KeyEvent.VK_W ||
                k == KeyEvent.VK_UP)
            up = true;

        if (k == KeyEvent.VK_S ||
                k == KeyEvent.VK_DOWN)
            down = true;

        if (k == KeyEvent.VK_A ||
                k == KeyEvent.VK_LEFT)
            left = true;

        if (k == KeyEvent.VK_D ||
                k == KeyEvent.VK_RIGHT)
            right = true;

        if (k == KeyEvent.VK_SPACE) {
            attack();
        }

        if (k == KeyEvent.VK_SHIFT) {
            dash();
        }

        if (k == KeyEvent.VK_1) {

            weapon = "SWORD";
            soundSwitch();
        }

        if (k == KeyEvent.VK_2) {

            weapon = "STICK";
            soundSwitch();
        }

        if (k == KeyEvent.VK_3) {

            if (hasBow) {

                weapon = "BOW";
                soundSwitch();

            } else {

                soundError();
            }
        }

        if (k == KeyEvent.VK_I) {

            toggleInventory();
        }

        if (k == KeyEvent.VK_E) {

            tryShop();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

        int k = e.getKeyCode();

        if (k == KeyEvent.VK_W ||
                k == KeyEvent.VK_UP)
            up = false;

        if (k == KeyEvent.VK_S ||
                k == KeyEvent.VK_DOWN)
            down = false;

        if (k == KeyEvent.VK_A ||
                k == KeyEvent.VK_LEFT)
            left = false;

        if (k == KeyEvent.VK_D ||
                k == KeyEvent.VK_RIGHT)
            right = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    // =========================================================
    // MOUSE
    // =========================================================

    @Override
    public void mousePressed(MouseEvent e) {

        requestFocus();

        mouseDown = true;

        mouseX = e.getX();
        mouseY = e.getY();

        // Shop clicks
        if (shopOpen) {

            int x = e.getX();
            int y = e.getY();

            if (x >= 410 && x <= 760) {

                if (y >= 275 && y <= 330) {
                    buyBow();
                }

                if (y >= 350 && y <= 405) {
                    buyArrows(10, 20);
                }

                if (y >= 425 && y <= 480) {
                    buyArrows(25, 40);
                }
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {

        mouseDown = false;
    }

    @Override
    public void mouseMoved(MouseEvent e) {

        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) {

        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    // =========================================================
    // SOUND
    // =========================================================

    void beep(
            double frequency,
            int milliseconds,
            double volume
    ) {

        try {

            float sampleRate = 44100;

            byte[] buffer =
                    new byte[
                            (int)(
                                    sampleRate *
                                    milliseconds /
                                    1000
                            )
                    ];

            for (int i = 0; i < buffer.length; i++) {

                double time =
                        i / sampleRate;

                double wave =
                        Math.sin(
                                2 *
                                Math.PI *
                                frequency *
                                time
                        );

                double fade =
                        1.0 -
                        (double)i /
                        buffer.length;

                buffer[i] =
                        (byte)(
                                wave *
                                127 *
                                volume *
                                fade
                        );
            }

            AudioFormat format =
                    new AudioFormat(
                            sampleRate,
                            8,
                            1,
                            true,
                            false
                    );

            SourceDataLine line =
                    AudioSystem.getSourceDataLine(format);

            line.open(format);

            line.start();

            line.write(
                    buffer,
                    0,
                    buffer.length
            );

            line.drain();
            line.stop();
            line.close();

        } catch (Exception ignored) {}
    }

    void soundSword() {

        new Thread(() -> {

            beep(180, 70, .5);
            beep(500, 80, .3);

        }).start();
    }

    void soundStick() {

        new Thread(() ->
                beep(90, 100, .6)
        ).start();
    }

    void soundBow() {

        new Thread(() -> {

            beep(350, 60, .35);
            beep(700, 70, .25);

        }).start();
    }

    void soundDamage() {

        new Thread(() ->
                beep(70, 130, .7)
        ).start();
    }

    void soundHit() {

        new Thread(() ->
                beep(120, 80, .4)
        ).start();
    }

    void soundDash() {

        new Thread(() -> {

            beep(100, 60, .4);
            beep(600, 100, .25);

        }).start();
    }

    void soundCoin() {

        new Thread(() -> {

            beep(700, 60, .3);
            beep(1000, 80, .3);

        }).start();
    }

    void soundSwitch() {

        new Thread(() ->
                beep(450, 70, .25)
        ).start();
    }

    void soundError() {

        new Thread(() ->
                beep(80, 120, .5)
        ).start();
    }

    void soundYeti() {

        new Thread(() -> {

            beep(55, 400, .8);
            beep(45, 500, .7);
            beep(70, 300, .6);

        }).start();
    }

    // =========================================================
    // MUSIC
    // =========================================================

    void startMusic() {

        int[] notes = {
                196,
                233,
                262,
                233,
                175,
                220,
                262,
                220
        };

        musicTimer =
                new javax.swing.Timer(
                        420,
                        e -> {

                            new Thread(() ->
                                    beep(
                                            notes[musicNote++ % notes.length],
                                            180,
                                            .08
                                    )
                            ).start();
                        }
                );

        musicTimer.start();
    }

    // =========================================================
    // TRANSFORM HELPERS
    // =========================================================

    // Custom Graphics2D transform helpers
    // implemented through a wrapper extension below.

    // =========================================================
    // GAME PANEL
    // =========================================================

    JPanel thisPanel() {
        return this;
    }

    // =========================================================
    // MAIN
    // =========================================================

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            JFrame frame =
                    new JFrame(
                            "VEER - WINTER HUNT"
                    );

            VeerWinterHunt game =
                    new VeerWinterHunt();

            frame.setContentPane(game);

            frame.pack();

            frame.setResizable(false);

            frame.setDefaultCloseOperation(
                    JFrame.EXIT_ON_CLOSE
            );

            frame.setLocationRelativeTo(null);

            frame.setVisible(true);

            game.requestFocusInWindow();
        });
    }

    // =========================================================
    // GRAPHICS HELPERS
    // =========================================================

    static class GraphicsHelper {

        static void translate(
                Graphics2D g,
                double x,
                double y
        ) {
            g.translate(x, y);
        }
    }

    // =========================================================
    // REQUIRED GRAPHICS EXTENSION
    // =========================================================

    // Java does not allow adding methods to Graphics2D.
    // These helpers perform the needed transformations.

    // =========================================================
    // OVERRIDES FOR DRAWING TRANSFORMATIONS
    // =========================================================

    // Save / restore transform utilities
    // are used below by replacing calls through methods.

    // =========================================================
    // FIXED TRANSFORM METHODS
    // =========================================================

}