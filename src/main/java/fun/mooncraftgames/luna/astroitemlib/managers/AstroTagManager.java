package fun.mooncraftgames.luna.astroitemlib.managers;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import fun.mooncraftgames.luna.astroforgebridge.AstroForgeBridge;
import fun.mooncraftgames.luna.astroitemlib.AstroItemLib;
import fun.mooncraftgames.luna.astroitemlib.data.AstroKeys;
import fun.mooncraftgames.luna.astroitemlib.tags.*;
import fun.mooncraftgames.luna.astroitemlib.tags.context.blocks.BlockChangeContext;
import fun.mooncraftgames.luna.astroitemlib.tags.context.blocks.BlockInteractContext;
import fun.mooncraftgames.luna.astroitemlib.tags.context.entities.EntityHitContext;
import fun.mooncraftgames.luna.astroitemlib.tags.context.entities.EntityInteractContext;
import fun.mooncraftgames.luna.astroitemlib.tags.context.item.*;
import fun.mooncraftgames.luna.astroitemlib.tasks.RunnableManageContinousTags;
import fun.mooncraftgames.luna.astroitemlib.tasks.interfaces.IAstroTask;
import fun.mooncraftgames.luna.astroitemlib.utilities.Utils;
import me.ryanhamshire.griefprevention.api.GriefPreventionApi;
import me.ryanhamshire.griefprevention.api.claim.Claim;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.manipulator.mutable.block.DirectionalData;
import org.spongepowered.api.data.type.HandType;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.effect.particle.ParticleEffect;
import org.spongepowered.api.effect.particle.ParticleOptions;
import org.spongepowered.api.effect.particle.ParticleTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.entity.DamageEntityEvent;
import org.spongepowered.api.event.entity.InteractEntityEvent;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.event.item.inventory.InteractItemEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.blockray.BlockRay;
import org.spongepowered.api.util.blockray.BlockRayHit;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.World;

import java.util.*;
import java.util.regex.Pattern;

public class AstroTagManager {

    public static final int PROCESSOR_SIZE = 10000; // Change it to something like 50 and make the max difference 5 ticks (Theoretical max of 250 players)

    private Map<String, AbstractTag> tagMap;
    private List<UUID> tagProcessors;

    private boolean overrideLeftClick;
    private boolean overrideRightClick;

    public AstroTagManager(){
        this.tagMap = new HashMap<>();
        this.tagProcessors = new ArrayList<>();
        overrideLeftClick = false;
        overrideRightClick = false;
    }

    public AstroTagManager registerTag(AbstractTag tag){
        tagMap.put(tag.getId().split(Pattern.quote(":"))[0].toLowerCase(), tag);
        return this;
    }

    public Optional<AbstractTag> getTag(String tag){
        String t = tag.toLowerCase().split(Pattern.quote(":"))[0];
        return tagMap.containsKey(t) ? Optional.of(tagMap.get(t)) : Optional.empty();
    }

    public List<UUID> getTagProcessorIDs(){ return tagProcessors; }
    public void addTagProcessor(UUID uuid){ tagProcessors.add(uuid); }
    public void removeTagProcessor(UUID uuid){ tagProcessors.remove(uuid); }

    /**
     * Takes in tags from an item and gives them a process order
     * based on priority. The order of tags does affect priority as
     * well where if two tags are equal priority, the one which comes
     * first gets to execute first out of the two.
     *
     * @param tagsIn - Unfiltered/Unmodified tags from an item
     * @return Ordered list of tags.
     */
    public String[] orderedTags(String[] tagsIn){
        List<String> tags = new ArrayList<>();
        for(String t:tagsIn){
            String shorttag = t.toLowerCase().split(Pattern.quote(":"))[0];
            if(!tagMap.containsKey(shorttag)){
                AstroItemLib.getLogger().warn(String.format("<@Data> An item uses an unregistered/unrecognised tag: %s | Is a plugin missing?", shorttag));
                continue;
            }
            if(tags.size() == 0){
                tags.add(t);
            } else {
                boolean added = false;
                for(int i = 0; i < tags.size(); i++){
                    if(tagMap.get(shorttag).getPriority().getIntegerPriority() > tagMap.get(tags.get(i)).getPriority().getIntegerPriority()){
                        tags.add(i, t);
                        added = true;
                        break;
                    }
                }
                if(!added) tags.add(t);
            }
        }
        return tags.toArray(new String[0]);
    }



    // -----------------------------------------------------------------------------

    @Listener(beforeModifications = true)
    public void onPlayerJoin(ClientConnectionEvent.Join event){
        for(UUID tagP : tagProcessors){
            Optional<IAstroTask> t = AstroItemLib.getTaskManager().getTask(tagP);
            if(t.isPresent()){
                IAstroTask task = t.get();
                if(task instanceof RunnableManageContinousTags){
                    RunnableManageContinousTags processor = (RunnableManageContinousTags) task;
                    if(processor.getPlayers().size() < PROCESSOR_SIZE){
                        processor.addPlayer(event.getTargetEntity().getUniqueId());
                        return;
                    }
                }
            }
        }
        AstroItemLib.getLogger().info("Starting new TagProcessor...");
        AstroItemLib.getTaskManager().registerTask(new RunnableManageContinousTags(1, tagProcessors.size()).addPlayer(event.getTargetEntity().getUniqueId()));

    }


    // -----------------------------------------------------------------------------

    //TODO: Pipeline 3.0, put all events through this.
    @Listener(beforeModifications = true, order = Order.DEFAULT)
    public void onGameEvent(Event e, @First Player player){
        if(e instanceof ChangeBlockEvent) return;
        // -- COMMON (Stuff which all levels should use)
        Optional<ItemStackSnapshot> i = e.getContext().get(EventContextKeys.USED_ITEM);
        if(!i.isPresent()) return;
        ItemStackSnapshot istack = i.get();
        Optional<List<String>> tgs = istack.get(AstroKeys.FUNCTION_TAGS);
        if(!tgs.isPresent()) return;
        List<String> tags = tgs.get();
        String[] otags = orderedTags(tags.toArray(new String[0]));

        // -- ITEM INTERACTS Level 3 (The top of the foodchain)

        if(e instanceof DamageEntityEvent){
            DamageEntityEvent event = (DamageEntityEvent) e;
            HandType handType = HandTypes.OFF_HAND;
            if(player.getItemInHand(HandTypes.MAIN_HAND).isPresent()) handType = player.getItemInHand(HandTypes.MAIN_HAND).get().equalTo(istack.createStack()) ? HandTypes.MAIN_HAND : HandTypes.OFF_HAND;
            UsedContext usedContext = new UsedContext(player, handType, ClickType.LEFT);
            EntityInteractContext interactContext = new EntityInteractContext(player, ClickType.LEFT, event.getTargetEntity(), event.isCancelled());
            for(String tag: otags){
                if(getTag(tag).isPresent()){
                    AbstractTag t = getTag(tag).get();
                    boolean result = true;
                    if(t.getType() == ExecutionTypes.ENTITY_HIT) { result = t.run(ExecutionTypes.ENTITY_HIT, tag, istack, new EntityHitContext(player, event)); }
                    if(t.getType() == ExecutionTypes.ENTITY_INTERACT) { result = t.run(ExecutionTypes.ENTITY_INTERACT, tag, istack, interactContext); }
                    if(t.getType() == ExecutionTypes.ITEM_USED) { result = t.run(ExecutionTypes.ITEM_USED, tag, istack, usedContext); }
                    if(!result) return;
                }
            }
            if(usedContext.isCancelled()) event.setCancelled(true);
            if(interactContext.isCancelled()) event.setCancelled(true);
            return;
        }

        // -- ITEM INTERACTS Level 2 (Stuff which are interacts but still encompass other events)

        if(e instanceof InteractEntityEvent){
            InteractEntityEvent event = (InteractEntityEvent) e;
            ClickType clickType = e instanceof InteractEntityEvent.Primary ? ClickType.LEFT : ClickType.RIGHT; // Does not anticipate it being neither.

            HandType handType = HandTypes.OFF_HAND;
            if(player.getItemInHand(HandTypes.MAIN_HAND).isPresent()) handType = player.getItemInHand(HandTypes.MAIN_HAND).get().equalTo(istack.createStack()) ? HandTypes.MAIN_HAND : HandTypes.OFF_HAND;

            UsedContext usedContext = new UsedContext(player, handType, ClickType.RIGHT);
            EntityInteractContext interactContext = new EntityInteractContext(player, clickType, event.getTargetEntity(), event.isCancelled());

            for(String tag: otags){
                if(getTag(tag).isPresent()){
                    AbstractTag t = getTag(tag).get();
                    boolean result = true;
                    if(t.getType() == ExecutionTypes.ENTITY_INTERACT) { result = t.run(ExecutionTypes.ENTITY_INTERACT, tag, istack, interactContext); }
                    if(t.getType() == ExecutionTypes.ITEM_USED) { result = t.run(ExecutionTypes.ITEM_USED, tag, istack, usedContext); }
                    if(!result) return;
                }
            }

            if(interactContext.isCancelled()) event.setCancelled(true);
            if(usedContext.isCancelled()) event.setCancelled(true);
            return;
        }

        if(e instanceof InteractBlockEvent){
            InteractBlockEvent event = (InteractBlockEvent) e;
            HandType handType = HandTypes.OFF_HAND;
            if(player.getItemInHand(HandTypes.MAIN_HAND).isPresent()) handType = player.getItemInHand(HandTypes.MAIN_HAND).get().equalTo(istack.createStack()) ? HandTypes.MAIN_HAND : HandTypes.OFF_HAND;

            UsedContext usedContext = new UsedContext(player, handType, ClickType.RIGHT);
            BlockInteractContext interactContext = new BlockInteractContext(player, event.getTargetBlock(), event.getTargetSide(), event.isCancelled());

            for(String tag: otags){
                if(getTag(tag).isPresent()){
                    AbstractTag t = getTag(tag).get();
                    boolean result = true;
                    if(t.getType() == ExecutionTypes.BLOCK_INTERACT) { result = t.run(ExecutionTypes.BLOCK_INTERACT, tag, istack, interactContext); }
                    if(t.getType() == ExecutionTypes.ITEM_USED) { result = t.run(ExecutionTypes.ITEM_USED, tag, istack, usedContext); }
                    if(!result) return;
                }
            }

            if (usedContext.isCancelled()) event.setCancelled(true);
            if (interactContext.isCancelled()) event.setCancelled(true);
        }

        // -- ITEM INTERACTS Level 1 (Covers all interactions which aren't caught by higher layers)
        if(e instanceof InteractItemEvent) {
            InteractItemEvent event = (InteractItemEvent) e;
            HandType type = e instanceof InteractItemEvent.Primary ? HandTypes.MAIN_HAND : HandTypes.OFF_HAND; // Doesn't anticipate it not being either Primary or Secondary. Potential issue.
            UsedContext usedContext = new UsedContext(player, type, ClickType.LEFT);
            for (String tag : otags) {
                if (getTag(tag).isPresent()) {
                    AbstractTag t = getTag(tag).get();
                    boolean result = true;
                    if (t.getType() == ExecutionTypes.ITEM_USED) {
                        result = t.run(ExecutionTypes.ITEM_USED, tag, istack, usedContext);
                    }
                    if (!result) return;
                }
            }
            if (usedContext.isCancelled()) event.setCancelled(true);
            return;
        }

    }

    // -----------------------------------------------------------------------------

    /* --- OLD (Pipeline 2.0) Item Use detectors ---
    @Listener(beforeModifications = true, order = Order.BEFORE_POST)
    public void onUseLeft(InteractItemEvent.Primary event, @First Player player){
        if(overrideLeftClick) {
            overrideLeftClick = false;
            return;
        }
        Optional<List<String>> tgs = event.getItemStack().get(AstroKeys.FUNCTION_TAGS);
        if(tgs.isPresent()){
            List<String> tags = tgs.get();
            HandType type = event.getHandType();
            ItemStackSnapshot istack = event.getItemStack();

            String[] otags = orderedTags(tags.toArray(new String[0]));

            UsedContext usedContext = new UsedContext(player, type, ClickType.LEFT);

            for(String tag: otags){
                if(getTag(tag).isPresent()){
                    AbstractTag t = getTag(tag).get();
                    boolean result = true;
                    if(t.getType() == ExecutionTypes.ITEM_USED) { result = t.run(ExecutionTypes.ITEM_USED, tag, istack, usedContext); }
                    if(!result) return;
                }
            }
            if(usedContext.isCancelled()) event.setCancelled(true);
        }
    }

    @Listener(beforeModifications = true, order = Order.BEFORE_POST)
    public void onUseRight(InteractItemEvent.Secondary event, @First Player player){
        if(overrideRightClick) {
            overrideRightClick = false;
            return;
        }
        Optional<List<String>> tgs = event.getItemStack().get(AstroKeys.FUNCTION_TAGS);
        if(tgs.isPresent()){
            List<String> tags = tgs.get();
            HandType type = event.getHandType();
            ItemStackSnapshot istack = event.getItemStack();

            String[] otags = orderedTags(tags.toArray(new String[0]));

            UsedContext usedContext = new UsedContext(player, type, ClickType.RIGHT);

            for(String tag: otags){
                if(getTag(tag).isPresent()){
                    AbstractTag t = getTag(tag).get();
                    boolean result = true;
                    if(t.getType() == ExecutionTypes.ITEM_USED) { result = t.run(ExecutionTypes.ITEM_USED, tag, istack, usedContext); }
                    if(!result) return;
                }
            }

            if(usedContext.isCancelled()) event.setCancelled(true);

        }
    }

     */

    /**
     * <h3>Item Hold (OnSelect) Events</h3>
     * Handles the single time trigger called when an item is
     * selected in the hotbar.
     *
     * @param event Internal Sponge supplier of event.
     * @param player Internal Sponge supplier of player.
     */
    @Listener(beforeModifications = true, order = Order.DEFAULT)
    public void onInventorySingleHold(ClickInventoryEvent.Held event, @First Player player){
        Optional<ItemStack> s = event.getFinalSlot().peek();
        if(!s.isPresent()) return;
        ItemStackSnapshot istack = s.get().createSnapshot();

        Optional<List<String>> tgs = istack.get(AstroKeys.FUNCTION_TAGS);
        if(!tgs.isPresent()) return;
        List<String> tags = tgs.get();

        String[] otags = orderedTags(tags.toArray(new String[0]));

        for(String tag: otags){
            if(getTag(tag).isPresent()){
                AbstractTag t = getTag(tag).get();
                boolean result = true;
                if(t.getType() == ExecutionTypes.ITEM_HOLD) { result = t.run(ExecutionTypes.ITEM_HOLD, tag, istack, new HoldContext(player, event)); }
                if(!result) return;
            }
        }
    }


    /**
     * <h3>Item Click Events</h3>
     * Handles tags which are trigger-able by an InventoryClickEvent
     * or other related events. Does not include support for drop (Above
     * in class)
     *
     * @param event Internal Sponge supplier of event.
     * @param player Internal Sponge supplier of player.
     */
    @Listener(beforeModifications = true, order = Order.DEFAULT)
    public void onInventoryClick(ClickInventoryEvent event, @First Player player){
        if(event instanceof ClickInventoryEvent.Open) return;
        if(event instanceof ClickInventoryEvent.Close) return;
        if(event instanceof ClickInventoryEvent.Held) return;

        event.getTransactions().forEach(transaction -> {
            ItemStackSnapshot istack = transaction.getOriginal();
            Optional<List<String>> tgs = istack.get(AstroKeys.FUNCTION_TAGS);
            if(!tgs.isPresent()) return;
            List<String> tags = tgs.get();

            //TODO: Determine ClickType & If shift.
            ClickType clickType = ClickType.UNKNOWN;
            InventoryChangeStates state = InventoryChangeStates.NOTHING;
            boolean isShift = false;

            if(event instanceof ClickInventoryEvent.Primary) clickType = ClickType.LEFT;
            if(event instanceof ClickInventoryEvent.Secondary) clickType = ClickType.RIGHT;
            if(event instanceof ClickInventoryEvent.Middle) clickType = ClickType.MIDDLE;
            if(event instanceof ClickInventoryEvent.Shift) isShift = true;
            if(event instanceof ClickInventoryEvent.Transfer){ clickType = ClickType.QUICK_SWITCH; }
            if(event instanceof ClickInventoryEvent.NumberPress){ clickType = ClickType.QUICK_SWITCH; }
            if(event instanceof ClickInventoryEvent.SwapHand){ clickType = ClickType.QUICK_SWITCH; }
            if(event instanceof ClickInventoryEvent.Drag){ clickType = ClickType.LEFT; isShift = true; }
            if(event instanceof ClickInventoryEvent.Creative) { clickType = ClickType.CREATIVE; }

            if(event instanceof ClickInventoryEvent.Drop){ state = InventoryChangeStates.DROP; }
            if(event instanceof ClickInventoryEvent.Pickup){ state = InventoryChangeStates.PICKUP; }

            if(clickType == ClickType.UNKNOWN && state == InventoryChangeStates.NOTHING) return;

            String[] otags = orderedTags(tags.toArray(new String[0]));

            for(String tag: otags){
                if(getTag(tag).isPresent()){
                    AbstractTag t = getTag(tag).get();
                    boolean result = true;
                    switch (state) {
                        case NOTHING:
                            if (t.getType() == ExecutionTypes.ITEM_CLICKED) { result = t.run(ExecutionTypes.ITEM_CLICKED, tag, istack, new ClickedContext(player, event, clickType, isShift)); }
                            break;
                        case DROP:
                            if (t.getType() == ExecutionTypes.ITEM_DROPPED) { result = t.run(ExecutionTypes.ITEM_DROPPED, tag, istack, new DroppedContext(player, (ClickInventoryEvent.Drop) event)); }
                            break;
                        case PICKUP:
                            if (t.getType() == ExecutionTypes.ITEM_PICKUP) { result = t.run(ExecutionTypes.ITEM_PICKUP, tag, istack, new PickupContext(player, (ClickInventoryEvent.Pickup) event)); }
                            break;
                        /*
                        Should be managed by the event above
                        case HOLD:
                            if (t.getType() == ExecutionTypes.ITEM_HOLD) { result = t.run(ExecutionTypes.ITEM_HOLD, tag, istack, new HoldContext(player, (ClickInventoryEvent.Held) event)); }
                            break;
                         */
                    }
                    if(!result) return;
                }
            }
        });
    }

    // -----

/*
    @Listener(beforeModifications = true, order = Order.DEFAULT)
    public void onEntityHit(DamageEntityEvent event, @First Player player){
        Optional<ItemStackSnapshot> s = event.getContext().get(EventContextKeys.USED_ITEM);
        if(!s.isPresent()) return;
        ItemStackSnapshot istack = s.get();

        Optional<List<String>> tgs = istack.get(AstroKeys.FUNCTION_TAGS);
        if(!tgs.isPresent()) return;
        List<String> tags = tgs.get();

        String[] otags = orderedTags(tags.toArray(new String[0]));

        overrideLeftClick = true;

        HandType handType = HandTypes.OFF_HAND;
        if(player.getItemInHand(HandTypes.MAIN_HAND).isPresent()) handType = player.getItemInHand(HandTypes.MAIN_HAND).get().equalTo(istack.createStack()) ? HandTypes.MAIN_HAND : HandTypes.OFF_HAND;

        UsedContext usedContext = new UsedContext(player, handType, ClickType.LEFT);

        for(String tag: otags){
            if(getTag(tag).isPresent()){
                AbstractTag t = getTag(tag).get();
                boolean result = true;
                if(t.getType() == ExecutionTypes.ENTITY_HIT) { result = t.run(ExecutionTypes.ENTITY_HIT, tag, istack, new EntityHitContext(player, event)); }
                if(t.getType() == ExecutionTypes.ITEM_USED) { result = t.run(ExecutionTypes.ITEM_USED, tag, istack, usedContext); }
                if(!result) return;
            }
        }

        if(usedContext.isCancelled()) event.setCancelled(true);
    }
 */

/*
    @Listener(beforeModifications = true, order = Order.DEFAULT)
    public void onEntityInteract(InteractEntityEvent event, @First Player player){
        if(event instanceof InteractEntityEvent.Primary){ return; }
        if(event instanceof InteractEntityEvent.Secondary){ overrideRightClick = true; }
        Optional<ItemStackSnapshot> s = event.getContext().get(EventContextKeys.USED_ITEM);
        if(!s.isPresent()) return;
        ItemStackSnapshot istack = s.get();

        Optional<List<String>> tgs = istack.get(AstroKeys.FUNCTION_TAGS);
        if(!tgs.isPresent()) return;
        List<String> tags = tgs.get();

        String[] otags = orderedTags(tags.toArray(new String[0]));

        HandType handType = HandTypes.OFF_HAND;
        if(player.getItemInHand(HandTypes.MAIN_HAND).isPresent()) handType = player.getItemInHand(HandTypes.MAIN_HAND).get().equalTo(istack.createStack()) ? HandTypes.MAIN_HAND : HandTypes.OFF_HAND;

        UsedContext usedContext = new UsedContext(player, handType, ClickType.RIGHT);

        for(String tag: otags){
            if(getTag(tag).isPresent()){
                AbstractTag t = getTag(tag).get();
                boolean result = true;
                if(t.getType() == ExecutionTypes.ENTITY_INTERACT) { result = t.run(ExecutionTypes.ENTITY_INTERACT, tag, istack, new EntityInteractContext(player, event)); }
                if(t.getType() == ExecutionTypes.ITEM_USED) { result = t.run(ExecutionTypes.ITEM_USED, tag, istack, usedContext); }
                if(!result) return;
            }
        }

        if(usedContext.isCancelled()) event.setCancelled(true);
    }

 */

/*
    @Listener(beforeModifications = true, order = Order.DEFAULT)
    public void onBlockInteract(InteractBlockEvent event, @First Player player){
        Optional<ItemStackSnapshot> s = event.getContext().get(EventContextKeys.USED_ITEM);
        if(!s.isPresent()) return;
        ItemStackSnapshot istack = s.get();

        if(event instanceof InteractBlockEvent.Primary){ overrideLeftClick = true; }
        if(event instanceof InteractBlockEvent.Secondary){ overrideRightClick = true; }

        Optional<List<String>> tgs = istack.get(AstroKeys.FUNCTION_TAGS);
        if(!tgs.isPresent()) return;
        List<String> tags = tgs.get();

        String[] otags = orderedTags(tags.toArray(new String[0]));

        HandType handType = HandTypes.OFF_HAND;
        if(player.getItemInHand(HandTypes.MAIN_HAND).isPresent()) handType = player.getItemInHand(HandTypes.MAIN_HAND).get().equalTo(istack.createStack()) ? HandTypes.MAIN_HAND : HandTypes.OFF_HAND;

        UsedContext usedContext = new UsedContext(player, handType, ClickType.RIGHT);

        for(String tag: otags){
            if(getTag(tag).isPresent()){
                AbstractTag t = getTag(tag).get();
                boolean result = true;
                if(t.getType() == ExecutionTypes.BLOCK_INTERACT) { result = t.run(ExecutionTypes.BLOCK_INTERACT, tag, istack, new BlockInteractContext(player, event)); }
                if(t.getType() == ExecutionTypes.ITEM_USED) { result = t.run(ExecutionTypes.ITEM_USED, tag, istack, usedContext); }
                if(!result) return;
            }
        }

        if (usedContext.isCancelled()) event.setCancelled(true);
    }
 */

    //TODO: Add the ability to modify block transactions. Cancel changes on the original list if conflicted.

    @Listener(beforeModifications = true, order = Order.DEFAULT) public void onBlockPlace(ChangeBlockEvent.Place event, @First Player player) { overrideRightClick = true; onBlockEditCommon(event, player); }

    @Listener(beforeModifications = true, order = Order.DEFAULT) public void onBlockBreak(ChangeBlockEvent.Break event, @First Player player) { overrideLeftClick = true; onBlockEditCommon(event, player); }

    // Should not have @Listener
    private void onBlockEditCommon(ChangeBlockEvent event, Player player) {
        Optional<ItemStackSnapshot> s = event.getContext().get(EventContextKeys.USED_ITEM);
        if(!s.isPresent()) return;
        ItemStackSnapshot istack = s.get();
        Optional<List<String>> tgs = istack.get(AstroKeys.FUNCTION_TAGS);
        if(!tgs.isPresent()) return;
        List<String> tags = tgs.get();
        String[] otags = orderedTags(tags.toArray(new String[0]));
        HandType handType = HandTypes.OFF_HAND;
        if(player.getItemInHand(HandTypes.MAIN_HAND).isPresent()) handType = player.getItemInHand(HandTypes.MAIN_HAND).get().equalTo(istack.createStack()) ? HandTypes.MAIN_HAND : HandTypes.OFF_HAND;
        ItemStack tool = player.getItemInHand(HandTypes.MAIN_HAND).orElse(ItemStack.builder().itemType(ItemTypes.AIR).quantity(1).build());

        Optional<BlockRayHit<World>> bRay = BlockRay.from(player).distanceLimit(100).skipFilter( lastHit -> {
                    return lastHit.getLocation().getBlock().getType().equals(BlockTypes.AIR) ||
                            lastHit.getLocation().getBlock().getType().equals(BlockTypes.WATER) ||
                            lastHit.getLocation().getBlock().getType().equals(BlockTypes.LAVA);
        }).stopFilter(BlockRay.allFilter()).end();
        Direction direction = Direction.NONE;
        BlockSnapshot blockHit = event.getTransactions().get(0).getOriginal();
        if(bRay.isPresent()){
            BlockRayHit<World> blockRay = bRay.get();
            direction = blockRay.getFaces()[0];
            blockHit = blockRay.getLocation().getBlock().snapshotFor(blockRay.getLocation());
        }
        ClickType type = event instanceof ChangeBlockEvent.Place ? ClickType.RIGHT : ClickType.LEFT;
        BlockChangeContext changecontext = new BlockChangeContext(player, event.getTransactions(), blockHit, direction);
        UsedContext usedContext = new UsedContext(player, handType, type);

        for(String tag: otags){
            if(getTag(tag).isPresent()){
                AbstractTag t = getTag(tag).get();
                boolean result = true;
                if(t.getType() == ExecutionTypes.BLOCK_CHANGE) { result = t.run(ExecutionTypes.BLOCK_CHANGE, tag, istack, changecontext); }
                if(t.getType() == ExecutionTypes.ITEM_USED) { result = t.run(ExecutionTypes.ITEM_USED, tag, istack, usedContext); }
                if(!result) return;
            }
        }

        if(usedContext.isCancelled()) { event.setCancelled(true); return; }
        if(changecontext.areAllChangesCancelled()) { event.setCancelled(true); return; }
        ArrayList<Transaction<BlockSnapshot>> originalBlockChanges = new ArrayList<>(event.getTransactions());
        AstroItemLib.getLogger().info(Arrays.toString(originalBlockChanges.toArray()));
        for(BlockChangeContext.BlockChange blockChange : changecontext.getBlockChanges().values()){
            if(blockChange.isOriginalTransaction()){
                Optional<Transaction<BlockSnapshot>> t = originalBlockChanges.stream().filter(change -> change.getOriginal().getPosition() == blockChange.getOriginalBlock().getPosition()).findFirst();
                if(!t.isPresent()){ AstroItemLib.getLogger().warn("Uh oh? A block change is missing? Someone messed up somehow. Skipping..."); continue; }
                if(blockChange.isCancelled()) { t.get().setValid(false); }
                if(blockChange.isModified()){
                    t.get().setValid(false);
                    digBlock(blockChange, tool, player);
                    if(blockChange.getBlockChangeType().equals(BlockChangeContext.BlockChangeType.PLACE)){ placeBlock(blockChange, player); }
                }
            } else {
                if(blockChange.isCancelled()) continue;
                digBlock(blockChange, tool, player);
                if(blockChange.getBlockChangeType().equals(BlockChangeContext.BlockChangeType.PLACE)){ placeBlock(blockChange, player); }
            }
        }
    }

    private static void placeBlock(BlockChangeContext.BlockChange blockChange, Player player){
        if(AstroItemLib.getGriefPrevention().isPresent()){
            GriefPreventionApi api = AstroItemLib.getGriefPrevention().get();
            Claim claim = api.getClaimManager(player.getLocation().getExtent()).getClaimAt(blockChange.getBlock().getLocation().get());
            Map<String, Boolean> placeperms = claim.getPermissions(player, claim.getContext());
            AstroItemLib.getLogger().info(Utils.dataToMap(placeperms));
        }
        DirectionalData data = Sponge.getDataManager().getManipulatorBuilder(DirectionalData.class).get().create();
        data.direction().set(blockChange.getDirection());
        player.getLocation().getExtent().setBlock(blockChange.getBlock().getPosition(), blockChange.getBlock().getState().with(data.asImmutable()).get());
    }
    private static void digBlock(BlockChangeContext.BlockChange blockChange, ItemStack tool, Player player){
        if(AstroItemLib.getGriefPrevention().isPresent()){
            GriefPreventionApi api = AstroItemLib.getGriefPrevention().get();
            Claim claim = api.getClaimManager(player.getWorld()).getClaimAt(blockChange.getBlock().getLocation().get());
            Map<String, Boolean> placeperms = claim.getPermissions(player, claim.getContext());
            AstroItemLib.getLogger().info(Arrays.toString(placeperms.keySet().toArray(new String[0])));
        }
        Vector3i loc = blockChange.getBlock().getPosition();
        if(blockChange.getDrops().size() == 0) {
            //player.getLocation().getExtent().digBlock(blockChange.getBlock().getPosition(), player.getProfile());
            AstroForgeBridge.digBlock(player, tool, loc.getX(), loc.getY(), loc.getZ());
        } else {
            player.getLocation().getExtent().spawnParticles(ParticleEffect.builder()
                    .velocity(new Vector3d(0, 0.1, 0))
                    .offset(new Vector3d(0.5, 0.5, 0.5))
                    .type(ParticleTypes.BREAK_BLOCK)
                    .option(ParticleOptions.BLOCK_STATE, blockChange.getBlock().getState())
                    .build(), blockChange.getBlock().getPosition().toDouble());
            player.getLocation().getExtent().setBlock(blockChange.getBlock().getPosition(), BlockState.builder().blockType(BlockTypes.AIR).build(), BlockChangeFlags.ALL);
            for(BlockDestroyLootEntry item:blockChange.getDrops()){
                switch (item.getType()){
                    case SUPPLYLOOT:
                        item.getLootTable().ifPresent(loot -> {
                            for(ItemStack stack : loot.rollLootPool(-1)){ Utils.dropItem(blockChange.getBlock().getLocation().get(), stack.createSnapshot(), 15); }
                        });
                        break;
                    case VANILLADROPS:
                        AstroForgeBridge.dropBlock(player, tool, loc.getX(), loc.getY(), loc.getZ());
                        break;
                    case ITEMSTACKSNAPSHOT:
                        item.getItemStack().ifPresent(snapshot -> {
                            Utils.dropItem(blockChange.getBlock().getLocation().get(), snapshot, 15);
                        });
                        break;
                }
                //Utils.dropItem(blockChange.getBlock().getLocation().get(), item, 15);
            }
        }
    }
    /*
    @Listener(beforeModifications = true, order = Order.DEFAULT)
    public void onBlockPlace(ChangeBlockEvent.Place event, @First Player player){
        Optional<ItemStackSnapshot> s = event.getContext().get(EventContextKeys.USED_ITEM);
        if(!s.isPresent()) return;
        ItemStackSnapshot istack = s.get();

        Optional<List<String>> tgs = istack.get(AstroKeys.FUNCTION_TAGS);
        if(!tgs.isPresent()) return;
        List<String> tags = tgs.get();

        String[] otags = orderedTags(tags.toArray(new String[0]));

        overrideRightClick = true;

        HandType handType = HandTypes.OFF_HAND;
        if(player.getItemInHand(HandTypes.MAIN_HAND).isPresent()) handType = player.getItemInHand(HandTypes.MAIN_HAND).get().equalTo(istack.createStack()) ? HandTypes.MAIN_HAND : HandTypes.OFF_HAND;

        for(String tag: otags){
            if(getTag(tag).isPresent()){
                AbstractTag t = getTag(tag).get();
                boolean result = true;
                if(t.getType() == ExecutionTypes.BLOCK_PLACE) { result = t.run(ExecutionTypes.BLOCK_PLACE, tag, istack, new BlockPlaceContext(player, event)); }
                if(t.getType() == ExecutionTypes.ITEM_USED) { result = t.run(ExecutionTypes.ITEM_USED, tag, istack, new UsedContext(player, handType, ClickType.RIGHT)); }
                if(!result) return;
            }
        }
    }
     */

    /*
    @Listener(beforeModifications = true, order = Order.DEFAULT)
    public void onBlockBreak(ChangeBlockEvent.Break event, @First Player player){
        Optional<ItemStackSnapshot> s = event.getContext().get(EventContextKeys.USED_ITEM);
        if(!s.isPresent()) return;
        ItemStackSnapshot istack = s.get();

        Optional<List<String>> tgs = istack.get(AstroKeys.FUNCTION_TAGS);
        if(!tgs.isPresent()) return;
        List<String> tags = tgs.get();

        String[] otags = orderedTags(tags.toArray(new String[0]));

        overrideLeftClick = true;

        HandType handType = HandTypes.OFF_HAND;
        if(player.getItemInHand(HandTypes.MAIN_HAND).isPresent()) handType = player.getItemInHand(HandTypes.MAIN_HAND).get().equalTo(istack.createStack()) ? HandTypes.MAIN_HAND : HandTypes.OFF_HAND;

        for(String tag: otags){
            if(getTag(tag).isPresent()){
                AbstractTag t = getTag(tag).get();
                boolean result = true;
                if(t.getType() == ExecutionTypes.BLOCK_BREAK) { result = t.run(ExecutionTypes.BLOCK_BREAK, tag, istack, new BlockBreakContext(player, event)); }
                if(t.getType() == ExecutionTypes.ITEM_USED) { result = t.run(ExecutionTypes.ITEM_USED, tag, istack, new UsedContext(player, handType, ClickType.RIGHT)); }
                if(!result) return;
            }
        }
    }
     */

}
