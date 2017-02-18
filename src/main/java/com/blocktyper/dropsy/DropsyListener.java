package com.blocktyper.dropsy;

import java.util.List;
import java.util.Random;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import com.blocktyper.v1_2_3.BlockTyperListener;
import com.blocktyper.v1_2_3.helpers.Key;

public class DropsyListener extends BlockTyperListener {
	public static final String REBREAK_KEY = "DROPSY_REBREAK";

	DropsyPlugin dropsyPlugin;

	public DropsyListener(DropsyPlugin dropsyPlugin) {
		super();
		init(dropsyPlugin);
		this.dropsyPlugin = dropsyPlugin;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void onBlockPlaceEvent(BlockPlaceEvent event) {
		if (event.getPlayer() == null || event.getBlock() == null) {
			return;
		}
		
		debugInfo("#########################################");
		debugInfo("#########################################");

		Block block = event.getBlock();

		String materialName = getMaterialNameFromBlock(block);

		debugInfo("place-material: " + materialName);

		Key materialRoot = new Key(Config.BLOCKS_ROOT).__(materialName);

		if (!materialIsEnabled(materialRoot)) {
			debugInfo(" - material not enabled");
			return;
		}

		boolean allowRebreak = isAllowRebreak(materialRoot);

		if (!allowRebreak) {
			debugInfo(" - no rebreak");
			MetadataValue mdv = new FixedMetadataValue(plugin, true);
			block.setMetadata(REBREAK_KEY, mdv);
		} else {
			debugInfo(" - rebreak OK");
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void onBlockBreakEvent(BlockBreakEvent event) {
		if (event.getBlock() == null) {
			return;
		}
		
		debugInfo("#########################################");
		debugInfo("#########################################");

		Block block = event.getBlock();

		String materialName = getMaterialNameFromBlock(block);

		Key materialRoot = new Key(Config.BLOCKS_ROOT).__(materialName);

		debugInfo("break-material: " + materialName);

		if (!materialIsEnabled(materialRoot)) {
			debugInfo(" - material not enabled");
			return;
		}

		double materialDropChance = getConfig().getDouble(materialRoot.end(Config.DROP_CHANCE),
				dropsyPlugin.getDropChance());

		boolean allowRebreak = isAllowRebreak(materialRoot);
		boolean destroyOnBreak = getConfig().getBoolean(materialRoot.end(Config.DESTROY_ON_BREAK),
				dropsyPlugin.isDestroyOnBreak());
		
		
		List<String> dropsFromRandomDistribution = null;
		
		if(getConfig().contains(materialRoot.end(Config.DROPS_DISTRIBUTION), true)){
			List<String> dropsDistribution = getConfig().getStringList(materialRoot.end(Config.DROPS_DISTRIBUTION));
			dropsFromRandomDistribution = DropsyPlugin.getRandomCsvStringListFromDistribution(dropsDistribution);
			
			if(dropsFromRandomDistribution == null || dropsFromRandomDistribution.isEmpty()){
				warning("break-material: " + materialName + " - Unexpected issue getting the random drops distribution list!");
			}else{
				debugInfo("  - Random drops distributution: " + dropsFromRandomDistribution);
			}
		}
		

		if (!allowRebreak && isRebreak(block)) {
			debugInfo(" - rebreak detected");
			return;
		}

		Key dropsRoot = new Key(materialRoot.end(Config.DROPS_ROOT));

		ConfigurationSection dropsConfigurationSection = getConfig().getConfigurationSection(dropsRoot.getVal());
		if (dropsConfigurationSection == null) {
			warning("break-material: " + materialName + " - DROPS SECTION NOT DEFINED");
			return;
		}

		Set<String> drops = dropsConfigurationSection.getKeys(false);

		if (drops == null || drops.isEmpty()) {
			warning("break-material: " + materialName + " - NO DROPS DEFINED");
			return;
		}

		boolean somethingDropped = false;

		for (String dropKey : drops) {
			
			
			// EXP
			RandomIntGenerator randomMaterialExpGenerator = new RandomIntGenerator() {
				@Override
				public Integer getRandomInt(String source) {
					return getCalculatedInt(materialRoot, Config.EXP, Config.EXP_RANGE,
							Config.EXP_DISTRIBUTION, dropsyPlugin, 0);
				}
			};
			
			// AMOUNT
			RandomIntGenerator randomMaterialAmountGenerator = new RandomIntGenerator() {
				@Override
				public Integer getRandomInt(String source) {
					return getCalculatedInt(materialRoot, Config.AMOUNT, Config.AMOUNT_RANGE,
							Config.AMOUNT_DISTRIBUTION, dropsyPlugin, 0);
				}
			};
			
			boolean forceDrop = false;
			if(dropsFromRandomDistribution != null){
				if(dropsFromRandomDistribution.contains(dropKey)){
					forceDrop = true;
					debugInfo("  - " + dropKey + " was included in the random drops distribution.");
				}else{
					debugInfo("  - Skipping " + dropKey + " as it was not part of random drops distribution.");
					continue;
				}
			}

			boolean somethingDroppedTemp = attemptDrop(block.getLocation(), dropsRoot, materialRoot, dropKey,
					randomMaterialExpGenerator, randomMaterialAmountGenerator, forceDrop, materialDropChance, event.getPlayer());

			if (!somethingDropped) {
				somethingDropped = somethingDroppedTemp;
			}
		}

		if (!somethingDropped) {
			debugInfo("  - nothing dropped");
			return;
		}

		if (destroyOnBreak) {
			debugInfo("  - destroying block");
			event.setCancelled(true);
			block.setType(Material.AIR);
		}

		if (dropsyPlugin.getDropSound() != null) {
			block.getWorld().playSound(block.getLocation(), dropsyPlugin.getDropSound(), 1f, 1f);
		}
	}

	private int getCalculatedInt(Key root, String simpleKey, String rangeKey, String distributionKey,
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
		
		int calculatedValue = DropsyPlugin.getIntFromSources(simpleKey, simpleValue, rangeValue, distributionValue, defaultGenerator, hardDefault);
		debugInfo("  - " + simpleKey + ": " + calculatedValue);
		return calculatedValue;
	}

	private boolean materialIsEnabled(Key materialRoot) {
		return getConfig().getBoolean(materialRoot.end(Config.ENABLED), false);
	}

	private boolean isAllowRebreak(Key materialRoot) {
		return getConfig().getBoolean(materialRoot.end(Config.ALLOW_REBREAK), dropsyPlugin.isAllowRebreak());
	}

	private String getMaterialNameFromBlock(Block block) {
		Material material = block.getType();
		@SuppressWarnings("deprecation")
		Byte blockData = block.getData();

		String materialName = material.name();

		if (blockData != null && blockData != 0) {
			materialName = materialName + "-" + blockData;
		}
		return materialName;
	}

	private boolean isRebreak(Block block) {
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
	private boolean attemptDrop(Location location, Key dropsRoot, Key materialRoot, String dropKey,
			RandomIntGenerator randomMaterialExpGenerator, RandomIntGenerator randomMaterialAmountGenerator, boolean forceDrop, double materialDropChance, Player player) {

		debugInfo("---------------------------------");
		debugInfo("---------------------------------");
		debugInfo("drop-material: " + dropKey);

		Key dropRoot = new Key(dropsRoot.end(dropKey));
		
		String dropMaterialName = dropKey;
		Byte dropMaterialData = null;
		if (dropMaterialName.contains("-")) {
			dropMaterialData = Byte.parseByte(dropMaterialName.substring(dropMaterialName.indexOf("-") + 1));
			dropMaterialName = dropMaterialName.substring(0, dropMaterialName.indexOf("-"));
		}
		
		Integer calculatedDropData = getCalculatedInt(dropRoot, Config.MATERIAL_DATA, Config.MATERIAL_DATA_RANGE, Config.MATERIAL_DATA_DISTRIBUTION,
				null, dropMaterialData != null ? dropMaterialData.intValue() : 0);
		
		if(calculatedDropData != null && calculatedDropData > 0){
			dropMaterialData = calculatedDropData.byteValue();
		}

		Material dropMaterial = Material.matchMaterial(dropMaterialName);
		ItemStack dropItem = new ItemStack(dropMaterial);

		if (dropMaterialData != null) {
			dropItem = new ItemStack(dropMaterial, 1, dropItem.getDurability(), dropMaterialData);
		}
		

		if (!getConfig().getBoolean(dropRoot.end(Config.ENABLED), false)) {
			debugInfo("  - not enabled");
			return false;
		}
		
		Integer highestY = getConfig().getInt(dropRoot.end(Config.HIGHEST_Y), -1);
		Integer lowestY = getConfig().getInt(dropRoot.end(Config.LOWEST_Y), -1);
		
		if(highestY >= 0 && location.getBlockY() > highestY){
			debugInfo(" - TOO HIGH ("+location.getBlockY()+" > "+highestY+")");
			return false;
		}else if(highestY >= 0){
			debugInfo("  - NOT TOO HIGH ("+location.getBlockY()+" <= "+highestY+")");
		}
		
		if(lowestY >= 0 && location.getBlockY() < lowestY){
			debugInfo(" - TOO LOW ("+location.getBlockY()+" < "+lowestY+")");
			return false;
		}else if(lowestY >= 0){
			debugInfo("  - NOT TOO LOW ("+location.getBlockY()+" >= "+lowestY+")");
		}

		String requiredItem = getConfig().getString(dropRoot.end(Config.REQUIRED_ITEM), null);

		if (requiredItem != null && !itemHasExpectedNbtKey(getPlayerHelper().getItemInHand(player), requiredItem)) {
			debugInfo("  - required item not in hand - " + requiredItem);
			return false;
		}

		if(!forceDrop){
			double dropChance = getConfig().getDouble(dropRoot.end(Config.DROP_CHANCE), materialDropChance);

			double randomDouble = (new Random()).nextDouble() * 100;

			if (dropChance < randomDouble) {
				debugInfo("  -  NO LUCK - [dropChance(" + dropChance + ") < rand(" + randomDouble
						+ ")]");
				return false;
			} else {
				debugInfo("  - RANDOM LUCK - [dropChance(" + dropChance + ") >= rand(" + randomDouble
						+ ")]");
			}
		}else{
			debugInfo("  - DROPS DISTRIBUTION LUCK");
		}
		

		//EXP
		int calculatedDropExp = getCalculatedInt(dropRoot, Config.EXP, Config.EXP_RANGE, Config.EXP_DISTRIBUTION,
				randomMaterialExpGenerator, 0);
		dropExp(calculatedDropExp, location);

		//AMOUNT
		int calculatedDropAmount = getCalculatedInt(dropRoot, Config.AMOUNT, Config.AMOUNT_RANGE,
				Config.AMOUNT_DISTRIBUTION, randomMaterialAmountGenerator, 0);

		dropItem(dropItem, calculatedDropAmount, location);

		return true;
	}

	private void dropItem(ItemStack item, int amount, Location location) {
		ItemStack itemToDrop = item.clone();
		int maxAmount = item.getType().getMaxStackSize();
		if (amount > maxAmount) {
			itemToDrop.setAmount(maxAmount);
			location.getWorld().dropItemNaturally(location, itemToDrop);
			dropItem(item, amount - maxAmount, location);
			return;
		}else if(amount > 0){
			itemToDrop.setAmount(amount);
			location.getWorld().dropItemNaturally(location, itemToDrop);
		}
	}

	private void dropExp(int amount, Location location) {
		if (amount > 1) {
			int randomAmount = new Random().nextInt(amount) + 1;
			ExperienceOrb orb = location.getWorld().spawn(location,
			ExperienceOrb.class); orb.setExperience(randomAmount);
			dropExp(amount-randomAmount, location);
			return;
		}else if(amount > 0){
			 ExperienceOrb orb = location.getWorld().spawn(location,
			 ExperienceOrb.class); orb.setExperience(amount);
		}
	}
}
