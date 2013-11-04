package leonid.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Useful file utilities to list files by specifying a path, which contains glob expression. JDK <code>java.nio.file.Files</code> utility class has a lot of 
 * useful file utilities. <code>java.nio.file.FileSystem.getPathMatcher</code> method returns a path matcher, which supports <code>glob</code> and <code>regex</code> expressions in the path.
 * <code>java.nio.file.Files.walkFileTree</code> method does a great job to traverse directory tree. 
 * <p/>
 * Only one small thing is not present, that is having the support of getting base directory for walking file tree with <code>java.nio.file.Files.walkFileTree</code> and pattern part from specified 
 * single path. Let's say you would like to print files in the following directory not caring of the direction of the slash (file separator):
 * <p><code>usr\**\*.xml</code></p>
 * To achieve it you invoke <code>java.nio.file.Files.walkFileTree</code> method passing base directory <code>usr</code> and implement path matching in <code>java.nio.file.FileVisitor</code>
 * by getting path matcher <code>java.nio.file.FileSystem.getPathMatcher</code>. It's very useful to have utility, which splits any path into base directory and the pattern.
 * <p/> 
 * This class contain utility methods to list files by specifying the path as absolute or relative, which may contain <code>glob</code> pattern. Having <code>glob</code> pattern in your path means that you may use the 
 * following wild cards:
 * <ul>
 * <li>The * character matches zero or more characters of a name component without crossing directory boundaries.</li>
 * <li>The ** characters matches zero or more characters crossing directory boundaries.</li>
 * <li>The ? character matches exactly one character of a name component.</li>
 * <li>The backslash character (\) is used to escape characters that would otherwise be interpreted as special characters. The expression \\ matches a single backslash and "\{" matches a left brace for example.</li>
 * <li>The [ ] characters are a bracket expression that match a single character of a name component out of a set of characters. For example, [abc]matches "a", "b", or "c". The hyphen (-) may be used to specify 
 * a range so [a-z] specifies a range that matches from "a" to "z"(inclusive). These forms can be mixed so [abce-g] matches "a", "b", "c", "e", "f" or "g". If the character after the [ is a ! then it is used for 
 * negation so [!a-c] matches any character except "a", "b", or "c". Within a bracket expression the *, ? and \ characters match themselves. The (-) character matches itself if it is the first character within 
 * the brackets, or the first character after the ! if negating.</li>  
 * <li>The { } characters are a group of subpatterns, where the group matches if any subpattern in the group matches. The "," character is used to separate the subpatterns. Groups cannot be nested. 
 * <p>For example, <code>../../{d?v,*bin}</code> will return <code>/bin, /dev, /sbin</code></p></li>
 *</ul>
 *
 * @see java.nio.file.Files#walkFileTree(Path, Set, int, FileVisitor)
 *
 * @author <a href="mailto:lporshnev@gmail.com">Leonid Bazhenov</a>
 */
public class FileUtils
{
  public static final String    NORM_FILE_SEPARATOR = "/";
  public static final String    GLOB_CHARS_REGEX = "\\{.+\\}|\\[.+\\]|\\?+|\\*{1,2}";// pattern to find glob expression in the path

  protected static final FileSystem  fileSystem = FileSystems.getDefault();
  protected static final Pattern     globPattern = Pattern.compile(GLOB_CHARS_REGEX);

  /**
   * This interface can be used by caller of <code>listFiles</code> to filter files further. <code>listFiles</code> method builds the list of files based
   * on the wild card specified in the path, passed as input. <code>fileAdd</code> method is invoked right before added the file, which path is matched
   * by path matcher.
   * 
   * @see leonid.util.FileUtils#listFiles(String, Set, FileFilter)
   */
  public static interface FileFilter
  {
    /**
     * This method is invoked right before adding the file to the file list.
     * @param filePath is the file path.
     * @param attrs is file attributes.
     * @return true if file must be added to the list
     *
     * @see leonid.util.FileUtils#listFiles(String, Set, FileFilter)
     */
    boolean fileAdd (Path filePath, BasicFileAttributes attrs);
  }

  /**
   * This is container class for keeping two parts of the path: base directory and wild card portion.
   * The split of the path is done based on the glob pattern syntax.
   *
   * @see leonid.util.FileUtils#listFiles(String, Set, FileFilter)
   */
  public static class WildcardPath
  {
    /**
     * Base directory, which is static part of the path.
     */
    public Path   baseDir = null;

    /**
     * Wildcard part of the path.
     */
    public String wildcard = null;

    /**
     * Prints base directory and wildcard.
     */
    public String toString()
    {
      return String.format("baseDir=%s%nwildCard=%s", baseDir, wildcard);
    }
  }

  /**
   * Splits the path into base directory as absolute path and wild card. <code>WildcardPath.baseDir</code> is always <b>absolute</b> and <b>normalized</b> path. 
   *
   * @param pathStr is path string, which will be parsed to get base directory and wild card. If it's <code>null</code> or empty string then current directory is assumed. 
   * @return WildcardPath object, which contains base directory as absolute path and wild card as a string.
   *
   * @see leonid.util.FileUtils#listFiles(String, Set, FileFilter)
   */
  public static WildcardPath splitPathToBaseDirAndWildcard(String pathStr)
  {
    WildcardPath  wildcardPath = new WildcardPath();

    // If pathStr is null or empty then current directory is considered.
    if (pathStr == null || (pathStr=pathStr.trim()).length() == 0)
    {
      wildcardPath.baseDir = fileSystem.getPath(".").toAbsolutePath().normalize();
      wildcardPath.wildcard = null;
    }
    // Otherwise we need to split pathStr into base directory static path portion
    // and dynamic wildcard path portion.
    else
    {
      pathStr = pathStr.replaceAll("\\\\", NORM_FILE_SEPARATOR);  // normalize scan path to have only right slashes...

      // Create the matcher to detect glob expression in the path
      Matcher       globMatcher = globPattern.matcher(pathStr);

      // If glob expression is found then given path is broken into
      // start directory and wild card portion of the path
      if (globMatcher.find())
      {
        int     globStartIndex = globMatcher.start();

        // If glob expression starts immediately then given path string is wildcard
        // to search in the current directory
        if (globStartIndex == 0)
        {
          wildcardPath.baseDir = fileSystem.getPath(".").toAbsolutePath().normalize();
          wildcardPath.wildcard = pathStr;
        }
        // Glob expression is in the middle of the given path string, hence
        // it's needed to deduce base directory
        else
        {
          int   beforeGlobFileSeparatorIndex =  pathStr.lastIndexOf(NORM_FILE_SEPARATOR, globStartIndex-1);

          // There is file separator found before glob expression, hence the path
          // before glob expression will be the base directory
          if (beforeGlobFileSeparatorIndex > -1)
          {
            // If glob expression starts immediately after file separator then just copy it
            if (beforeGlobFileSeparatorIndex + 1 == globStartIndex)
            {
              wildcardPath.baseDir = fileSystem.getPath(pathStr.substring(0,globStartIndex)).toAbsolutePath().normalize();
              wildcardPath.wildcard = pathStr.substring(globStartIndex);
            }
            // There are some characters before glob and after file separator, hence they also
            // should be copied to the wildcard
            else
            {
              wildcardPath.baseDir = fileSystem.getPath(pathStr.substring(0,beforeGlobFileSeparatorIndex + 1)).toAbsolutePath().normalize();
              wildcardPath.wildcard = pathStr.substring(beforeGlobFileSeparatorIndex + 1);
            }
          }
          // There are no any file separator found in the path string, hence that is either
          // complete wildcard or drive letter for windows systems plus wildcard
          else
          {
            wildcardPath.baseDir = fileSystem.getPath(pathStr.substring(0,globStartIndex));

            // If there is name found and built base directory for the current platform,
            // then it means that we still have a part of wildcard to be concatenated
            if (wildcardPath.baseDir.getNameCount() > 0)
            {
              wildcardPath.wildcard = wildcardPath.baseDir.getName(0) + pathStr.substring(globStartIndex); // build final wildcard
              wildcardPath.baseDir = wildcardPath.baseDir.toAbsolutePath().resolve("..").normalize(); // go to upper level directory, since it was not a dir, but a portion of wildcard
            }
            else
            {
              wildcardPath.wildcard = pathStr.substring(globStartIndex);
              wildcardPath.baseDir = wildcardPath.baseDir.toAbsolutePath().normalize();
            }
          }
        }
        
        // Remove last file separator in the wildcard since it does not make nay sense 
        if (wildcardPath.wildcard.lastIndexOf(NORM_FILE_SEPARATOR) == wildcardPath.wildcard.length()-1)
        {
          wildcardPath.wildcard = wildcardPath.wildcard.substring(0, wildcardPath.wildcard.length()-1); 
        }
      }
      // If glob expression is not found then either we have the path, which points to
      // the directory to list or actual file, hence wild card is not used.
      else
      {
        wildcardPath.baseDir = fileSystem.getPath(pathStr).toAbsolutePath().normalize();
        wildcardPath.wildcard = null;
      }
    }

    return wildcardPath;
  }

  /**
   * Returns the list of files as <code>Path</code> objects. <code>startPath</code> is a path to directory or actual file, which may contain wild card 
   * in <code>glob</code> syntax. 
   * <p/>
   * Internally it invokes <code>java.nio.file.Files.walkFileTree</code>.
   * 
   * @param startPath is the path, which may contain wild card in <code>glob</code> syntax.
   * @return list of files.
   *
   * @throws IOException if <code>startPath</code> does not point to a real file or directory in the system.
   * 
   * @see leonid.util.FileUtils#listFiles(String, Set, FileFilter)
   */
  public static List<Path> listFiles(String startPath) throws IOException
  {
    return listFiles(startPath, null, null);
  }

  /**
   * Returns the list of files as <code>Path</code> objects. <code>startPath</code> is a path to directory or actual file, which may contain wild card 
   * in <code>glob</code> syntax. 
   * <p/>
   * Internally it invokes <code>java.nio.file.Files.walkFileTree</code>.
   * 
   * @param startPath is the path, which may contain wild card in <code>glob</code> syntax.
   * @param options is a set of <code>FileVisitOption</code> or null if file system links are not supposed to be followed.
   * @return list of files.
   *
   * @throws IOException if <code>startPath</code> does not point to a real file or directory in the system.
   * 
   * @see leonid.util.FileUtils#listFiles(String, Set, FileFilter)
   */
  public static List<Path> listFiles(String startPath, Set<FileVisitOption> options) throws IOException
  {
    return listFiles(startPath, options, null);
  }

  /**
   * Returns the list of files as <code>Path</code> objects. <code>startPath</code> is a path to directory or actual file, which may contain wild card 
   * in <code>glob</code> syntax. Final list of files, which satisfy <code>glob</code> expression can be filtered further if <code>fileFilter</code> is not null.
   * <p/>
   * Internally it invokes <code>java.nio.file.Files.walkFileTree</code>.
   *
   * @param startPath is the path, which may contain wild card in <code>glob</code> syntax.
   * @param options is a set of <code>FileVisitOption</code> or null if file system links are not supposed to be followed.
   * @param fileFilter is reference to an object, which does further filtering of files, which were matched the <code>glob</code> expression of <code>startPath</code>.
   * @return list of files.
   *
   * @throws IOException if <code>startPath</code> does not point to a real file or directory in the system.
   */
  public static List<Path> listFiles(String startPath, Set<FileVisitOption> options, FileFilter fileFilter) throws IOException
  {
    List<Path>    fileList = new ArrayList<>();
    
    doListFiles(fileList, startPath, options, null, fileFilter);
    
    return fileList;
  }
 
  /**
   * Walks a file tree from the <code>startPath</code>, which is a path to directory or actual file, which may contain wild card 
   * in <code>glob</code> syntax. Provided <code>visitor</code> is invoked only for matched files. Symbolic links are not followed.
   * <p/>
   * <b>NOTE</b> that:
   * <ul>
   * <li>Unlike <code>java.nio.file.Files.walkFileTree</code> it excepts start path not as a base directory to start scanning, but as a path with wild card.</li>
   * <li>It does not have max depth input parameter, since it may be indirectly specified by <code>*</code> and <code>**</code> directory wild cards.
   * <li><code>visitor</code> is invoked only for matched files.</li>
   * </ul>
   * <p/>
   * Internally it invokes <code>java.nio.file.Files.walkFileTree</code>.
   * 
   * @param startPath is the path, which may contain wild card in <code>glob</code> syntax.
   * @param visitor is the file visitor to invoke for each file, which matches the wild card <code>glob</code> pattern in <code>startPath</code>.
   * @return the starting file.
   * 
   * @throws IOException
   * 
   * @see leonid.util.FileUtils#walkFileTree(String, Set, FileVisitor)
   */
  public static Path walkFileTree(String startPath, FileVisitor<? super Path> visitor) throws IOException
  {
    return walkFileTree(startPath, null, visitor);
  }
  
  /**
   * Walks a file tree from the <code>startPath</code>, which is a path to directory or actual file, which may contain wild card 
   * in <code>glob</code> syntax. Provided <code>visitor</code> is invoked only for matched files.
   * <p/>
   * <b>NOTE</b> that:
   * <ul>
   * <li>Unlike <code>java.nio.file.Files.walkFileTree</code> it excepts start path not as a base directory to start scanning, but as a path with wild card.</li>
   * <li>It does not have max depth input parameter, since it may be indirectly specified by <code>*</code> and <code>**</code> directory wild cards.
   * <li><code>visitor</code> is invoked only for matched files.</li>
   * </ul>
   * <p/>
   * Internally it invokes <code>java.nio.file.Files.walkFileTree</code>.
   * 
   * @param startPath is the path, which may contain wild card in <code>glob</code> syntax.
   * @param options is a set of <code>FileVisitOption</code> or null if file system links are not supposed to be followed.
   * @param visitor is the file visitor to invoke for each file, which matches the wild card <code>glob</code> pattern in <code>startPath</code>.
   * @return the starting file.
   * 
   * @throws IOException
   */
  public static Path walkFileTree(String startPath, Set<FileVisitOption> options, FileVisitor<? super Path> visitor) throws IOException
  {
    Objects.requireNonNull(visitor, "File visitor cannot be null!");
    return doListFiles(null, startPath, options, visitor, null);
  }
  
  /**
   * Helper method to work file tree.
   * 
   * @param fileList is the list of files to collect if it's not <code>null</code>.
   * @param startPath is the path, which may contain wild card in <code>glob</code> syntax.
   * @param options is a set of <code>FileVisitOption</code> or null if file system links are not supposed to be followed.
   * @param visitor is the file visitor to invoke for each file, which matches the wild card <code>glob</code> pattern in <code>startPath</code>.
   * @param fileFilter is reference to an object, which does further filtering of files, which were matched the <code>glob</code> expression of <code>startPath</code>.
   * @return the starting file.
   * 
   * @throws IOException
   */
  protected static Path doListFiles(List<Path> fileList, String startPath, Set<FileVisitOption> options, 
                                    FileVisitor<? super Path> visitor, FileFilter fileFilter) throws IOException
  {
    WildcardPath  wildcardPath = splitPathToBaseDirAndWildcard(startPath);
    Path          startDir = null;
    Path          startingFileFound = null;

    startDir = wildcardPath.baseDir.toRealPath();

    if (startDir != null)
    {
      // Listing the directory is needed only if path points to the directory
      if (Files.isDirectory(startDir))
      {
        if (options == null)  options = EnumSet.noneOf(FileVisitOption.class);

        // If wild card portion of the path is not present then
        // just list the directory, which is present by the path
        // without visiting any sub directories.
        if (wildcardPath.wildcard == null)
        {
          startingFileFound = Files.walkFileTree(startDir, options, 1 /* visit only this directory */,
              new ListFileVisitor(fileList /* file list to collect */,
                    null/* path matcher is null, i.e. all visited files will be added to the list*/,
                    0 /* value does not matter for null path matcher */,
                    visitor,
                    fileFilter /* wildcard is a base filter, after which file can be removed from the list as per fileFilter */));
        }
        else
        {
          int maxDepth = Integer.MAX_VALUE;

          // If '**' pattern is present in the path wildcard then directory search depth must
          // be maximum, since it's recursive directory pattern.
          // Otherwise we need to calculate the search directory depth.
          if (wildcardPath.wildcard.indexOf("**") == -1)
          {
            int separatorIndex = -1;
            int separatorCount = 0;

            while ((separatorIndex = wildcardPath.wildcard.indexOf(NORM_FILE_SEPARATOR, separatorIndex+1)) > -1)
            {
              separatorCount++;
            }

            maxDepth = separatorCount + 1;
          }

          startingFileFound = Files.walkFileTree(startDir, options, maxDepth /* visit as deeper as estimated above based on wildcard */,
              new ListFileVisitor(fileList /* file list to collect */,
                    fileSystem.getPathMatcher("glob:" + wildcardPath.wildcard) /* only those files to be added to the list, which path is matched by path matcher */,
                    startDir.getNameCount() /* Path element start index of the path matcher in the absolute path from where to apply matcher pattern */,
                    visitor,
                    fileFilter /* wildcard is a base filter, after which file can be removed from the list as per fileFilter */));
        }
      }
      // Given path is actual file, hence just return path to it only
      else
      {
        fileList.add(startDir);
      }
    }
    
    return startingFileFound;
  }

  /**
   * Implementation of <code>FileVisitor</code> to build the list of files based on the <code>glob</code> expression of
   * the file path.
  */
  protected static class ListFileVisitor extends SimpleFileVisitor<Path>
  {
    PathMatcher               pathMatcher;
    List<Path>                fileList;
    int                       pathMatcherStartIndex;
    FileVisitor<? super Path> visitor;
    FileFilter                fileFilter;
    

    public ListFileVisitor(List<Path> fileList, PathMatcher pathMatcher, int pathMatcherStartIndex, FileVisitor<? super Path> visitor, FileFilter fileFilter)
    {
      this.fileList = fileList;
      this.pathMatcher = pathMatcher;
      this.pathMatcherStartIndex = pathMatcherStartIndex;
      this.visitor = visitor;
      this.fileFilter = fileFilter;
    }

    @Override
    public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException
    {
      FileVisitResult   visitResult = FileVisitResult.CONTINUE;
      
      boolean   isAddFile = pathMatcher == null || pathMatcher.matches(filePath.subpath(pathMatcherStartIndex, filePath.getNameCount()));
      
      if (isAddFile && fileFilter != null) isAddFile = fileFilter.fileAdd(filePath, attrs);
      
      if (isAddFile)
      {
        if (fileList != null)     fileList.add(filePath);
        if (visitor != null)      visitResult = visitor.visitFile(filePath, attrs);
      }

      return visitResult;
    }
    
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
    {
      return visitor != null ? visitor.preVisitDirectory(dir, attrs) : super.preVisitDirectory(dir, attrs); 
    }
     
    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
    {
      return visitor != null ? visitor.postVisitDirectory(dir, exc) : super.postVisitDirectory(dir, exc); 
    }    
    
    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException
    {
      return visitor != null ? visitor.visitFileFailed(file, exc) : super.visitFileFailed(file, exc);
    }
  }
}
