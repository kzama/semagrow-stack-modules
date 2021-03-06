package eu.semagrow.stack.modules.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * {@todo class description}
 * 
 * @author Georgios Giannakopoulos
 */

public class ReactivityParameters {
    /**
     * Strategy of delivering tuples one at a time, even if no complete
     * results have been gathered.
     */
    public static String STRATEGY_DELIVER_ON_ARRIVAL = "DeliverOnArrival";
    /**
     * Strategy of delivering tuples only after the complete
     * results have been gathered.
     */
    public static String STRATEGY_DELIVER_ON_COMPLETION = "DeliverOnCompletion";
    
    /**
     * Set of strategies.
     * NOTE: Should be updated with any new strategies.
     */
    final protected Set<String> AllowedStrategies = new HashSet<String>(
            Arrays.asList(new String[]{ STRATEGY_DELIVER_ON_ARRIVAL, 
                STRATEGY_DELIVER_ON_COMPLETION })
        );
    
    /**
     * The maximum time for a response (i.e. before timeout).
     */
    protected int MaximumResponseTime;
    protected String Strategy;

    /**
     * Initializes the reactivity parameters with a given max response time and
     * a record fetching strategy.
     * @param MaximumResponseTime The maximum response time in milliseconds.
     * @param Strategy The strategy, which should be one of the allowed 
     * strategies defined as constant in the class definition.
     * @throws eu.semagrow.stack.modules.utils.ReactivityParameters.InvalidStrategyException 
     * when the strategy is not known or allowed.
     */
    public ReactivityParameters(int MaximumResponseTime, String Strategy) throws 
            InvalidStrategyException {
        this.MaximumResponseTime = MaximumResponseTime;
        if (!AllowedStrategies.contains(Strategy))
            throw new InvalidStrategyException(Strategy + " is not a valid "
                    + "strategy.");
        this.Strategy = Strategy;
    }


    /**
     * An exception thrown whan an invalid strategy is designated as
     * part of the reactivity parameters.
     */
    public class InvalidStrategyException extends Exception {

        public InvalidStrategyException(String sMsg) {
            super(sMsg);
        }
        
    }

    public int getMaximumResponseTime() {
        return MaximumResponseTime;
    }

    public String getStrategy() {
        return this.Strategy;
    }

    
}
