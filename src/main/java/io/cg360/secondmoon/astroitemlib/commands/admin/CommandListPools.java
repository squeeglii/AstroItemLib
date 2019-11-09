package io.cg360.secondmoon.astroitemlib.commands.admin;

import io.cg360.secondmoon.astroitemlib.AstroItemLib;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;

import java.util.Arrays;
import java.util.Iterator;

/**
 * (C) Copyright 2019 - Will Scully (CloudGamer360), All rights reserved
 *
 * By using the following application, you are accepting all responsibility
 * for any damage or legal issues which arise over it. Additionally, you are
 * agreeing not to use this application or it's components anywhere other
 * than the Mooncraft Minecraft Server unless you have written permission from
 * the copyright holder.
 *
 *
 * Failure to follow the license will result in a termination of license.
 */
public class CommandListPools implements CommandExecutor {

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        String[] pools = AstroItemLib.getAstroManager().getPools();
        String s = "";
        Iterator<String> i = Arrays.asList(pools).iterator();
        while(i.hasNext()){
            s = s.concat(i.next());
            if(i.hasNext()) s = s.concat(", ");
        }
        src.sendMessage(Text.of(TextColors.LIGHT_PURPLE, TextStyles.BOLD, "POOLS ", TextStyles.RESET, "Here are the available pools: \n", TextColors.GRAY, s));
        return CommandResult.success();
    }
}
