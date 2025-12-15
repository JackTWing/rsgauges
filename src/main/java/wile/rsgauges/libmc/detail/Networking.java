/*
 * @file Networking.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Main client/server message handling.
 */
package wile.rsgauges.libmc.detail;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.BiConsumer;

public class Networking
{
  private static final String PROTOCOL = "1";
  private static String MODID = "";

  public static void init(String modid)
  {
    MODID = modid;
  }

  public static void register(RegisterPayloadHandlersEvent event)
  {
    PayloadRegistrar registrar = event.registrar(PROTOCOL);

    registrar.playToServer(PacketTileNotifyClientToServer.TYPE, PacketTileNotifyClientToServer.STREAM_CODEC, PacketTileNotifyClientToServer::handle);
    registrar.playToClient(PacketTileNotifyServerToClient.TYPE, PacketTileNotifyServerToClient.STREAM_CODEC, PacketTileNotifyServerToClient::handle);

    registrar.playToServer(PacketContainerSyncClientToServer.TYPE, PacketContainerSyncClientToServer.STREAM_CODEC, PacketContainerSyncClientToServer::handle);
    registrar.playToClient(PacketContainerSyncServerToClient.TYPE, PacketContainerSyncServerToClient.STREAM_CODEC, PacketContainerSyncServerToClient::handle);

    registrar.playToClient(OverlayTextMessage.TYPE, OverlayTextMessage.STREAM_CODEC, OverlayTextMessage::handle);
  }

  private static ResourceLocation resource(String path)
  {
    return new ResourceLocation(MODID, path);
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entity notifications
  //--------------------------------------------------------------------------------------------------------------------

  public interface IPacketTileNotifyReceiver
  {
    default void onServerPacketReceived(CompoundTag nbt) {}
    default void onClientPacketReceived(Player player, CompoundTag nbt) {}
  }

  public record PacketTileNotifyClientToServer(BlockPos pos, CompoundTag nbt) implements CustomPacketPayload
  {
    public static final Type<PacketTileNotifyClientToServer> TYPE = new Type<>(resource("tile_notify_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTileNotifyClientToServer> STREAM_CODEC = StreamCodec.of(
      (buf, payload) -> {
        buf.writeBlockPos(payload.pos);
        buf.writeNbt(payload.nbt);
      },
      buf -> new PacketTileNotifyClientToServer(buf.readBlockPos(), buf.readNbt())
    );

    public static void sendToServer(BlockPos pos, CompoundTag nbt)
    { if((pos!=null) && (nbt!=null)) PacketDistributor.sendToServer(new PacketTileNotifyClientToServer(pos, nbt)); }

    public static void sendToServer(BlockEntity te, CompoundTag nbt)
    { if((te!=null) && (nbt!=null)) PacketDistributor.sendToServer(new PacketTileNotifyClientToServer(te.getBlockPos(), nbt)); }

    @Override
    public Type<PacketTileNotifyClientToServer> type()
    { return TYPE; }

    public void handle(IPayloadContext context)
    {
      context.enqueueWork(() -> {
        Player player = context.player();
        if(player==null) return;
        Level world = player.level();
        BlockEntity te = world.getBlockEntity(pos);
        if(!(te instanceof IPacketTileNotifyReceiver receiver)) return;
        receiver.onClientPacketReceived(player, nbt);
      });
    }
  }

  public record PacketTileNotifyServerToClient(BlockPos pos, CompoundTag nbt) implements CustomPacketPayload
  {
    public static final Type<PacketTileNotifyServerToClient> TYPE = new Type<>(resource("tile_notify_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTileNotifyServerToClient> STREAM_CODEC = StreamCodec.of(
      (buf, payload) -> {
        buf.writeBlockPos(payload.pos);
        buf.writeNbt(payload.nbt);
      },
      buf -> new PacketTileNotifyServerToClient(buf.readBlockPos(), buf.readNbt())
    );

    public static void sendToPlayer(Player player, BlockEntity te, CompoundTag nbt)
    {
      if(!(player instanceof ServerPlayer serverPlayer) || (player instanceof FakePlayer) || (te==null) || (nbt==null)) return;
      PacketDistributor.sendToPlayer(serverPlayer, new PacketTileNotifyServerToClient(te.getBlockPos(), nbt));
    }

    public static void sendToPlayers(BlockEntity te, CompoundTag nbt)
    {
      if(te==null || te.getLevel()==null) return;
      for(Player player: te.getLevel().players()) sendToPlayer(player, te, nbt);
    }

    @Override
    public Type<PacketTileNotifyServerToClient> type()
    { return TYPE; }

    public void handle(IPayloadContext context)
    {
      context.enqueueWork(() -> {
        if((nbt==null) || (pos==null)) return;
        Level world = SidedProxy.getWorldClientSide();
        if(world == null) return;
        BlockEntity te = world.getBlockEntity(pos);
        if(!(te instanceof IPacketTileNotifyReceiver receiver)) return;
        receiver.onServerPacketReceived(nbt);
      });
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // (GUI) Container synchronization
  //--------------------------------------------------------------------------------------------------------------------

  public interface INetworkSynchronisableContainer
  {
    void onServerPacketReceived(int windowId, CompoundTag nbt);
    void onClientPacketReceived(int windowId, Player player, CompoundTag nbt);
  }

  public record PacketContainerSyncClientToServer(int id, CompoundTag nbt) implements CustomPacketPayload
  {
    public static final Type<PacketContainerSyncClientToServer> TYPE = new Type<>(resource("container_sync_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketContainerSyncClientToServer> STREAM_CODEC = StreamCodec.of(
      (buf, payload) -> {
        buf.writeInt(payload.id);
        buf.writeNbt(payload.nbt);
      },
      buf -> new PacketContainerSyncClientToServer(buf.readInt(), buf.readNbt())
    );

    public static void sendToServer(int windowId, CompoundTag nbt)
    { if(nbt!=null) PacketDistributor.sendToServer(new PacketContainerSyncClientToServer(windowId, nbt)); }

    public static void sendToServer(AbstractContainerMenu container, CompoundTag nbt)
    { if(nbt!=null) PacketDistributor.sendToServer(new PacketContainerSyncClientToServer(container.containerId, nbt)); }

    @Override
    public Type<PacketContainerSyncClientToServer> type()
    { return TYPE; }

    public void handle(IPayloadContext context)
    {
      context.enqueueWork(() -> {
        Player player = context.player();
        if(!(player instanceof ServerPlayer serverPlayer)) return;
        if(!(serverPlayer.containerMenu instanceof INetworkSynchronisableContainer container)) return;
        if(serverPlayer.containerMenu.containerId != id) return;
        container.onClientPacketReceived(id, serverPlayer, nbt);
      });
    }
  }

  public record PacketContainerSyncServerToClient(int id, CompoundTag nbt) implements CustomPacketPayload
  {
    public static final Type<PacketContainerSyncServerToClient> TYPE = new Type<>(resource("container_sync_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketContainerSyncServerToClient> STREAM_CODEC = StreamCodec.of(
      (buf, payload) -> {
        buf.writeInt(payload.id);
        buf.writeNbt(payload.nbt);
      },
      buf -> new PacketContainerSyncServerToClient(buf.readInt(), buf.readNbt())
    );

    public static void sendToPlayer(Player player, int windowId, CompoundTag nbt)
    {
      if(!(player instanceof ServerPlayer serverPlayer) || (player instanceof FakePlayer) || (nbt==null)) return;
      PacketDistributor.sendToPlayer(serverPlayer, new PacketContainerSyncServerToClient(windowId, nbt));
    }

    public static void sendToPlayer(Player player, AbstractContainerMenu container, CompoundTag nbt)
    {
      if(container==null) return;
      sendToPlayer(player, container.containerId, nbt);
    }

    public static <C extends AbstractContainerMenu & INetworkSynchronisableContainer> void sendToListeners(Level world, C container, CompoundTag nbt)
    {
      for(Player player: world.players()) {
        if(player.containerMenu.containerId != container.containerId) continue;
        sendToPlayer(player, container.containerId, nbt);
      }
    }

    @Override
    public Type<PacketContainerSyncServerToClient> type()
    { return TYPE; }

    public void handle(IPayloadContext context)
    {
      context.enqueueWork(() -> {
        Player player = SidedProxy.getPlayerClientSide();
        if(!(player != null && player.containerMenu instanceof INetworkSynchronisableContainer container)) return;
        if(player.containerMenu.containerId != id) return;
        container.onServerPacketReceived(id, nbt);
      });
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Main window GUI text message
  //--------------------------------------------------------------------------------------------------------------------

  public record OverlayTextMessage(Component data, int delay) implements CustomPacketPayload
  {
    public static final int DISPLAY_TIME_MS = 3000;
    public static final Type<OverlayTextMessage> TYPE = new Type<>(resource("overlay_text"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OverlayTextMessage> STREAM_CODEC = StreamCodec.of(
      (buf, payload) -> buf.writeComponent(payload.data),
      buf -> new OverlayTextMessage(buf.readComponent(), DISPLAY_TIME_MS)
    );

    private static BiConsumer<Component, Integer> handler_ = null;

    public static void setHandler(BiConsumer<Component, Integer> handler)
    { if(handler_==null) handler_ = handler; }

    public static void sendToPlayer(Player player, Component message, int delay)
    {
      if(!(player instanceof ServerPlayer serverPlayer) || (player instanceof FakePlayer)) return;
      PacketDistributor.sendToPlayer(serverPlayer, new OverlayTextMessage(message, delay));
    }

    @Override
    public Type<OverlayTextMessage> type()
    { return TYPE; }

    public void handle(IPayloadContext context)
    {
      context.enqueueWork(() -> {
        if(handler_ != null) handler_.accept(data, delay);
      });
    }
  }
}
