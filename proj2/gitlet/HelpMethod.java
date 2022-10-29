package gitlet;

/** Additional utilities.
 *  @author ASmellyCat
 */
import static gitlet.Utils.*;
import java.io.File;
import java.io.Serializable;
import java.util.*;

import static gitlet.Repository.*;
import static gitlet.MyUtils.*;

/**represents some methods that need to be used in Repository to simplify the code.
 * @author ASmellyCat
 * */

public class HelpMethod implements Serializable{

    /** Judge whether gitlet repository exist, and quit with a message if not. */
    public static void gitletRepoExists() {
        if (!GITLET_DIR.exists()) {
            exit("Not yet initialize a Getlet Repository.");
        }
    }
    /**
     * Get current commit SHA-1 ID
     * @return String of current commit SHA-1 ID
     * */
    public static String getCurrentCommitID() {
        return readContentsAsString(getActiveBranchFile());
    }
    /** get commit from OBJECT file. */
    public static Commit getCommit(String id) {
        if (id == null) {
            return null;
        }
        File file = objectFile(id);
        if (file.length() == 0) {
            exit("No commit with that id exists.");
        }
        return readObject(objectFile(id), Commit.class);
    }
    /**
     * @param branchName String of a given branch name.
     * @return Commit of a given branch name. */
    public static String getCommitIDByBranchName(String branchName) {
        File file = join(HEADS_DIR, branchName);
        return readContentsAsString(file);
    }
    /** get all commits and save it into a Set*/
    public static Set<Commit> getAllCommits() {
        Set<Commit> commitsSet = new HashSet<>();
        String commitsID = readContentsAsString(COMMITS);
        return getAllCommitsHelp(commitsSet, commitsID);
    }
    /** Using recursive method to get all the commits*/
    private static Set<Commit> getAllCommitsHelp(Set<Commit> commitsSet, String commitsID) {
        if (!commitsID.isEmpty()) {
            commitsSet.add(getCommit(commitsID.substring(0, 40)));
            return getAllCommitsHelp(commitsSet, commitsID.substring(40));
        }
        return commitsSet;
    }
    /** reset a commit files.
     * @param commitID String of a given commit SHA-1 ID */
    public static void resetACommit(String commitID) {
        StagingArea stageArea = getStagingArea();
        Commit commitGiven = getCommit(commitID);
        Map<String, String> trackedGiven = commitGiven.getFiles();
        List<String> trackedCurrent =  stageArea.getTrackedFiles();
        for (Map.Entry<String,String> entry: trackedGiven.entrySet()) {
            String filePath = entry.getKey();
            Blob blob = getBlob(entry.getValue());
            if (!trackedCurrent.contains(filePath)) {
                if (join(filePath).length() != 0) {
                    exit("There is an untracked file in the way; delete it, or add and commit it first.");
                }
            }
            updateFileWithBlob(filePath, blob);
            trackedCurrent.remove(filePath);
        }
        for (String filePath : trackedCurrent) {
            restrictedDelete(filePath);
        }
        stageArea.updateAllTracked(trackedGiven);
    }
    /**
     * get a latest common ancestor of two branches.
     * */
    public static Commit getSplitCommit(Commit headCommit, Commit otherCommit) {
        Comparator<Commit> commitComparator = Comparator.comparing(Commit::getDate).reversed();
        Queue<Commit> commitsQueue = new PriorityQueue<>(commitComparator);
        commitsQueue.add(headCommit);
        commitsQueue.add(otherCommit);
        Set<String> checkedCommitIDs = new HashSet<>();
        while (true) {
            Commit latestCommit = commitsQueue.poll();;
            String parentID = latestCommit.getParentID();
            Commit parentCommit = getCommit(parentID);
            if (checkedCommitIDs.contains(parentID)) {
                return parentCommit;
            }
            commitsQueue.add(parentCommit);
            checkedCommitIDs.add(parentID);
        }
    }

    /**
     * to merge by comparing files in HEAD Commit, other Commit and Split Commit.
     * */
    public static boolean toMerge(Commit splitCommit, Commit headCommit, Commit otherCommit) {
        boolean flag = false;
        StagingArea stageArea = getStagingArea();
        Map<String, String> splitTracked = splitCommit.getFiles();
        Map<String, String> headTracked = headCommit.getFiles();
        Map<String, String> otherTracked = otherCommit.getFiles();
        Set<String> checkedSplit = splitTracked.keySet();
        Set<String> checkedOther = otherTracked.keySet();
        for (String filePath : checkedSplit) {
            String splitID = splitTracked.get(filePath);
            String headID = headTracked.get(filePath);
            String otherID = otherTracked.get(filePath);
            if (headID != null) {
                if (headID.equals(splitID)) {
                    if (otherID == null) {
                        restrictedDelete(filePath);
                        stageArea.remove(filePath);
                        flag = true;
                    } else if (!headID.equals(otherID)) {
                        Blob otherBlob = getBlob(otherTracked.get(filePath));
                        updateFileWithBlob(filePath, otherBlob);
                        stageArea.add(otherBlob.getCurrentFile());
                        flag = true;
                    }
                } else if (otherID != null) {
                    if (!otherID.equals(headID)) {
                        flag = conflictMerge(filePath, headTracked, otherTracked);
                    }
                }
            }
            checkedOther.remove(filePath);
        }
        for (String filePath : checkedOther) {
            String otherID = otherTracked.get(filePath);
            String splitID = splitTracked.get(filePath);
            String headID = headTracked.get(filePath);
            if (splitID == null && headID == null) {
                Blob otherBlob = getBlob(otherTracked.get(filePath));
                if (getFileByName(filePath).length() != 0) {
                    exit("There is an untracked file in the way; delete it, or add and commit it first.");
                }
                updateFileWithBlob(filePath, otherBlob);
                stageArea.add(otherBlob.getCurrentFile());
                flag = true;
            }
            if (splitID == null && headID != null && !otherID.equals(headID)) {
                flag = conflictMerge(filePath, headTracked, otherTracked);
            }
        }
        return flag;
    }

    /**
     *
     * */
    public static boolean conflictMerge(String filePath, Map<String, String> headMap, Map<String, String> otherMap) {
        Blob otherBlob = getBlob(otherMap.get(filePath));
        Blob headBlob = getBlob(headMap.get(filePath));
        updatedFileMerged(filePath, headBlob, otherBlob);
        getStagingArea().add(otherBlob.getCurrentFile());
        return true;
    }

    public static void overwriteMerge(String filePath, Map<String, String> headMap, Map<String, String> otherMap) {
        Blob otherBlob = getBlob(otherMap.get(filePath));
        Blob headBlob = getBlob(headMap.get(filePath));
        updatedFileMerged(filePath, headBlob, otherBlob);
        getStagingArea().add(otherBlob.getCurrentFile());
    }


    /**
     * Change HEAD file that points to the active branch.
     * @param branchName String of the name of branch.
     * */
    public static void activateBranch(String branchName) {
        writeContents(HEAD, DEFAULT_HEAD_PREFIX + branchName);
    }

    /**
     * create a certain branch file.
     * @param branchName String of the name of branch.
     * @return File of branch pointer*/
    public static File createBranchFile(String branchName) {
        return join(HEADS_DIR, branchName);
    }
    /** Get active branch File.
     * @return File of active branch.
     */
    public static File getActiveBranchFile() {
        String activeBranchFilePath = readContentsAsString(HEAD).split(": ")[1].trim();
        return join(GITLET_DIR, activeBranchFilePath);
    }

    /**
     * get a branch file given a branch name
     * @param branchName the branch name
     * @return File of the branch file in heads provided a branch name.
     * */
    public static File getBranchFile(String branchName) {
        return join(HEADS_DIR, branchName);
    }

    /** @return boolean of whether a branch has created. */
    public static boolean branchExists(String branchName) {
        List<String> branchNames = plainFilenamesIn(HEADS_DIR);
        if (!(branchNames.isEmpty())) {
            return branchNames.contains(branchName);
        }
        return false;
    }

    /** Using recursive method to print log. */
    public static void printLog(Commit commit) {
        if (commit != null) {
            printOneLog(commit);
            printLog(getCommit(commit.getParentID()));
        }
    }
    /** printed one commit in required format. */
    public static void printOneLog(Commit commit) {
        System.out.println("===");
        System.out.println("commit " + commit.getCommitID());
        System.out.println("Date: " + commit.getTimestamp());
        System.out.println(commit.getMessage());
        System.out.println();
    }

    /** get the branch names in a list. */
    public static List<String> getBranchNames() {
        List<String> branchNames = new ArrayList<>(plainFilenamesIn(HEADS_DIR));
        String activaBranchName = getActiveBranchFile().getName();
        branchNames.add(0,"*" + activaBranchName);
        branchNames.remove(activaBranchName);
        return branchNames;
    }

    /** the format to print status. */
    public static void printStatusFormat(String outline, List<String> names) {
        System.out.print("=== ");
        System.out.print(outline);
        System.out.println(" ===");
        if (names != null) {
            names.forEach(any ->{
                System.out.println(getRelativeFileName(any));
            });
        }
        System.out.println();
    }

    /**
     * get Staging Area instance from file ".gitlet/index".
     * @return StagingArea instance.
     */
    public static StagingArea getStagingArea() {
        return readObject(INDEX, StagingArea.class);
    }

    /**
     * get blob given a SHA-1 ID.
     * */
    public static Blob getBlob(String blobID) {
        return readObject(objectFile(blobID), Blob.class);
    }


    /**
     * save the object to temp.
     * @param file File that need to be used to store object.
     * @param obj Serializable object that need to be stored.
     * */
    public static void saveObjectFile(File file, Serializable obj) {
        File dir = file.getParentFile();
        if (!dir.exists()) {
            dir.mkdir();
        }
        writeObject(file, obj);
    }
    /** get the temp object file stored in OBJECT_FILE. */
    public static File objectFile(String id) {
        File fileDir = join(OBJECT_DIR, id.substring(0, 2));
        if (!fileDir.exists()) {
            fileDir.mkdir();
        }
        String fileName = id.substring(2);
        // check the short input commit ID such as 1d575e62
        if (fileName.length() < 38) {
            // get the full 38 digits file ID.
            String fullFileName = shortCommitIDFind(fileDir, fileName.substring(0, 4));
            if (fullFileName == null) {
                exit("No commit with that id exists.");
            }
            return join(fileDir, fullFileName);
        }
        return join(fileDir, fileName);
    }

    /**
     * if the intput commit ID in command "Check out" is 6 digits,
     * then find the corresponding file.
     * @param fileDir File directory of that id (first two digits)
     * @param id String of 6 digits input commit ID.
     * @return String of fileName in full SHA-1 ID.
     * */
    public static String shortCommitIDFind(File fileDir, String id) {
        List<String> fileNames = plainFilenamesIn(fileDir);
        for (String fileName : fileNames) {
            if (id.equals(fileName.substring(0,4))) {
                return fileName;
            }
        }
        return null;
    }

    /**
     * @param filePath String of a existing file that needs to be written into.
     * @param blob Blob of a stored file that need to overwrite a file.
     * */
    public static void updateFileWithBlob(String filePath, Blob blob) {
        writeContents(join(filePath), blob.getFileContents());
    }

    public static void updatedFileMerged(String filePath, Blob head, Blob given) {
        String text1 = "<<<<<<< HEAD" + "\n";
        String text2 = head.getFileContents().toString();
        String text3 = "=======" + "\n";
        String text4 = given.getFileContents().toString();
        String text5 = ">>>>>>>" + '\n';
        writeContents(join(filePath), text1 + text2 + text3 + text4 + text5);



    }
}


