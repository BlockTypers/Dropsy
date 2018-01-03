package com.blocktyper.dropsy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;

import com.blocktyper.v1_2_3.BlockTyperListener;
import com.blocktyper.v1_2_3.helpers.Key;

public class DropsyListenerBase extends BlockTyperListener {
	public static final String REBREAK_KEY = "DROPSY_REBREAK";

	protected DropsyPlugin dropsyPlugin;

	public DropsyListenerBase(DropsyPlugin dropsyPlugin) {
		super();
		init(dropsyPlugin);
		this.dropsyPlugin = dropsyPlugin;
	}
	
	static Map<String, RandomIntGenerator> intGenerators = new HashMap<String, RandomIntGenerator>();
	
	// EXP
	protected RandomIntGenerator randomExpGeneratorInstance(Key materialRoot){
		return randomIntGeneratorInstance(materialRoot, Config.EXP, Config.EXP_RANGE, Config.EXP_DISTRIBUTION, 0);
	}
	
	// AMOUNT
	protected RandomIntGenerator randomAmountGeneratorInstance(Key root){
		return randomIntGeneratorInstance(root, Config.AMOUNT, Config.AMOUNT_RANGE, Config.AMOUNT_DISTRIBUTION, 0);
	}
	
	
	protected RandomIntGenerator randomIntGeneratorInstance(Key root, String statName, String range, String distribution, int hardDefault){
		if(intGenerators.containsKey(root.end(statName))){
			return intGenerators.get(root.end(statName));
		}
		RandomIntGenerator randomMaterialExpGenerator = new RandomIntGenerator() {
			@Override
			public Integer getRandomInt(String source) {
				return getCalculatedInt(root, statName, range, distribution,
						dropsyPlugin, 0);
			}
		};
		intGenerators.put(root.end(statName), randomMaterialExpGenerator);
		return randomMaterialExpGenerator;
	}
	
	protected String getVariationKey(String key, int variation){
		return key + "_" + variation;
	}
	


	
	
	

	protected Boolean isInDistribution(List<String> distributionList, String key) {
		if (distributionList != null) {
			if (distributionList.contains(key)) {
				debugInfo("  - " + key + " was included in the random distribution.");
				return true;
			} else {
				debugInfo("  - Skipping " + key + " as it was not part of random distribution.");
				return false;
			}
		}
		return null;
	}

	protected boolean drop(Location location, Key dropsRoot, String dropKey, Key parentRoot,
			List<String> dropsFromRandomDistribution, Player player, double materialDropChance) {

		Boolean isInDistribution = isInDistribution(dropsFromRandomDistribution, dropKey);
		boolean forceDrop = isInDistribution == null ? false : isInDistribution;
		return attemptDrop(location, dropsRoot, dropKey, randomExpGeneratorInstance(parentRoot),
				randomAmountGeneratorInstance(parentRoot), forceDrop, materialDropChance, player);
	}
	
	protected boolean spell(Location location, Key spellsRoot, String spellKey, Key parentRoot,
			List<String> spellsFromRandomDistribution, Player player, double materialSpellChance, String target) {

		Boolean isInDistribution = isInDistribution(spellsFromRandomDistribution, spellKey);
		boolean forceSpell = isInDistribution == null ? false : isInDistribution;
		return attemptSpell(location, spellsRoot, spellKey, forceSpell, materialSpellChance, player, target);
	}
	

	protected int getCalculatedInt(Key root, String simpleKey, String rangeKey, String distributionKey,
			RandomIntGenerator defaultGenerator, int hardDefault) {
		Integer simpleValue = null;
		String rangeValue = null;
		List<String> distributionValue = null;

		if (getConfig().contains(root.end(simpleKey), true)) {
			simpleValue = getConfig().getInt(root.end(simpleKey));
		}

		if (getConfig().contains(root.end(rangeKey), true)) {
			rangeValue = getConfig().getString(root.end(rangeKey), null);
		}

		if (getConfig().contains(root.end(distributionKey), true)) {
			distributionValue = getConfig().getStringList(root.end(distributionKey));
		}

		int calculatedValue = DropsyPlugin.getIntFromSources(simpleKey, simpleValue, rangeValue, distributionValue,
				defaultGenerator, hardDefault);
		debugInfo("  - " + simpleKey + ": " + calculatedValue);
		return calculatedValue;
	}

	protected boolean keyExists(Key root) {
		return getConfig().contains(root.getVal(), false);
	}
	
	protected boolean keyIsEnabled(Key root) {
		return getConfig().getBoolean(root.end(Config.ENABLED), false);
	}

	protected boolean isAllowRebreak(Key materialRoot) {
		return getConfig().getBoolean(materialRoot.end(Config.ALLOW_REBREAK), dropsyPlugin.isAllowRebreak());
	}
	
	protected boolean isSpellAllowRebreak(Key materialRoot) {
		return getConfig().getBoolean(materialRoot.end(Config.SPELL_ALLOW_REBREAK), dropsyPlugin.isAllowRebreak());
	}

	protected String getMaterialNameFromBlock(Block block) {
		Material material = block.getType();
		@SuppressWarnings("deprecation")
		Byte blockData = block.getData();

		String materialName = material.name();

		if (blockData != null && blockData != 0) {
			materialName = materialName + "-" + blockData;
		}
		return materialName;
	}

	protected boolean isRebreak(Block block) {
		if (block == null) {
			return false;
		}

		List<MetadataValue> rebreakMeta = block.getMetadata(REBREAK_KEY);
		if (rebreakMeta == null || rebreakMeta.isEmpty() || rebreakMeta.get(0) == null) {
			return false;
		}

		return true;
	}

	@SuppressWarnings("deprecation")
	protected boolean attemptDrop(Location location, Key dropsRoot, String dropKey,
			RandomIntGenerator randomMaterialExpGenerator, RandomIntGenerator randomMaterialAmountGenerator,
			boolean forceDrop, double materialDropChance, Player player) {

		debugInfo("---------------------------------");
		debugInfo("---------------------------------");
		debugInfo("drop-material: " + dropKey);

		Key dropRoot = new Key(dropsRoot.end(dropKey));

		if(!shouldItemBeDropped(dropRoot, location, player, forceDrop, materialDropChance)){
			return false;
		}
		
		String dropMaterialName = null;
		
		boolean isCustomDrop = dropKey.startsWith("_");
		
		if(isCustomDrop){
			if(getConfig().contains(dropRoot.end(Config.MATERIAL))){
				dropMaterialName = getConfig().getString(dropRoot.end(Config.MATERIAL));
			}
		}else{
			dropMaterialName = dropKey;
		}
		
		ItemStack dropItem = null;
		
		if(dropMaterialName != null){
			Byte dropMaterialData = null;
			if (dropMaterialName.contains("-")) {
				dropMaterialData = Byte.parseByte(dropMaterialName.substring(dropMaterialName.indexOf("-") + 1));
				dropMaterialName = dropMaterialName.substring(0, dropMaterialName.indexOf("-"));
			}

			Integer calculatedDropData = getCalculatedInt(dropRoot, Config.MATERIAL_DATA, Config.MATERIAL_DATA_RANGE,
					Config.MATERIAL_DATA_DISTRIBUTION, null, dropMaterialData != null ? dropMaterialData.intValue() : 0);

			if (calculatedDropData != null && calculatedDropData > 0) {
				dropMaterialData = calculatedDropData.byteValue();
			}

			Material dropMaterial = Material.matchMaterial(dropMaterialName);
			
			dropItem = new ItemStack(dropMaterial);

			if (dropMaterialData != null) {
				dropItem = new ItemStack(dropMaterial, 1, dropItem.getDurability(), dropMaterialData);
			}
		}else{
			if(!getConfig().contains(dropRoot.end(Config.RECIPE))){
				warning("  - no recipe for custom drop: " + dropKey);
				return false;
			}
			
			String recipeKey = getConfig().getString(dropRoot.end(Config.RECIPE));
			
			dropItem = recipeRegistrar().getItemFromRecipe(recipeKey, player, null, 1);
		}
		
		if(dropItem == null){
			warning("  item for drop could not be determined: " + dropKey);
			return false;
		}
		
		if(getConfig().contains(dropRoot.end("name"))){
			String customName = getConfig().getString(dropRoot.end("name"));
			ItemMeta itemMeta = dropItem.getItemMeta();
			if(itemMeta != null){
				itemMeta.setDisplayName(customName);
				dropItem.setItemMeta(itemMeta);
			}
		}
		
		if(getConfig().contains(dropRoot.end("lore"))){
			List<String> lore = getConfig().getStringList(dropRoot.end("lore"));
			ItemMeta itemMeta = dropItem.getItemMeta();
			if(itemMeta != null){
				itemMeta.setLore(lore);
				dropItem.setItemMeta(itemMeta);
			}
		}
		
		if(getConfig().contains(dropRoot.end("enchantments"))){
			ConfigurationSection enchantments = getConfig().getConfigurationSection(dropRoot.end("enchantments"));
			if(enchantments != null && enchantments.getKeys(false) != null){
				for(String enchantmentName : enchantments.getKeys(false)){
					Enchantment enchantment = Enchantment.getByName(enchantmentName);
					
					if(enchantment == null){
						warning("Enchantment not recognized: " + enchantment);
						continue;
					}
					int level = 1;
					if(getConfig().contains(dropRoot.__("enchantments").__(enchantmentName).end("level"))){
						level = getConfig().getInt(dropRoot.__("enchantments").__(enchantmentName).end("level"));
					}
					dropItem.addUnsafeEnchantment(enchantment, level);
				}
					
			}
		}
		
		
		// EXP
		int calculatedDropExp = getCalculatedInt(dropRoot, Config.EXP, Config.EXP_RANGE, Config.EXP_DISTRIBUTION,
				randomMaterialExpGenerator, 0);
		dropExp(calculatedDropExp, location);

		// AMOUNT
		int calculatedDropAmount = getCalculatedInt(dropRoot, Config.AMOUNT, Config.AMOUNT_RANGE,
				Config.AMOUNT_DISTRIBUTION, randomMaterialAmountGenerator, 0);

		dropItem(dropItem, calculatedDropAmount, location);

		return true;
	}
	
	
	
	
	private boolean shouldItemBeDropped(Key dropRoot, Location location, Player player, boolean forceDrop, double materialDropChance){
		if (!getConfig().getBoolean(dropRoot.end(Config.ENABLED), false)) {
			debugInfo("  - not enabled");
			return false;
		}

		Integer highestY = getConfig().getInt(dropRoot.end(Config.HIGHEST_Y), -1);
		Integer lowestY = getConfig().getInt(dropRoot.end(Config.LOWEST_Y), -1);

		if (highestY >= 0 && location.getBlockY() > highestY) {
			debugInfo(" - TOO HIGH (" + location.getBlockY() + " > " + highestY + ")");
			return false;
		} else if (highestY >= 0) {
			debugInfo("  - NOT TOO HIGH (" + location.getBlockY() + " <= " + highestY + ")");
		}

		if (lowestY >= 0 && location.getBlockY() < lowestY) {
			debugInfo(" - TOO LOW (" + location.getBlockY() + " < " + lowestY + ")");
			return false;
		} else if (lowestY >= 0) {
			debugInfo("  - NOT TOO LOW (" + location.getBlockY() + " >= " + lowestY + ")");
		}

		String requiredItem = getConfig().getString(dropRoot.end(Config.REQUIRED_ITEM), null);

		if (requiredItem != null && !itemHasExpectedNbtKey(getPlayerHelper().getItemInHand(player), requiredItem)) {
			debugInfo("  - required item not in hand - " + requiredItem);
			return false;
		}

		if (!forceDrop) {
			double dropChance = getConfig().getDouble(dropRoot.end(Config.DROP_CHANCE), materialDropChance);

			double randomDouble = (new Random()).nextDouble() * 100;

			if (dropChance < randomDouble) {
				debugInfo("  -  NO LUCK - [dropChance(" + dropChance + ") < rand(" + randomDouble + ")]");
				return false;
			} else {
				debugInfo("  - RANDOM LUCK - [dropChance(" + dropChance + ") >= rand(" + randomDouble + ")]");
			}
		} else {
			debugInfo("  - DROPS DISTRIBUTION LUCK");
		}
		
		return true;
	}
	
	
	
	protected boolean attemptSpell(Location location, Key spellsRoot, String spellKey, 
			boolean forceSpell, double materialSpellChance, Player player, String target) {

		debugInfo("---------------------------------");
		debugInfo("---------------------------------");
		debugInfo("spell: " + spellKey);

		Key spellRoot = new Key(spellsRoot.end(spellKey));

		if (!getConfig().getBoolean(spellRoot.end(Config.ENABLED), false)) {
			debugInfo("  - not enabled");
			return false;
		}

		Integer highestY = getConfig().getInt(spellRoot.end(Config.HIGHEST_Y), -1);
		Integer lowestY = getConfig().getInt(spellRoot.end(Config.LOWEST_Y), -1);

		if (highestY >= 0 && location.getBlockY() > highestY) {
			debugInfo(" - TOO HIGH (" + location.getBlockY() + " > " + highestY + ")");
			return false;
		} else if (highestY >= 0) {
			debugInfo("  - NOT TOO HIGH (" + location.getBlockY() + " <= " + highestY + ")");
		}

		if (lowestY >= 0 && location.getBlockY() < lowestY) {
			debugInfo(" - TOO LOW (" + location.getBlockY() + " < " + lowestY + ")");
			return false;
		} else if (lowestY >= 0) {
			debugInfo("  - NOT TOO LOW (" + location.getBlockY() + " >= " + lowestY + ")");
		}

		String requiredItem = getConfig().getString(spellRoot.end(Config.REQUIRED_ITEM), null);

		if (requiredItem != null && !itemHasExpectedNbtKey(getPlayerHelper().getItemInHand(player), requiredItem)) {
			debugInfo("  - required item not in hand - " + requiredItem);
			return false;
		}

		if (!forceSpell) {
			double spellChance = getConfig().getDouble(spellRoot.end(Config.SPELL_CHANCE), materialSpellChance);

			double randomDouble = (new Random()).nextDouble() * 100;

			if (spellChance < randomDouble) {
				debugInfo("  -  NO LUCK - [spellChance(" + spellChance + ") < rand(" + randomDouble + ")]");
				return false;
			} else {
				debugInfo("  - RANDOM LUCK - [spellChance(" + spellChance + ") >= rand(" + randomDouble + ")]");
			}
		} else {
			debugInfo("  - SPELLS DISTRIBUTION LUCK");
		}
		
		
		List<String> commands = null;
		List<String> messages = null;
		Map<String,String> variablesMap = getVariablesMap(spellRoot, Config.VARIABLES);
		
		if(getConfig().contains(spellRoot.end(Config.COMMAND))){
			messages = getLocaleStringList(spellRoot, Config.MESSAGES, player);
			String commandString = getConfig().getString(spellRoot.end(Config.COMMAND), null);
			commands = new ArrayList<>();
			commands.add(commandString);
			
		}else if(getConfig().contains(spellRoot.end(Config.COMMAND_SEQUENCE))){
			String source = null;
			if(getConfig().contains(spellRoot.end(Config.SOURCE))){
				source = getLocaleString(spellRoot, Config.SOURCE, player);
				variablesMap.put(Config.SOURCE, source);
			}
			
			String commandSequence = getConfig().getString(spellRoot.end(Config.COMMAND_SEQUENCE), null);
			Key commandSequenceRoot = new Key(Config.COMMAND_SEQUENCES).__(commandSequence);
			
			
			messages = getLocaleStringList(commandSequenceRoot, Config.MESSAGES, player);
			commands = getConfig().getStringList(commandSequenceRoot.end(Config.COMMANDS));
		}else{
			warning("No " + Config.COMMAND + " or " + Config.COMMAND_SEQUENCES + " section!");
		}
		
		if(commands != null && !commands.isEmpty()){
			return executeCommands(commands, messages, variablesMap, player, target);
		}

		return false;
	}
	
	protected boolean executeCommands(List<String> commands, List<String> messages, Map<String,String> variablesMap, Player player, String targetName){
		
		for(String command : commands){
			String commandToRun = command;
			if(variablesMap != null && !variablesMap.isEmpty()){
				for(String variable : variablesMap.keySet()){
					commandToRun = commandToRun.replace("&"+variable, variablesMap.get(variable));
				}
			}
			commandToRun = replacePlayerAndTargetVariables(commandToRun, player.getName(), targetName);
			
			plugin.debugInfo("running command...");
			plugin.debugInfo("/" + commandToRun);
			dropsyPlugin.getServer().dispatchCommand(dropsyPlugin.getServer().getConsoleSender(), commandToRun);
		}
		
		if(messages != null){
			for(String message : messages){
				String messageToSend = message;
				if(variablesMap != null && !variablesMap.isEmpty()){
					for(String variable : variablesMap.keySet()){
						messageToSend = messageToSend.replace("&"+variable, variablesMap.get(variable));
					}
				}
				messageToSend = replacePlayerAndTargetVariables(messageToSend, player.getName(), targetName);
				player.sendMessage(messageToSend);
			}
		}
		
		return true;
	}
	
	protected String replacePlayerAndTargetVariables(String command, String playerName, String targetName){
		
		if(playerName != null){
			command = command.replace("&PLAYER", playerName);
		}
		
		if(targetName != null){
			command = command.replace("&TARGET", targetName);
		}
				
		return command;

	}
	
	protected Map<String,String> getVariablesMap(Key root, String key){
		Map<String,String> variablesMap = new HashMap<>();
		if(getConfig().contains(root.end(key))){
			ConfigurationSection variablesConfiguration = getConfig().getConfigurationSection(root.end(key));
			
			if(variablesConfiguration != null){
				for(String variable : variablesConfiguration.getKeys(false)){
					variablesMap.put(variable, variablesConfiguration.getString(variable));
				}
			}
			
		}
		return variablesMap;
	}
	
	protected String getLocaleString(Key root, String key, HumanEntity player){
		String returnValue = null;
		if(getConfig().contains(root.end(key))){
			String playersLanguageCode = getPlayerHelper().getLanguage(player);
			returnValue = getConfig().getString(root.end(key + "." + playersLanguageCode), null);
			
			if(returnValue == null){
				returnValue = getConfig().getString(root.end(key + ".default"), null);
				
				if(returnValue == null){
					ConfigurationSection sourceSection = getConfig().getConfigurationSection(root.end(key));
					
					if(sourceSection != null){
						for(String languageCode : sourceSection.getKeys(false)){
							returnValue = getConfig().getString(root.end(key + "." + languageCode), null);
							break;
						}
					}
				}
			}
		}
		return returnValue;
	}
	
	protected List<String> getLocaleStringList(Key root, String key, HumanEntity player){
		List<String> returnValue = null;
		if(getConfig().contains(root.end(key))){
			String playersLanguageCode = getPlayerHelper().getLanguage(player);
			returnValue = getConfig().getStringList(root.end(key + "." + playersLanguageCode));
			
			if(returnValue == null){
				returnValue = getConfig().getStringList(root.end(key + ".default"));
				
				if(returnValue == null){
					ConfigurationSection sourceSection = getConfig().getConfigurationSection(root.end(key));
					
					if(sourceSection != null){
						for(String languageCode : sourceSection.getKeys(false)){
							returnValue = getConfig().getStringList(root.end(key + "." + languageCode));
							break;
						}
					}
				}
			}
		}
		return returnValue;
	}

	protected void dropItem(ItemStack item, int amount, Location location) {
		ItemStack itemToDrop = item.clone();
		int maxAmount = item.getType().getMaxStackSize();
		if (amount > maxAmount) {
			itemToDrop.setAmount(maxAmount);
			location.getWorld().dropItemNaturally(location, itemToDrop);
			dropItem(item, amount - maxAmount, location);
			return;
		} else if (amount > 0) {
			itemToDrop.setAmount(amount);
			location.getWorld().dropItemNaturally(location, itemToDrop);
		}
	}

	protected void dropExp(int amount, Location location) {
		if (amount > 1) {
			int randomAmount = new Random().nextInt(amount) + 1;
			ExperienceOrb orb = location.getWorld().spawn(location, ExperienceOrb.class);
			orb.setExperience(randomAmount);
			dropExp(amount - randomAmount, location);
			return;
		} else if (amount > 0) {
			ExperienceOrb orb = location.getWorld().spawn(location, ExperienceOrb.class);
			orb.setExperience(amount);
		}
	}

	protected RegionsToProcess getRegionsToProcess(String world, int x, int z) {

		List<String> defaultRegions = new ArrayList<>();
		List<String> priorityRegions = new ArrayList<>();

		Key regionsRoot = new Key(Config.REGIONS_ROOT);

		Integer priority = null;

		boolean worldSpecificDefaultFound = false;

		for (String region : getConfig().getConfigurationSection(regionsRoot.getVal()).getKeys(false)) {
			Key regionRoot = regionsRoot.__(region);

			boolean isDefaultRegion = !getConfig().contains(regionRoot.end(Config.BOUNDS));

			if (getConfig().contains(regionsRoot.end(Config.WORLDS))) {
				List<String> worlds = getConfig().getStringList(regionRoot.end(Config.WORLDS));
				if (worlds == null || !worlds.contains(world)) {
					continue;
				} else if (!worldSpecificDefaultFound && isDefaultRegion) {
					worldSpecificDefaultFound = true;
					defaultRegions.clear();
				}
			} else if (isDefaultRegion && worldSpecificDefaultFound) {
				continue;
			}

			if (isDefaultRegion) {
				defaultRegions.add(region);
				continue;
			}

			if (!getConfig().contains(regionRoot.end(Config.PRIORITY))) {
				continue;
			}

			int tempPriority = getConfig().getInt(regionRoot.end(Config.PRIORITY));

			if (priority == null) {
				priority = getConfig().getInt(regionRoot.end(Config.PRIORITY));
			} else if (tempPriority > priority) {
				continue;
			} else if (tempPriority < priority) {
				priorityRegions.clear();
			}

			Key boundsRoot = regionRoot.__(Config.BOUNDS);

			String highestXString = getConfig().getString(boundsRoot.end(Config.HIGHEST_X), null);
			String lowestXString = getConfig().getString(boundsRoot.end(Config.LOWEST_X), null);
			String highestZString = getConfig().getString(boundsRoot.end(Config.HIGHEST_Z), null);
			String lowestZString = getConfig().getString(boundsRoot.end(Config.LOWEST_Z), null);

			Long highestX = highestXString != null ? Long.parseLong(highestXString) : null;
			Long lowestX = lowestXString != null ? Long.parseLong(lowestXString) : null;
			Long highestZ = highestZString != null ? Long.parseLong(highestZString) : null;
			Long lowestZ = lowestZString != null ? Long.parseLong(lowestZString) : null;

			if (highestX != null && x > highestX) {
				continue;
			}
			if (lowestX != null && x < lowestX) {
				continue;
			}

			if (highestZ != null && z > highestZ) {
				continue;
			}
			if (lowestZ != null && z < lowestZ) {
				continue;
			}

			priorityRegions.add(region);
		}

		RegionsToProcess regionsToProcess = new RegionsToProcess();
		regionsToProcess.setDefaultRegions(defaultRegions);
		regionsToProcess.setPriorityRegions(priorityRegions);

		return regionsToProcess;
	}
}
