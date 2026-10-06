package org.fourz.RVNKQuests.npc;

/**
 * The quest context of one NPC dialogue line (#2214).
 *
 * <p>The line comes from the RVNKLore entry {@code npc_<key>_<context>}, read through
 * {@code ILoreIntegration.getNPCDialogue(key, context)}.</p>
 *
 * <p>Declared in priority order, lowest first. When one click advances several quests, the
 * highest-priority context wins and only that line is sent.</p>
 */
public enum DialogueContext {
    /** A quest is in progress: a TALK_TO objective advanced it, or the player clicked an NPC of a quest they are on. */
    ACTIVE("active"),
    /** An NPC_INTERACT trigger offered (started) the quest. */
    OFFER("offer"),
    /** The click completed the quest, or the player clicked an NPC of a quest they completed. */
    DONE("done");

    private final String loreSuffix;

    DialogueContext(String loreSuffix) {
        this.loreSuffix = loreSuffix;
    }

    /** The {@code <context>} part of the lore entry name. */
    public String loreSuffix() {
        return loreSuffix;
    }

    /** The lore entry name for an NPC key: {@code npc_<key>_<context>}. */
    public String loreEntryName(String npcKey) {
        return "npc_" + npcKey + "_" + loreSuffix;
    }

    /** The higher-priority context of the two; null-safe. */
    public static DialogueContext max(DialogueContext a, DialogueContext b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
