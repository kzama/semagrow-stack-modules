package eu.semagrow.stack.modules.sails.semagrow.evaluation.monitoring.querylog;

import java.io.InputStream;

/**
 * Created by angel on 10/22/14.
 */
public interface QueryLogParserFactory {

    QueryLogParser getQueryLogParser(InputStream in);

}