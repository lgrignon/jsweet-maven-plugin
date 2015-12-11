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
	<version>1.0.0-SNAPSHOT</version>
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
targetVersion | enum | ES3, ES5, ES6 | ES3 | test


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