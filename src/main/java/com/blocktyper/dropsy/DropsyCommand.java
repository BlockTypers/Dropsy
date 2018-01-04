package com.blocktyper.dropsy;

import java.util.Arrays;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.blocktyper.v1_2_6.BlockTyperCommand;

public class DropsyCommand extends BlockTyperCommand {

	DropsyPlugin dropsyPlugin;

	public DropsyCommand(DropsyPlugin dropsyPlugin) {
		init(dropsyPlugin);
		this.dropsyPlugin = dropsyPlugin;
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
			dropsyPlugin.loadAllSettings();
		}

		return true;
	}

	private void showUsage(CommandSender sender, Command command) {
		for (String supportedArg : SUPPORTED_ARGS) {
			sender.sendMessage("/" + command.getLabel() + " " + supportedArg);
		}

	}

}
