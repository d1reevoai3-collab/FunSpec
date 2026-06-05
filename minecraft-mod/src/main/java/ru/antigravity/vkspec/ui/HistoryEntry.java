package ru.antigravity.vkspec.ui;

/**
 * Запись в истории заявок.
 */
public class HistoryEntry {
    public final String nickname;
    public final String server;
    public final String reason;
    public final long peerId;
    public final long conversationMessageId;
    public final long timestamp; // System.currentTimeMillis()
    
    public String claimedBy = null;  // null = ожидание

    public HistoryEntry(String nickname, String server, String reason, long peerId, long conversationMessageId) {
        this.nickname = nickname;
        this.server = server;
        this.reason = reason;
        this.peerId = peerId;
        this.conversationMessageId = conversationMessageId;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isClaimed() {
        return claimedBy != null;
    }
}

