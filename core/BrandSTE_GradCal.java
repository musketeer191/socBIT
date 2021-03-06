package core;

import helpers.UtilFuncs;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import defs.Params;
import defs.SocBIT_Params;

class BrandSTE_GradCal extends STE_GradCal {

	
	public BrandSTE_GradCal(Trainer trainer) {
		super(trainer);
		calculator = new BrandSTE_Cal(ds, hypers);
	}

	@Override
	Params calculate(Params params) {
		
		SocBIT_Params grad = new SocBIT_Params(ds.numUser, ds.numItem, ds.numBrand, numTopic);
		SocBIT_Params castParams = (SocBIT_Params) params;
		estimated_ratings = calculator.estRatings(params);
		rating_errors = calculator.calRatingErrors(params);
		
		// gradients for user feats
		for (int u = 0; u < ds.numUser; u++) {
			RealVector userTopicGrad = calUserTopicGrad(params, u);
			grad.topicUser.setColumnVector(u, userTopicGrad);
			RealVector userBrandGrad = calUserBrandGrad(castParams, u);
			grad.brandUser.setColumnVector(u, userBrandGrad);
			
			grad.userDecisionPrefs[u] = userDecPrefDiff(castParams, u);
		}
		// gradients for item feats
		for (int i = 0; i < ds.numItem; i++) {
			grad.topicItem.setColumnVector(i, calItemTopicGrad(params, i));
			RealVector itemBrandGrad = calItemBrandGrad(castParams, i);
			grad.brandItem.setColumnVector(i, itemBrandGrad);
		}
		return grad;
	}
	
	@Override
	RealVector calItemTopicGrad(Params params, int itemIndex) {
		RealVector itemTopicFeats = params.topicItem.getColumnVector(itemIndex);
		RealVector itemTopicGrad = itemTopicFeats.mapMultiply(hypers.topicLambda);
		
		RealVector sum = new ArrayRealVector(numTopic);
		for (int u = 0; u < ds.numUser; u++) {
			double rate_err = rating_errors.getEntry(u, itemIndex);
			if (rate_err != 0) {
				double logisDiff = UtilFuncs.logisDiff(estimated_ratings.getEntry(u, itemIndex));
				RealVector userTopicFeats = params.topicUser.getColumnVector(u);
				RealVector comboTopicFeat = comboTopicFeat(userTopicFeats, u, params);
				
				RealVector correctionByUser = comboTopicFeat.mapMultiply(rate_err).mapMultiply(logisDiff);
				SocBIT_Params castParams = (SocBIT_Params) params;
				double decPref = castParams.userDecisionPrefs[u];
				sum = sum.add(correctionByUser.mapMultiply(decPref));
			}
		}
		
		itemTopicGrad = itemTopicGrad.add(sum);
		return itemTopicGrad;
	}
	
	private RealVector calItemBrandGrad(SocBIT_Params params, int i) {
		
		RealVector itemBrandFeats = params.brandItem.getColumnVector(i);
		RealVector itemBrandGrad = itemBrandFeats.mapMultiply(hypers.brandLambda);
		
		RealVector sum = new ArrayRealVector(ds.numBrand);
		for (int u = 0; u < ds.numUser; u++) {
			double rate_err = rating_errors.getEntry(u, i);
			if (rate_err != 0) {
				double logisDiff = UtilFuncs.logisDiff(estimated_ratings.getEntry(u, i));
				RealVector personalBrandFeats = params.brandUser.getColumnVector(u);
				RealVector comboBrandFeat = calComboBrandFeat(personalBrandFeats, u, params);
				RealVector correctionByUser = comboBrandFeat.mapMultiply(rate_err).mapMultiply(logisDiff);
				
				double decPref = params.userDecisionPrefs[u];
				sum = sum.add(correctionByUser.mapMultiply(1 - decPref));
			}
		}
		itemBrandGrad = itemBrandGrad.add(sum);
		return itemBrandGrad;
	}

	private RealVector calComboBrandFeat(RealVector personalBrandFeats, int u, SocBIT_Params params) {
		
		RealVector friendFeats = new ArrayRealVector(ds.numBrand);
		for (int v = 0; v < ds.numUser; v++) {
			double influenceWeight = ds.edge_weights.getEntry(v, u);
			if (influenceWeight > 0) {
				RealVector vFeat = params.brandUser.getColumnVector(v);
				friendFeats = friendFeats.add(vFeat.mapMultiply(influenceWeight));
			}
		}
		RealVector comboFeat = personalBrandFeats.mapMultiply(hypers.alpha);
		comboFeat = comboFeat.add(friendFeats.mapMultiply(1 - hypers.alpha));
		return comboFeat;
	}

	private double userDecPrefDiff(SocBIT_Params params, int u) {
		double userDecisionPref = params.userDecisionPrefs[u];
		double decisionLambda = hypers.decisionLambda;
		double decisionPrefDiff = decisionLambda * (userDecisionPref - 0.5);
		
		
		
		// TODO Auto-generated method stub
		double rating_sum = 0;
		for (int i = 0; i < ds.numItem; i++) {
			double oneRatingErr = rating_errors.getEntry(u, i);
			if (oneRatingErr != 0) {
				double logisDiff = UtilFuncs.logisDiff(estimated_ratings.getEntry(u, i));
				double topicRatingEst = estTopicRating(u, i, params);
				double brandRatingEst = estBrandRating(u, i, params);
				double term = oneRatingErr * logisDiff * (topicRatingEst - brandRatingEst);
				rating_sum += term;
			}
		}
		decisionPrefDiff += rating_sum;
		return decisionPrefDiff;
	}

	private double estBrandRating(int u, int i, SocBIT_Params params) {
		
		RealVector beta_u = params.brandUser.getColumnVector(u);
		RealVector beta_i = params.brandItem.getColumnVector(i);
		double personal = beta_u.dotProduct(beta_i);
		double social = 0;
		for (int v = 0; v < ds.numUser; v++) {
			double influence = ds.edge_weights.getEntry(v, u);
			if (influence > 0) {
				RealVector beta_v = params.brandUser.getColumnVector(v);
				social += influence * beta_v.dotProduct(beta_i);
			}
		}
		
		return alpha*personal + (1 - alpha)*social;
	}

	private double estTopicRating(int u, int i, SocBIT_Params params) {
		
		RealVector theta_u = params.topicUser.getColumnVector(u);
		RealVector theta_i = params.topicItem.getColumnVector(i);
		double personal = theta_u.dotProduct(theta_i);
		double social = 0;
		for (int v = 0; v < ds.numUser; v++) {
			double influence = ds.edge_weights.getEntry(v, u);
			if (influence > 0) {
				RealVector theta_v = params.topicUser.getColumnVector(v);
				social += influence * theta_v.dotProduct(theta_i);
			}
		}
		
		return alpha*personal + (1 - alpha)*social;
	}
	

	@Override
	RealVector calUserTopicGrad(Params params, int u) {
		
		RealVector userTopicFeats = params.topicUser.getColumnVector(u);
		RealVector userTopicGrad = userTopicFeats.mapMultiply(hypers.topicLambda);
		
		SocBIT_Params castParams = (SocBIT_Params) params;
		RealVector personal_part = calTopicPersonalPart(u, castParams);
		RealVector influenceePart = calTopicInfluenceePart(u, castParams);
		
		RealVector sum = personal_part.mapMultiply(alpha).add(influenceePart.mapMultiply(1 - alpha));
		userTopicGrad = userTopicGrad.add(sum); 
		return userTopicGrad;
	}
	
	private RealVector calUserBrandGrad(SocBIT_Params params, int u) {
		
		RealVector userBrandFeats = params.brandUser.getColumnVector(u);
		RealVector userBrandGrad = userBrandFeats.mapMultiply(hypers.brandLambda);
		
		SocBIT_Params castParams = (SocBIT_Params) params;
		RealVector personal_part = calBrandPersonalPart(u, castParams);
		RealVector influenceePart = calBrandInfluenceePart(u, castParams);
		RealVector sum = personal_part.mapMultiply(alpha).add(influenceePart.mapMultiply(1 - alpha));
		userBrandGrad = userBrandGrad.add(sum);
		
		return userBrandGrad;
	}
	
	private RealVector calBrandInfluenceePart(int u, SocBIT_Params params) {
		
		RealVector influenceePart = new ArrayRealVector(ds.numBrand);
		for (int v = 0; v < ds.numUser; v++) {
			double influencedLevel = ds.edge_weights.getEntry(u, v);
			if (influencedLevel > 0) {
				for (int j = 0; j < ds.numItem; j++) {
					double oneRatingErr = rating_errors.getEntry(v, j);
					if (oneRatingErr != 0) {
						RealVector itemBrandFeats = params.brandItem.getColumnVector(j);
						double logisDiff = UtilFuncs.logisDiff(estimated_ratings.getEntry(v, j));
						double vDecPref = params.userDecisionPrefs[v];
						double coef = influencedLevel * oneRatingErr * logisDiff * (1 - vDecPref);
						influenceePart = influenceePart.add(itemBrandFeats.mapMultiply(coef));
					}
				}
			}
		}
		
		return influenceePart;
	}

	protected RealVector calTopicInfluenceePart(int u, SocBIT_Params params) {

		// influencee: those who are influenced by/trust u, thus include u's feat in their rating
		RealVector influenceePart = new ArrayRealVector(numTopic);	
		
		for (int v = 0; v < ds.numUser; v++) {
			double influencedLevel = ds.edge_weights.getEntry(u, v);
			if (influencedLevel > 0) {
				for (int j = 0; j < ds.numItem; j++) {
					double oneRatingErr = rating_errors.getEntry(v, j);
					if (oneRatingErr != 0) {
						RealVector itemTopicFeats = params.topicItem.getColumnVector(j);
						double logisDiff = UtilFuncs.logisDiff(estimated_ratings.getEntry(v, j));
						double vDecPref = params.userDecisionPrefs[v];
						double coef = influencedLevel * oneRatingErr * logisDiff * vDecPref;
						influenceePart = influenceePart.add(itemTopicFeats.mapMultiply(coef));

					}
				}
			}
		}
		return influenceePart;
	}
	
	private RealVector calBrandPersonalPart(int u, SocBIT_Params params) {
		
		RealVector personal_part = new ArrayRealVector(ds.numBrand);
		double personalDecPref = params.userDecisionPrefs[u];
		
		for (int i = 0; i < ds.numItem; i++) {
			double oneRatingErr = rating_errors.getEntry(u, i);
			if (oneRatingErr != 0) {
				RealVector itemBrandFeats = params.brandItem.getColumnVector(i);
				double logisDiff = UtilFuncs.logisDiff(estimated_ratings.getEntry(u, i));
				
				double coef = (1 - personalDecPref) * oneRatingErr * logisDiff;
				personal_part = personal_part.add(itemBrandFeats.mapMultiply(coef));
			}
		}
		
		return personal_part;
	}

	protected RealVector calTopicPersonalPart(int u, SocBIT_Params params) {
		
		RealVector personal_part = new ArrayRealVector(numTopic);
		double personalDecPref = params.userDecisionPrefs[u];
		
		for (int i = 0; i < ds.numItem; i++) {
			double oneRatingErr = rating_errors.getEntry(u, i);
			if (oneRatingErr != 0) {
				RealVector itemTopicFeats = params.topicItem.getColumnVector(i);
				double logisDiff = UtilFuncs.logisDiff(estimated_ratings.getEntry(u, i));
				
				double coef = oneRatingErr*logisDiff*personalDecPref;
				personal_part = personal_part.add(itemTopicFeats.mapMultiply(coef));
			}
		}
		return personal_part;
	}
	
	
}
