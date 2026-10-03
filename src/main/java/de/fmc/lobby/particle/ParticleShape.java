package de.fmc.lobby.particle;

import com.destroystokyo.paper.ParticleBuilder;
import org.bukkit.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Formen der Partikel. Pro Durchgang werden nur "amount" Punkte gezeichnet,
 * die Bewegung entsteht über den laufenden Schritt-Zähler.
 */
public enum ParticleShape {

    /** Ring um die Hüfte */
    RING {
        @Override
        void draw(ParticleBuilder b, World w, double x, double y, double z, long step, int amount) {
            double base = step * 0.35;
            for (int i = 0; i < amount; i++) {
                double angle = base + i * (Math.PI * 2 / amount);
                b.location(w, x + Math.cos(angle) * 0.7, y + 1.0, z + Math.sin(angle) * 0.7).spawn();
            }
        }
    },
    /** Aufsteigende Spirale */
    HELIX {
        @Override
        void draw(ParticleBuilder b, World w, double x, double y, double z, long step, int amount) {
            double height = (step * 0.08) % 2.0;
            double base = step * 0.4;
            for (int i = 0; i < amount; i++) {
                double angle = base + i * (Math.PI * 2 / amount);
                b.location(w, x + Math.cos(angle) * 0.55, y + height, z + Math.sin(angle) * 0.55).spawn();
            }
        }
    },
    /** Heiligenschein über dem Kopf */
    HALO {
        @Override
        void draw(ParticleBuilder b, World w, double x, double y, double z, long step, int amount) {
            double base = step * 0.3;
            for (int i = 0; i < amount; i++) {
                double angle = base + i * (Math.PI * 2 / amount);
                b.location(w, x + Math.cos(angle) * 0.35, y + 2.2, z + Math.sin(angle) * 0.35).spawn();
            }
        }
    },
    /** Kleine Wolke an den Füßen */
    FEET {
        @Override
        void draw(ParticleBuilder b, World w, double x, double y, double z, long step, int amount) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < amount; i++) {
                b.location(w, x + random.nextDouble(-0.3, 0.3), y + 0.1, z + random.nextDouble(-0.3, 0.3)).spawn();
            }
        }
    };

    abstract void draw(ParticleBuilder builder, World world, double x, double y, double z, long step, int amount);
}
