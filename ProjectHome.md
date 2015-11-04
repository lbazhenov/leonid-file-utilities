
# Introduction #
Useful file utilities to list files by specifying a path, which contains glob expression. JDK **java.nio.file.Files** utility class has a lot of useful file utilities. **java.nio.file.FileSystem.getPathMatcher** method returns a path matcher, which supports **glob** and **regex** expressions in the path. **java.nio.file.Files.walkFileTree** method does a great job to traverse directory tree.
<p />
Only one small thing is not present, that is having the support of getting base directory for walking file tree with  **java.nio.file.Files.walkFileTree** and pattern part from specified single path. Let's say you would like to print files in the following directory not caring of the direction of the slash (file separator):
```
usr\**\*.xml
```
To achieve it you need to invoke **java.nio.file.Files.walkFileTree** method passing the base directory **usr** and implement path matching in **java.nio.file.FileVisitor** by getting path matcher **java.nio.file.FileSystem.getPathMatcher**. This utility simplify it. It splits any path either absolute or relative into base directory and the wild card.
<p />
This class contain utility methods to list files by specifying the path as absolute or relative, which may contain **glob** pattern. Having **glob** pattern in your path means that you may use the following wild cards:
<ul>
<blockquote><li>The <code>*</code> character matches zero or more characters of a name component without crossing directory boundaries.</li>
<li>The <code>*</code><code>*</code> characters matches zero or more characters crossing directory boundaries.</li>
<li>The ? character matches exactly one character of a name component.</li>
<li>The backslash character (\) is used to escape characters that would otherwise be interpreted as special characters. The expression \\ matches a single backslash and "\{" matches a left brace for example.</li>
<li>The [ ] characters are a bracket expression that match a single character of a name component out of a set of characters. For example, <code>[</code>abc<code>]</code> matches "a", "b", or "c". The hyphen (-) may be used to specify<br>
a range so [a-z] specifies a range that matches from "a" to "z"(inclusive). These forms can be mixed so [abce-g] matches "a", "b", "c", "e", "f" or "g". If the character after the [ is a ! then it is used for<br>
negation so [!a-c] matches any character except "a", "b", or "c". Within a bracket expression the <code>*</code>, ? and \ characters match themselves. The (-) character matches itself if it is the first character within<br>
the brackets, or the first character after the ! if negating.</li>
<li>The { } characters are a group of subpatterns, where the group matches if any subpattern in the group matches. The "," character is used to separate the subpatterns. Groups cannot be nested.<br>
<p>For example, <b>../../{d?v,<code>*</code>bin}</b> will return <b>/bin, /dev, /sbin</b></p></li>
</ul></blockquote>

# Example #
Let's build a program, which lists files for the directory provided as input. It works the same way as unix **ls** command and additionally it supports **glob** syntax in the path like recursive directory traversing using **`*``*`** notation.

```
package your.app;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import leonid.util.FileUtils;

public class FileList
{
  public static void main (String[] args) throws IOException
  {
    String    startPath = null;
    
    if (args != null && args.length > 0)
    {
      startPath = args[0];
    }
    
    Path  firstPath = FileUtils.walkFileTree(startPath, 
        new SimpleFileVisitor<Path>()
        {
          @Override
          public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException
          {
            String  fileType = "Other";
            
            if      (attrs.isDirectory())     fileType="Dir";
            else if (attrs.isSymbolicLink())  fileType="Link";
            else if (attrs.isRegularFile())   fileType="File";
            
            System.out.printf("%s\t%s%n", fileType, filePath);
            
            return FileVisitResult.CONTINUE;  
          }
        });
    
    System.out.println("\nDone printing: " + firstPath);  
  }
}
```

Just add **file-utils-1.0.jar** into your class path or use maven assembly plugin to have single urban jar file. For the latest you need to deploy **file-utils-1.0.jar** into your local repository by executing the following command:

```
mvn install:install-file -DgroupId=leonid.util \
  -DartifactId=file-utils \
  -Dversion=1.0 \
  -Dpackaging=jar \
  -Dfile=file-utils-1.0.jar
```

Then you may have the following **pom.xml** for your project:

```
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>your.app</groupId>
  <artifactId>file-list</artifactId>
  <version>1.0</version>
  <name>File List</name>
  <description>Example implementation of file listing utility.</description>
  
  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  
  <dependencies>
    <dependency>
      <groupId>leonid.util</groupId>
      <artifactId>file-utils</artifactId>
      <version>1.0</version>
    </dependency>
  </dependencies>
    
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.1</version>
        <configuration>
          <source>1.7</source>
          <target>1.7</target>
        </configuration>
      </plugin>      
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-assembly-plugin</artifactId>
        <version>2.4</version>
        
        <configuration>
          <finalName>fl</finalName>
          <appendAssemblyId>false</appendAssemblyId>
          <archive>
            <manifest>
              <mainClass>your.app.FileList</mainClass>
            </manifest>
          </archive>
          <descriptorRefs>
            <descriptorRef>jar-with-dependencies</descriptorRef>
          </descriptorRefs>
        </configuration>
        </plugin>
    </plugins>
  </build>
</project>
```

Now you may play with your **ls** utility. The following I have on my system:

```
$ java -jar fl.jar ../../tmp
Dir	/private/tmp/launch-01OzmI
Dir	/private/tmp/launch-a70VVq
Dir	/private/tmp/launch-cAU0sv
Dir	/private/tmp/launch-EuTron

$ java -jar fl.jar ../../**/Java
Dir	/Library/Java/Extensions
Link	/Library/Java/Home
Dir	/Library/Java/JavaVirtualMachines
```

# Java Doc #
For details, please refer to the following [JavaDoc](https://leonid-file-utilities.googlecode.com/svn/trunk/file-utils/javadoc/apidocs/index.html)