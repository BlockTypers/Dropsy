package com.blocktyper.dropsy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.ResourceBundle;

import org.bukkit.Sound;

import com.blocktyper.v1_2_3.BlockTyperBasePlugin;
import com.blocktyper.v1_2_3.recipes.IRecipe;

public class DropsyPlugin extends BlockTyperBasePlugin implements RandomIntGenerator {

	public static String NBT_RECIPE_KEY = "DropsyPluginNBTRecipeKey";
	public static final String RESOURCE_NAME = "com.blocktyper.dropsy.resources.DropsyMessages";

	public static final String GOOEY_INVIS_KEY = "#GOOEY_";

	private Sound dropSound = null;
	private double dropChance = 0;
	private boolean allowRebreak = false;
	private boolean destroyOnBreak = false;

	private Integer amount = null;
	private String amountRange = null;
	private List<String> amountDistribution = null;

	private Integer exp = null;
	private String expRange = null;
	private List<String> expDistribution = null;

	public DropsyPlugin() {
		super();
	}

	public void onEnable() {
		super.onEnable();

		registerListener(new DropsyListener(this));
		registerCommand("dropsy", new DropsyCommand(this));

		loadAllSettings();
	}

	// region PUBLIC ACCESSORS
	public int getAmount() {
		return getIntFromSources(Config.AMOUNT, amount, amountRange, amountDistribution, null, 1);
	}

	public int getExp() {
		return getIntFromSources(Config.EXP, exp, expRange, expDistribution, null, 0);
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
	// endregion

	// region IBlockTyperPlugin METHODS
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
	// endregion

	// region PRIVATE HELPERS
	private void loadAllSettings() {
		dropChance = getConfig().getDouble(Config.DROP_CHANCE, 0.0);
		allowRebreak = getConfig().getBoolean(Config.ALLOW_REBREAK, false);
		destroyOnBreak = getConfig().getBoolean(Config.DESTROY_ON_BREAK, false);

		loadAmountSettings();

		loadExperienceSettings();

		if (getConfig().contains(Config.DROP_SOUND)) {
			String dropSoundString = getConfig().getString(Config.DROP_SOUND, null);
			if (dropSoundString != null) {
				dropSound = Sound.valueOf(dropSoundString);
			}
		}
	}

	private void loadAmountSettings() {
		if (getConfig().contains(Config.AMOUNT, true)) {
			amount = getConfig().getInt(Config.AMOUNT);
		}

		if (getConfig().contains(Config.AMOUNT_RANGE, true)) {
			amountRange = getConfig().getString(Config.AMOUNT_RANGE, null);
		}

		if (getConfig().contains(Config.AMOUNT_DISTRIBUTION, true)) {
			amountDistribution = getConfig().getStringList(Config.AMOUNT_DISTRIBUTION);
		}
	}

	private void loadExperienceSettings() {
		if (getConfig().contains(Config.EXP, true)) {
			exp = getConfig().getInt(Config.EXP);
		}

		if (getConfig().contains(Config.EXP_RANGE, true)) {
			expRange = getConfig().getString(Config.EXP_RANGE, null);
		}

		if (getConfig().contains(Config.EXP_DISTRIBUTION, true)) {
			expDistribution = getConfig().getStringList(Config.EXP_DISTRIBUTION);
		}
	}
	// endregion

	// region STATIC HELPERS
	public static int getIntFromSources(String sourceKey, Integer basicValue, String rangeExpression,
			List<String> distribution, RandomIntGenerator defaultGenerator, int hardDefault) {

		if (basicValue != null) {
			return basicValue;
		} else if (rangeExpression != null) {
			Integer returnVal = getRangedInt(rangeExpression);
			if (returnVal != null) {
				return returnVal;
			}
		} else if (distribution != null && !distribution.isEmpty()) {
			Integer returnVal = getRandomIntFromDistribution(distribution);
			if (returnVal != null) {
				return returnVal;
			}
		}

		return defaultGenerator != null ? defaultGenerator.getRandomInt(sourceKey) : hardDefault;
	}

	public static Integer getRangedInt(String rangeExpression) {
		Integer returnValue = null;

		if (rangeExpression != null && rangeExpression.contains("~")) {
			String lowString = rangeExpression.substring(0, rangeExpression.indexOf("~"));
			String highString = rangeExpression.substring(rangeExpression.indexOf("~") + 1);

			int low = Integer.parseInt(lowString);
			int high = Integer.parseInt(highString);

			returnValue = low + (new Random().nextInt((high - (low - 1))));
		}

		return returnValue;
	}

	public static Integer getRandomIntFromDistribution(List<String> distribution) {
		Integer returnValue = null;

		String stringValue = getRandomStringFromDistribution(distribution);

		if (stringValue != null) {
			returnValue = Integer.parseInt(stringValue);
		}

		return returnValue;
	}

	public static List<String> getRandomCsvStringListFromDistribution(List<String> distribution) {
		List<String> returnValue = null;

		String stringValue = getRandomStringFromDistribution(distribution);

		if (stringValue != null) {
			returnValue = Arrays.asList(stringValue.split("\\s*,\\s*"));
		}

		return returnValue;
	}

	public static String getRandomStringFromDistribution(List<String> distribution) {
		String returnValue = null;

		Map<Integer, String> distributionMap = new HashMap<>();
		int placeHolder = 0;
		if (distribution != null && !distribution.isEmpty()) {
			for (String distributionExpression : distribution) {
				String valueString = distributionExpression.substring(0, distributionExpression.indexOf("("));
				String percentageString = distributionExpression.substring(distributionExpression.indexOf("(") + 1,
						distributionExpression.lastIndexOf(")"));
				int percentage = Integer.parseInt(percentageString);
				for (int index = 0; index < percentage; index++) {
					distributionMap.put(placeHolder, valueString);
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
	// endregion

	@Override
	public Integer getRandomInt(String source) {
		if (source.equals(Config.AMOUNT)) {
			return getAmount();
		} else if (source.equals(Config.EXP)) {
			return getExp();
		}
		return null;
	}

}
