package com.blocktyper.dropsy;

import java.util.List;
import java.util.Random;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
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
	DropsyPlugin dropsyPlugin;

	public DropsyListener(DropsyPlugin dropsyPlugin) {
		super();
		init(dropsyPlugin);
		this.dropsyPlugin = dropsyPlugin;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void onBlockBreakEvent(BlockPlaceEvent event) {
		if (event.getPlayer() == null || event.getBlock() == null) {
			return;
		}

		Block block = event.getBlock();

		String materialName = getMaterialNameFromBlock(block);

		debugInfo("materialName: " + materialName);

		Key materialRoot = new Key(DropsyPlugin.BLOCKS_ROOT).__(materialName);

		if (!materialIsEnabled(materialRoot)) {
			debugInfo("materialName: " + materialName + " - material not enabled");
			return;
		}

		boolean allowRebreak = isAllowRebreak(materialRoot);

		if (!allowRebreak) {
			debugInfo("materialName: " + materialName + " - no rebreak");
			MetadataValue mdv = new FixedMetadataValue(plugin, true);
			block.setMetadata(DropsyPlugin.REBREAK_KEY, mdv);
		} else {
			debugInfo("materialName: " + materialName + " - rebreak OK");
		}

	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void onBlockBreakEvent(BlockBreakEvent event) {
		if (event.getBlock() == null) {
			return;
		}

		Block block = event.getBlock();

		String materialName = getMaterialNameFromBlock(block);

		Key materialRoot = new Key(DropsyPlugin.BLOCKS_ROOT).__(materialName);

		debugInfo("materialRoot: " + materialName);

		if (!materialIsEnabled(materialRoot)) {
			debugInfo("materialRoot: " + materialName + " - material not enabled");
			return;
		}

		double materialDropChance = getConfig().getDouble(materialRoot.end(DropsyPlugin.DROP_CHANCE),
				dropsyPlugin.getDropChance());

		boolean allowRebreak = isAllowRebreak(materialRoot);
		boolean destroyOnBreak = getConfig().getBoolean(materialRoot.end(DropsyPlugin.DESTROY_ON_BREAK),
				dropsyPlugin.isDestroyOnBreak());

		if (!allowRebreak && isRebreak(block)) {
			debugInfo("materialRoot: " + materialName + " - rebreak detected");
			return;
		}

		Integer materialAmount = null;
		String materialAmountRange = null;
		List<String> materialAmountDistribution = null;

		if (getConfig().contains(materialRoot.end(DropsyPlugin.AMOUNT))) {
			materialAmount = getConfig().getInt(materialRoot.end(DropsyPlugin.AMOUNT));
		}

		materialAmountRange = getConfig().getString(materialRoot.end(DropsyPlugin.AMOUNT_RANGE), null);

		if (getConfig().contains(materialRoot.end(DropsyPlugin.AMOUNT_DISTRIBUTION))) {
			materialAmountDistribution = getConfig().getStringList(materialRoot.end(DropsyPlugin.AMOUNT_DISTRIBUTION));
		}

		int calculatedMaterialAmount = DropsyPlugin.getIntFromSources(materialAmount, materialAmountRange,
				materialAmountDistribution, dropsyPlugin.getAmount());

		Key dropsRoot = new Key(materialRoot.end(DropsyPlugin.DROPS_ROOT));

		ConfigurationSection dropsConfigurationSection = getConfig().getConfigurationSection(dropsRoot.getVal());
		if (dropsConfigurationSection == null) {
			warning("materialRoot: " + materialName + " - DROPS SECTION NOT DEFINED");
			return;
		}

		Set<String> drops = dropsConfigurationSection.getKeys(false);

		if (drops == null || drops.isEmpty()) {
			warning("materialRoot: " + materialName + " - NO DROPS DEFINED");
			return;
		}

		boolean somethingDropped = false;

		for (String dropKey : drops) {

			boolean somethingDroppedTemp = attemptDrop(block.getLocation(), dropsRoot, materialRoot, dropKey,
					calculatedMaterialAmount, materialDropChance, event.getPlayer());

			if (!somethingDropped) {
				somethingDropped = somethingDroppedTemp;
			}
		}

		if (!somethingDropped) {
			debugInfo("materialRoot: " + materialName + " - nothing dropped");
			return;
		}

		if (destroyOnBreak) {
			debugInfo("materialRoot: " + materialName + " - destroying block");
			event.setCancelled(true);
			block.setType(Material.AIR);
		}
		
		if(dropsyPlugin.getDropSound() != null){
			block.getWorld().playSound(block.getLocation(), dropsyPlugin.getDropSound(), 1f, 1f);
		}

	}

	private boolean materialIsEnabled(Key materialRoot) {
		return getConfig().getBoolean(materialRoot.end(DropsyPlugin.ENABLED), false);
	}

	private boolean isAllowRebreak(Key materialRoot) {
		return getConfig().getBoolean(materialRoot.end(DropsyPlugin.ALLOW_REBREAK), dropsyPlugin.isAllowRebreak());
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

		List<MetadataValue> rebreakMeta = block.getMetadata(DropsyPlugin.REBREAK_KEY);
		if (rebreakMeta == null || rebreakMeta.isEmpty() || rebreakMeta.get(0) == null) {
			return false;
		}

		return true;
	}

	@SuppressWarnings("deprecation")
	private boolean attemptDrop(Location location, Key dropsRoot, Key materialRoot, String dropKey,
			int calculatedMaterialAmount, double materialDropChance, Player player) {

		debugInfo("dropKey: " + dropKey);

		String dropMaterialName = dropKey;
		Byte dropMaterialData = null;
		if (dropMaterialName.contains("-")) {
			dropMaterialData = Byte.parseByte(dropMaterialName.substring(dropMaterialName.indexOf("-") + 1));
			dropMaterialName = dropMaterialName.substring(0, dropMaterialName.indexOf("-"));
		}

		Material dropMaterial = Material.matchMaterial(dropMaterialName);
		ItemStack dropItem = new ItemStack(dropMaterial);

		if (dropMaterialData != null) {
			dropItem = new ItemStack(dropMaterial, 1, dropItem.getDurability(), dropMaterialData);
		}

		Key dropRoot = new Key(dropsRoot.end(dropKey));

		if (!getConfig().getBoolean(dropRoot.end(DropsyPlugin.ENABLED), false)) {
			debugInfo("dropKey: " + dropKey + " - not enabled");
			return false;
		}
		
		String requiredItem = getConfig().getString(dropRoot.end(DropsyPlugin.REQUIRED_ITEM), null);
		
		if(requiredItem != null && !itemHasExpectedNbtKey(getPlayerHelper().getItemInHand(player), requiredItem)){
			debugInfo("dropKey: " + dropKey + " - required item not in hand - " + requiredItem);
			return false;
		}

		double dropChance = getConfig().getDouble(dropRoot.end(DropsyPlugin.DROP_CHANCE), materialDropChance);

		double randomDouble = (new Random()).nextDouble() * 100;

		if (dropChance < randomDouble) {
			debugInfo("dropKey: " + dropKey + " - NO LOVE - [dropChance(" + dropChance + ") < rand(" + randomDouble
					+ ")]");
			return false;
		}else{
			debugInfo("dropKey: " + dropKey + " - YES LOVE - [dropChance(" + dropChance + ") >= rand(" + randomDouble
					+ ")]");
		}

		Integer amount = null;
		String amountRange = null;
		List<String> amountDistribution = null;

		if (getConfig().contains(materialRoot.end(DropsyPlugin.AMOUNT))) {
			amount = getConfig().getInt(materialRoot.end(DropsyPlugin.AMOUNT));
		}

		amountRange = getConfig().getString(materialRoot.end(DropsyPlugin.AMOUNT_RANGE), null);

		if (getConfig().contains(materialRoot.end(DropsyPlugin.AMOUNT_DISTRIBUTION))) {
			amountDistribution = getConfig().getStringList(materialRoot.end(DropsyPlugin.AMOUNT_DISTRIBUTION));
		}

		int calculatedDropAmount = DropsyPlugin.getIntFromSources(amount, amountRange, amountDistribution,
				calculatedMaterialAmount);

		debugInfo("dropKey: " + dropKey + " - dropping: " + calculatedDropAmount);

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
		}
		itemToDrop.setAmount(amount);
		location.getWorld().dropItemNaturally(location, itemToDrop);
	}
}
