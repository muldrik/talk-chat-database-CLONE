### Run registry with
    ./gradlew :registry:run
    
### Run client (example)
    ./gradlew :client:run --args='name=myName protocol=HTTP registry=http://0.0.0.0:8088 port=8080' --console=plain
    
    ./gradlew :client:run --args='name=user2 protocol=WEBSOCKET registry=http://0.0.0.0:8088 port=8082' --console=plain
   
##### To change the database modify 
    registry/resources/application.conf
    
```
ktor {
    deployment {
        ...
        database = memory
        //OR
        database = sql
        database_path = {PATH}
        test_database_path = {PATH}
        // default path: ./build/usersDatabase
        // default test path: ./build/testUsersDatabase
        //PATH begins in the folder "registry"
        ...
    }
    ...
}
```

Run every client with different name and port
