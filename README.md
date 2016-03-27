This is a fork of the JSweet Maven plugin .

Keep in mind that this is not the original version .
All the functionalities are experimental and could change at any time .
Do not expect any support , otherwise use the original version at [https://github.com/lgrignon/jsweet-maven-plugin](https://github.com/lgrignon/jsweet-maven-plugin)

This fork introduce a new mojo called "jetty-watch" which launch
the jsweet transpiler and a jetty server in a parallel way .
The mojo listen for file change and call the transpiler or reload the jetty instance on demands.

# Plugin usage

```
$  mvn clean install jsweet:jetty-watch
```

A complete example can be found at :

[https://github.com/EPOTH/jsweet-jersey-backend-example](https://github.com/EPOTH/jsweet-jersey-backend-example)




