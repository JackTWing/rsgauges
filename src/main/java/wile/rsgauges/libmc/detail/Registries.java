/*
 * @file Registries.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Common game registry handling.
 */
package wile.rsgauges.libmc.detail;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.commons.lang3.tuple.Pair;
import wile.rsgauges.ModRsGauges;
import wile.rsgauges.detail.ModResources;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Registries
{
  private static String modid = null;
  private static String creative_tab_icon = "";

  public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModRsGauges.MODID);
  private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ModRsGauges.MODID);
  private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModRsGauges.MODID);
  private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ModRsGauges.MODID);
  private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ModRsGauges.MODID);
  private static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, ModRsGauges.MODID);
  public static final DeferredRegister<SoundEvent> sound_deferred_register = DeferredRegister.create(Registries.SOUND_EVENT, ModRsGauges.MODID);

  private static final Map<String, DeferredBlock<? extends Block>> registered_blocks = new HashMap<>();
  private static final Map<String, DeferredItem<? extends Item>> registered_items = new HashMap<>();
  private static final Map<String, DeferredHolder<BlockEntityType<?>, BlockEntityType<?>>> registered_block_entity_types = new HashMap<>();
  private static final Map<String, DeferredHolder<EntityType<?>, EntityType<?>>> registered_entity_types = new HashMap<>();
  private static final Map<String, DeferredHolder<MenuType<?>, MenuType<?>>> registered_menu_types = new HashMap<>();
  private static final Map<String, TagKey<Block>> registered_block_tag_keys = new HashMap<>();
  private static final Map<String, TagKey<Item>> registered_item_tag_keys = new HashMap<>();
  private static final Map<TagKey<Block>, Set<ResourceLocation>> optional_block_tag_defaults = new HashMap<>();
  private static final Map<TagKey<Item>, Set<ResourceLocation>> optional_item_tag_defaults = new HashMap<>();
  private static final ArrayList<Pair<Class<?>, DeferredBlock<? extends Block>>> registered_block_classes = new ArrayList<>();

  public static void init(String mod_id, String creative_tab_icon_item_name)
  {
    modid = mod_id;
    creative_tab_icon = creative_tab_icon_item_name;
  }

  public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RSGAUGES_TAB = CREATIVE_MODE_TABS.register(ModRsGauges.MODID,
    () -> CreativeModeTab.builder()
      .icon(() -> {
        DeferredItem<? extends Item> iconItem = registered_items.get(creative_tab_icon);
        return (iconItem != null) ? new ItemStack(iconItem.get()) : ItemStack.EMPTY;
      })
      .title(Component.literal(ModRsGauges.MODNAME))
      .build());

  public static CreativeModeTab getCreativeModeTab()
  {
    return RSGAUGES_TAB.get();
  }

  public static Block getBlock(String block_name)
  { return registered_blocks.get(block_name).get(); }

  public static Item getItem(String name)
  { return registered_items.get(name).get(); }

  public static EntityType<?> getEntityType(String name)
  { return registered_entity_types.get(name).get(); }

  public static BlockEntityType<?> getBlockEntityType(String block_name)
  { return registered_block_entity_types.get(block_name).get(); }

  public static MenuType<?> getMenuType(String name)
  { return registered_menu_types.get(name).get(); }

  public static BlockEntityType<?> getBlockEntityTypeOfBlock(String block_name)
  { return getBlockEntityType("tet_"+block_name); }

  public static MenuType<?> getMenuTypeOfBlock(String name)
  { return getMenuType("ct_"+name); }

  public static TagKey<Block> getBlockTagKey(String name)
  { return registered_block_tag_keys.get(name); }

  public static TagKey<Item> getItemTagKey(String name)
  { return registered_item_tag_keys.get(name); }

  @Nonnull
  public static List<Block> getRegisteredBlocks()
  { return registered_blocks.values().stream().map(DeferredHolder::get).collect(Collectors.toList()); }

  @Nonnull
  public static List<Item> getRegisteredItems()
  { return registered_items.values().stream().map(DeferredHolder::get).collect(Collectors.toList()); }

  @Nonnull
  public static List<BlockEntityType<?>> getRegisteredBlockEntityTypes()
  { return registered_block_entity_types.values().stream().map(DeferredHolder::get).collect(Collectors.toList()); }

  @Nonnull
  public static List<EntityType<?>> getRegisteredEntityTypes()
  { return registered_entity_types.values().stream().map(DeferredHolder::get).collect(Collectors.toList()); }

  public static <T extends Item> void addItem(String registry_name, Supplier<T> supplier)
  {
    DeferredItem<? extends Item> item = ITEMS.register(registry_name, supplier);
    registered_items.put(registry_name, item);
  }

  public static <T extends Block> void addBlock(String registry_name, Supplier<T> block_supplier, Class<?> clazz)
  {
    DeferredBlock<? extends Block> block = BLOCKS.register(registry_name, block_supplier);
    DeferredItem<BlockItem> blockItem = ITEMS.registerSimpleBlockItem(block);
    registered_blocks.put(registry_name, block);
    registered_items.put(registry_name, blockItem);
    registered_block_classes.add(Pair.of(clazz, block));
  }

  public static <T extends BlockEntity> void addBlockEntityType(String registry_name, BlockEntityType.BlockEntitySupplier<T> ctor, String... block_names)
  {
    List<Block> blocks = Arrays.stream(block_names)
      .map(registered_blocks::get)
      .filter(Objects::nonNull)
      .map(DeferredHolder::get)
      .toList();
    DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> blockEntityType = BLOCK_ENTITY_TYPES.register(registry_name,
      () -> BlockEntityType.Builder.of(ctor, blocks.toArray(new Block[0])).build(null));
    registered_block_entity_types.put(registry_name, blockEntityType);
  }

  public static <T extends BlockEntity> void addBlockEntityType(String registry_name, BlockEntityType.BlockEntitySupplier<T> ctor, Class<? extends Block> block_clazz)
  {
    List<Block> blocks = registered_block_classes.stream()
      .filter(entry -> block_clazz.isAssignableFrom(entry.getLeft()))
      .map(entry -> entry.getRight().get())
      .toList();
    DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> blockEntityType = BLOCK_ENTITY_TYPES.register(registry_name,
      () -> BlockEntityType.Builder.of(ctor, blocks.toArray(new Block[0])).build(null));
    registered_block_entity_types.put(registry_name, blockEntityType);
  }

  public static void addEntityType(String registry_name, Supplier<EntityType<?>> supplier)
  {
    DeferredHolder<EntityType<?>, EntityType<?>> entityType = ENTITY_TYPES.register(registry_name, supplier);
    registered_entity_types.put(registry_name, entityType);
  }

  public static void addMenuType(String registry_name, MenuType.MenuSupplier<?> supplier)
  {
    DeferredHolder<MenuType<?>, MenuType<?>> menuType = MENU_TYPES.register(registry_name, () -> new MenuType<>(supplier, FeatureFlags.DEFAULT_FLAGS));
    registered_menu_types.put(registry_name, menuType);
  }

  public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor, Class<?> clazz)
  {
    addBlock(registry_name, block_supplier, clazz);
    addBlockEntityType("tet_"+registry_name, block_entity_ctor, registry_name);
  }

  public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor, MenuType.MenuSupplier<?> menu_type_supplier, Class<?> clazz)
  {
    addBlock(registry_name, block_supplier, clazz);
    addBlockEntityType("tet_"+registry_name, block_entity_ctor, registry_name);
    addMenuType("ct_"+registry_name, menu_type_supplier);
  }

  public static void addOptionalBlockTag(String tag_name, ResourceLocation... default_blocks)
  {
    TagKey<Block> key = TagKey.create(Registries.BLOCK, new ResourceLocation(modid, tag_name));
    registered_block_tag_keys.put(tag_name, key);
    optional_block_tag_defaults.put(key, Arrays.stream(default_blocks).collect(Collectors.toCollection(LinkedHashSet::new)));
  }

  public static void addOptionalBlockTag(String tag_name, String... default_blocks)
  {
    addOptionalBlockTag(tag_name, Arrays.stream(default_blocks).map(ResourceLocation::new).toArray(ResourceLocation[]::new));
  }

  public static void addOptionaItemTag(String tag_name, ResourceLocation... default_items)
  {
    TagKey<Item> key = TagKey.create(Registries.ITEM, new ResourceLocation(modid, tag_name));
    registered_item_tag_keys.put(tag_name, key);
    optional_item_tag_defaults.put(key, Arrays.stream(default_items).collect(Collectors.toCollection(LinkedHashSet::new)));
  }

  public static void addOptionaItemTag(String tag_name, String... default_items)
  {
    addOptionaItemTag(tag_name, Arrays.stream(default_items).map(ResourceLocation::new).toArray(ResourceLocation[]::new));
  }

  public static boolean matchesBlockTag(Block block, TagKey<Block> tag)
  {
    if(block.builtInRegistryHolder().is(tag)) return true;
    Set<ResourceLocation> defaults = optional_block_tag_defaults.get(tag);
    if((defaults == null) || defaults.isEmpty()) return false;
    ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
    return (key != null) && defaults.contains(key);
  }

  public static boolean matchesItemTag(Item item, TagKey<Item> tag)
  {
    if(item.builtInRegistryHolder().is(tag)) return true;
    Set<ResourceLocation> defaults = optional_item_tag_defaults.get(tag);
    if((defaults == null) || defaults.isEmpty()) return false;
    ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
    return (key != null) && defaults.contains(key);
  }

  public static boolean matchesBlockTag(Block block, ResourceLocation tag)
  {
    return matchesBlockTag(block, TagKey.create(Registries.BLOCK, tag));
  }

  public static boolean matchesItemTag(Item item, ResourceLocation tag)
  {
    return matchesItemTag(item, TagKey.create(Registries.ITEM, tag));
  }

  public static void registerAll(IEventBus eventBus)
  {
    ModResources.ALARM_SIREN_SOUND = ModResources.createSoundEvent("alarm_siren_sound");

    CREATIVE_MODE_TABS.register(eventBus);
    BLOCKS.register(eventBus);
    ITEMS.register(eventBus);
    BLOCK_ENTITY_TYPES.register(eventBus);
    ENTITY_TYPES.register(eventBus);
    MENU_TYPES.register(eventBus);
    sound_deferred_register.register(eventBus);
  }
}
