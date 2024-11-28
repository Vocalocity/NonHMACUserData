### About
Crawls Kibana to collect information on user who are using HDAP API with non-HMAC credentials. App collects data in batches of 2 hour and for a period of 2 weeks. Once the data collection is over the is segregated into external and internal server users.

### Required
a. Maven </br>
b. Java

### APIs
1. Update Kibana Cookie: </br>
**cURL**: curl --location 'http://<HOST>/crawler/headers' \
   --header 'Content-Type: application/json' \
   --data '{
   "cookie": <COOKIE> 
   "osdVersion" : <VERSION>,
   "osdXsrf":"osd-fetch"
   }' </br>
**Use**: Use to update the Kibana cookie once it expires.</br>
**Response**: NIL</br>
2. Start Data collection:</br>
   **cURL**: curl --location 'http://<HOST>/api/nonhmacdata' \
   --header 'Content-Type: application/json' \
   --data '{
   "start" : <START_DATE>,
   "end" : <END_DATE>   
   }' </br>
   **Use**: Starts the data collection from specified till the end date. </br>
   **Response**: Last date till the application collected the data it failed at Unauthorized exception. </br>
3. Combine Data:</br>
   **cURL**: curl --location 'http://<HOST>/api/nonhmacdata/combine' </br>
   **Use**: Combines the data collected by *2nd* API. Remove duplicated and segregated data into internal and external sources.</br>
   **Response**: NIL </br>

