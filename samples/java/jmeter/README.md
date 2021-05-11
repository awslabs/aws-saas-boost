# Load Testing the Application

## Running load across all tenants
1. Install Jmeter
1. Tenant-Load-From-SaaS-Boost.jmx will get tenant urls from SaaS Boost 
and hit the different end points of the application
1. To run from command line execute command first get the PUBLIC API URL for SaaS Boost from the console 
by logging into SaaS Boost and going to Settings
1. Use that API URL (without the https and the /v1) along with the user name and password for SaaS Boost in this command:
    ```
    jmeter -n -t "Tenant-Load-From-SaaS-Boost.jmx" -JAPI_URL=<public_api_url> -JSAAS_BOOST_USER=<user> -JSAAS_BOOST_PASSWORD=<password>
    
   For Example:
    jmeter -n -t "Tenant-Load-From-SaaS-Boost.jmx" -JAPI_URL=0123456789.execute-api.us-west-2.amazonaws.com -JSAAS_BOOST_USER=admin -JSAAS_BOOST_PASSWORD=your_password
    ```
1. If you want to run more threads or take a look at the test plan open it up in Jmeter GUI.

## Running load across subset of tenants
1. To run load against specific tenants, use Tenant-Load-From-URL-File.jmx
1. First modify the tenant-urls.txt file with the tenant URLs to test against
1. Run jmeter from commmand in the directory where the tenant-urls.txt is located:
    ```
    jmeter -n -t "Tenant-Load-From-URL-File.jmx"
    ```
