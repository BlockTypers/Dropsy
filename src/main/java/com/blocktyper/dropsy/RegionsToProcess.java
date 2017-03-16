package com.blocktyper.dropsy;

import java.util.List;

public class RegionsToProcess {
	private List<String> defaultRegions;
	private List<String> priorityRegions;

	public List<String> getDefaultRegions() {
		return defaultRegions;
	}

	public void setDefaultRegions(List<String> defaultRegions) {
		this.defaultRegions = defaultRegions;
	}

	public List<String> getPriorityRegions() {
		return priorityRegions;
	}

	public void setPriorityRegions(List<String> priorityRegions) {
		this.priorityRegions = priorityRegions;
	}
}
