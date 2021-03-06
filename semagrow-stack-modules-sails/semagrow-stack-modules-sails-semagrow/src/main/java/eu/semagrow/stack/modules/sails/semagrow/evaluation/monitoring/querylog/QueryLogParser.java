package eu.semagrow.stack.modules.sails.semagrow.evaluation.monitoring.querylog;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by angel on 10/21/14.
 */
public interface QueryLogParser {

    void setQueryRecordHandler(QueryLogHandler handler);

    void parseQueryLog(InputStream in) throws IOException, QueryLogException;

}
