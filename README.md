# JSweet maven plugin

Unleash the power of JSweet into your maven project

## Table of Contents
1. [Basic Configuration](#basic-configuration)
2. [Run JSweet](#run-jsweet)
3. [Hot transpilation](#hot-transpilation)
4. [Advanced configuration](#advanced-configuration)

## Basic Configuration ##

Add the JSweet's plugin repositories to your project's pom.xml:
```xml
<pluginRepositories>
	<pluginRepository>
		<id>jsweet-plugins-release</id>
		<name>plugins-release</name>
		<url>http://repository.jsweet.org/artifactory/plugins-release-local</url>
	</pluginRepository>
	<pluginRepository>
		<snapshots />
		<id>jsweet-plugins-snapshots</id>
		<name>plugins-snapshot</name>
		<url>http://repository.jsweet.org/artifactory/plugins-snapshot-local</url>
	</pluginRepository>
</pluginRepositories>
```

Configure your pom's sourceDirectory, as usual:
```xml
<build>
	<sourceDirectory>src</sourceDirectory>
```

Add your JSweet dependencies (candies):
```xml
<dependencies>
	<dependency>
		<groupId>org.jsweet.candies</groupId>
		<artifactId>angular</artifactId>
		<version>1.4.1-SNAPSHOT</version>
	</dependency>
```

Enable the JSweet transpiler plugin for the preferred phase (here, generate-sources):
```xml
<plugin>
	<groupId>org.jsweet</groupId>
	<artifactId>jsweet-maven-plugin</artifactId>
	<version>3.0.0</version>
	<configuration>
		<outDir>javascript</outDir>
		<targetVersion>ES6</targetVersion>
	</configuration>
	<executions>
		<execution>
			<id>generate-js</id>
			<phase>generate-sources</phase>
			<goals>
				<goal>jsweet</goal>
			</goals>
		</execution>
	</executions>
</plugin>
```

The configuration options of the plugin:

Name     |    Type       | Values | Default
-------- | ------------- | ------ | -------
targetVersion | enum | ES3, ES5, ES6 | ES3
module | enum | Any of the ModuleKind enum's values (e.g. none, commonjs, umd, es2015). | none
moduleResolution | enum | The module resolution strategy (classic, node). | classic
outDir | string | JS files output directory | target/js
tsOut | string | Specify where to place generated TypeScript files. | target/ts
tsOnly | boolean | Do not compile the TypeScript output (let an external TypeScript compiler do so). | false
tsserver | boolean | Faster ts2js transpilation using tsserver | false
includes | string[] | Java source files to be included | -
excludes | string[] | Source files to be excluded | -
bundle | boolean | Bundle up all the generated code in a single file, which can be used in the browser. The bundle files are called 'bundle.ts', 'bundle.d.ts', or 'bundle.js' depending on the kind of generated code. NOTE: bundles are not compatible with any module kind other than 'none'. | false
sourceMap | boolean | Generate source map files for the Java files, so that it is possible to debug Java files directly with a debugger that supports source maps (most JavaScript debuggers). | true
sourceRoot | string | Specify the location where debugger should locate Java files instead of source locations. Use this flag if the sources will be located at run-time in a different location than that at design-time. The location specified will be embedded in the sourceMap to direct the debugger where the source files will be located. | -
compileSourceRootsOverride | string | Specify other source location/s instead of default source locations. Use this flag if the sources will be generated/preprocessed at build-time to a non standard location. | default compile sources
encoding | string | Force the Java compiler to use a specific encoding (UTF-8, UTF-16, ...). | UTF-8
noRootDirectories | boolean | Skip the root directories (i.e. packages annotated with @jsweet.lang.Root) so that the generated file hierarchy starts at the root directories rather than including the entire directory structure. | false
enableAssertions | boolean | Java 'assert' statements are transpiled as runtime JavaScript checks. | false
verbose | boolean | Turn on general information logging (INFO LEVEL). | false
veryVerbose | boolean | Turn on all levels of logging. | false
jdkHome | string | Set the JDK home directory to be used to find the Java compiler. If not set, the transpiler will try to use the JAVA_HOME environment variable. Note that the expected JDK version is greater or equals to version 8. | ${java.home}
declaration | boolean | Generate the d.ts files along with the js files, so that other programs can use them to compile. | false
dtsOut | string | Specify where to place generated d.ts files when the declaration option is set (by default, d.ts files are generated in the JavaScript output directory - next to the corresponding js files). | outDir
candiesJsOut | string | Specify where to place extracted JavaScript files from candies. | -
ingoreDefinitions | boolean | Ignore definitions from def.* packages, so that they are not generated in d.ts definition files. If this option is not set, the transpiler generates d.ts definition files in the directory given by the tsout option. | false
factoryClassName | string | Use the given factory to tune the default transpiler behavior. | -
header | file | A file that contains a header to be written at the beginning of each generated file. If left unspecified, JSweet will generate a default header. | -
workingDir | directory | The directory JSweet uses to store temporary files such as extracted candies. JSweet uses '.jsweet' if left unspecified. | -
disableSinglePrecisionFloats | boolean | By default, for a target version >=ES5, JSweet will force Java floats to be mapped to JavaScript numbers that will be constrained with ES5 Math.fround function. If this option is true, then the calls to Math.fround are erased and the generated program will use the JavaScript default precision (double precision). | false
extraSystemPath | string | Allow an extra path to be added to the system path. | -
ignoredProblems | string[] | Array of JSweetProblems which won't be reported or cause failure. | -
## Run JSweet ##

Then, just run the maven command line as usual:

```
$ mvn generate-sources -P client
```

## Hot transpilation ##

JSweet maven plugin is now able to watch changes in your JSweet files and transpile them on the fly. Try it with 

```
mvn jsweet:watch
```

## Advanced configuration ##

You can use the plugin with profiles in order to transpile differently several parts of
your application. For instance, a node server and a HTML5 client app:
```xml
<profiles>
		<profile>
			<id>client</id>
			<build>
				<plugins>
					<plugin>
						<groupId>org.jsweet</groupId>
						<artifactId>jsweet-maven-plugin</artifactId>
						<version>3.0.0</version>
						<configuration>
							<outFile>client/out.js</outFile>
							<targetVersion>ES6</targetVersion>
							<includes>
								<include>**/*.java</include>
							</includes>
							<excludes>
								<exclude>**/server/**</exclude>
							</excludes>
						</configuration>
						<executions>
							<execution>
								<id>generate-js</id>
								<phase>generate-sources</phase>
								<goals>
									<goal>jsweet</goal>
								</goals>
							</execution>
						</executions>
					</plugin>
				</plugins>
			</build>
		</profile>
		<profile>
			<id>server</id>
			<build>
				<plugins>
					<plugin>
						<groupId>org.jsweet</groupId>
						<artifactId>jsweet-maven-plugin</artifactId>
						<version>3.0.0</version>
						<configuration>
							<outFile>server/full.js</outFile>
							<module>commonjs</module>
							<targetVersion>ES5</targetVersion>
							<includes>
								<include>**/*.java</include>
							</includes>
							<excludes>
								<exclude>**/app/**</exclude>
							</excludes>
						</configuration>
						<executions>
							<execution>
								<id>generate-js</id>
								<phase>generate-sources</phase>
								<goals>
									<goal>jsweet</goal>
								</goals>
							</execution>
						</executions>
					</plugin>
				</plugins>
			</build>
		</profile>
</profiles>
```

then run the desired profile:
```
$ mvn generate-sources -P client
```

### Use with code generators / preprocessors 


When using some code generators/preprocessors (eg: [Project Lombok](https://projectlombok.org/setup/maven)) the code is not ready for JSweet fully yet (eg: there are custom annotations to handle beforehand).

To let JSweet use a non default source directory, the compileSourceRootsOverride config element can be used, for instance with Lombok:

```xml
<build>                          
    <plugins>
        <plugin>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok-maven-plugin</artifactId>
            <version>1.18.12.0</version>
            <executions>
                <execution>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>delombok</goal>
                    </goals>
                    <configuration>
                        <addOutputDirectory>false</addOutputDirectory>
                        <sourceDirectory>src/main/java</sourceDirectory>
                    </configuration>
                </execution>
            </executions>
        </plugin>
        <plugin>
            <groupId>org.jsweet</groupId>
            <artifactId>jsweet-maven-plugin</artifactId>
            <version>3.0.0</version>
            <configuration>
                <compileSourceRootsOverride>
                    <compileSourceRoot>target/generated-sources/delombok</compileSourceRoot>
                </compileSourceRootsOverride>
                <sourceRoot>target/generated-sources/delombok</sourceRoot>
                <outDir>javascript</outDir>
                <targetVersion>ES5</targetVersion>
            </configuration>
            <executions>
                <execution>
                    <id>generate-js</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>jsweet</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```
