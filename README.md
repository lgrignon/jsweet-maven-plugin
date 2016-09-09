# JSweet maven plugin usage

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
	<version>1.2.0-SNAPSHOT</version>
	<configuration>
		<outDir>javascript</outDir>
		<targetVersion>ES3</targetVersion>
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

Name     |    Type       | Values | Default | Example
-------- | ------------- | ------ | ------- | -------
targetVersion | enum | ES3, ES5, ES6 | ES3 | ``` <targetVersion>ES3</targetVersion> ```
module | enum | commonjs, amd, system, umd | none | ```<module>commonjs</module>```
outDir | string | JS files output directory | .jsweet/js | ```<outDir>js</outDir>```
tsOut | string | Temporary TypeScript output directory | .jsweet/ts | ```<tsOut>temp/ts</tsOut>```
tsOnly | boolean | If true, JSweet will not generate any JavaScript | false | ```<tsOnly>true</tsOnly>```
includes | string[] | Java source files to be included | N/A | ```<includes><include>**/*.java</include></includes>```
excludes | string[] | Source files to be excluded | N/A | ```<excludes><exclude>**/lib/**</exclude></excludes>```
bundle | boolean | Concats all JS file into one bundle | false |   ```<bundle>true</bundle>```
bundlesDirectory | string | JS bundles output directory | N/A | ```<bundlesDirectory>js/dist</bundlesDirectory>```
sourceMap | boolean | In-browser debug mode - true for java, typescript else | true | ```<sourceMap>true</sourceMap>```
encoding | string | Java files encoding | UTF-8 | ```<encoding>ISO-8859-1</encoding>```
noRootDirectories | boolean | Output is relative to @jsweet.lang.Root package's directories | false | ```<noRootDirectories>true</noRootDirectories>```
enableAssertions | boolean | Java assert statements are transpiled as JS check | false | ```<enableAssertions>true</enableAssertions>```
verbose | boolean | Verbose transpiler output | false | ```<verbose>true</verbose>```
jdkHome | string | Alternative JDK >= 8 directory, for instance if running Maven with a JRE | ${java.home} | ```<jdkHome>/opt/jdk8</jdkHome>```
declaration | boolean | Generates TypeScript d.ts | false | ```<declaration>true</declaration>```
dtsOut | string | TypeScript d.ts output directory when the declaration option is true | outDir | ```<dtsOut>typings</dtsOut>```
candiesJsOut | string | Directory where to extract candies' Javascript |  | ```<candiesJsOut>www/js/candies</candiesJsOut>```
definitions | boolean | Generates only definitions from def.* packages in d.ts definition files, in the tsOut directory (do not confuse with the 'declaration' option) | false | ```<definitions>true</definition>```
disableJavaAddons | boolean | Disables Java-specific code generation behavior (for advanced users only) | false | ```<disableJavaAddons>true</disableJavaAddons>```

Then, just run the maven command line as usual:

```
$ mvn generate-sources -P client
```

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
						<version>1.0.0-SNAPSHOT</version>
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
						<version>1.0.0-SNAPSHOT</version>
						<configuration>
							<outFile>server/full.js</outFile>
<!-- 							<outDir>server</outDir> -->
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




