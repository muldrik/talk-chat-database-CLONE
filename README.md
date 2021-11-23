# Talk chat

The project implements a simple chat system includin

 * user registry service (HTTP REST API)
 * client app supporting HTTP, UDP or WEBSOCKET
  
## Server (`registry` subproject)

Powered by [Ktor](https://ktor.io/)

The service supports the following interface:

 *  User registration 
    ```
    POST /v1/users
    { "user" : "<name>", "address" : { "protocol" : "<HTTP | WEBSOCKET | UDP>", "host": "<host or ip>", "port": "<port>" } }
    ```
    If sucessful return `200 OK`
    ```
    { "status" : "ok" } 
    ```
    If the user already exists - `409 Conflict`
    Username must not be empty and should consist of [a-zA-Z0-9-_.]. Invalid username returns - `400 Bad Request` 

 *  User update 
    ```
    PUT /v1/users/{user}
    { "protocol" : "<HTTP | WEBSOCKET | UDP>", "host": "<host or ip>", "port": "<port>" }
    ```
    On success return `200 OK`
    ```
    { "status" : "ok" }     
    ```
    If the user does not exist - create a new one
    
    Username must not be empty and should consist of [a-zA-Z0-9-_.]. Invalid username returns - `400 Bad Request` 

 *  Retreiving the user list
    ```
    GET /v1/users/
    ```
    On success returns `200 OK`. Example:
    ```
    {
      "ws1" : {
        "protocol" : "WEBSOCKET",
        "host" : "127.0.0.1",
        "port" : 8083
      },
      "udp2" : {
        "protocol" : "UDP",
        "host" : "127.0.0.1",
        "port" : 3002
      },
      "http1" : {
        "protocol" : "HTTP",
        "host" : "127.0.0.1",
        "port" : 8080
      }
    }
    ```

 *  Deleting a user
    ```
    DELETE /v1/users/{user}
    ```
    On success return `200 OK`
    ```
    { "status" : "ok" }     
    ```

### Local database
By default the user data is stored in-memory, this can be changed to store data in an SQL database by changing config file (example in DOC.md)


### Tests

`./gradlew :registry:test` to run server tests

`./gradlew :client:test` to run client app tests

Note: every 3 minutes /v1/health is queried. Clients not responding 3 times in a row are automatically deleted

## Client app (`client` subproject)

CLI chat with the following commands 

* `:update` - Update the available user list quering the registry
* `:user <user>` - Choose a user for sending messages
* `<text>` - Send a message to the selected user
             Error, if user isn't found
* `:exit` - Exit, deleting the client from the register
  

#### Examples to running and configuring can be found in DOC.md

