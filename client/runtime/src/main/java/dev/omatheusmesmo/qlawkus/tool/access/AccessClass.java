package dev.omatheusmesmo.qlawkus.tool.access;

/**
 * What a tool does to the world, from the least to the most consequential. A tool carries exactly one
 * class: the most consequential effect it can have.
 */
public enum AccessClass {
    /** Looks at something and changes nothing. */
    READ,
    /** Makes something new. */
    CREATE,
    /** Changes something that already exists. */
    UPDATE,
    /** Removes or cancels something. */
    DELETE,
    /** Delivers something to a third party, or gives a third party access. */
    SEND,
    /** Runs a command or a script. */
    EXECUTE
}
