package com.github.industrialcraft.paperbyte;

import com.github.industrialcraft.paperbyte.server.GameServer;
import com.github.industrialcraft.paperbyte.server.events.PlayerEvent;
import com.github.industrialcraft.paperbyte.server.world.ServerPlayerEntity;

import java.util.ArrayList;

public class ChatMessageEvent extends PlayerEvent {
    public boolean cancelled;
    public final String message;
    public final ArrayList<ServerPlayerEntity> recipients;
    public ChatMessageEvent(GameServer server, ServerPlayerEntity playerEntity, String message, ArrayList<ServerPlayerEntity> recipients) {
        super(server, playerEntity);
        this.message = message;
        this.recipients = recipients;
        this.cancelled = false;
    }
}
