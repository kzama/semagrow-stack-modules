package eu.semagrow.stack.modules.sails.semagrow.evaluation.monitoring.querylog;

/**
 * Created by angel on 14/5/2015.
 */
public class FileQueryLogConfig implements QueryLogConfig {

    private String filename;


    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }


    public boolean rotate() { return false; }

}
