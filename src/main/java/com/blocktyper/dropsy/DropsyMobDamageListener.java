package com.blocktyper.dropsy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import com.blocktyper.v1_2_3.helpers.Key;

public class DropsyMobDamageListener extends DropsyListenerBase {
	
	public static int SPELL_TRIGGER_TYPE_ATTACK = 1;
	public static int SPELL_TRIGGER_TYPE_KILL = 2;
	public static int SPELL_TRIGGER_TYPE_ATTACK_DROP = 3;
	public static int SPELL_TRIGGER_TYPE_KILL_DROP = 4;
	
	public static Map<Integer, String> SPELL_TRIGGER_LOG_MESSAGES;
	
	static{
		SPELL_TRIGGER_LOG_MESSAGES = new HashMap<Integer, String>();
		SPELL_TRIGGER_LOG_MESSAGES.put(SPELL_TRIGGER_TYPE_ATTACK, "OnAttack-Spell");
		SPELL_TRIGGER_LOG_MESSAGES.put(SPELL_TRIGGER_TYPE_KILL, "OnKill-Spell");
		SPELL_TRIGGER_LOG_MESSAGES.put(SPELL_TRIGGER_TYPE_ATTACK_DROP, "OnAttackDrop-Spell");
		SPELL_TRIGGER_LOG_MESSAGES.put(SPELL_TRIGGER_TYPE_KILL_DROP, "OnKillDrop-Spell");
	}

	public DropsyMobDamageListener(DropsyPlugin dropsyPlugin) {
		super(dropsyPlugin);
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
		if (!(event.getDamager() instanceof Player)) {
			return;
		}

		Player player = null;
		if (!(event.getDamager() instanceof Player)) {

			if (event.getDamager() instanceof Projectile) {
				if (((Projectile) event.getDamager()).getShooter() instanceof Player) {
					player = (Player) ((Projectile) event.getDamager()).getShooter();
				}
			}

		} else {
			player = (Player) event.getDamager();
		}

		if (player == null) {
			return;
		}

		if (event.getEntity() == null) {
			return;
		}

		Entity entity = event.getEntity();

		RegionsToProcess regionsToProcess = getRegionsToProcess(entity.getWorld().getName(),
				entity.getLocation().getBlockX(), entity.getLocation().getBlockZ());

		processAttackAllRegions(player, entity, regionsToProcess, false);

		if (entity instanceof Damageable) {
			Damageable damageable = (Damageable) entity;
			if (event.getFinalDamage() >= damageable.getHealth()) {
				boolean isKill = true;
				processAttackAllRegions(player, entity, regionsToProcess, isKill);
			}
		}
	}

	private void processAttackAllRegions(Player player, Entity entity, RegionsToProcess regionsToProcess, boolean isKill) {

		boolean doNotProcessDefaults = false;

		for (String priorityRegion : regionsToProcess.getPriorityRegions()) {
			Key regionsRoot = new Key(Config.REGIONS_ROOT).__(priorityRegion);

			if (!doNotProcessDefaults && getConfig().contains(regionsRoot.end(Config.IGNORE_DEFAULT_MOBS))) {
				doNotProcessDefaults = getConfig().getBoolean(regionsRoot.end(Config.IGNORE_DEFAULT_MOBS));
			}

			// tail recursion
			boolean doLoop = true;
			Integer processVariation = null;
			Integer lastProcessVariation = null;
			while (doLoop) {
				processVariation = processEntityDamageForRegion(priorityRegion, player, entity, processVariation,
						isKill);
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
					processVariation = processEntityDamageForRegion(defaultRegion, player, entity, processVariation,
							isKill);
					doLoop = processVariation != null
							&& (lastProcessVariation == null || processVariation > lastProcessVariation);
					lastProcessVariation = processVariation;
				}
			}
		}
	}

	protected Integer processEntityDamageForRegion(String regionName, Player player, Entity entity, Integer variation,
			boolean isKill) {
		debugInfo("#########################################");
		debugInfo("#########################################");
		debugInfo("REGION: " + regionName);

		String originalEntityName = entity.getName();
		String entityName = originalEntityName;
		
		String logType = "[" + (isKill ? "kill" : "attack")+"-entityName]: ";
		

		debugInfo(logType + entityName);
		if (variation != null) {
			entityName = getVariationKey(entityName, variation);
			debugInfo("  - variation: " + entityName);
			variation++;
		} else {
			variation = 1;
		}

		String mobsRootType = isKill ? Config.MOBS_KILL_ROOT : Config.MOBS_DAMAGE_ROOT;

		Key materialRoot = new Key(regionName + "-" + mobsRootType).__(entityName);

		if (!keyIsEnabled(materialRoot)) {
			debugInfo("  - material not enabled");

			Key nextMaterialKey = new Key(regionName + "-" + mobsRootType)
					.__(getVariationKey(originalEntityName, variation));
			if (keyExists(nextMaterialKey)) {
				return variation;
			}

			return null;
		}

		processSpellsSection(player, entity.getLocation(), materialRoot, isKill ? SPELL_TRIGGER_TYPE_KILL : SPELL_TRIGGER_TYPE_ATTACK);

		processDropsForEntity(player, entity.getLocation(), materialRoot, isKill);

		return variation;
	}

	private void processSpellsSection(Player player, Location location, Key parentRoot, int triggerTypeId) {
		double entitySpellChance = getConfig().getDouble(parentRoot.end(Config.SPELL_CHANCE),
				dropsyPlugin.getSpellChance());

		List<String> spellsFromRandomDistribution = null;
		
		String spellTriggerType = SPELL_TRIGGER_LOG_MESSAGES.containsKey(triggerTypeId) ? SPELL_TRIGGER_LOG_MESSAGES.get(triggerTypeId) : null;
		
		if(spellTriggerType == null){
			warning("Unrecognized Spell Trigger Type: " + triggerTypeId);
			spellTriggerType = "["+triggerTypeId+"]: ";
		}else{
			spellTriggerType = "["+spellTriggerType+"]: ";
		}

		if (getConfig().contains(parentRoot.end(Config.SPELLS_DISTRIBUTION), true)) {
			List<String> spellsDistribution = getConfig().getStringList(parentRoot.end(Config.SPELLS_DISTRIBUTION));
			spellsFromRandomDistribution = DropsyPlugin.getRandomCsvStringListFromDistribution(spellsDistribution);

			if (spellsFromRandomDistribution == null || spellsFromRandomDistribution.isEmpty()) {
				warning(spellTriggerType + parentRoot.getVal()
						+ " - Unexpected issue getting the random drops distribution list!");
			} else {
				debugInfo("  - Random drops distributution: " + spellsFromRandomDistribution);
			}
		}

		Key spellsRoot = new Key(parentRoot.end(Config.SPELLS_ROOT));

		ConfigurationSection spellsConfigurationSection = getConfig().getConfigurationSection(spellsRoot.getVal());
		if (spellsConfigurationSection == null) {
			debugInfo(spellTriggerType + parentRoot.getVal() + " - SPELLS SECTION NOT DEFINED");
			return;
		}

		Set<String> spells = spellsConfigurationSection.getKeys(false);

		if (spells == null || spells.isEmpty()) {
			debugInfo(spellTriggerType + parentRoot.getVal() + " - NO SPELLS DEFINED");
			return;
		}

		boolean somethingWasCast = false;

		for (String spellKey : spells) {

			boolean somethingWasCastTemp = spell(location, spellsRoot, spellKey, parentRoot,
					spellsFromRandomDistribution, player, entitySpellChance, null);
			if (somethingWasCastTemp && !somethingWasCast) {
				somethingWasCast = true;
			}
		}

		if (!somethingWasCast) {
			debugInfo(spellTriggerType + "  - no spell");
			return;
		}
	}

	private void processDropsForEntity(Player player, Location location, Key entityRoot, boolean isKill) {
		double materialDropChance = getConfig().getDouble(entityRoot.end(Config.DROP_CHANCE),
				dropsyPlugin.getDropChance());

		List<String> dropsFromRandomDistribution = null;

		if (getConfig().contains(entityRoot.end(Config.DROPS_DISTRIBUTION), true)) {
			List<String> dropsDistribution = getConfig().getStringList(entityRoot.end(Config.DROPS_DISTRIBUTION));
			dropsFromRandomDistribution = DropsyPlugin.getRandomCsvStringListFromDistribution(dropsDistribution);

			if (dropsFromRandomDistribution == null || dropsFromRandomDistribution.isEmpty()) {
				warning("damage-entity: " + entityRoot.getVal()
						+ " - Unexpected issue getting the random drops distribution list!");
			} else {
				debugInfo("  - Random drops distributution: " + dropsFromRandomDistribution);
			}
		}

		Key dropsRoot = new Key(entityRoot.end(Config.DROPS_ROOT));

		ConfigurationSection dropsConfigurationSection = getConfig().getConfigurationSection(dropsRoot.getVal());
		if (dropsConfigurationSection == null) {
			debugInfo("damage-entity: " + entityRoot.getVal() + " - DROPS SECTION NOT DEFINED");
			return;
		}

		Set<String> drops = dropsConfigurationSection.getKeys(false);

		if (drops == null || drops.isEmpty()) {
			debugInfo("damage-entity: " + entityRoot.getVal() + " - NO DROPS DEFINED");
			return;
		}

		boolean somethingDropped = false;

		for (String dropKey : drops) {

			boolean somethingDroppedTemp = drop(location, dropsRoot, dropKey, entityRoot,
					dropsFromRandomDistribution, player, materialDropChance);
			
			if(somethingDropped){
				Key dropRoot = new Key(dropsRoot.end(dropKey));
				processSpellsSection(player, location, dropRoot, isKill ? SPELL_TRIGGER_TYPE_KILL_DROP : SPELL_TRIGGER_TYPE_ATTACK_DROP);
			}
			
			if (somethingDroppedTemp && !somethingDropped) {
				somethingDropped = true;
			}
		}

		if (!somethingDropped) {
			debugInfo("  - nothing dropped");
			return;
		}

		if (dropsyPlugin.getDropSound() != null) {
			location.getWorld().playSound(location, dropsyPlugin.getDropSound(), 1f, 1f);
		}
	}
}
