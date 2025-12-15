/*
 * @file OptionalRecipeCondition.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Recipe condition to enable opt'ing out JSON based recipes.
 */
package wile.rsgauges.libmc.detail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class OptionalRecipeCondition implements ICondition
{
  private static ResourceLocation NAME;

  private final List<ResourceLocation> all_required;
  private final List<ResourceLocation> any_missing;
  private final List<ResourceLocation> all_required_tags;
  private final List<ResourceLocation> any_missing_tags;
  private final @Nullable ResourceLocation result;
  private final boolean result_is_tag;
  private final boolean experimental;

  private static boolean with_experimental = false;
  private static boolean without_recipes = false;
  private static Predicate<Block> block_optouts = (block)->false;
  private static Predicate<Item> item_optouts = (item)->false;

  private static DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS;
  private static DeferredHolder<MapCodec<? extends ICondition>, MapCodec<OptionalRecipeCondition>> CODEC_HOLDER;

  private record RequirementSet(List<ResourceLocation> items, List<ResourceLocation> tags)
  {
    static final RequirementSet EMPTY = new RequirementSet(List.of(), List.of());

    RequirementSet(List<ResourceLocation> items, List<ResourceLocation> tags)
    {
      this.items = List.copyOf(items);
      this.tags = List.copyOf(tags);
    }

    RequirementSet()
    {
      this(List.of(), List.of());
    }
  }

  private record ResultTarget(ResourceLocation id, boolean isTag)
  {
    static ResultTarget fromString(String value)
    {
      if(value.startsWith("#"))
        return new ResultTarget(new ResourceLocation(value.substring(1)), true);
      return new ResultTarget(new ResourceLocation(value), false);
    }

    String toStringValue()
    {
      return (isTag ? "#" : "") + id;
    }
  }

  private static final Codec<RequirementSet> REQUIREMENT_CODEC = Codec.STRING.listOf().xmap(
    list -> {
      List<ResourceLocation> items = new ArrayList<>();
      List<ResourceLocation> tags = new ArrayList<>();
      for(String entry : list) {
        if(entry.startsWith("#")) {
          tags.add(new ResourceLocation(entry.substring(1)));
        } else {
          items.add(new ResourceLocation(entry));
        }
      }
      return new RequirementSet(items, tags);
    },
    requirement -> {
      List<String> encoded = new ArrayList<>();
      requirement.items().forEach(loc -> encoded.add(loc.toString()));
      requirement.tags().forEach(loc -> encoded.add("#" + loc));
      return encoded;
    }
  );

  private static final Codec<ResultTarget> RESULT_CODEC = Codec.STRING.xmap(ResultTarget::fromString, ResultTarget::toStringValue);

  public static final MapCodec<OptionalRecipeCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
      REQUIREMENT_CODEC.optionalFieldOf("required", RequirementSet.EMPTY).forGetter(condition -> new RequirementSet(condition.all_required, condition.all_required_tags)),
      REQUIREMENT_CODEC.optionalFieldOf("missing", RequirementSet.EMPTY).forGetter(condition -> new RequirementSet(condition.any_missing, condition.any_missing_tags)),
      RESULT_CODEC.optionalFieldOf("result").forGetter(condition -> condition.result == null ? Optional.empty() : Optional.of(new ResultTarget(condition.result, condition.result_is_tag))),
      Codec.BOOL.optionalFieldOf("experimental", false).forGetter(condition -> condition.experimental)
  ).apply(instance, (required, missing, resultTarget, experimental) -> {
      RequirementSet req = required;
      RequirementSet miss = missing;
      ResultTarget target = resultTarget.orElse(null);
      ResourceLocation result = target == null ? null : target.id();
      boolean resultIsTag = target != null && target.isTag();
      return new OptionalRecipeCondition(result, req.items(), miss.items(), req.tags(), miss.tags(), experimental, resultIsTag);
  }));

  public static void init(String modid, Logger logger)
  {
    NAME = new ResourceLocation(modid, "optional");
    if(CONDITION_CODECS == null)
    {
      CONDITION_CODECS = DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, modid);
      CODEC_HOLDER = CONDITION_CODECS.register("optional", () -> CODEC);
    }
  }

  public static void register(IEventBus modEventBus)
  {
    if(CONDITION_CODECS != null)
    {
      CONDITION_CODECS.register(modEventBus);
    }
  }

  public static void on_config(boolean enable_experimental, boolean disable_all_recipes,
                               Predicate<Block> block_optout_provider,
                               Predicate<Item> item_optout_provider)
  {
    with_experimental = enable_experimental;
    without_recipes = disable_all_recipes;
    block_optouts = block_optout_provider;
    item_optouts = item_optout_provider;
  }

  public OptionalRecipeCondition(ResourceLocation result, List<ResourceLocation> required, List<ResourceLocation> missing, List<ResourceLocation> required_tags, List<ResourceLocation> missing_tags, boolean isexperimental, boolean result_is_tag)
  {
    all_required = List.copyOf(required);
    any_missing = List.copyOf(missing);
    all_required_tags = List.copyOf(required_tags);
    any_missing_tags = List.copyOf(missing_tags);
    this.result = result;
    this.result_is_tag = result_is_tag;
    experimental=isexperimental;
  }

  @Override
  public ResourceLocation getID()
  { return NAME; }

  @Override
  public boolean test(IContext context)
  {
    if(without_recipes) return false;
    if((experimental) && (!with_experimental)) return false;
    if(result != null)
    {
      Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(result);
      if(itemOpt.isEmpty()) return false;
      Item item = itemOpt.get();
      if(item_optouts.test(item)) return false;
      Optional<Block> blockOpt = BuiltInRegistries.BLOCK.getOptional(result);
      if(blockOpt.isPresent() && block_optouts.test(blockOpt.get())) return false;
    }
    if(!all_required.isEmpty())
    {
      for(ResourceLocation rl:all_required) {
        if(BuiltInRegistries.ITEM.getOptional(rl).isEmpty()) return false;
      }
    }
    if(!all_required_tags.isEmpty())
    {
      for(ResourceLocation rl:all_required_tags) {
        if(!context.isTagLoaded(ItemTags.create(rl))) return false;
      }
    }
    if(!any_missing.isEmpty())
    {
      for(ResourceLocation rl:any_missing) {
        if(BuiltInRegistries.ITEM.getOptional(rl).isEmpty()) return true;
      }
      return false;
    }
    if(!any_missing_tags.isEmpty())
    {
      for(ResourceLocation rl:any_missing_tags) {
        if(!context.isTagLoaded(ItemTags.create(rl))) return true;
      }
      return false;
    }
    return true;
  }

  @Override
  public MapCodec<? extends ICondition> codec()
  {
    return CODEC;
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("Optional recipe, all-required: [");
    for(ResourceLocation e:all_required) sb.append(e.toString()).append(",");
    for(ResourceLocation e:all_required_tags) sb.append("#").append(e.toString()).append(",");
    if(sb.charAt(sb.length()-1) == ',') sb.delete(sb.length()-1, sb.length());
    sb.append("], any-missing: [");
    for(ResourceLocation e:any_missing) sb.append(e.toString()).append(",");
    for(ResourceLocation e:any_missing_tags) sb.append("#").append(e.toString()).append(",");
    if(sb.charAt(sb.length()-1) == ',') sb.delete(sb.length()-1, sb.length());
    sb.append("]");
    if(experimental) sb.append(" EXPERIMENTAL");
    return sb.toString();
  }

  public JsonObject toJson()
  {
    JsonObject json = new JsonObject();
    JsonArray required = new JsonArray();
    JsonArray missing = new JsonArray();
    for(ResourceLocation e:all_required) required.add(e.toString());
    for(ResourceLocation e:all_required_tags) required.add("#" + e);
    for(ResourceLocation e:any_missing) missing.add(e.toString());
    for(ResourceLocation e:any_missing_tags) missing.add("#" + e);
    json.add("required", required);
    json.add("missing", missing);
    if(result != null)
    {
      json.addProperty("result", (result_is_tag ? "#" : "") + result);
    }
    if(experimental) json.addProperty("experimental", true);
    return json;
  }

  public static OptionalRecipeCondition fromJson(JsonObject json)
  {
    List<ResourceLocation> required = new ArrayList<>();
    List<ResourceLocation> missing = new ArrayList<>();
    List<ResourceLocation> required_tags = new ArrayList<>();
    List<ResourceLocation> missing_tags = new ArrayList<>();
    ResourceLocation result = null;
    boolean experimental = false;
    boolean result_is_tag = false;
    if(json.has("result")) {
      String s = json.get("result").getAsString();
      if(s.startsWith("#")) {
        result = new ResourceLocation(s.substring(1));
        result_is_tag = true;
      } else {
        result = new ResourceLocation(s);
      }
    }
    if(json.has("required")) {
      for(JsonElement e:GsonHelper.getAsJsonArray(json, "required")) {
        String s = e.getAsString();
        if(s.startsWith("#")) {
          required_tags.add(new ResourceLocation(s.substring(1)));
        } else {
          required.add(new ResourceLocation(s));
        }
      }
    }
    if(json.has("missing")) {
      for(JsonElement e:GsonHelper.getAsJsonArray(json, "missing")) {
        String s = e.getAsString();
        if(s.startsWith("#")) {
          missing_tags.add(new ResourceLocation(s.substring(1)));
        } else {
          missing.add(new ResourceLocation(s));
        }
      }
    }
    if(json.has("experimental")) experimental = json.get("experimental").getAsBoolean();
    return new OptionalRecipeCondition(result, required, missing, required_tags, missing_tags, experimental, result_is_tag);
  }
}
