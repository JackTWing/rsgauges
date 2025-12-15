/*
 * @file ModRsGauges.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2018 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Main mod class.
 */
package wile.rsgauges;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import wile.rsgauges.detail.BlockCategories;
import wile.rsgauges.libmc.detail.Auxiliaries;
import wile.rsgauges.libmc.detail.Networking;
import wile.rsgauges.libmc.detail.OptionalRecipeCondition;
import wile.rsgauges.libmc.detail.PlayerBlockInteraction;
import wile.rsgauges.libmc.detail.Registries;
import wile.rsgauges.libmc.detail.Overlay;

import java.util.List;

@Mod(ModRsGauges.MODID)
public class ModRsGauges
{
  public static final String MODID = "rsgauges";
  public static final String MODNAME = "Gauges and Switches";
  public static final int VERSION_DATAFIXER = 0;
  private static final Logger LOGGER = LogManager.getLogger();

  // -------------------------------------------------------------------------------------------------------------------

  public ModRsGauges(IEventBus modEventBus, ModContainer modContainer)
  {
    Auxiliaries.init(MODID, LOGGER, ModConfig::getServerConfig);
    Auxiliaries.logGitVersion(MODNAME);
    Registries.init(MODID, "industrial_small_lever");
    Registries.registerAll(modEventBus);
    ModContent.init(MODID);
    OptionalRecipeCondition.init(MODID, LOGGER);
    Networking.init(MODID);

    modContainer.registerConfig(ModConfig.Type.COMMON, ModConfig.COMMON_CONFIG_SPEC);
    modContainer.registerConfig(ModConfig.Type.SERVER, ModConfig.SERVER_CONFIG_SPEC);

    modEventBus.addListener(ForgeEvents::onSetup);
    modEventBus.addListener(ForgeEvents::onClientSetup);
    modEventBus.addListener(this::addCreative);
    modEventBus.addListener(Networking::register);
    OptionalRecipeCondition.register(modEventBus);

    PlayerBlockInteraction.init(MODID, LOGGER);
  }

  private void addCreative(BuildCreativeModeTabContentsEvent event)
  {
    if(event.getTabKey().equals(Registries.RSGAUGES_TAB.getKey()))
    {
      List<Block> blocks = Registries.getRegisteredBlocks();
      List<Item> items = Registries.getRegisteredItems();

      blocks.forEach(event::accept);
      items.forEach(event::accept);
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Events
  // -------------------------------------------------------------------------------------------------------------------

  @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD)
  public static final class ForgeEvents
  {
    public static void onSetup(final FMLCommonSetupEvent event)
    {
      BlockCategories.update();
    }

    public static void onClientSetup(final FMLClientSetupEvent event)
    {
      Overlay.register();
      ModContent.processContentClientSide(event);
    }

    @SubscribeEvent
    public static void onConfigLoad(final ModConfigEvent.Loading event)
    { ModConfig.apply(); }

    @SubscribeEvent
    public static void onConfigReload(final ModConfigEvent.Reloading event)
    {
      try {
        Auxiliaries.logger().info("Config file changed {}", event.getConfig().getFileName());
        ModConfig.apply();
      } catch(Throwable e) {
        Auxiliaries.logger().error("Failed to load changed config: " + e.getMessage());
      }
    }
  }
}
