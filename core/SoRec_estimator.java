package core;

import org.apache.commons.math3.linear.RealMatrix;

class SoRec_estimator {
	
	private Params params;
	
	RealMatrix estRatings() {
		RealMatrix estRatings = params.topicUser.transpose().multiply(params.topicItem);
		return estRatings;
	}
	
//	RealMatrix soRecEdgeWeights() {
//		
//	}
}