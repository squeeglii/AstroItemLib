package io.cg360.secondmoon.astroitemlib.tags.data.entities;

import io.cg360.secondmoon.astroitemlib.tags.data.ExecutionContext;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.entity.InteractEntityEvent;

public class EntityInteractContext extends ExecutionContext {

    private InteractEntityEvent event;

    public EntityInteractContext(Player player, InteractEntityEvent event) {
        super(player);
        this.event = event;
    }

    public InteractEntityEvent getEvent() { return event; }
}
