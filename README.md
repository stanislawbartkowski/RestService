# RestService
Javadoc : https://stanislawbartkowski.github.io/javadoc/RestService/

https://hoteljavaopensource.blogspot.com/2020/02/simple-restservice-library.html

# Installation

>git clone https://github.com/stanislawbartkowski/RestService.git<br>
>cd RestService<br>
> mvn clean package<br>
>./mvndeploy.sh<br>

It adds RestService jar as a Maven dependency in the local repository.

Deploy to Maven github<br>

> mvn deploy<br>


# Maven dependency
```XML
<dependency>
     <groupId>com.restservice</groupId>
    <artifactId>restservice</artifactId>
     <version>1.0</version>
     <scope>jar</scope>
</dependency>

```

# Resolve dependency using GitHub Maven

> pom.xml
```XML
  <repositories>
        <repository>
            <id>github</id>
            <name>GitHub stanislawbartkowski Apache Maven Packages</name>
            <url>https://maven.pkg.github.com/stanislawbartkowski/RestService/</url>
        </repository>
    </repositories>
```

Access to GitHub Maven requires authentication. Specify credentials in mvn settings.<br>
> vi ~/.m2/settings.xml
> 
```XML
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                      http://maven.apache.org/xsd/settings-1.0.0.xsd">

  <activeProfiles>
    <activeProfile>github</activeProfile>
  </activeProfiles>

  <servers>
    <server>
      <id>github</id>
      <username>you user name</username>
      <password>your GitHub token</password>
    </server>
  </servers>
</settings>

```


# Practical example

https://github.com/stanislawbartkowski/MockRestService


