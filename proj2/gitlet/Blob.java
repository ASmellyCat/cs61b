package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import static gitlet.Utils.*;
import static gitlet.MyUtils.*;

/**Represents a blob object.
 * @author ASmellyCat
 * a blob contains:
 * 1. sha1 id
 * 2. contents
 * 4. file path
 */

public class Blob implements Serializable {
    private final String fileContents;
    private final String fileID;

    private final String filePath;

    private final File currentFile;
    /**
     * Creates a blob object with the specified parameters.
     * @param file currentFile.
     */
    public Blob(File file) {
        currentFile = file;
        fileContents = readContentsAsString(file);
        filePath = file.getAbsolutePath();
        fileID = generateBlobID();
    }

    /**
     * Get SHA-1 ID of this blob object.
     * @return String SHA-1 of this blob object.
     */
    public String shaID() {
        return fileID;
    }
    /**
     * Get the contents of this file in string format.
     * @return String contents of this file, which converted into a blob.
     * */
    public String contents() {
        return fileContents;
    }

    /**
     * Get the absolute file path of this blob.
     * @return String that demonstrates the current blob file path.
     */
    public String absolutePath() {
        return filePath;
    }

    //????????/
    public void save() {
        File objectFile = objectFile(fileID);
        saveObjectFile(objectFile, this);
    }



    /** private HELP method. */

    /**
     * generate the SHA-1 ID of Blob, using the absolute filepath and file contents.
     * @return String that generated by SHA-1.
     */
    private String generateBlobID() {
        return sha1(filePath, fileContents);
    }
}
