package eu.semagrow.stack.modules.sails.semagrow.evaluation.monitoring.querylog;


/**
 * Created by angel on 14/5/2015.
 */
public class QueryLogConfigException extends Exception {

    public QueryLogConfigException(Exception e) {
        super(e);
    }

    public QueryLogConfigException(String msg) {
        super(msg);
    }
}
