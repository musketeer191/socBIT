package defs;

import java.util.Optional;

public class Errors {
	public double topicUser;
	public double topicItem;
	
	public Double brandUser;
	public Double brandItem;
	public Double decisionPrefs;
	
	public Errors(double topicUser, double topicItem, Optional<Double> maybe_brandUser, Optional<Double> maybe_brandItem,
					Optional<Double> maybe_decisionPrefs) {
		
		this.topicUser = topicUser;
		this.topicItem = topicItem;
		this.brandUser = maybe_brandUser.orElse(Double.NaN);
		this.brandItem = maybe_brandItem.orElse(Double.NaN);
		this.decisionPrefs = maybe_decisionPrefs.orElse(Double.NaN);
	}

	@Override
	public String toString() {
		return  topicUser + ", " + topicItem + ", " + brandUser + ", " + brandItem + ", " + decisionPrefs ;
	}

	
	
//	public Errors(double topicUser, double topicItem) {
//		
//		this.topicUser = topicUser;
//		this.topicItem = topicItem;
//		
//		brandUser = Optional.empty();
//		brandItem = Optional.empty();
//		decisionPrefs = Optional.empty();
//	}
}
