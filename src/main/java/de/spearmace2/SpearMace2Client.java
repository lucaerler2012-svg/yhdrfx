package de.spearmace2;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Speer in der Hand + Spieler innerhalb von 6 Bloecken (Luftlinie) = Funktion ist "bereit".
 * Solange du nicht links klickst, passiert nichts.
 *
 * Klickst du dann links, geht dieser Schlag noch mit dem SPEER raus. Im naechsten Tick wechselt
 * die Mod auf den Mace, damit dein naechster Schlag mit dem Mace kommt.
 *
 * Abgestimmt auf "windspear" (Wind Charge -> Speer bei Linksklick):
 * Es wird nur gewechselt, wenn der Speer schon am ENDE des vorherigen Ticks in der Hand war
 * und der Klick in diesem Tick neu beginnt. So wechselt ein einzelner Klick nie in einem Zug von
 * der Wind Charge ueber den Speer zum Mace. Die Logik haengt nicht von der Mod-Reihenfolge ab.
 *
 * Fallstrecke und Schwung gehoeren zum Spieler, nicht zur Waffe, und bleiben beim Slot-Wechsel
 * erhalten.
 */
public class SpearMace2Client implements ClientModInitializer {

    /** Luftlinie (3D) in Bloecken, innerhalb der die Funktion bereit ist. */
    private static final double READY_RANGE = 6.0;

    private boolean attackWasDown;
    private boolean spearHeldAtEndOfLastTick;
    private boolean switchNextTick;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private void onStartTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            attackWasDown = false;
            switchNextTick = false;
            return;
        }

        boolean down = client.options.keyAttack.isDown();
        boolean newClick = down && !attackWasDown;
        attackWasDown = down;

        // Der Schlag aus dem letzten Tick ist mit dem Speer rausgegangen, jetzt auf den Mace.
        if (switchNextTick) {
            switchNextTick = false;
            switchToMace(client.player);
            return;
        }

        if (!newClick) {
            return;
        }
        if (!spearHeldAtEndOfLastTick || !isSpear(client.player.getMainHandItem())) {
            return;
        }
        if (!playerInRange(client)) {
            return;
        }

        // Noch nicht wechseln: dieser Klick wird in diesem Tick mit dem Speer ausgefuehrt.
        switchNextTick = true;
    }

    private void onEndTick(Minecraft client) {
        spearHeldAtEndOfLastTick = client.player != null && isSpear(client.player.getMainHandItem());
    }

    private static void switchToMace(Player me) {
        Inventory inv = me.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (inv.getItem(slot).is(Items.MACE)) {
                inv.setSelectedSlot(slot);
                return;
            }
        }
    }

    private static boolean playerInRange(Minecraft client) {
        for (Player other : client.level.players()) {
            if (other == client.player || other.isSpectator()) {
                continue;
            }
            if (other.distanceTo(client.player) <= READY_RANGE) {
                return true;
            }
        }
        return false;
    }

    // Gleiche Erkennung wie in der windspear-Mod.
    private static boolean isSpear(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.endsWith("_spear");
    }
}
