package eu.semagrow.stack.modules.sails.semagrow.evaluation.monitoring.querylog;


/**
 * Created by angel on 10/21/14.
 */
public interface QueryLogFactory {

    //QueryLogWriter getQueryRecordLogger(OutputStream out);
    QueryLogWriter getQueryRecordLogger(QueryLogConfig config) throws QueryLogConfigException;

}