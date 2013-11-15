# Salesforce WSDL Maven Plugin

## Description

Download WSDL files directly from salesforce.com by just providing your username
and password.

## Goals

### download

The default lifecycle phase is `initialize`.

#### Available parameters

* #### useSandbox

  Set to true if the authorization server to use is the sandbox one
  (`https://test.salesforce.com`).  
  Default to false for production/development authorization server
  (`https://login.salesforce.com`).

  - **Property:** `sfdc.useSandbox`
  - **Required:** Yes


* #### cookiesDirectory

  Path to the directory where the cookies are stored.

  The default path is `${basedir}/cookies`.

  - **Property:** `sfdc.cookiesDirectory`
  - **Required:** Yes

* #### filename

  Name of the file to override the default one provided by the resource
  server by the time of the download. (e.g. `partner.wsdl`)

  - **Property:** `sfdc.wsdl.filename`

* #### outputDirectory

  Location of the file.

  The default location is `${basedir}/src/main/wsdl`.

  - **Property:** `sfdc.wsdl.outputDirectory`
  - **Required:** Yes

* #### password

  Password (without the security token) to connect to the salesforce.com
  organization.

  - **Property:** `sfdc.password`
  - **Required:** Yes

* #### username

  Email address based username to connect to the salesforce.com organization.

  - **Property:** `sfdc.username`
  - **Required:** Yes

* #### wsdlUri

  Relative URI of the WSDL.

  The default URI is `soap/wsdl.jsp`.  
  For an Apex class based Web Service it can be
  `services/wsdl/class/MyApexClassName`.

  - **Property:** `sfdc.wsdl.uri`
  - **Required:** Yes

#### Examples of Usage

* #### POM

  In your `pom.xml` use the plugin as follow:

  ```XML
  <build>
    ...
    <plugins>
      ...
    <plugin>
      <groupId>vitkin.sfdc</groupId>
      <artifactId>wsdl-maven-plugin</artifactId>
      <version>1.0</version>
      <configuration>
        <cookiesDirectory>${basedir}/my-cookies</cookiesDirectory>
        <!--<useSandbox>false</useSandbox>-->
        <outputDirectory>${basedir}/src/main/soap</outputDirectory>
        <wsdlUri>soap/wsdl.jsp?notimestamp=1&amp;type=*&amp;extended=1&amp;email=1</wsdlUri>
        <filename>enterprise-extended-email.wsdl</filename>
        <!--<username>user.name@domain.tld</username>-->
        <!--<password>123456</password>-->
      </configuration>
      <executions>
        <execution>
          <!--<id>default</id>-->
          <!--<phase>initialize</phase>-->
          <goals>
            <goal>download</goal>
          </goals>
        </execution>
        <execution>
          <id>partner</id>
          <configuration>
          <wsdlUri>soap/wsdl.jsp?notimestamp=1</wsdlUri>
          <!--<filename>partner.wsdl</filename>-->
          </configuration>
          <goals>
            <goal>download</goal>
          </goals>
        </execution>
          <execution>
          <id>sandbox-MyApexClassName</id>
          <configuration>
          <wsdlUri>services/wsdl/class/MyApexClassName</wsdlUri>
          <!--<filename>MyApexClassName.wsdl</filename>-->
          </configuration>
          <goals>
            <goal>download</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
    ...
    </plugins>
    ...
  </build>
  ...
  <properties>
    ...
    <sfdc.username>user.name@domain.tld</sfdc.username>
    <sfdc.password>123456</sfdc.password>
    ...
  </properties>
  ```

* #### CLI

  Example of invocation for the default `partner.wsdl`:
  ```bash
  mvn vitkin.sfdc:wsdl-maven-plugin::download \
      -Dsfdc.username=user.name@domain.tld \
      -Dsfdc.password=123456
  ```

## Integration test

Call the `run-it` profile:
```bash
mvn -P run-it \
    -Dsfdc.username=user.name@domain.tld \
    -Dsfdc.password=123456
```

---
> ## Notes
>
> ### Inspiration
> The project inspiration came while experimenting with cURL and
> eventually obtaining the below result based on only two commands:
>  1. Log in:
>     ```bash
>     curl https://login.salesforce.com/ \
>          -c cookie-jar.txt \
>          -d "un=user.name%40domain.tld" \
>          -d "pw=123456" \
>          -v
>     ```
>
>  2. Get the WSDL:
>     ```bash
>     curl https://login.salesforce.com/soap/wsdl.jsp \
>          -b cookie-jar.txt \
>          -L \
>          -v
>     ```
>
> Actually thanks to the redirection (`-L`) the WSDL request can be based on
> the authorization server URL (i.e. `https://login.salesforce.com` or
> `https://test.salesforce.com`) and for which the response will anyway redirect
> to the resource server based URL (i.e. `https://na#.salesforce.com`,
> `https://eu#.salesforce.com`, `https://cs#.salesforce.com`, etc).
>
> It is anyway preferable to directly use the resource server based URL to get
> the WSDL since some problems related to network availabilities can be 
> encountered with the authorization servers.
>
> ### Cookies
> For convenience cookies are stored in XML format so they can be reviewed and
> even edited. 
>
> ### Avoid unnecessary logging in
> Actually among the cookies in the `cookie-jar.txt` you have one named
> `sid` which domain matches the resource server and which value is your
> Session ID and if the underlying session has not yet expired, then you don't
> need to log in again and you can directly just use the second command with
> the resource based URL.
>
> To determine if you need to log in again, the code relies on the expiration
> date of the cookie named `oid` which domain also matches the resource server.
>
> ### Public IP activation
> In case if your public IP needs activation (e.g. first time connection to
> Salesforce server) then an error message is displayed with the activation URL
> to be opened with a browser behind the same public IP.  
> Just follow on the page displayed in the browser the instructions for
> activation and once done run the build again.
>
> ### Avoid public IP activation
> Having the cookies stored not only permit to avoid to log in at first
> execution of the goal but also avoid the activation in case if your public IP
> has changed.  
> Indeed among the cookies the one called `clientSrc` contains the previous
> public IP that has been authorized and Salesforce server uses it to
> automatically activate your new public IP.
