package umontreal.ssj.stat.density;

import umontreal.ssj.functionfit.LeastSquares;
import umontreal.ssj.hups.RQMCPointSet;
import umontreal.ssj.mcqmctools.MonteCarloModelDensityKnown;
import umontreal.ssj.mcqmctools.MonteCarloModelDouble;
import umontreal.ssj.mcqmctools.RQMCExperiment;
import umontreal.ssj.stat.Tally;
import umontreal.ssj.util.PrintfFormat;

/**
 * Implements a parametric model that is particularly designed for the estimation
 * of the density of a random variable \f$X = g(\mathbf U)\f$, where
 * \f$\mathbf U = (U_1,U_2,\dots,U_d)\sim U(0,1)^d \f$ with a given function
 * \f$g:(0,1)^d\rightarrow\mathbb{R}\f$ over a finite interval \f$[a,b]\f$. Such situations arise naturally in
 * uncertainty quantification. The observations \f$X_0,X_1,\dots,X_n\f$ which are used to construct the density will 
 * typically be generated by simulation of \f$\mathbf U\f$, either by Monte Carlo (MC)
 * or by randomized quasi-Monte Carlo (RQMC).
 * 
 * The model applies to density estimators that rely on the selection of a bandwidth
 *  (or binwidth) such as histogram estimators, 
 *  see @ref DEHistogram, and kernel density estimators (KDEs), see
 *   @ref DEKernelDensity, for instance. It is 
 *   thoroughly investigated in  (TODO: cite our paper) and can be explained as follows. 
 *   It is known in general, that the mean integrated square error (MISE) can be rewritten
 *   as the sum of the integrated variance (IV) and the integrated square bias
 *   
 *   \f[ \textrm{MISE} = \int_a^b\mathbb{E} [\hat{f}_{n,h}(x) - f(x)]^2\mathrm{d}x =
 * \int_a^b\textrm{Var}[\hat{f}_{n,h}(x)]\mathrm{d}x + \int_a^b \left(
 * \mathbb{E}[\hat{f}_{n,h}(x)] - f(x) \right)^2\mathrm{d}x = \textrm{IV} + \textrm{ISB}, \f]
 * 
 * where \f$f\f$ denotes the true density and \f$\hat{f}_{n,h}\f$ the density estimator.
 * For MC it is known that, asymptotically, \f$\textrm{IV}\approx C n^{-1} h^{-1}\f$ and
 * \f$\textrm{ISB}\approx B h^{\alpha}\f$, \f$C,B,\alpha>0\f$. Observe that the power
 * of \f$h\f$ is positive in the ISB, while it appears to be negative in the IV.
 * 
 * The goal is to select an optimal band- or binwidth \f$h_*\f$ which balances these
 *  two terms, which is also known as the infamous <em>variance-bias tradeoff</em>. 
 *  This is possible, as soon as all the parameters \f$C,B,\alpha\f$ are explicitly 
 *  known (or can be sufficiently well estimated, at least). For histograms and KDEs this is indeed the case. 
 *  Let \f$R(g)\f$ denote the roughness functional over the 
 *  interval \f$[a,b]\f$
 *  and let \f$\mu_k(g)\f$ be the \f$k\f$-th moment of a function \f$g\f$, i.e. 
 *  
 *  \f[
 *  R(g) = \int_a^bg^2(x)\mathrm{d}x,\qquad\text{and}\quad 
 *  \mu_k(g)=\int_{-\infty}^{\infty}x^kg(x)\mathrm{d}x.
 *  \f]
 *  
 *  Assuming that all the entities exist and are finite, the table below lists the parameter
 *  values for histograms and KDEs for observations generated with MC, see \cite tSCO15a.
 *  <center>
 *  <table>
 *  <caption id="mc_parameters"> Asymptotically optimal parameter values with MC</caption>
 *  <tr><th>Parameter   <th>Histogram       <th>KDE
 *  <tr><td>@f$C@f$		<td>1				<td>@f$\mu_0(K^2)@f$
 *  <tr><td>@f$\alpha@f$<td>2				<td> 4
 *  <tr><td>@f$B@f$     <td>@f$R(f')/12@f$	<td> @f$ \mu_2^2(K) R(f'')/4@f$
 *  </table>
 *  </center>
 * 
 * The only highly unpleasant quantities are the roughness functional of the first or second derivative of the unknown density. This, however, can
 *  be overcome by using derivative estimates as implemented in \ref DensityDerivativeEstimator and plugging the results back into the expression for the 
 *  ISB. The kernel assumed for the derivative estimation is a standard normal. To estimate the \f$h^{r_0}_{\text{AMISE}} \f$, where \f$r_0\f$ can be seen
 *  in the table above,  we use one step of the recursion
 *  DensityDerivativeEstimator#hAmiseR(int, int, double, double[], double, DensityDerivativeEstimator, double[], double[], double, double). For the 
 *  initial value we estimate the empirical standard deviation \f$\hat{\sigma}\f$ from the observations \f$X_0,\dots,X_{n-1}\f$ and assume \f$f\f$ to be a normal distribution
 *  with standard deviation \f$\hat{\sigma}\f$.
 *  
 *  If the user wishes to use different kernels or other initial values than those stated above, it is only necessary to overwrite the corresponding methods
 *  for estimating \f$B\f$. The rest will remain intact.
 *  
 *  Let us now turn to the IV. What changes with RQMC is that it can cause significant variance reduction, which, however, can come at price of a worse dependency on \f$h\f$. This was
 *  proven in (TODO: cite our paper) theoretically and also observed empirically. To reflect this behavior, we choose the model
 *  
 * \f[
 * \textrm{IV}\approx C n^{-\beta}h^{-\delta},\qquad \beta,\delta>1, 
 * \f]
 * 
 * locally, i.e. within a small region w.r.t. \f$(n,h)\f$. This proved to be a reasonable assumption.
 * 
 * Observe that taking the logarithm of both sides of the above model assumption yields a linear model with variables \f$\log n\f$ and \f$\log h\f$, and
 * unknown parameters \f$\log C,\beta,\delta\f$. Based on this linear model, one can estimate the empirical IV for several reasonable values of 
 * \f$(n,h)\f$ with the methods provided by \ref DensityEstimator and subsequently obtain estimates for the unknown parameters. Together with the 
 * estimated parameters from the ISB, one can thus compute an estimate for \f$h_*\f$.
 * 
 * Finally, one can test the estimator with the selected \f$h_*\f$ out of the box, estimate its empirical IV, compute its modeled ISB and add these
 * quantities to obtain an estimator for the empirical MISE.
 * 
 * 
 */


public class DEModelBandwidthBased implements DensityEstimationModel {

	/**Power of \f$h\f$ in ISB*/
	private double alpha;
	/**Multiplicative constant in ISB*/
	private double B;
	/**Negative power of \f$n\f$ in IV*/
	private double beta;
	/**Multiplicative constant in IV*/
	private double C;
	/**Negative power of \f$h\f$ in IV*/
	private double delta;
	
	/**
	 * left boundary of the interval over which we estimate.
	 */
	private double a;
	/**
	 * right boundary of the interval over which we estimate.
	 */
	private double b;
	/**
	 * flag whether to display output during running experiment or not.
	 */
	private boolean displayExec = false;
	
	/**
	 * base of all logarithms used in the experiment.
	 */
	private double baseOfLog = 2.0;

	/**
	 * logarithms in base #baseOfLog of the number of points.
	 */
	private double[] logN;
	
	/**
	 * logarithms in base #baseOfLog of the bin-/bandwidths.
	 */
	private double[] logH;
	
	private double [] logIV3D;
	

	
	
	/**
	 * Constructs an instance of this parametric model. The density estimator \a de is
	 * only used to determine the value for \f$\alpha\f$ and does currently only work
	 * with \ref DEHistogram and \ref DEKernelDensity. In any other case refer to 
	 * #DEModelBandwidthBased(double, MonteCarloModelDouble, double, double).
	 * @param de the density estimator to obtain \f$\alpha\f$.
	 * @param model the model from which the observations \f$X_0,\dots,X_{n-1}\f$ are generated.
	 * @param a left boundary of the interval over which we estimate.
	 * @param b right boundary of the interval over which we estimate.
	 */
	public DEModelBandwidthBased(DensityEstimator de, double a, double b) {

		setRange(a,b);
		String id = de.toString().toLowerCase();
		if(id.startsWith("histogram"))
			this.alpha = 2.0;
		else if(id.startsWith("kde"))
			this.alpha = 4.0;
		else
			System.out.println("Warning: Value of alpha not known for this estimator. Please set it manually.");
	}
	/**
	 * Constructs an instance of this parametric model.
	 * @param alpha the value for \f$\alpha\f$ used.
	 * @param a left boundary of the interval over which we estimate.
	 * @param b right boundary of the interval over which we estimate.
	 */
	public DEModelBandwidthBased(double alpha, double a, double b) {
		this.alpha = alpha;
		setRange(a,b);
	}
	
	/**
	 * Sets the flag whether to display output during running experiment or not to
	 * \a displayExec.
	 * @param displayExec flag, whether display output during running experiment or not.
	 */
	public void setDisplayExec(boolean displayExec) {
		this.displayExec = displayExec;
	}
	
	/**
	 * Gives flag whether to display output during running experiment or not.
	 * @return flag, whether to display output during running experiment or not.
	 */
	public boolean getDisplayExec() {
		return displayExec;
	}
	
	/**
	 * Sets the current interval over which we estimate to \f$[a,b]\f$.
	 * @param a left boundary of the interval over which we estimate.
	 * @param b right boundary of the interval over which we estimate.
	 */
	public void setRange(double a, double b) {
		this.a = a;
		this.b = b;
	}
	
	/**
	 * Gives the left boundary of the interval over which we estimate.
	 * @return the left boundary of the interval over which we estimate.
	 */
	public double geta() {
		return a;
	}
	
	/**
	 * Gives the right boundary of the interval over which we estimate.
	 * @return the right boundary of the interval over which we estimate.
	 */
	public double getb() {
		return b;
	}
	
	/**
	 * Gives the current value of \f$alpha\f$.
	 * 
	 * @return \f$\alpha\f$.
	 */
	public double getAlpha() {
		return alpha;
	}

	/**
	 * Sets the current value of \f$\alpha\f$ to \a alpha.
	 * 
	 * @param alpha the desired value for \f$\alpha\f$.
	 */
	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}

	/**
	 * Gives the current value of \f$\B\f$.
	 * 
	 * @return \f$\B\f$.
	 */
	public double getB() {
		return B;
	}

	/**
	 * Sets the current value of \f$B\f$ to \a B.
	 * 
	 * @param B the desired value for \f$B\f$.
	 */
	public void setB(double B) {
		this.B = B;
	}

	/**
	 * Sets the current value of \f$\beta\f$ to \a beta$.
	 * 
	 * @param beta the desired value for \f$\beta\f$.
	 */
	public void setBeta(double beta) {
		this.beta = beta;
	}

	/**
	 * Gives the current value of \f$\beta\f$.
	 * 
	 * @return \f$\beta\f$.
	 */
	public double getBeta() {
		return beta;
	}

	/**
	 * Sets the current value of \f$C\f$ to \a C.
	 * 
	 * @param C the desired value for \f$C\f$.
	 */
	public void setC(double C) {
		this.C = C;
	}

	/**
	 * Gives the current value of \f$C\f$.
	 * 
	 * @return \f$C\f$.
	 */
	public double getC() {
		return C;
	}

	/**
	 * Gives the current value of \f$\delta\f$.
	 * 
	 * @return \f$\delta\f$.
	 */
	public double getDelta() {
		return delta;
	}

	/**
	 * Sets the current value of \f$\delta\f$ to \a delta.
	 * 
	 * @param delta the desired value for \f$\delta\f$.
	 */
	public void setDelta(double delta) {
		this.delta = delta;
	}
	
	/**
	 * Gives the logs of the bin-/bandwidths considered.
	 * @return the logs of the bin-/bandwidths considered.
	 */
	public double[] getLogH() {
		return logH;
	}
	
	/**
	 * Gives the logs of the numbers of observations considered.
	 * @return the logs of the numbers of observations consider
	 */
	public double[] getLogN() {
		return logN;
	}
	
	/**
	 * Sets the logs of the bin-/bandwidths based on the bin-/bandwidths passed in \a hArray.
	 * @param hArray an array containing the desired bin-/bandwidths.
	 */
	public void setLogH(double[] hArray) {
		logH = new double[hArray.length];
		for(int i = 0; i < hArray.length; i++)
			logH[i] = Math.log(hArray[i])/Math.log(baseOfLog);
	}
	
	/**
	 * Sets the logs of the numbers of observations based on the numbers of observations passed in \a N.
	 * @param N
	 */
	public void setLogN(int[] N) {
		logN = new double[N.length];
		for(int i = 0; i < N.length; i++)
			logN[i] = Math.log(N[i])/Math.log(baseOfLog);
	}
	
	/**
	 * For parameter estimation of the IV this method produces a formatted string carrying
	 * basic information that can be used as a introductory head for the experiment.
	 *
	 * @param pointLabel a description of the point set employed.
	 * @param estimatorLabel a description of the density estimator. 
	 * @param numEvalPoints the number of evaluation points to estimate the empirical IV
	 * @param m the number of independent replications of the observations.
	 * @return a formatted introductory head for the parameter estimation of the IV.
	 */
	public String parametersIVFormatHead(String pointLabel, String estimatorLabel, int numEvalPoints,int m) {
		StringBuffer sb = new StringBuffer("");
		sb.append("Model parameter estimation for the IV over the interval [" + a + ", " + b +"]\n");
		sb.append("----------------------------------------------------------------\n\n");
		sb.append("Estimator: " + estimatorLabel +"\n");
		sb.append("Point set used: " + pointLabel +"\n");
		sb.append("Number of repititions: m = " + m + "\n");
		sb.append("Evaluation points: " + numEvalPoints + "\n") ;
		sb.append("----------------------------------------------------------------\n\n");	
		return sb.toString();
	}
	
	public String parametersIVEstimate(RQMCPointSet[] rqmcPts, MonteCarloModelDouble model, int m, DEHistogram de, double[] hArray, double[] evalPoints) {
		StringBuffer sb = new StringBuffer("");
		double [][] data;
		double [] variance;
		double[][] density;
		logIV3D = new double[rqmcPts.length * hArray.length];
		double [][] regDataX = new double[logIV3D.length][];
		String str;
		setLogH(hArray);
		logN = new double[rqmcPts.length];
		for(int i = 0; i < rqmcPts.length; i++)
			logN[i] = Math.log((double) rqmcPts[i].getNumPoints())/Math.log(baseOfLog);
		Tally statReps = new Tally();
		
		str = parametersIVFormatHead(rqmcPts[0].getLabel(),"Histogram",evalPoints.length,m);
		
		str += "log(n)\t\t log(h)\t\t empirical IV \n\n";
		
		sb.append(str);
		if(displayExec)
			System.out.print(str);
		
		for(int i = 0; i < logN.length; i++) { //point sets indexed by i
			for(int j = 0; j < logH.length; j++) { //h's indexed by j
				data = new double[m][];

				RQMCExperiment.simulReplicatesRQMC(model, rqmcPts[i], m, statReps, data);
				de.setH(hArray[j]);
				density = new double[m][de.getNumBins()];
				density = de.evalDensity(data, a, b);
				
				variance = new double[de.getNumBins()];
				
				regDataX[i * logH.length + j] = new double[2];
					regDataX[i * logH.length + j][0] = logN[i];
					regDataX[i * logH.length + j][1] = logH[j];
					
				logIV3D[i * logH.length + j] = Math.log(DensityEstimator.computeIV(density,a,b,variance))/Math.log(baseOfLog);
				
				str = PrintfFormat.f(3,1, logN[i]) + "\t " + PrintfFormat.f(6, 4, logH[j]) + "\t " + logIV3D[i * logH.length + j] +"\n";
				sb.append(str);
				if(displayExec)
					System.out.print(str);
			}
			
			
		}
		
		double [] regCoeffs = LeastSquares.calcCoefficients0(regDataX, logIV3D);
		setC(Math.pow(baseOfLog, regCoeffs[0]));
		setBeta(-regCoeffs[1]);
		setDelta(-regCoeffs[2]);
		
		str = "\n\n";
		str += "C =\t" + getC() + "\n";
		str += "beta =\t" + getBeta() + "\n";
		str += "delta =\t" + getDelta() + "\n\n";
		
		sb.append(str);
		if(displayExec)
			System.out.print(str);
		

		return sb.toString();
	}
	
	@Override
	public double estimateIV(DensityEstimator de) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double estimateISB(DensityEstimator de) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double estimateMISE(DensityEstimator de) {
		// TODO Auto-generated method stub
		return 0;
	}

	
	/**
	 * Computes the asymptotically optimal multiplicative constant in the ISB for a
	 * histogram as described above. The bandwidth in these density derivate
	 * estimates is computed recursively via
	 * {@link #hOptAmise(DensityDerivativeEstimator, double[], double, double[], double[], int, double)}
	 * starting with initial value \a init for the roughness functional of the
	 * derivative of the density of order \a maxDerivative. Each occuring
	 * integration, e.g. for evaluating a roughness-functional, is carried out by a
	 * quadrature rule using the integration nodes \a evalPoints.
	 * 
	 * Analogously to
	 * {@link #hOptAmise(DensityDerivativeEstimator, double[], double, double[], double[], int, double)}
	 * \a mu2 is the second moment of the kernel and \a mu2Derivatives contains the
	 * second moments of the derivatives of \f$k\f$ decreasing w.r.t. the order of
	 * the derivatives, starting with order \a maxDerivative - 2.
	 * 
	 * Note that for a histogram estimator \a maxDerivative has to be odd!
	 * 
	 * @param dde            the DDE we use for determining the optimal bandwidth.
	 * @param data           the observations gained from @f$m@f$ independent
	 *                       simulations.
	 * @param mu2            the second moment of \f$k\f$.
	 * @param mu2Derivatives the second moments of the derivatives of \f$k\f$.
	 * @param evalPoints     the integration nodes.
	 * @param maxDerivative  the highest order of the density derivative considered.
	 * @param init           initial value of the roughness functional in the
	 *                       denominator.
	 * @param de             the histogram estimator for the sought density.
	 * @return the asymptotically optimal multiplicative constant in the ISB.
	 * 
	 * @remark **Florian:** Maybe we should throw an exception, when \a
	 *         maxDerivative is not odd
	 */
	// TODO: add exception if maxDerivative is odd!
	public static double computeB(DensityDerivativeEstimator dde, double[][] data, double mu2, double[] mu2Derivatives,
			double[] evalPoints, int maxDerivative, double init, DEHistogram de) {
		int m = data.length;
		double B = 0.0;
		double a = de.geta();
		double b = de.getb();

		int numEvalPoints = evalPoints.length;
		double[] estDensDerivative = new double[numEvalPoints];
//		Arrays.fill(estDensDerivative, 0.0);

		for (int r = 0; r < m; r++) {
			double h = hOptAmise(dde, data[r], mu2, mu2Derivatives, evalPoints, maxDerivative, init);
			// System.out.println("h = " + h);
			dde.setOrder(1);
			dde.setH(h);
			dde.constructDensity(data[r]);
			dde.evalDensity(evalPoints, estDensDerivative);
			double roughnessFunctional = 0.0;
			for (int i = 0; i < numEvalPoints; i++)
				roughnessFunctional += estDensDerivative[i] * estDensDerivative[i];
			roughnessFunctional *= (b - a) / (double) numEvalPoints;

			B += roughnessFunctional;
		}

		return B / (12.0 * (double) m);

	}

	/**
	 * Same as
	 * {@link #computeB(DensityDerivativeEstimator, double[][], double, double[], double[], int, double, DEHistogram)}
	 * but with \a numEvalPoints equidistant integration nodes.
	 * 
	 * @param dde            the DDE we use for determining the optimal bandwidth.
	 * @param data           the observations gained from @f$m@f$ independent
	 *                       simulations.
	 * @param mu2            the second moment of \f$k\f$.
	 * @param mu2Derivatives the second moments of the derivatives of \f$k\f$.
	 * @param numEvalPoints  the number of equidistant integration nodes.
	 * @param maxDerivative  the highest order of the density derivative considered.
	 * @param init           initial value of the roughness functional in the
	 *                       denominator.
	 * @param de             the histogram estimator for the sought density.
	 * @return the asymptotically optimal multiplicative constant in the ISB.
	 */

	public static double computeB(DensityDerivativeEstimator dde, double[][] data, double mu2, double[] mu2Derivatives,
			int numEvalPoints, int maxDerivative, double init, DEHistogram de) {
		return computeB(dde, data, mu2, mu2Derivatives, dde.getEquidistantPoints(numEvalPoints), maxDerivative, init,
				de);
	}

	/**
	 * Computes the asymptotically optimal multiplicative constant in the ISB for a
	 * KDE as described above. The bandwidth in these density derivate estimates is
	 * computed recursively via
	 * {@link #hOptAmise(DensityDerivativeEstimator, double[], double, double[], double[], int, double)}
	 * starting with initial value \a init for the roughness functional of the
	 * derivative of the density of order \a maxDerivative. Each occuring
	 * integration, e.g. for evaluating a roughness-functional, is carried out by a
	 * quadrature rule using the integration nodes \a evalPoints.
	 * 
	 * Analogously to
	 * {@link #hOptAmise(DensityDerivativeEstimator, double[], double, double[], double[], int, double)}
	 * \a mu2 is the second moment of the kernel and \a mu2Derivatives contains the
	 * second moments of the derivatives of \f$k\f$ decreasing w.r.t. the order of
	 * the derivatives, starting with order \a maxDerivative - 2.
	 * 
	 * Note that for a KDE \a maxDerivative has to be even!
	 * 
	 * @param dde            the DDE we use for determining the optimal bandwidth.
	 * @param data           the observations gained from @f$m@f$ independent
	 *                       simulations.
	 * @param                * @param mu2 the second moment of \f$k\f$.
	 * @param mu2Derivatives the second moments of the derivatives of \f$k\f$.
	 * @param evalPoints     the integration nodes.
	 * @param maxDerivative  the highest order of the density derivative considered.
	 * @param init           initial value of the roughness functional in the
	 *                       denominator.
	 * @param de             the KDE for the sought density.
	 * @return the asymptotically optimal multiplicative constant in the ISB.
	 */
	// TODO: add exception that maxDerivative has to be even!
	public static double computeB(DensityDerivativeEstimator dde, double[][] data, double mu2, double[] mu2Derivatives,
			double[] evalPoints, int maxDerivative, double init, DEKernelDensity de) {
		int m = data.length;
		double B = 0.0;
		double a = de.geta();
		double b = de.getb();

		int numEvalPoints = evalPoints.length;
		double[] estDensDerivative = new double[numEvalPoints];
//		Arrays.fill(estDensDerivative, 0.0);

		for (int r = 0; r < m; r++) {
			double h = hOptAmise(dde, data[r], mu2, mu2Derivatives, evalPoints, maxDerivative, init);
			// System.out.println("h = " + h);
			dde.setOrder(2);
			dde.setH(h);
			dde.constructDensity(data[r]);
			dde.evalDensity(evalPoints, estDensDerivative);
			double roughnessFunctional = 0.0;
			for (int i = 0; i < numEvalPoints; i++)
				roughnessFunctional += estDensDerivative[i] * estDensDerivative[i];
			roughnessFunctional *= (b - a) / (double) numEvalPoints;

			B += roughnessFunctional;
		}

		return 0.25 * mu2 * B / (double) m;

	}

	/**
	 * Same as
	 * {@link #computeB(DensityDerivativeEstimator, double[][], double, double[], double[], int, double, DEKernelDensity)}
	 * but with \a numEvalPoints equidistant integration nodes.
	 * 
	 * @param dde            the DDE we use for determining the optimal bandwidth.
	 * @param data           the observations gained from @f$m@f$ independent
	 *                       simulations.
	 * @param mu2            the second moment of \f$k\f$.
	 * @param mu2Derivatives the second moments of the derivatives of \f$k\f$.
	 * @param numEvalPoints  the number of equidistant integration nodes.
	 * @param maxDerivative  the highest order of the density derivative considered.
	 * @param init           initial value of the roughness functional in the
	 *                       denominator.
	 * @param de             the KDE for the sought density.
	 * @return the asymptotically optimal multiplicative constant in the ISB.
	 */

	public static double computeB(DensityDerivativeEstimator dde, double[][] data, double mu2, double[] mu2Derivatives,
			int numEvalPoints, int maxDerivative, double init, DEKernelDensity de) {
		return computeB(dde, data, mu2, mu2Derivatives, dde.getEquidistantPoints(numEvalPoints), maxDerivative, init,
				de);
	}

	/**
	 * Computes the asymptotically optimal multiplicative constant in the ISB for a
	 * histogram. This works exactly as
	 * {@link #computeB(DensityDerivativeEstimator, double[][], double, double[], double[], int, double, DEHistogram)}
	 * but with the kernel of the DDE being a Gaussian.
	 * 
	 * In this case, the second moment of the kernel is 1 and those of the
	 * derivatives can be obtained via {@link #densFunctionalGaussian(int, double)}.
	 * 
	 * Note that for a histogram estimator \a maxDerivative has to be odd!
	 * 
	 * @param dde           the DDE we use for determining the optimal bandwidth.
	 * @param data          the observations gained from @f$m@f$ independent
	 *                      simulations.
	 * @param evalPoints    the integration nodes.
	 * @param maxDerivative the highest order of the density derivative considered.
	 * @param init          initial value of the roughness functional in the
	 *                      denominator.
	 * @param de            the histogram estimator for the sought density.
	 * @return the asymptotically optimal multiplicative constant in the ISB.
	 */
	public static double computeB(DDEGaussian dde, double[][] data, double[] evalPoints, int maxDerivative, double init,
			DEHistogram de) {
		int t = (maxDerivative - 1) / 2; // maxDerivative = 2t + 1
		double[] mu2Derivatives = new double[t - 1];
		for (int r = t - 1; r >= 1; r--)
			// the first order is 2t-1, this is init.
			// the first in mu2Derivatives is 2t-3, the second 2t-5,..., the last 2*1-1.
			mu2Derivatives[t - 1 - r] = densityFunctionalGaussian(2 * r - 1, 1.0);
		return computeB(dde, data, 1.0, mu2Derivatives, evalPoints, maxDerivative, init, de);

	}

	/**
	 * Same as
	 * {@link #computeB(DDEGaussian, double[][], double[], int, double, DEHistogram)}
	 * but with \a numEvalPoints equidistant integration nodes.
	 * 
	 * @param dde           the DDE we use for determining the optimal bandwidth.
	 * @param data          the observations gained from @f$m@f$ independent
	 *                      simulations.
	 * @param numEvalPoints the number of equidistant integration nodes.
	 * @param maxDerivative the highest order of the density derivative considered.
	 * @param init          initial value of the roughness functional in the
	 *                      denominator.
	 * @param de            the histogram estimator for the sought density.
	 * @return the asymptotically optimal multiplicative constant in the ISB.
	 */
	public static double computeB(DDEGaussian dde, double[][] data, int numEvalPoints, int maxDerivative, double init,
			DEHistogram de) {
		return computeB(dde, data, dde.getEquidistantPoints(numEvalPoints), maxDerivative, init, de);
	}

	/**
	 * Computes the asymptotically optimal multiplicative constant in the ISB for a
	 * KDE. This works exactly as
	 * {@link #computeB(DensityDerivativeEstimator, double[][], double, double[], double[], int, double, DEKernelDensity)}
	 * but with the kernel of the DDE being a Gaussian.
	 * 
	 * In this case, the second moment of the kernel is 1 and those of the
	 * derivatives can be obtained via {@link #densFunctionalGaussian(int, double)}.
	 * 
	 * Note that for a histogram estimator \a maxDerivative has to be odd!
	 * 
	 * @param dde           the DDE we use for determining the optimal bandwidth.
	 * @param data          the observations gained from @f$m@f$ independent
	 *                      simulations.
	 * @param evalPoints    the integration nodes.
	 * @param maxDerivative the highest order of the density derivative considered.
	 * @param init          initial value of the roughness functional in the
	 *                      denominator.
	 * @param de            the KDE for the sought density.
	 * @return the asymptotically optimal multiplicative constant in the ISB.
	 */
	public static double computeB(DDEGaussian dde, double[][] data, double[] evalPoints, int maxDerivative, double init,
			DEKernelDensity de) {
		int t = maxDerivative / 2; // maxDerivative = 2t
		double[] mu2Derivatives = new double[t - 2];
		for (int r = t - 1; r >= 2; r--)
			// the first order is 2t-2, this is init.
			// the first in mu2Derivatives is 2t-4, the second 2t-6,..., the last 2*2-2.
			mu2Derivatives[t - 1 - r] = densityFunctionalGaussian(2 * r - 2, 1.0);
		return computeB(dde, data, 1.0, mu2Derivatives, evalPoints, maxDerivative, init, de);

	}

	/**
	 * Same as
	 * {@link #computeB(DDEGaussian, double[][], double[], int, double, DEKernelDensity)}
	 * but with \a numEvalPoints equidistant integration nodes.
	 * 
	 * @param dde           the DDE we use for determining the optimal bandwidth.
	 * @param data          the observations gained from @f$m@f$ independent
	 *                      simulations.
	 * @param numEvalPoints the number of equidistant integration nodes.
	 * @param maxDerivative the highest order of the density derivative considered.
	 * @param init          initial value of the roughness functional in the
	 *                      denominator.
	 * @param de            the KDE for the sought density.
	 * @return the asymptotically optimal multiplicative constant in the ISB.
	 */
	public static double computeB(DDEGaussian dde, double[][] data, int numEvalPoints, int maxDerivative, double init,
			DEKernelDensity de) {
		return computeB(dde, data, dde.getEquidistantPoints(numEvalPoints), maxDerivative, init, de);
	}

	

	




	/**
	 * Gives the estimated IV based on the (local) model assumption
	 * \f$Cn^{-\beta}h^{-\delta} \f$. Note that this requires the parameters \a C,
	 * \a beta, and \a delta to be set for this estimator.
	 * 
	 * @param n the number of observations.
	 * @return the estimated IV.
	 */
	public double computeEstimatedIV(int n) {
		return C * Math.pow(n, -beta) * Math.pow(h, -delta);
	}

	/**
	 * Gives the estimated ISB for when the exact density of the underlying model is
	 * not known, based on its asymptotic value \f$Bh^{\alpha}\f$. Note that this
	 * requires the parameter \a B to be set.
	 * 
	 * @return the estimated ISB.
	 */
	public double computeEstimatedISB() {
		return B * Math.pow(h, alpha);
	}

	/**
	 * Same as {@link #computeEstimatedISB()} with dummy arguments to overload
	 * {@link #computeISB(MonteCarloModelDensityKnown, double[][], double[])} for
	 * situations, where the true density is not known.
	 * 
	 * @return the estimated ISB.
	 */
	public double computeISB(MonteCarloModelDouble model, double[][] estDensities, double[] evalPoints) {
		return computeEstimatedISB();
	}

	/**
	 * Computes the estimated MISE, i.e. the sum of {@link #computeEstimatedIV(int)}
	 * and {@link #computeDensityISB()}.
	 * 
	 * @param n the number of observations.
	 * @return the estimated MISE
	 */
	public double computeEstimatedMISE(int n) {
		return computeEstimatedISB() + computeEstimatedIV(n);
	}

	/**
	 * Computes the semi-empirical MISE for situations, where the true density is
	 * not known. I.e., it takes the sum of the empirical IV
	 * {@link #computeIV(double[][])} and the estimated ISB
	 * {@link #computeEstimatedISB()}.
	 * 
	 * The estimate of the empirical IV is based on \f$m\f$ realizations of the
	 * density estimator, which have previously been evaluated at the \f$k\f$ points
	 * stored in \a evalPoints. The matrix \a estDensities has dimensions \f$m\times
	 * k \f$, i.e. each row contains the evaluations of one density estimator.
	 * 
	 * @param the \f$m\times k\f$matrix containing the results of evaluating \f$m\f$
	 *            densities at \f$k\f$ evaluation points. the observations to
	 *            construct the density.
	 * @return the semi-empirical MISE
	 */
	public double computeMISE(double[][] estDensities) {
		double iv = computeIV(estDensities);
		return iv + computeEstimatedISB();
	}

	/**
	 * Same as {@link #computeMISE(double[][], double[])} but with a dummy argument
	 * \a model to overload
	 * {@link #computeMISE(MonteCarloModelDensityKnown, double[][], double[])} for
	 * cases where the true density is not known.
	 * 
	 * @param model
	 * @param estDensities the \f$m\times k\f$matrix containing the results of
	 *                     evaluating \f$m\f$ densities at \f$k\f$ evaluation
	 *                     points. the observations to construct the density.
	 * @return an estimate for the MISE
	 */
	public double computeMISE(MonteCarloModelDouble model, double[][] estDensities, double[] evalPoints) {
		return computeMISE(estDensities);
	}

	/**
	 * This method estimates the IV, ISB, and MISE based on \f$m\f$ realizations of
	 * the density estimator, which have previously been evaluated at the \f$k\f$
	 * points stored in \a evalPoints, and returns them in an array in this order.
	 * The matrix \a estDensities has dimensions \f$m\times k \f$, i.e. each row
	 * contains the evaluations of one density estimator.
	 * 
	 * The estimate for the IV is obtained from an estimate of the empirical IV
	 * {@link #computeIV(double[][])}, the ISB is estimated by
	 * {@link #computeEstimatedISB()}, and the MISE-estimate is obtained by their
	 * sum.
	 * 
	 * 
	 * @param estDensities the \f$m\times k\f$matrix containing the results of
	 *                     evaluating \f$m\f$ densities at \f$k\f$ evaluation
	 *                     points.
	 * @return estimates for the IV, the ISB, and the MISE.
	 */
	public double[] computeIVandISBandMISE(double[][] estDensities) {
		double iv = computeIV(estDensities);
		double isb = computeEstimatedISB();
		double[] res = { iv, isb, iv + isb };
		return res;
	}

	/**
	 * Same as {@link #computeIVandISBandMISE(double[][])} but with dummy arguments
	 * to overload
	 * {@link #computeIVandISBandMISE(MonteCarloModelDensityKnown, double[][], double[])}
	 * when the true density is not known.
	 * 
	 * @param model
	 * @param estDensities
	 * @param evalPoints
	 * @return
	 */
	public double[] computeIVandISBandMISE(MonteCarloModelDouble model, double[][] estDensities, double[] evalPoints) {
		return computeIVandISBandMISE(estDensities);
	}

}
