# wlst-test-maven-plugin

The WLST Test Maven Plugin executes unit tests written in the WLST-flavor of Jython as part of a Maven build.  

For projects that include WLST scripts, this makes unit testing of those scripts possible.  By default, the plugin looks for WLST scripts to test in `src/main/python` and for unit tests in `src/test/python`.  To run the unit tests, simply configure the plugin in Maven and point it to the Oracle Home that provides the WLST shell script this plugin is to run.

For example:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>test</groupId>
  <artifactId>wlst-project</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <build>
    <plugins>
      <plugin>
        <groupId>io.rhpatrick.maven</groupId>
        <artifactId>wlst-test-maven-plugin</artifactId>
        <version>1.0</version>
        <executions>
          <execution>
            <id>run-wlst-tests</id>
            <goals>
              <goal>test</goal>
            </goals>
            <configuration>
              <wlstScriptDirectory>/opt/wls12213</wlstScriptDirectory>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

This will cause the `wlst-test-maven-plugin` to copy the main WLST scripts and the unit test WLST scripts to a temporary directory, invoke WLST with the environment required to run the tests, and run the tests using the Python unittest module.  By default, the plugin assumes the `wlstScriptDirectory` points to an Oracle Home based on WebLogic Server 12.2.1 or newer.  If using an older version, simple set the `usingOldWlstVersion` parameter to true.

The plugin supports customizing the WLST execution environment to meet most needs.  To add artifacts to the WLST execution classpath, the wlstExtClasspath configuration element supports both file paths and project dependencies.  For example:

```xml
      <plugin>
        <groupId>io.rhpatrick.maven</groupId>
        <artifactId>wlst-test-maven-plugin</artifactId>
        <version>1.0</version>
        <executions>
          <execution>
            <id>run-wlst-tests</id>
            <goals>
              <goal>test</goal>
            </goals>
            <configuration>
              <wlstScriptDirectory>/opt/wls12213</wlstScriptDirectory>
              <wlstExtClasspath>
                <element>org.antlr:antlr4-runtime:jar</element>
                <element>${project.build.directory}/my-special-output-directory</element>
              </wlstExtClasspath>
            </configuration>
          </execution>
        </executions>
      </plugin>
```

This will cause the plugin to resolve the path to the `antlr4-runtime.jar` from the project's dependency list and add it and the `target/my-special-output-directory` elements to the WLST execution classpath.

Custom environment variables and Java system properties can be specified using the environmentVariables and/or systemProperties maps.  For example:

```xml
      <plugin>
        <groupId>io.rhpatrick.maven</groupId>
        <artifactId>wlst-test-maven-plugin</artifactId>
        <version>1.0</version>
        <executions>
          <execution>
            <id>run-wlst-tests</id>
            <goals>
              <goal>test</goal>
            </goals>
            <configuration>
              <wlstScriptDirectory>/opt/wls12213</wlstScriptDirectory>
              <environmentVariables>
                <DOMAIN_HOME>${project.build.directory}/domains/mydomain</DOMAIN_HOME>
              </environmentVariables>
              <systemProperties>
                <myproject.logging.dir>${project.build.directory}/logs</myproject.logging.dir>
                <myproject.logging.level>DEBUG</myproject.logging.level>
              </systemProperties>
            </configuration>
          </execution>
        </executions>
      </plugin>
```

This will cause `DOMAIN_HOME` environment variable to be set to point to the `target/domain/mydomain` directory and the `myproject.logging.dir` and `myproject.logging.level` Java system properties to be set to their respective values in the WLST execution environment.
