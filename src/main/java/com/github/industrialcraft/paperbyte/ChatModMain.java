package com.github.industrialcraft.paperbyte;

import com.badlogic.gdx.graphics.Color;
import com.github.industrialcraft.identifier.Identifier;
import com.github.industrialcraft.paperbyte.common.gui.RectUIComponent;
import com.github.industrialcraft.paperbyte.common.gui.TextUIComponent;
import com.github.industrialcraft.paperbyte.common.net.ClientInputPacket;
import com.github.industrialcraft.paperbyte.common.util.Position;
import com.github.industrialcraft.paperbyte.server.Logger;
import com.github.industrialcraft.paperbyte.server.PlayerMap;
import com.github.industrialcraft.paperbyte.server.events.PlayerInputEvent;
import com.github.industrialcraft.paperbyte.server.events.SendGUIEvent;
import com.github.industrialcraft.paperbyte.server.events.ServerEvent;
import com.github.industrialcraft.paperbyte.server.events.StartGameEvent;
import com.github.industrialcraft.paperbyte.server.world.ServerEntity;
import com.github.industrialcraft.paperbyte.server.world.ServerPlayerEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.cydhra.eventsystem.EventManager;
import net.cydhra.eventsystem.listeners.EventHandler;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.util.ArrayList;
import java.util.List;

public class ChatModMain extends Plugin {
    public PlayerMap<PlayerChatData> playerChatData;
    private Logger logger;
    private CommandDispatcher<ServerPlayerEntity> commandDispatcher;
    public ChatModMain(PluginWrapper wrapper) {
        super(wrapper);
        this.logger = new Logger(this);
        this.commandDispatcher = new CommandDispatcher<>();
    }
    public CommandDispatcher<ServerPlayerEntity> getCommandDispatcher() {
        return commandDispatcher;
    }
    @Override
    public void start() {
        EventManager.registerListeners(this);
    }
    @EventHandler
    public void onServerStart(StartGameEvent event){
        this.playerChatData = new PlayerMap<>(event.server);
        CommandDispatcher<ServerPlayerEntity> dispatcher = event.server.getPlugin(ChatModMain.class).getCommandDispatcher();
        dispatcher.register(LiteralArgumentBuilder.<ServerPlayerEntity>literal("spawn").then(RequiredArgumentBuilder.<ServerPlayerEntity,String>argument("type", StringArgumentType.greedyString()).executes(context -> {
            String type = StringArgumentType.getString(context, "type");
            Identifier id = Identifier.parse(type);
            ClientInputPacket lastInput = context.getSource().getLastInput();
            ServerEntity entity = event.server.getEntityRegistry().createEntity(id, new Position(lastInput.worldMouseX, lastInput.worldMouseY), context.getSource().getWorld());
            if(entity == null)
                getChatData(context.getSource()).addToMessageHistory("Unable to spawn entity");
            else
                getChatData(context.getSource()).addToMessageHistory("Spawned " + entity.entityId);
            return 1;
        })));
        dispatcher.register(LiteralArgumentBuilder.<ServerPlayerEntity>literal("remove").then(RequiredArgumentBuilder.<ServerPlayerEntity,Integer>argument("id", IntegerArgumentType.integer()).executes(context -> {
            int id = IntegerArgumentType.getInteger(context, "id");
            context.getSource().getWorld().getEntities().forEach(entity -> {
                if(entity.entityId == id){
                    entity.remove();
                    getChatData(context.getSource()).addToMessageHistory("Removed entity");
                }
            });
            return 1;
        })));
    }
    @EventHandler
    public void onInput(PlayerInputEvent event){
        event.addListener(new ClientInputPacket.Visitor() {
            @Override
            public void keyTyped(char c) {
                PlayerChatData chatData = getChatData(event.playerEntity);
                if(!chatData.isChatEnabled()){
                    if(Character.toLowerCase(c) == 't')
                        chatData.setChatEnabled(true);
                    return;
                }
                if(c == 8){
                    if(chatData.chatBox.length() > 0) {
                        chatData.chatBox = chatData.chatBox.substring(0, chatData.chatBox.length() - 1);
                    }
                } else if(c == '\n'){
                    if(chatData.chatBox.startsWith("/")){
                        final ParseResults<ServerPlayerEntity> parseResult = commandDispatcher.parse(chatData.chatBox.replaceFirst("/",""), event.playerEntity);
                        chatData.setChatEnabled(false);
                        try {
                            final int result = commandDispatcher.execute(parseResult);
                        } catch (CommandSyntaxException | RuntimeException e) {
                            getChatData(event.playerEntity).addToMessageHistory("Error(" + e.getClass().getSimpleName() + ") executing command: " + e.getMessage());
                        }
                        return;
                    }
                    ChatMessageEvent chatMessageEvent = new ChatMessageEvent(event.server, event.playerEntity, chatData.chatBox, new ArrayList<>(event.server.getPlayers()));
                    EventManager.callEvent(chatMessageEvent);
                    for(ServerPlayerEntity player : chatMessageEvent.recipients){
                        getChatData(player).addToMessageHistory(chatMessageEvent.message);
                    }
                    chatData.setChatEnabled(false);
                } else {
                    chatData.chatBox = chatData.chatBox + c;
                }
                event.playerEntity.markUIDirty();
            }
            @Override
            public void keyDown(int i) {
                if(i == 111){
                    PlayerChatData chatData = getChatData(event.playerEntity);
                    chatData.setChatEnabled(false);
                }
            }
            @Override
            public void keyUp(int i) {

            }
        });
    }
    @EventHandler
    public void onUIUpdate(SendGUIEvent event){
        PlayerChatData chatData = getChatData(event.playerEntity);
        if(chatData.isChatEnabled()) {
            event.packet.addUIComponent(new RectUIComponent(Color.GRAY, -950, -950, 1900, 150));
            if (chatData.chatBox.length() > 0)
                event.packet.addUIComponent(new TextUIComponent(Color.WHITE, -900, -760, chatData.chatBox, 2));
        }
        int size = Math.min(chatData.messageHistory.size(), 10);
        for (int i = 0; i < size; i++) {
            event.packet.addUIComponent(new TextUIComponent(Color.WHITE, -900, -700 + (i * 60), chatData.messageHistory.get(size - i - 1), 2));
        }
    }
    private PlayerChatData getChatData(ServerPlayerEntity playerEntity){
        PlayerChatData chatData = playerChatData.get(playerEntity);
        if(chatData == null){
            chatData = new PlayerChatData(playerEntity);
            playerChatData.put(playerEntity, chatData);
        }
        return chatData;
    }
    public static class PlayerChatData{
        public final ServerPlayerEntity player;
        public String chatBox;
        private List<String> messageHistory;
        private boolean chatEnabled;
        public PlayerChatData(ServerPlayerEntity player) {
            this.player = player;
            this.chatBox = "";
            this.messageHistory = new ArrayList<>();
            this.chatEnabled = false;
        }
        public boolean isChatEnabled() {
            return chatEnabled;
        }
        public void setChatEnabled(boolean chatEnabled) {
            if(this.chatEnabled == chatEnabled)
                return;
            this.chatEnabled = chatEnabled;
            this.chatBox = "";
            player.markUIDirty();
        }
        public void addToMessageHistory(String message){
            this.messageHistory.add(message);
            player.markUIDirty();
        }
    }
}