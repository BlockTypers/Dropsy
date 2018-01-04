package com.blocktyper.dropsy;

import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import com.blocktyper.v1_2_6.helpers.Key;

public class DropsyBlockListener extends DropsyListenerBase {

	public DropsyBlockListener(DropsyPlugin dropsyPlugin) {
		super(dropsyPlugin);
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void onBlockPlaceEvent(BlockPlaceEvent event) {
		if (event.getPlayer() == null || event.getBlock() == null) {
			return;
		}

		debugInfo("#########################################");
		debugInfo("#########################################");

		Block block = event.getBlock();
		debugInfo("place-material: " + getMaterialNameFromBlock(block));

		RegionsToProcess regionsToProcess = getRegionsToProcess(event.getBlock().getWorld().getName(),
				event.getBlock().getX(), event.getBlock().getZ());

		for (String priorityRegion : regionsToProcess.getPriorityRegions()) {
			if (setRebreakMeta(block, priorityRegion)) {
				return;
			}
		}

		for (String defaultRegion : regionsToProcess.getDefaultRegions()) {
			if (setRebreakMeta(block, defaultRegion)) {
				return;
			}
		}

	}

	protected boolean setRebreakMeta(Block block, String region) {
		String materialName = getMaterialNameFromBlock(block);
		Key materialRoot = new Key(region + "-" + Config.BLOCKS_ROOT).__(materialName);
		if (!keyIsEnabled(materialRoot)) {
			debugInfo(" - material not enabled - " + region);
			return false;
		}
		MetadataValue mdv = new FixedMetadataValue(plugin, true);
		block.setMetadata(REBREAK_KEY, mdv);
		return true;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void onBlockBreakEvent(BlockBreakEvent event) {
		if (event.getBlock() == null) {
			return;
		}

		RegionsToProcess regionsToProcess = getRegionsToProcess(event.getBlock().getWorld().getName(),
				event.getBlock().getX(), event.getBlock().getZ());

		boolean doNotProcessDefaults = false;

		for (String priorityRegion : regionsToProcess.getPriorityRegions()) {
			Key regionsRoot = new Key(Config.REGIONS_ROOT).__(priorityRegion);

			if (!doNotProcessDefaults && getConfig().contains(regionsRoot.end(Config.IGNORE_DEFAULT_BLOCKS))) {
				doNotProcessDefaults = getConfig().getBoolean(regionsRoot.end(Config.IGNORE_DEFAULT_BLOCKS));
			}

			// tail recursion
			boolean doLoop = true;
			Integer processVariation = null;
			Integer lastProcessVariation = null;
			while (doLoop) {
				processVariation = processBlockBreakForRegion(priorityRegion, event, processVariation);
				doLoop = processVariation != null
						&& (lastProcessVariation == null || processVariation > lastProcessVariation);
				lastProcessVariation = processVariation;
			}
		}

		if (!doNotProcessDefaults) {
			for (String defaultRegion : regionsToProcess.getDefaultRegions()) {
				// tail recursion
				boolean doLoop = true;
				Integer processVariation = null;
				Integer lastProcessVariation = null;
				while (doLoop) {
					processVariation = processBlockBreakForRegion(defaultRegion, event, processVariation);
					doLoop = processVariation != null
							&& (lastProcessVariation == null || processVariation > lastProcessVariation);
					lastProcessVariation = processVariation;
				}
			}
		}
	}

	protected Integer processBlockBreakForRegion(String regionName, BlockBreakEvent event, Integer variation) {
		debugInfo("#########################################");
		debugInfo("#########################################");
		debugInfo("REGION: " + regionName);

		Block block = event.getBlock();

		String originalMaterialName = getMaterialNameFromBlock(block);
		String materialName = originalMaterialName;

		debugInfo("break-material: " + materialName);
		if (variation != null) {
			materialName = getVariationKey(materialName, variation);
			debugInfo("variation: " + materialName);
			variation++;
		} else {
			variation = 1;
		}

		Key materialRoot = new Key(regionName + "-" + Config.BLOCKS_ROOT).__(materialName);

		if (!keyIsEnabled(materialRoot)) {
			debugInfo(" - material not enabled");

			Key nextMaterialKey = new Key(regionName + "-" + Config.BLOCKS_ROOT)
					.__(getVariationKey(originalMaterialName, variation));
			if (keyExists(nextMaterialKey)) {
				return variation;
			}

			return null;
		}

		processDropsForBlockBreakForRegion(event, materialName, block, materialRoot);

		processSpellsForRegion(event, materialRoot, block);

		return variation;
	}

	private void processSpellsForRegion(BlockBreakEvent event, Key parent, Block block) {
		double materialSpellChance = getConfig().getDouble(parent.end(Config.SPELL_CHANCE),
				dropsyPlugin.getDropChance());
		boolean allowRebreak = isSpellAllowRebreak(parent);
		boolean destroyOnBreak = getConfig().getBoolean(parent.end(Config.SPELL_DESTROY_ON_BREAK),
				dropsyPlugin.isDestroyOnBreak());
		
		String logType = block != null ? "break: " : "drop";
		

		List<String> spellsFromRandomDistribution = null;

		if (getConfig().contains(parent.end(Config.SPELLS_DISTRIBUTION), true)) {
			List<String> spellsDistribution = getConfig().getStringList(parent.end(Config.SPELLS_DISTRIBUTION));
			spellsFromRandomDistribution = DropsyPlugin.getRandomCsvStringListFromDistribution(spellsDistribution);

			if (spellsFromRandomDistribution == null || spellsFromRandomDistribution.isEmpty()) {
				warning(logType + "-material: " + parent.getVal()
						+ " - Unexpected issue getting the random drops distribution list!");
			} else {
				debugInfo("  - Random drops distributution: " + spellsFromRandomDistribution);
			}
		}

		if (block != null && !allowRebreak && isRebreak(block)) {
			debugInfo(" - rebreak detected");
			return;
		}

		Key spellsRoot = new Key(parent.end(Config.SPELLS_ROOT));

		ConfigurationSection dropsConfigurationSection = getConfig().getConfigurationSection(spellsRoot.getVal());
		if (dropsConfigurationSection == null) {
			debugInfo(logType + "-material: " + parent.getVal() + " - SPELLS SECTION NOT DEFINED");
			return;
		}

		Set<String> drops = dropsConfigurationSection.getKeys(false);

		if (drops == null || drops.isEmpty()) {
			debugInfo(logType + "-material: " + parent.getVal() + " - NO SPELLS DEFINED");
			return;
		}

		boolean somethingWasCast = false;

		for (String spellKey : drops) {

			boolean somethingWasCastTemp = spell(block != null ? block.getLocation() : event.getPlayer().getLocation(), spellsRoot, spellKey, parent,
					spellsFromRandomDistribution, event.getPlayer(), materialSpellChance);
			if (somethingWasCastTemp && !somethingWasCast) {
				somethingWasCast = true;
			}
		}
		
		if (!somethingWasCast) {
			debugInfo("  - no spell");
		}else if (block != null && destroyOnBreak && !event.isCancelled()) {
			debugInfo("  - destroying block");
			event.setCancelled(true);
			block.setType(Material.AIR);
		}
		return;
	}

	private void processDropsForBlockBreakForRegion(BlockBreakEvent event, String materialName, Block block,
			Key materialRoot) {
		double materialDropChance = getConfig().getDouble(materialRoot.end(Config.DROP_CHANCE),
				dropsyPlugin.getDropChance());

		boolean allowRebreak = isAllowRebreak(materialRoot);
		boolean destroyOnBreak = getConfig().getBoolean(materialRoot.end(Config.DESTROY_ON_BREAK),
				dropsyPlugin.isDestroyOnBreak());
		
		String logType = block != null ? "break" : "drop";
		
		warning(logType + "-material: " + materialName);

		List<String> dropsFromRandomDistribution = null;

		if (getConfig().contains(materialRoot.end(Config.DROPS_DISTRIBUTION), true)) {
			List<String> dropsDistribution = getConfig().getStringList(materialRoot.end(Config.DROPS_DISTRIBUTION));
			dropsFromRandomDistribution = DropsyPlugin.getRandomCsvStringListFromDistribution(dropsDistribution);

			if (dropsFromRandomDistribution == null || dropsFromRandomDistribution.isEmpty()) {
				warning(logType + "-material: " + materialName
						+ " - Unexpected issue getting the random drops distribution list!");
			} else {
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
			debugInfo("break-material: " + materialName + " - DROPS SECTION NOT DEFINED");
			return;
		}

		Set<String> drops = dropsConfigurationSection.getKeys(false);

		if (drops == null || drops.isEmpty()) {
			debugInfo("break-material: " + materialName + " - NO DROPS DEFINED");
			return;
		}

		boolean somethingDropped = false;

		for (String dropKey : drops) {

			boolean somethingDroppedTemp = drop(block.getLocation(), dropsRoot, dropKey, materialRoot,
					dropsFromRandomDistribution, event.getPlayer(), materialDropChance);
			
			if(somethingDropped){
				Key dropRoot = new Key(dropsRoot.end(dropKey));
				processSpellsForRegion(event, dropRoot, null);
			}
			
			
			if (somethingDroppedTemp && !somethingDropped) {
				somethingDropped = true;
			}
		}

		if (!somethingDropped) {
			debugInfo("  - nothing dropped");
			return;
		}

		if (destroyOnBreak && !event.isCancelled()) {
			debugInfo("  - destroying block");
			event.setCancelled(true);
			block.setType(Material.AIR);
		}

		if (dropsyPlugin.getDropSound() != null) {
			block.getWorld().playSound(block.getLocation(), dropsyPlugin.getDropSound(), 1f, 1f);
		}
	}
}
