package ru.antigravity.vkspec.ui;

public class SpecNotification {
    public final String nickname;
    public final String server;
    public final String reason;
    public final long peerId;
    public final long conversationMessageId;
    public final int durationTicks;
    
    public int ticksLeft;
    public boolean claimed = false;
    public String claimedBy = null;
    
    public SpecNotification(String nickname, String server, String reason, long peerId, long conversationMessageId, int durationTicks) {
        this.nickname = nickname;
        this.server = server;
        this.reason = reason;
        this.peerId = peerId;
        this.conversationMessageId = conversationMessageId;
        this.durationTicks = durationTicks;
        this.ticksLeft = durationTicks;
    }
    
    public void tick() {
        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }
    
    public boolean isExpired() {
        return ticksLeft <= 0;
    }
}
