package com.blocktyper.dropsy;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.ResourceBundle;

import org.bukkit.Sound;

import com.blocktyper.v1_2_3.BlockTyperBasePlugin;
import com.blocktyper.v1_2_3.recipes.IRecipe;

public class DropsyPlugin extends BlockTyperBasePlugin {
	
	public static String NBT_RECIPE_KEY = "DropsyPluginNBTRecipeKey";
	public static final String RESOURCE_NAME = "com.blocktyper.dropsy.resources.DropsyMessages";

	public static final String GOOEY_INVIS_KEY = "#GOOEY_";

	public static final String BLOCKS_ROOT = "blocks";

	public static final String DROPS_ROOT = "drops";

	public static final String ENABLED = "enabled";

	public static final String DROP_SOUND = "drop-sound";
	public static final String ALLOW_REBREAK = "allow-rebreak";
	public static final String DESTROY_ON_BREAK = "destroy-on-break";
	public static final String DROP_CHANCE = "drop-chance-percent";
	public static final String AMOUNT = "amount";
	public static final String AMOUNT_RANGE = "amount-range";
	public static final String AMOUNT_DISTRIBUTION = "amount-chance-percent";
	
	public static final String REQUIRED_ITEM = "required-item";

	public static final String REBREAK_KEY = "DROPSY_REBREAK";

	private Sound dropSound = null;
	private double dropChance = 0;
	private boolean allowRebreak = false;
	private boolean destroyOnBreak = false;

	private Integer amount = null;
	private String amountRange = null;
	private List<String> amountDistribution = null;

	public DropsyPlugin() {
		super();
	}

	public void onEnable() {
		super.onEnable();

		saveDefaultConfig();
		getConfig().options().copyDefaults(true);
		saveConfig();
		reloadConfig();

		registerListener(new DropsyListener(this));

		dropChance = getConfig().getDouble(DROP_CHANCE, 0.0);
		allowRebreak = getConfig().getBoolean(ALLOW_REBREAK, false);
		destroyOnBreak = getConfig().getBoolean(DESTROY_ON_BREAK, false);

		if (getConfig().contains(AMOUNT)) {
			amount = getConfig().getInt(AMOUNT);
		}

		amountRange = getConfig().getString(AMOUNT_RANGE, null);

		if (getConfig().contains(AMOUNT_DISTRIBUTION)) {
			amountDistribution = getConfig().getStringList(AMOUNT_DISTRIBUTION);
		}
		
		if (getConfig().contains(DROP_SOUND)) {
			String dropSoundString = getConfig().getString(DROP_SOUND, null);
			if(dropSoundString != null){
				dropSound = Sound.valueOf(dropSoundString);
			}
		}
	}

	public int getAmount() {
		return getIntFromSources(amount, amountRange, amountDistribution, 1);
	}

	public double getDropChance() {
		return dropChance;
	}

	public boolean isAllowRebreak() {
		return allowRebreak;
	}

	public boolean isDestroyOnBreak() {
		return destroyOnBreak;
	}
	
	

	public Sound getDropSound() {
		return dropSound;
	}

	public static int getIntFromSources(Integer basicValue, String rangeExpression, List<String> distribution,
			int defaultReturn) {

		if (basicValue != null) {
			return basicValue;
		} else if (rangeExpression != null) {
			Integer returnVal = getRangedInt(rangeExpression);
			if (returnVal != null) {
				return returnVal;
			}
		} else if (distribution != null && distribution.isEmpty()) {
			Integer returnVal = getRandomIntFromDistribution(distribution);
			if (returnVal != null) {
				return returnVal;
			}
		}

		return defaultReturn;
	}

	public static Integer getRangedInt(String rangeExpression) {
		Integer returnValue = null;

		if (rangeExpression != null && rangeExpression.contains("~")) {
			String lowString = rangeExpression.substring(0, rangeExpression.indexOf("~"));
			String highString = rangeExpression.substring(rangeExpression.indexOf("~") + 1);

			int low = Integer.parseInt(lowString);
			int high = Integer.parseInt(highString);

			int returnInt = new Random().nextInt(low + high);
			returnInt = returnInt + low;
		}

		return returnValue;
	}

	public static Integer getRandomIntFromDistribution(List<String> distribution) {
		Integer returnValue = null;

		Map<Integer, Integer> distributionMap = new HashMap<>();
		int placeHolder = 0;
		if (distribution != null && !distribution.isEmpty()) {
			for (String distributionExpression : distribution) {
				String valueString = distributionExpression.substring(0, distributionExpression.indexOf("("));
				int value = Integer.parseInt(valueString);
				String percentageString = distributionExpression.substring(distributionExpression.indexOf("(") + 1,
						distributionExpression.lastIndexOf(")"));
				int percentage = Integer.parseInt(percentageString);
				for (int index = 0; index < percentage; index++) {
					distributionMap.put(placeHolder, value);
					placeHolder++;
				}
			}
		}

		if (!distributionMap.isEmpty()) {
			int randomIndex = new Random().nextInt(distributionMap.size());
			returnValue = distributionMap.get(randomIndex);
		}

		return returnValue;
	}

	@Override
	public IRecipe bootstrapRecipe(IRecipe recipe) {
		return recipe;
	}

	@Override
	public String getRecipesNbtKey() {
		return NBT_RECIPE_KEY;
	}

	@Override
	public ResourceBundle getBundle(Locale locale) {
		return ResourceBundle.getBundle(RESOURCE_NAME, locale);
	}

}
