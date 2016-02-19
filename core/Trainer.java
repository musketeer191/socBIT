package core;

import java.io.IOException;

import myUtil.Savers;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import defs.Dataset;
import defs.Hypers;
import defs.InvalidModelException;
import defs.ParamModelMismatchException;

public class Trainer {
	
	private static final double EPSILON = 1;
	private static final double ALPHA = 0.5;
	private static final double GAMMA = Math.pow(10, -4);
	private static final double EPSILON_STEP = Math.pow(10, -10);
	
	Dataset ds;
	
	// settings of this trainer
	String model;
	int numTopic;
	Hypers hypers;
	private int maxIter;
	private double stepSize;
	
	public Trainer(Dataset ds, int numTopic, Hypers hypers, int maxIter) {
		this.ds = ds;
		this.numTopic = numTopic;
		this.hypers = hypers;
		this.maxIter = maxIter;
		stepSize = 1/ALPHA;
	}
	
	/**
	 * @param initParams
	 * @param resDir
	 * @return local optimal parameters which give a local minimum of the objective function (minimum errors + regularizers)
	 * @throws IOException 
	 * @throws InvalidModelException 
	 */
	SocBIT_Params gradDescent(SocBIT_Params initParams, String resDir) throws IOException, InvalidModelException {
		
		printStartMsg();
		int numIter = 0;
//		StringBuilder sbParams = new StringBuilder("iter, ...");
		StringBuilder sbObjValue = new StringBuilder("iter, obj_value \n");

		
		SocBIT_Params cParams = new SocBIT_Params(initParams);
		double cValue = objValue(initParams);
		sbObjValue = sbObjValue.append(numIter + "," + cValue + "\n");
		System.out.println(numIter + ", " + cValue);
		double difference = Double.POSITIVE_INFINITY;
		
		GradCal gradCal = new GradCal(this);
		// while not convergence and still can try more
		while ( isLarge(difference) && (numIter < maxIter) ) {
			numIter ++;
			Params cGrad = gradCal.calculate(cParams, this.model);
			SocBIT_Params nParams = lineSearch(cParams, cGrad, cValue);
			double nValue = objValue(nParams);
			sbObjValue = sbObjValue.append(numIter + "," + nValue + "\n");
			difference = nValue - cValue;
			
			// prep for next iter
			cParams = new SocBIT_Params(nParams);						
			cValue = nValue;
			System.out.println(numIter + "," + cValue);
		}
		
		if (!isLarge(difference)) {
			printConvergeMsg();
			String fout = resDir + "obj_values.csv";
			Savers.save(sbObjValue.toString(), fout);
		} else {
			System.out.println("Not converged yet but already exceeded the maximum number of iterations. Training stopped!!!");
		}
		
		return cParams;
	}

	private void printStartMsg() {
		System.out.println("Start training...");
		System.out.println("Iter, Objective value");
	}

	private void printConvergeMsg() {
		System.out.println("Converged to a local minimum :)");
		System.out.println("Training done.");
		System.out.println();
	}

	private boolean isLarge(double difference) {
		return Math.abs(difference) > EPSILON;
	}
	
	private SocBIT_Params lineSearch(Params cParams, Params cGrad, double cValue) {
		
		Params nParams = buildParams(cParams, this.model);
		boolean sufficentReduction = false;
		
		while (!sufficentReduction && (stepSize > EPSILON_STEP)) {
			stepSize = stepSize * ALPHA;
			nParams = update(cParams, stepSize, cGrad);
			// todo: may need some projection here to guarantee some constraints
			double funcDiff = objValue(nParams) - cValue;
			double sqDiff = sqDiff(nParams, cParams);
			double reduction = - GAMMA/stepSize * sqDiff;
			sufficentReduction = (funcDiff < reduction);
			
			if (funcDiff == 0) {
				System.out.println("Meet a local minimum !!!");
				return nParams;
			}
		}
		
		if (sufficentReduction) {
//			System.out.println("Found new params with sufficient reduction");
			return nParams;
		} else {
			System.out.println("Cannot find new params with sufficient reduction. "
					+ "Line search stopped due to step size too small");
			
			return cParams;
		}
	}

	private Params buildParams(Params cParams, String model) throws ParamModelMismatchException, InvalidModelException {
		
		if (model.equalsIgnoreCase("STE")) {
			return cParams;
		} 
		else {
			if (model.equalsIgnoreCase("socBIT")) {
				if (cParams instanceof SocBIT_Params) {
					return (SocBIT_Params) cParams;
				} 
				else {
					String msg = "Input params type and model mismatch!!!";
					throw new ParamModelMismatchException(msg);
				}
			} 
			else {
				throw new InvalidModelException();
			}
		}
		
	}

	private Params update(Params cParams, double stepSize, SocBIT_Params cGrad) throws ParamModelMismatchException, InvalidModelException {
		
		Params nParams = buildParams(cParams, model);
		
		updateUserComponents(cParams, nParams, cGrad, stepSize);
		updateItemComponents(cParams, nParams, cGrad, stepSize);
		
		return nParams;
	}

	private void updateItemComponents(Params cParams, Params nParams, Params cGrad, double stepSize) {
		
		if (model.equalsIgnoreCase("socBIT")) {//cParams instanceof SocBIT_Params && nParams instanceof SocBIT_Params
			updateItemParamsBySocBIT( (SocBIT_Params) cParams, (SocBIT_Params) nParams, (SocBIT_Params) cGrad, stepSize);
		} 
		else {
			if (model.equalsIgnoreCase("STE")) {
				updateItemParamsBySTE(cParams, nParams, cGrad, stepSize);
			}
		}
	}

	private void updateItemParamsBySTE(Params cParams, Params nParams, Params cGrad, double stepSize) {
		
		for (int i = 0; i < ds.numItem; i++) {
			RealVector curTopicFeat = cParams.topicItem.getColumnVector(i);
			RealVector topicDescent = cGrad.topicItem.getColumnVector(i).mapMultiply(-stepSize);
			RealVector nextTopicFeat = curTopicFeat.add(topicDescent);
			nParams.topicItem.setColumnVector(i, nextTopicFeat);
		}
	}

	private void updateItemParamsBySocBIT(SocBIT_Params cParams, SocBIT_Params nParams, SocBIT_Params cGrad, double stepSize) {
		
		for (int i = 0; i < ds.numItem; i++) {
			// topic component
			RealVector curTopicFeat = cParams.topicItem.getColumnVector(i);
			RealVector topicDescent = cGrad.topicItem.getColumnVector(i).mapMultiply(-stepSize);
			RealVector nextTopicFeat = curTopicFeat.add(topicDescent);
			nParams.topicItem.setColumnVector(i, nextTopicFeat);
			// brand component
			RealVector curBrandFeat = cParams.brandItem.getColumnVector(i);
			RealVector brandDescent = cGrad.brandItem.getColumnVector(i).mapMultiply(-stepSize);
			RealVector nextBrandFeat = curBrandFeat.add(brandDescent);
			nParams.brandItem.setColumnVector(i, nextBrandFeat);
		}
	}

	private void updateUserComponents(Params cParams, Params nParams, Params cGrad, double stepSize) {
		
		if (model.equalsIgnoreCase("socBIT")) {
			updateUserParamsBySocBIT((SocBIT_Params) cParams, (SocBIT_Params) nParams, (SocBIT_Params) cGrad, stepSize);
		} 
		else {
			if (model.equalsIgnoreCase("STE")) {
				updateUserParamsBySTE(cParams, nParams, cGrad, stepSize);
			}
		}
	}

	private void updateUserParamsBySTE(Params cParams, Params nParams, Params cGrad, double stepSize) {
		
		for (int u = 0; u < ds.numUser; u++) {
			
			RealVector curTopicFeat = cParams.topicUser.getColumnVector(u);
			RealVector topicDescent = cGrad.topicUser.getColumnVector(u).mapMultiply( -stepSize);
			RealVector nextTopicFeat = curTopicFeat.add(topicDescent);
			nParams.topicUser.setColumnVector(u, nextTopicFeat);
		}
	}

	private void updateUserParamsBySocBIT(SocBIT_Params cParams,
			SocBIT_Params nParams, SocBIT_Params cGrad, double stepSize) {
		
		for (int u = 0; u < ds.numUser; u++) {
			// user decision pref
			nParams.userDecisionPrefs[u] = cParams.userDecisionPrefs[u] - stepSize * cGrad.userDecisionPrefs[u];
			// topic component
			RealVector curTopicFeat = cParams.topicUser.getColumnVector(u);
			RealVector topicDescent = cGrad.topicUser.getColumnVector(u).mapMultiply( -stepSize);
			RealVector nextTopicFeat = curTopicFeat.add(topicDescent);
			nParams.topicUser.setColumnVector(u, nextTopicFeat);
			// brand component
			RealVector curBrandFeat = cParams.brandUser.getColumnVector(u);
			RealVector brandDescent = cGrad.brandUser.getColumnVector(u).mapMultiply(-stepSize);
			RealVector nextBrandFeat = curBrandFeat.add(brandDescent);
			nParams.brandUser.setColumnVector(u, nextBrandFeat);
		}
	}

	private double objValue(SocBIT_Params params) {
		
		SocBIT_Estimator socBIT_Estimator = new SocBIT_Estimator(params);
		RealMatrix estimated_ratings = socBIT_Estimator.estRatings();
		RealMatrix estimated_weights = socBIT_Estimator.estWeights();
		
		RealMatrix edge_weight_errors = ErrorCal.edgeWeightErrors(estimated_weights, ds.edge_weights);
		RealMatrix rating_errors = ErrorCal.ratingErrors(estimated_ratings, ds.ratings);
		
		double val = square(rating_errors.getFrobeniusNorm());
		val += hypers.weightLambda * square(edge_weight_errors.getFrobeniusNorm());
		val += hypers.topicLambda * ( square(params.topicUser.getFrobeniusNorm()) + square(params.topicItem.getFrobeniusNorm()) );
		val += hypers.brandLambda * ( square(params.brandUser.getFrobeniusNorm()) + square(params.brandItem.getFrobeniusNorm()) );
		for (int u = 0; u < ds.numUser; u++) {
			val += hypers.decisionLambda * square(params.userDecisionPrefs[u] - 0.5);
		}
		
		return val;
	}
	
	private double sqDiff(SocBIT_Params p1, SocBIT_Params p2) {

		double sq_diff = square(p1.topicUser.subtract(p2.topicUser).getFrobeniusNorm());
		sq_diff += square(p1.topicItem.subtract(p2.topicItem).getFrobeniusNorm());
		sq_diff += square(p1.brandUser.subtract(p2.brandUser).getFrobeniusNorm());
		sq_diff += square(p1.brandItem.subtract(p2.brandItem).getFrobeniusNorm());
		for (int u = 0; u < ds.numUser; u++) {
			sq_diff += square(p1.userDecisionPrefs[u] - p2.userDecisionPrefs[u]);
		}

		return sq_diff;
	}
	
	private double square(double d) {
		return Math.pow(d, 2);
	}
}