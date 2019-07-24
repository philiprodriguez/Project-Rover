package xyz.philiprodriguez.projectroverserver;


import org.apache.commons.math3.util.FastMath;

import java.util.Arrays;

// This class exists to provider helper methods for driving an arm
public class ArmDriver {

    private final double l_1;
    private final double l_2;
    private final int searchIterations;

    //TODO: Increase range to -7.5 to 130 deg AFTER replacing the first ternary search
    private static final double thetaOneMin = -0.1309;
    private static final double thetaOneMax = 2.2689;

    private static final double thetaTwoMin = 0.7854;
    private static final double thetaTwoMax = 5.218;


    /**
     * Initialize an instance of ArmDriver with an arm's parameters.
     *
     * @param l_1 is the length of the first arm segment
     * @param l_2 is the length of the second arm segment
     */
    public ArmDriver(double l_1, double l_2, int searchIterations) {
        this.l_1 = l_1;
        this.l_2 = l_2;
        this.searchIterations = searchIterations;
        if (searchIterations < 5 || searchIterations > 100) {
            throw new IllegalStateException("searchIterations must be in the interval [5, 100].");
        }
    }

    /**
     * Compute x_2 given the thetas
     * @return the value of x_2.
     */
    public double x_2(double thetaB, double thetaOne, double thetaTwo) {
        return l_1*cos(thetaOne)*cos(thetaB)+l_2*cos(thetaOne+thetaTwo-FastMath.PI)*cos(thetaB);
    }

    /**
     * Compute y_2 given the thetas
     * @return the value of y_2.
     */
    public double y_2(double thetaB, double thetaOne, double thetaTwo) {
        return l_1*cos(thetaOne)*sin(thetaB)+l_2*cos(thetaOne+thetaTwo-FastMath.PI)*sin(thetaB);
    }

    /**
     * Compute z_2 given the thetas
     * @return the value of z_2.
     */
    public double z_2(double thetaB, double thetaOne, double thetaTwo) {
        return l_1*sin(thetaOne)+l_2*sin(thetaOne+thetaTwo-FastMath.PI);
    }

    /**
     * Compute the distance from p_2, the point constructed using the thetas, to the provided
     * target point (via the coordinate arguments).
     * @return the distance from the target point to the point p_2.
     */
    public double distance(double x_t, double y_t, double z_t, double thetaB, double thetaOne, double thetaTwo) {
        double x_2 = x_2(thetaB, thetaOne, thetaTwo);
        double y_2 = y_2(thetaB, thetaOne, thetaTwo);
        double z_2 = z_2(thetaB, thetaOne, thetaTwo);
        return FastMath.sqrt((x_t-x_2)*(x_t-x_2)+(y_t-y_2)*(y_t-y_2)+(z_t-z_2)*(z_t-z_2));
    }

    /**
     * Provided some height, determine the inner and outer radius limits in the xy plane using the
     * angle limits for thetaB, thetaOne, thetaTwo.
     * @return The inner radius and outer radius, in that order.
     */
    public double[] radiiAtHeight(double z_t) {
        double[] result = new double[2];
        // So the game here boils down to setting z_2 to z_t and then solving from there...

        // Outer radius is the easier of the two, since we can basically ignore thetaTwo and
        // instead just assume we have a line of l_1+l_2 length.
        double len = l_1+l_2;
        double outerRadius = len*cos(FastMath.asin(z_t/len));
        result[1] = outerRadius;
        return result;
    }

    /**
     * Provided a target point, compute the three thetas. Return null if the computed thetas plot a
     * point whose distance is greater than maxDistance from the target point.
     * @return an array of the three thetas in the order thetaB, thetaOne, thetaTwo, or null if no
     * solution thetas could be found that defined a point within maxDistance from the target point.
     */
    public double[] getThetas(double x_t, double y_t, double z_t, double maxDistance) {
        double[] result1 = new double[3];

        // Instead of searching for thetaB consider that for a fixed thetaB, the remaining two
        // thetas can only access a plane spanned by the +z unit vector and the unit vector in the
        // xy plane with an angle of thetaB from the x axis. The only possible exact solutions for
        // thetaB will be when thetaB causes the plane to intersect with the target point. There are
        // only two such options then for thetaB: atan2(y_t, x_t) and atan2(y_t, x_t)+180. However,
        // for this specific case of the arm we want to ignore the +180 option to avoid the arm
        // violently swinging around "unexpectedly".

        double thetaB = FastMath.atan2(y_t, x_t);

        // We must keep in mind that the Teensy code only allows thetaB to span, at the current time
        // of writing, from -0.3491 to 3.8397, and importantly this means we absolutely must convert
        // the output of atan2 to avoid clipping, which can be from [-pi, pi] according to the docs.

        if (thetaB < (-FastMath.PI/2.0)) {
            thetaB += 2.0*FastMath.PI;
        }

        double[] distResult = distanceThetaB(x_t, y_t, z_t, thetaB);
        result1[0] = thetaB;
        result1[1] = distResult[1];
        result1[2] = distResult[2];

        // Sanity check the result
        if (distance(x_t, y_t, z_t, result1[0], result1[1], result1[2]) > maxDistance)
            return null;

        return Arrays.copyOf(result1, result1.length);
    }

    /**
     * Provided some thetaB value, determine the minimum distance achievable to the target point,
     * via ternary search.
     *
     * @return The smallest achievable distance using the provided thetaB and any thetaOne
     * and thetaTwo, and the thetaOne and thetaTwo used
     */
    private double[] distanceThetaB(double x_t, double y_t, double z_t, double thetaB) {
        double[] result2 = new double[3];

        // First we must divide our thetaOne space
        double splitTheta = FastMath.asin(z_t/(FastMath.sqrt(x_t*x_t+y_t*y_t+z_t*z_t)));

        // Then we want to search above and below splitTheta
        double topmax = thetaOneMax;
        double topmin = splitTheta;  //TODO: what if splitTheta is less than thetaOneMin???
        double botmax = splitTheta;
        double botmin = thetaOneMin;

        // Once again searchIterations iterations max
        double topmid1 = -1;
        double topmid2 = -1;
        double botmid1 = -1;
        double botmid2 = -1;
        double[] topmid1result = null;
        double[] topmid2result = null;
        double[] botmid1result = null;
        double[] botmid2result = null;
        for (int i = 0; i < searchIterations; i++) {
            topmid1 = topmin+((topmax-topmin)/3.0f);
            topmid2 = topmin+2.0f*((topmax-topmin)/3.0f);

            botmid1 = botmin+((botmax-botmin)/3.0f);
            botmid2 = botmin+2.0f*((botmax-botmin)/3.0f);

            topmid1result = distanceThetaOne(x_t, y_t, z_t, thetaB, topmid1);
            topmid2result = distanceThetaOne(x_t, y_t, z_t, thetaB, topmid2);

            botmid1result = distanceThetaOne(x_t, y_t, z_t, thetaB, botmid1);
            botmid2result = distanceThetaOne(x_t, y_t, z_t, thetaB, botmid2);

            if (topmid1result[0] > topmid2result[0]) {
                topmin = topmid1;
            } else {
                topmax = topmid2;
            }

            if (botmid1result[0] > botmid2result[0]) {
                botmin = botmid1;
            } else {
                botmax = botmid2;
            }
        }

        // Update our result
        //TODO: remove the 9999's which currently force the top result to be taken
        if (FastMath.min(topmid1result[0], topmid2result[0])-999999 <= FastMath.min(botmid1result[0], botmid2result[0])) {
            // Top is better
            if (topmid1result[0] < topmid2result[0]) {
                result2[0] = topmid1result[0];
                result2[1] = topmid1;
                result2[2] = topmid1result[1];
            } else {
                result2[0] = topmid2result[0];
                result2[1] = topmid2;
                result2[2] = topmid2result[1];
            }
        } else {
            // Bot is better
            if (botmid1result[0] < botmid2result[0]) {
                result2[0] = botmid1result[0];
                result2[1] = botmid1;
                result2[2] = botmid1result[1];
            } else {
                result2[0] = botmid2result[0];
                result2[1] = botmid2;
                result2[2] = botmid2result[1];
            }
        }

        return result2;
    }

    /**
     * Provided the target point and thetaB and thetaOne, return the minimal distance achievable
     * by searching over thetaTwo as well as the optimal thetaTwo.
     *
     * @return The smallest distance and the thetaTwo used to achieve it.
     */
    private double[] distanceThetaOne(double x_t, double y_t, double z_t, double thetaB, double thetaOne) {
        double[] result3 = new double[2];

        double ttmax = thetaTwoMax;
        double ttmin = thetaTwoMin;

        double mid1 = -1;
        double mid2 = -1;
        double mid1result = -1;
        double mid2result = -1;
        for (int i = 0; i < searchIterations; i++) {
            mid1 = ttmin+((ttmax-ttmin)/3.0f);
            mid2 = ttmin+2.0f*((ttmax-ttmin)/3.0f);

            mid1result = distance(x_t, y_t, z_t, thetaB, thetaOne, mid1);
            mid2result = distance(x_t, y_t, z_t, thetaB, thetaOne, mid2);

            if (mid1result > mid2result) {
                ttmin = mid1;
            }  else {
                ttmax = mid2;
            }
        }

        // Update result
        if (mid1result < mid2result) {
            result3[0] = mid1result;
            result3[1] = mid1;
        } else {
            result3[0] = mid2result;
            result3[1] = mid2;
        }

        return result3;
    }

    /*
        BELOW THIS POINT IS GENERAL MATH CODE FOR EXTREMELY FAST MATH FUNCTIONS
     */

    private static double cos(double x) {
        return sin(x+1.57079632679489661923);
    }

    /**
     * Approximated using Bhaskara I's approximation formula
     */
    private static double sin(double x) {
        // Bring into range [0, 2pi]
        // TODO: Use modulus or division to remove looping here
        while (x < 0)
            x += 6.28318530717958647693;
        while (x > 6.28318530717958647693)
            x -= 6.28318530717958647693;

        // Compute
        if (x <= 3.14159265358979323846) {
            double xshift = x-1.57079632679489661923;
            return (9.86960440108935861883 - 4 * (xshift * xshift)) / (9.86960440108935861883 + (xshift * xshift));
        } else {
            double xshift = x-4.71238898038468985769;
            return -1.0*((9.86960440108935861883 - 4 * (xshift * xshift)) / (9.86960440108935861883 + (xshift * xshift)));
        }
    }

    /**
     * Give approxmiations a rough test against Java's Math class.
     */
    public static void testApproximations() {
        System.out.println("START TRIG");
        double maxdif = -1.0;
        double avgdif = 0.0;
        int count = 0;
        for (double i = -10; i < 10; i+=0.001) {
            double difs = Math.abs(sin(i)-Math.sin(i));
            double difc = Math.abs(cos(i)-Math.cos(i));
            maxdif = Math.max(maxdif, difs);
            maxdif = Math.max(maxdif, difc);
            avgdif += difs + difc;
            count += 2;
        }
        avgdif /= count;
        System.out.println("Max Difference = " + maxdif);
        System.out.println("Avg Difference = " + avgdif);
        System.out.println("END TRIG");
    }
}
