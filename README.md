[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/mojohaus/versions-maven-plugin.svg?label=License)](http://www.apache.org/licenses/)
![GitHub Action build status badge](https://github.com/mfoo/unnecessary-exclusions-maven-plugin/actions/workflows/maven-tests.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=mfoo_unnecessary-exclusions-maven-plugin&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=mfoo_unnecessary-exclusions-maven-plugin)
[![Known Vulnerabilities](https://snyk.io/test/github/mfoo/unnecessary-exclusions-maven-plugin/badge.svg)](https://snyk.io/test/github/mfoo/libyear-maven-plugin)

# unnecessary-exclusions-maven-plugin

This Maven plugin identifies dependency exclusions that can be removed without affecting your build. It is designed to
help keep your project's pom files clean and is especially useful in refactoring projects.

The plugin requires JDK 11+.

## Example
Imagine that my team's project depends on another team's project, but that team's project has a non-test-scoped JUnit dependency. I might have this in my pom:

```xml
<dependencies>
    <dependency>
        <groupId>com.mycompany</groupId>
        <artifactId>project</artifactId>
        <version>1.0.0</version>
        <exclusions>
            <exclusion>
                <groupId>org.junit.jupiter</groupId>
                <artifactId>junit-jupiter-api</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
</dependencies>
```

Then the other team releases version 1.0.1, fixing the `<scope>` on `junit-jupiter-api`. I bump my version, but I forget to remove the `<exclusion>` section. Now my `pom.xml` has an unnecessary exclusion. This plugin highlights the issue:

```shell
$ mvn io.github.mfoo:unnecessary-exclusions-maven-plugin:analyze | grep WARNING
[WARNING] Dependency com.mycompany:project excludes org.junit.jupiter:junit-jupiter-api unnecessarily
```

## Usage
You can either invoke the plugin manually:
```shell
mvn io.github.mfoo:unnecessary-exclusions-maven-plugin:analyze
```

Or you can include it in your build:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.mfoo</groupId>
      <artifactId>unnecessary-exclusions-maven-plugin</artifactId>
      <version>1.0.0</version>
      <executions>
        <execution>
          <id>exclusion-analysis</id>
          <goals>
            <goal>analyze</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```