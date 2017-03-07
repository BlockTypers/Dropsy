package com.blocktyper.dropsy;

import java.util.Arrays;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.blocktyper.v1_2_3.BlockTyperCommand;
import com.blocktyper.v1_2_3.IBlockTyperPlugin;

public class DropsyCommand extends BlockTyperCommand {

	public DropsyCommand(IBlockTyperPlugin plugin) {
		init(plugin);
	}

	private static String RELOAD = "reload";

	private static List<String> SUPPORTED_ARGS = Arrays.asList(RELOAD);

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args == null || args.length < 1 || args[0] == null || !SUPPORTED_ARGS.contains(args[0].toLowerCase())) {
			showUsage(sender, command);
			return true;
		}

		if (args[0].equals(RELOAD)) {
			plugin.reloadConfig();
		}

		return true;
	}

	private void showUsage(CommandSender sender, Command command) {
		for (String supportedArg : SUPPORTED_ARGS) {
			sender.sendMessage("/" + command.getLabel() + " " + supportedArg);
		}

	}

}
