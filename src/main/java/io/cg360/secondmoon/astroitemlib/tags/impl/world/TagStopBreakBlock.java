package io.cg360.secondmoon.astroitemlib.tags.impl.world;

import io.cg360.secondmoon.astroitemlib.tags.AbstractTag;
import io.cg360.secondmoon.astroitemlib.tags.ExecutionTypes;
import io.cg360.secondmoon.astroitemlib.tags.TagPriority;
import io.cg360.secondmoon.astroitemlib.tags.context.ExecutionContext;
import io.cg360.secondmoon.astroitemlib.tags.context.blocks.BlockChangeContext;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

/**
 * Stops an item from breaking a block when used
 */
public class TagStopBreakBlock extends AbstractTag {

    public TagStopBreakBlock(String id, TagPriority priority, ExecutionTypes type) {
        super(id, priority, type);
    }

    @Override
    public boolean run(ExecutionTypes type, String tag, ItemStackSnapshot itemStack, ExecutionContext context) {
        if(type == ExecutionTypes.BLOCK_CHANGE) ((BlockChangeContext) context).setCancelAllChanges(true);
        return true;
    }
}
