package eu.semagrow.stack.modules.sails.semagrow.evaluation.monitoring;

/**
 * Created by angel on 6/26/14.
 */
public interface MeasurementPoint {

    String getId();

    long getCount();

    long getRunningTime();

}
